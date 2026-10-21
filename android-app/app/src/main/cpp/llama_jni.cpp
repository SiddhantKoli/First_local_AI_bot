// JNI bridge between Kotlin and llama.cpp's C API.
//
// Every llama_* call below is checked against include/llama.h at the tag pinned
// in scripts/fetch-llama-cpp.sh (b11799). This is a thin wrapper: no llama.cpp
// headers are re-declared, no API is guessed, and no third-party binding sits in
// between. Keep the return codes in sync with LlmEngine.kt.

#include <jni.h>
#include <android/log.h>
#include <llama.h>

#include <algorithm>
#include <atomic>
#include <string>
#include <vector>

#define LOG_TAG "LlamaJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int32_t RESULT_OK = 0;
constexpr int32_t RESULT_NOT_LOADED = -1;
constexpr int32_t RESULT_PROMPT_TOO_LONG = -2;
constexpr int32_t RESULT_DECODE_FAILED = -3;
constexpr int32_t RESULT_STOPPED = -4;
constexpr int32_t RESULT_CALLBACK_FAILED = -5;

// Matches llama_context_params.n_batch: llama_decode() rejects larger batches.
constexpr int32_t BATCH_SIZE = 256;

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int32_t n_ctx = 0;
    std::atomic<bool> cancelled{false};
};

Session * as_session(jlong handle) {
    return reinterpret_cast<Session *>(handle);
}

void throw_illegal_state(JNIEnv * env, const char * message) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
        env->DeleteLocalRef(clazz);
    }
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text) {
    // A negative return is the required buffer size.
    int32_t count = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), nullptr, 0, true, false);
    if (count == 0) return {};
    if (count < 0) count = -count;

    std::vector<llama_token> tokens(count);
    const int32_t written = llama_tokenize(
            vocab, text.c_str(), (int32_t) text.size(), tokens.data(), count, true, false);
    if (written < 0) return {};
    tokens.resize(written);
    return tokens;
}

// Bytes of the longest complete UTF-8 sequence at the start of `text`.
// A token can end mid-character, so pieces are buffered until they are valid.
size_t utf8_complete_prefix(const std::string & text) {
    size_t i = 0;
    while (i < text.size()) {
        const auto lead = static_cast<unsigned char>(text[i]);
        size_t length = 1;
        if (lead >= 0xF0) length = 4;
        else if (lead >= 0xE0) length = 3;
        else if (lead >= 0xC0) length = 2;
        if (i + length > text.size()) return i;
        i += length;
    }
    return i;
}

struct ProgressBridge {
    JNIEnv * env = nullptr;
    jobject sink = nullptr;
    jmethodID method = nullptr;
};

// llama_progress_callback: return false to abort loading.
bool on_load_progress(float progress, void * user_data) {
    auto * bridge = static_cast<ProgressBridge *>(user_data);
    if (bridge == nullptr || bridge->sink == nullptr || bridge->method == nullptr) return true;
    const jboolean keep = bridge->env->CallBooleanMethod(bridge->sink, bridge->method, (jfloat) progress);
    if (bridge->env->ExceptionCheck()) {
        bridge->env->ExceptionClear();
        return true;
    }
    return keep == JNI_TRUE;
}

// Sends already-valid UTF-8 bytes to Kotlin. Bytes, not String, so multi-byte
// characters never hit JNI's modified-UTF-8 limits.
bool emit(JNIEnv * env, jobject sink, jmethodID method, const std::string & text) {
    if (text.empty()) return true;
    jbyteArray bytes = env->NewByteArray((jsize) text.size());
    if (bytes == nullptr) {
        env->ExceptionClear();
        return false;
    }
    env->SetByteArrayRegion(bytes, 0, (jsize) text.size(),
                            reinterpret_cast<const jbyte *>(text.data()));
    const jboolean keep = env->CallBooleanMethod(sink, method, bytes);
    env->DeleteLocalRef(bytes);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    return keep == JNI_TRUE;
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_sainiksahayak_app_llm_LlamaNative_backendInit(JNIEnv *, jobject) {
    // Only initialises ggml/backend registries; cheap and idempotent.
    llama_backend_init();
}

JNIEXPORT jlong JNICALL
Java_com_sainiksahayak_app_llm_LlamaNative_loadModel(
        JNIEnv * env, jobject, jstring jpath, jint context_size, jint thread_count, jobject jprogress) {
    const char * path_chars = env->GetStringUTFChars(jpath, nullptr);
    if (path_chars == nullptr) return 0;
    const std::string path(path_chars);
    env->ReleaseStringUTFChars(jpath, path_chars);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU only: this build has no GPU backend

    ProgressBridge bridge;
    if (jprogress != nullptr) {
        jclass sink_class = env->GetObjectClass(jprogress);
        const jmethodID method = env->GetMethodID(sink_class, "onProgress", "(F)Z");
        env->DeleteLocalRef(sink_class);
        if (method != nullptr) {
            bridge = ProgressBridge{env, jprogress, method};
            model_params.progress_callback = on_load_progress;
            model_params.progress_callback_user_data = &bridge;
        }
    }

    llama_model * model = llama_model_load_from_file(path.c_str(), model_params);
    if (model == nullptr) {
        LOGE("llama_model_load_from_file failed for %s", path.c_str());
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (uint32_t) context_size;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = thread_count;
    ctx_params.n_threads_batch = thread_count;
    ctx_params.no_perf = true;

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("llama_init_from_model failed (n_ctx=%d)", context_size);
        llama_model_free(model);
        return 0;
    }

    auto * session = new Session();
    session->model = model;
    session->ctx = ctx;
    session->vocab = llama_model_get_vocab(model);
    session->n_ctx = (int32_t) ctx_params.n_ctx;
    LOGI("model loaded: n_ctx=%d threads=%d", session->n_ctx, thread_count);
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_sainiksahayak_app_llm_LlamaNative_freeModel(JNIEnv *, jobject, jlong handle) {
    Session * session = as_session(handle);
    if (session == nullptr) return;
    if (session->ctx != nullptr) llama_free(session->ctx);
    if (session->model != nullptr) llama_model_free(session->model);
    delete session;
}

JNIEXPORT jbyteArray JNICALL
Java_com_sainiksahayak_app_llm_LlamaNative_applyTemplate(
        JNIEnv * env, jobject, jlong handle, jobjectArray roles, jobjectArray contents) {
    Session * session = as_session(handle);
    if (session == nullptr) {
        throw_illegal_state(env, "No model is loaded");
        return nullptr;
    }
    const char * tmpl = llama_model_chat_template(session->model, nullptr);
    if (tmpl == nullptr) {
        throw_illegal_state(env, "This GGUF has no chat template. Use an Instruct model.");
        return nullptr;
    }

    const jsize count = std::min(env->GetArrayLength(roles), env->GetArrayLength(contents));
    std::vector<std::string> storage; // keeps role/content bytes alive
    storage.reserve((size_t) count * 2);
    for (jsize i = 0; i < count; ++i) {
        auto jrole = (jstring) env->GetObjectArrayElement(roles, i);
        auto jcontent = (jstring) env->GetObjectArrayElement(contents, i);
        const char * role = env->GetStringUTFChars(jrole, nullptr);
        const char * content = env->GetStringUTFChars(jcontent, nullptr);
        storage.emplace_back(role != nullptr ? role : "user");
        storage.emplace_back(content != nullptr ? content : "");
        if (role != nullptr) env->ReleaseStringUTFChars(jrole, role);
        if (content != nullptr) env->ReleaseStringUTFChars(jcontent, content);
        env->DeleteLocalRef(jrole);
        env->DeleteLocalRef(jcontent);
    }

    std::vector<llama_chat_message> messages;
    messages.reserve(storage.size() / 2);
    for (size_t i = 0; i + 1 < storage.size(); i += 2) {
        messages.push_back({storage[i].c_str(), storage[i + 1].c_str()});
    }

    // First call measures, second fills. add_ass=true opens the assistant turn.
    int32_t needed = llama_chat_apply_template(
            tmpl, messages.data(), messages.size(), true, nullptr, 0);
    if (needed <= 0) {
        throw_illegal_state(env, "llama_chat_apply_template failed");
        return nullptr;
    }
    std::string formatted((size_t) needed, '\0');
    const int32_t written = llama_chat_apply_template(
            tmpl, messages.data(), messages.size(), true, formatted.data(), needed);
    if (written < 0) {
        throw_illegal_state(env, "llama_chat_apply_template failed");
        return nullptr;
    }
    formatted.resize(std::min<int32_t>(written, needed));

    // Returned as UTF-8 bytes: JNI strings are modified UTF-8, which cannot
    // represent 4-byte sequences. Kotlin converts with String(bytes, UTF_8).
    jbyteArray bytes = env->NewByteArray((jsize) formatted.size());
    if (bytes == nullptr) {
        env->ExceptionClear();
        throw_illegal_state(env, "Out of memory while building the prompt");
        return nullptr;
    }
    env->SetByteArrayRegion(bytes, 0, (jsize) formatted.size(),
                            reinterpret_cast<const jbyte *>(formatted.data()));
    return bytes;
}

JNIEXPORT jint JNICALL
Java_com_sainiksahayak_app_llm_LlamaNative_generate(
        JNIEnv * env, jobject, jlong handle, jstring jprompt, jint max_tokens,
        jfloat temperature, jfloat top_p, jint top_k, jobject sink) {
    Session * session = as_session(handle);
    if (session == nullptr) return RESULT_NOT_LOADED;
    if (sink == nullptr) return RESULT_CALLBACK_FAILED;

    const char * prompt_chars = env->GetStringUTFChars(jprompt, nullptr);
    if (prompt_chars == nullptr) return RESULT_DECODE_FAILED;
    const std::string prompt(prompt_chars);
    env->ReleaseStringUTFChars(jprompt, prompt_chars);

    std::vector<llama_token> tokens = tokenize(session->vocab, prompt);
    if (tokens.empty()) {
        LOGE("tokenize returned no tokens");
        return RESULT_DECODE_FAILED;
    }
    if ((int32_t) tokens.size() + 8 >= session->n_ctx) {
        LOGE("prompt too long: %zu tokens, context %d", tokens.size(), session->n_ctx);
        return RESULT_PROMPT_TOO_LONG;
    }

    session->cancelled.store(false);
    llama_memory_clear(llama_get_memory(session->ctx), true);

    llama_sampler_chain_params chain_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(chain_params);
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    jclass sink_class = env->GetObjectClass(sink);
    const jmethodID on_token = env->GetMethodID(sink_class, "onToken", "([B)Z");
    env->DeleteLocalRef(sink_class);
    if (on_token == nullptr) {
        llama_sampler_free(sampler);
        return RESULT_CALLBACK_FAILED;
    }

    int32_t result = RESULT_OK;

    // Evaluate the prompt in n_batch-sized slices. Positions are tracked by
    // llama_decode, so the slices simply continue where the previous one ended.
    for (size_t offset = 0; offset < tokens.size(); offset += BATCH_SIZE) {
        const auto count = (int32_t) std::min<size_t>(BATCH_SIZE, tokens.size() - offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, count);
        if (llama_decode(session->ctx, batch) != 0) {
            LOGE("llama_decode failed on prompt slice at %zu", offset);
            result = RESULT_DECODE_FAILED;
            break;
        }
    }

    std::string pending;
    if (result == RESULT_OK) {
        const int32_t max_new = std::min<int32_t>(
                max_tokens, session->n_ctx - (int32_t) tokens.size() - 1);

        for (int32_t i = 0; i < max_new; ++i) {
            if (session->cancelled.load()) {
                result = RESULT_STOPPED;
                break;
            }

            // Samples and accepts in one call (see llama.h).
            const llama_token id = llama_sampler_sample(sampler, session->ctx, -1);
            if (llama_vocab_is_eog(session->vocab, id)) break;

            char piece[256];
            const int32_t piece_len = llama_token_to_piece(
                    session->vocab, id, piece, (int32_t) sizeof(piece), 0, false);
            if (piece_len > 0) pending.append(piece, (size_t) piece_len);

            const size_t complete = utf8_complete_prefix(pending);
            if (complete > 0) {
                const std::string text = pending.substr(0, complete);
                pending.erase(0, complete);
                if (!emit(env, sink, on_token, text)) {
                    result = RESULT_CALLBACK_FAILED;
                    break;
                }
            }

            llama_token next = id;
            llama_batch batch = llama_batch_get_one(&next, 1);
            if (llama_decode(session->ctx, batch) != 0) {
                LOGE("llama_decode failed at token %d", i);
                result = RESULT_DECODE_FAILED;
                break;
            }
        }

        // Flush a trailing partial sequence (never happens with valid UTF-8).
        if (!pending.empty() && result == RESULT_OK) {
            emit(env, sink, on_token, pending);
        }
    }

    llama_sampler_free(sampler);
    return result;
}

JNIEXPORT void JNICALL
Java_com_sainiksahayak_app_llm_LlamaNative_cancel(JNIEnv *, jobject, jlong handle) {
    Session * session = as_session(handle);
    if (session != nullptr) session->cancelled.store(true);
}

JNIEXPORT void JNICALL
Java_com_sainiksahayak_app_llm_LlamaNative_resetContext(JNIEnv *, jobject, jlong handle) {
    Session * session = as_session(handle);
    if (session != nullptr) llama_memory_clear(llama_get_memory(session->ctx), true);
}

} // extern "C"

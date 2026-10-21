package com.sainiksahayak.app.llm

/**
 * Direct JNI bridge to llama.cpp's C API.
 *
 * The native side (src/main/cpp/llama_jni.cpp) is compiled against the llama.cpp
 * headers at the tag pinned in scripts/fetch-llama-cpp.sh, so the signatures here
 * match that version exactly. There is no third-party wrapper library involved:
 * the C API is the maintained interface, and we only add the JNI glue.
 */
internal object LlamaNative {
    init {
        System.loadLibrary("llamajni")
    }

    /** llama_backend_init(); safe to call more than once. */
    external fun backendInit()

    /** Returns an opaque session handle, or 0 on failure. */
    external fun loadModel(
        path: String,
        contextSize: Int,
        threadCount: Int,
        progress: LoadProgress?,
    ): Long

    external fun freeModel(handle: Long)

    /** Formats messages with the GGUF's own chat template (llama_chat_apply_template). */
    external fun applyTemplate(handle: Long, roles: Array<String>, contents: Array<String>): ByteArray

    /**
     * Generates tokens, calling [sink] with UTF-8 text as it is produced.
     * Returns 0 on success, or a negative error code (see [LlmEngine]).
     */
    external fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        sink: TokenSink,
    ): Int

    /** Asks a running [generate] call to stop at the next token. */
    external fun cancel(handle: Long)

    /** Drops the KV cache so the next prompt starts from an empty context. */
    external fun resetContext(handle: Long)
}

/** Implemented in Kotlin, called from native code once per token. */
fun interface TokenSink {
    /**
     * @param utf8 one or more complete UTF-8 sequences (a token may end mid-character,
     *             so the native side buffers until the bytes are valid).
     * @return false to stop generation.
     */
    fun onToken(utf8: ByteArray): Boolean
}

/** llama.cpp model-loading progress; return false to abort the load. */
fun interface LoadProgress {
    fun onProgress(fraction: Float): Boolean
}

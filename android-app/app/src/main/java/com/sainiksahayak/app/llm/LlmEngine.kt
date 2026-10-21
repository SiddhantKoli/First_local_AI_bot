package com.sainiksahayak.app.llm

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ModelState {
    data object NoModel : ModelState
    data class Importing(val progress: Float) : ModelState
    data class Loading(val progress: Float = 0f) : ModelState
    data class Ready(val name: String) : ModelState
    data class Failed(val message: String) : ModelState
}

/** One message for the chat template: role is "system", "user" or "assistant". */
data class ChatTurn(val role: String, val content: String)

/**
 * Owns the native session and serialises generation.
 *
 * Generation is blocking inside llama.cpp, so it runs on Dispatchers.Default and
 * streams tokens through a channel. Only one generation may run at a time: the
 * KV cache and sampler belong to a single session.
 */
class LlmEngine(context: Context) {

    private val store = ModelStore(context)
    private val _state = MutableStateFlow<ModelState>(ModelState.NoModel)
    val state = _state.asStateFlow()

    private val generateLock = Mutex()
    @Volatile
    private var handle: Long = 0L
    private var backendReady = false

    /** Loads the previously imported model, if any. Called once at startup. */
    suspend fun restore() {
        if (!store.exists()) {
            _state.value = ModelState.NoModel
            return
        }
        load(store.file)
    }

    /** Copies a picked document into app storage, then loads it. */
    suspend fun importModel(uri: Uri) {
        // Wait for an in-flight generation to leave the native session before
        // freeing it; a freed context in the middle of llama_decode is a crash.
        generateLock.withLock { closeSession() }
        _state.value = ModelState.Importing(0f)
        val file = try {
            store.copyFromUri(uri) { progress -> _state.value = ModelState.Importing(progress) }
        } catch (e: Exception) {
            _state.value = ModelState.Failed(e.message ?: "Could not import the model")
            return
        }
        load(file)
    }

    private suspend fun load(file: File) {
        _state.value = ModelState.Loading()
        val newHandle = withContext(Dispatchers.IO) {
            ensureBackend()
            runCatching {
                LlamaNative.loadModel(file.absolutePath, CONTEXT_SIZE, THREADS) { fraction ->
                    // Called on this thread while llama.cpp reads the tensors.
                    _state.value = ModelState.Loading(fraction)
                    true
                }
            }.getOrDefault(0L)
        }
        if (newHandle == 0L) {
            _state.value = ModelState.Failed(
                "The model file could not be loaded. It may be corrupt, or the phone is out of memory."
            )
            return
        }
        closeSession()
        handle = newHandle
        _state.value = ModelState.Ready(store.displayName)
    }

    /**
     * Streams the model's reply, one token at a time.
     *
     * @throws IllegalStateException if no model is loaded or generation fails.
     */
    fun generate(
        turns: List<ChatTurn>,
        maxTokens: Int = MAX_NEW_TOKENS,
        temperature: Float = 0.3f,
        topP: Float = 0.9f,
        topK: Int = 40,
    ): Flow<String> = channelFlow {
        val session = handle
        check(session != 0L) { "No model loaded" }
        val prompt = String(
            LlamaNative.applyTemplate(
                session,
                turns.map { it.role }.toTypedArray(),
                turns.map { it.content }.toTypedArray(),
            ),
            Charsets.UTF_8,
        )

        withContext(Dispatchers.Default) {
            generateLock.withLock {
                val result = LlamaNative.generate(session, prompt, maxTokens, temperature, topP, topK) { token ->
                    // Fails once the collector is cancelled, which stops the native loop.
                    trySend(String(token, Charsets.UTF_8)).isSuccess
                }
                if (result != OK && result != STOPPED) throw IllegalStateException(describe(result))
            }
        }
    }.buffer(Channel.UNLIMITED)

    /** Stops the current generation at the next token boundary. */
    fun stop() {
        val session = handle
        if (session != 0L) LlamaNative.cancel(session)
    }

    /** Frees the KV cache; called after each answer so prompts never accumulate. */
    fun resetContext() {
        val session = handle
        if (session != 0L) LlamaNative.resetContext(session)
    }

    fun closeSession() {
        val session = handle
        handle = 0L
        if (session != 0L) LlamaNative.freeModel(session)
    }

    private fun ensureBackend() {
        if (backendReady) return
        LlamaNative.backendInit()
        backendReady = true
    }

    private fun describe(code: Int): String = when (code) {
        ERR_NOT_LOADED -> "No model is loaded."
        ERR_PROMPT_TOO_LONG -> "This question plus its context does not fit in the model's context window."
        ERR_DECODE -> "llama.cpp could not evaluate the prompt (out of memory?)."
        ERR_CALLBACK -> "The answer was interrupted."
        else -> "llama.cpp error $code"
    }

    private companion object {
        /**
         * 2048 tokens fits the system prompt (~150), four ~800 character chunks
         * (~1000) and the answer. Raising it costs KV-cache RAM: roughly 115 KB
         * per token for Llama 3.2 3B on CPU, i.e. ~235 MB here.
         */
        const val CONTEXT_SIZE = 2048
        const val MAX_NEW_TOKENS = 320

        /** Big cores only; more threads than that just fights for the same cache. */
        val THREADS = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

        // Must match the return codes in llama_jni.cpp.
        const val OK = 0
        const val ERR_NOT_LOADED = -1
        const val ERR_PROMPT_TOO_LONG = -2
        const val ERR_DECODE = -3
        const val STOPPED = -4
        const val ERR_CALLBACK = -5
    }
}

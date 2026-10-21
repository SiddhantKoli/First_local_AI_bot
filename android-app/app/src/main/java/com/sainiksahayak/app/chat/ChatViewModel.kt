package com.sainiksahayak.app.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sainiksahayak.app.knowledge.Bm25Retriever
import com.sainiksahayak.app.knowledge.KnowledgeBase
import com.sainiksahayak.app.knowledge.Retriever
import com.sainiksahayak.app.knowledge.ScoredChunk
import com.sainiksahayak.app.llm.ChatTurn
import com.sainiksahayak.app.llm.LlmEngine
import com.sainiksahayak.app.llm.ModelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val model: ModelState = ModelState.NoModel,
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    /** Short progress hint under the input, e.g. "Searching knowledge base…". */
    val status: String? = null,
)

/**
 * The whole RAG loop for one question:
 *
 *   condense (optional) -> BM25 search -> relevance gate -> prompt -> stream
 *
 * Everything runs on-device. If retrieval finds nothing relevant we answer with
 * the canned refusal instead of letting the model improvise.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = LlmEngine(application)
    private val _ui = MutableStateFlow(ChatUiState())
    val ui = _ui.asStateFlow()

    private var retriever: Retriever? = null
    private var chatJob: Job? = null
    private var nextId = 1L

    init {
        // Mirror the engine's model state into the UI state.
        viewModelScope.launch {
            engine.state.collect { model -> _ui.update { it.copy(model = model) } }
        }
        viewModelScope.launch {
            val chunks = withContext(Dispatchers.IO) { KnowledgeBase.load(application) }
            retriever = Bm25Retriever(chunks)
            engine.restore()
        }
    }

    fun onModelSelected(uri: Uri) {
        stop()
        viewModelScope.launch { engine.importModel(uri) }
    }

    fun send(raw: String) {
        val question = raw.trim()
        val current = _ui.value
        val activeRetriever = retriever
        if (question.isEmpty() || current.busy || activeRetriever == null) return
        if (current.model !is ModelState.Ready) return

        // Snapshot before appending, so the placeholder never enters the prompt.
        val history = current.messages.toTurns()
        val userMessage = ChatMessage(id = nextId++, role = Role.User, text = question)
        val placeholder = ChatMessage(id = nextId++, role = Role.Assistant, streaming = true)
        _ui.update {
            it.copy(
                messages = it.messages + userMessage + placeholder,
                busy = true,
                status = "Thinking…",
            )
        }

        chatJob = viewModelScope.launch {
            try {
                // 1. Follow-ups need context to become a standalone search query.
                val query = if (history.any { it.role == "user" }) {
                    condense(question, history)
                } else {
                    question
                }

                // 2. Retrieve. BM25 is pure CPU and takes well under a millisecond.
                _ui.update { it.copy(status = "Searching knowledge base…") }
                val hits = withContext(Dispatchers.Default) {
                    activeRetriever.search(query, Retriever.DEFAULT_TOP_K)
                }

                // 3. Relevance gate: no good match means no LLM call at all.
                if (!isRelevant(hits)) {
                    updateAssistant { it.copy(text = Prompting.NO_ANSWER) }
                    return@launch
                }

                updateAssistant { it.copy(sources = hits.map(SourceRef::from)) }
                _ui.update { it.copy(status = null) }

                // 4. Generate, streaming tokens into the bubble.
                engine.generate(
                    Prompting.answerTurns(hits, question, history),
                    maxTokens = MAX_ANSWER_TOKENS,
                ).collect { token -> appendToken(token) }
                engine.resetContext()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateAssistant {
                    it.copy(text = error.message ?: "Generation failed", isError = true)
                }
            } finally {
                updateAssistant { it.copy(streaming = false) }
                _ui.update { it.copy(busy = false, status = null) }
            }
        }
    }

    /** Both gates must pass: a decent score *and* real query coverage. */
    private fun isRelevant(hits: List<ScoredChunk>): Boolean {
        val best = hits.firstOrNull() ?: return false
        return best.score >= Bm25Retriever.MIN_SCORE && best.coverage >= Bm25Retriever.MIN_COVERAGE
    }

    /** Stop button: flips the native flag first, then cancels the coroutine. */
    fun stop() {
        engine.stop()
        chatJob?.cancel()
        chatJob = null
        updateAssistant { it.copy(streaming = false) }
        _ui.update { it.copy(busy = false, status = null) }
    }

    fun newChat() {
        stop()
        _ui.update { it.copy(messages = emptyList()) }
    }

    private suspend fun condense(question: String, history: List<ChatTurn>): String {
        val buffer = StringBuilder()
        return try {
            _ui.update { it.copy(status = "Understanding follow-up…") }
            engine.generate(
                Prompting.condenseTurns(history, question),
                maxTokens = MAX_CONDENSE_TOKENS,
                temperature = 0f,
            ).collect { buffer.append(it) }
            engine.resetContext()
            Prompting.sanitizeQuery(buffer.toString(), question)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Condensation is best-effort: fall back to the raw question.
            question
        }
    }

    private fun appendToken(token: String) = updateAssistant { it.copy(text = it.text + token) }

    private fun updateAssistant(transform: (ChatMessage) -> ChatMessage) {
        _ui.update { state ->
            val index = state.messages.indexOfLast { it.role == Role.Assistant }
            if (index < 0) return@update state
            val updated = state.messages.toMutableList()
            updated[index] = transform(updated[index])
            state.copy(messages = updated)
        }
    }

    private fun List<ChatMessage>.toTurns(): List<ChatTurn> =
        filter { it.text.isNotBlank() && !it.isError }
            .map { ChatTurn(if (it.role == Role.User) "user" else "assistant", it.text) }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
        engine.closeSession()
    }

    private companion object {
        const val MAX_ANSWER_TOKENS = 320
        const val MAX_CONDENSE_TOKENS = 96
    }
}

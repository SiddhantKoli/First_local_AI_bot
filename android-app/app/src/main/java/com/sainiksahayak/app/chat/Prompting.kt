package com.sainiksahayak.app.chat

import com.sainiksahayak.app.knowledge.ScoredChunk
import com.sainiksahayak.app.llm.ChatTurn

/**
 * All prompt text lives here so it can be tuned without touching the chat logic.
 *
 * Two different jobs share the model:
 *  1. condensation - turn "what about at night?" into a standalone search query,
 *  2. answering    - write the reply using only the retrieved chunks.
 */
object Prompting {

    /** Exact refusal text, used verbatim by both the prompt and the BM25 cut-off. */
    const val NO_ANSWER = "I don't have information on that in my knowledge base."

    private val FOLLOW_UP_REFERENCE = Regex(
        """\b(it|its|they|them|their|this|that|these|those|there|then|also|instead|same|what about|how about|and what|and how)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun needsCondensation(question: String): Boolean =
        FOLLOW_UP_REFERENCE.containsMatchIn(question)

    private const val ANSWER_RULES = """
You are Sainik Sahayak, an offline assistant for Indian Army personnel.
Answer using ONLY the numbered CONTEXT entries below.

Rules:
1. Use only the CONTEXT. Do not add facts from your own training.
2. If the CONTEXT does not contain the answer, reply exactly: "$NO_ANSWER"
3. Answer the question directly and concisely in at most three short sentences.
4. Do not include source numbers or a Sources line; the app displays sources separately.
"""

    private val CONDENSE_RULES = """
You rewrite a follow-up question into a standalone search query.
Use the conversation to resolve pronouns and ellipsis ("it", "that one", "and at night?").
Output ONE line with the rewritten question and nothing else. No quotes, no explanation.
If the question already makes sense on its own, repeat it unchanged.
""".trimIndent()

    /** Numbered context block: headings are included so the model can cite them. */
    fun contextBlock(hits: List<ScoredChunk>): String = buildString {
        for (hit in hits) {
            val chunk = hit.chunk
            append('[').append(hit.rank).append("] ")
            append(chunk.category)
            chunk.subcategory?.let { append(" > ").append(it) }
            append(": ").append(chunk.topic).append('\n')
            append(chunk.text.trim()).append("\n\n")
        }
    }.trim()

    /**
     * System prompt + up to two previous turns + the question.
     *
     * The previous turns are not extra knowledge: they only let the model follow
     * a follow-up ("what about at night?") that retrieval already resolved.
     */
    fun answerTurns(
        hits: List<ScoredChunk>,
        question: String,
        history: List<ChatTurn> = emptyList(),
    ): List<ChatTurn> {
        val system = ANSWER_RULES.trimIndent() +
            "\nCONTEXT:\n" + contextBlock(hits)
        return buildList {
            add(ChatTurn("system", system))
            addAll(history.takeLast(2))
            add(ChatTurn("user", question))
        }
    }

    fun condenseTurns(history: List<ChatTurn>, question: String): List<ChatTurn> {
        val transcript = history.takeLast(3).joinToString("\n") { turn ->
            "${turn.role}: ${turn.content.take(500)}"
        }
        return listOf(
            ChatTurn("system", CONDENSE_RULES),
            ChatTurn(
                "user",
                "Conversation:\n$transcript\n\nLatest question: $question\n\nStandalone query:",
            ),
        )
    }

    /** Models sometimes add a label or quotes; keep the first useful line only. */
    fun sanitizeQuery(raw: String, fallback: String): String {
        val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return fallback
        val cleaned = line
            .removePrefix("Standalone query:")
            .trim()
            .trim('"', '\'', '`', '*')
            .trim()
        return cleaned.take(300).ifBlank { fallback }
    }
}

package com.sainiksahayak.app.chat

import com.sainiksahayak.app.knowledge.ScoredChunk

enum class Role { User, Assistant }

/** A knowledge entry that was used to answer; shown under the answer. */
data class SourceRef(
    val index: Int,
    val category: String,
    val subcategory: String?,
    val topic: String,
    val score: Double,
) {
    val label: String
        get() = buildString {
            append(category)
            subcategory?.let { append(" › ").append(it) }
            append(" — ").append(topic)
        }

    companion object {
        fun from(hit: ScoredChunk) = SourceRef(
            index = hit.rank,
            category = hit.chunk.category,
            subcategory = hit.chunk.subcategory,
            topic = hit.chunk.topic,
            score = hit.score,
        )
    }
}

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String = "",
    val sources: List<SourceRef> = emptyList(),
    val streaming: Boolean = false,
    val isError: Boolean = false,
)

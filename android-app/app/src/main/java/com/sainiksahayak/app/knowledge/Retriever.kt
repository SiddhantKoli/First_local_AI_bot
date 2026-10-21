package com.sainiksahayak.app.knowledge

/** A chunk plus how well it matched the query (higher is better). */
data class ScoredChunk(
    val chunk: KnowledgeChunk,
    val score: Double,
    val rank: Int,
    /**
     * Share of the query's IDF mass this chunk explains, 0..1.
     * Low coverage means only a common word matched, which is the usual shape
     * of an out-of-scope question; the chat layer uses it as a second gate.
     */
    val coverage: Double = 1.0,
)

/**
 * Anything that can turn a question into ranked chunks.
 *
 * BM25 is the only implementation today: no model, no index file, instant start
 * and fully offline. An embedding retriever can be added later by implementing
 * this interface (see README, "Adding embeddings later") -- the chat layer only
 * depends on [Retriever], never on BM25 directly.
 */
interface Retriever {
    fun search(query: String, topK: Int = DEFAULT_TOP_K): List<ScoredChunk>

    companion object {
        /** Four chunks is roughly 1000 tokens of context: fits the 2048 context. */
        const val DEFAULT_TOP_K = 4
    }
}

package com.sainiksahayak.app.knowledge

import kotlin.math.ln

/**
 * Okapi BM25 over the shipped chunks -- a few hundred documents, so a plain
 * in-memory inverted index is fast (sub-millisecond) and needs no database.
 *
 *   score(q, d) = SUM over terms t of
 *       idf(t) * (tf(t,d) * (k1 + 1)) / (tf(t,d) + k1 * (1 - b + b * |d| / avgdl))
 *
 * k1 controls term-frequency saturation, b controls length normalisation.
 * The topic (and subcategory) are indexed [TOPIC_WEIGHT] extra times because a
 * chunk's heading is a much stronger signal than a word buried in the body.
 *
 * This class is pure Kotlin (no Android APIs) so it is unit-testable on the JVM.
 */
class Bm25Retriever(
    private val chunks: List<KnowledgeChunk>,
    private val k1: Double = 1.5,
    private val b: Double = 0.75,
) : Retriever {

    private class Posting(val doc: Int, val tf: Int)

    private val postings: Map<String, List<Posting>>
    private val idf: Map<String, Double>
    private val docLength: IntArray
    private val avgDocLength: Double

    init {
        require(chunks.isNotEmpty()) { "Bm25Retriever needs at least one chunk" }

        val lengths = IntArray(chunks.size)
        val index = HashMap<String, MutableList<Posting>>()

        for ((i, chunk) in chunks.withIndex()) {
            val tokens = indexTokens(chunk)
            lengths[i] = tokens.size
            // Count term frequency per document in one pass, then add postings.
            for ((term, tf) in tokens.groupingBy { it }.eachCount()) {
                index.getOrPut(term) { ArrayList() }.add(Posting(i, tf))
            }
        }

        docLength = lengths
        avgDocLength = lengths.average()
        postings = index
        val total = chunks.size.toDouble()
        idf = index.mapValues { (_, docs) ->
            ln(1.0 + (total - docs.size + 0.5) / (docs.size + 0.5))
        }
    }

    override fun search(query: String, topK: Int): List<ScoredChunk> {
        val terms = tokenize(query).distinct()
        if (terms.isEmpty()) return emptyList()

        val scores = DoubleArray(chunks.size)
        var queryMass = 0.0
        for (term in terms) {
            val weight = idf[term] ?: continue
            // Upper bound for this term: an infinitely repeated term saturates at (k1 + 1).
            queryMass += weight * (k1 + 1)
            for (posting in postings[term].orEmpty()) {
                val dl = docLength[posting.doc]
                val denominator = posting.tf + k1 * (1 - b + b * dl / avgDocLength)
                scores[posting.doc] += weight * posting.tf * (k1 + 1) / denominator
            }
        }

        return scores.indices
            .filter { scores[it] > 0.0 }
            .sortedByDescending { scores[it] }
            .take(topK)
            .mapIndexed { rank, doc ->
                val coverage = if (queryMass > 0) (scores[doc] / queryMass).coerceIn(0.0, 1.0) else 0.0
                ScoredChunk(chunks[doc], scores[doc], rank + 1, coverage)
            }
    }

    private fun indexTokens(chunk: KnowledgeChunk): List<String> {
        val tokens = ArrayList<String>(256)
        tokens += tokenize(chunk.topic)
        repeat(TOPIC_WEIGHT) { tokens += tokenize(chunk.topic) }
        chunk.subcategory?.let { tokens += tokenize(it) }
        tokens += tokenize(chunk.text)
        return tokens
    }

    companion object {
        /** Heading repeated this many extra times in the index. */
        private const val TOPIC_WEIGHT = 2

        /**
         * Relevance gates, tuned on the real asset (see Bm25RetrieverTest).
         *
         * A single threshold on the raw score is not enough: "best cricket player
         * in the world" scores 7.3 because the word "best" appears in a heading,
         * while a terse but valid "what is a Subedar" scores 7.2. Requiring both a
         * decent score and decent query coverage separates the two.
         */
        const val MIN_SCORE = 6.5
        const val MIN_COVERAGE = 0.40

        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

        private val STOPWORDS = setOf(
            "a", "about", "all", "also", "am", "an", "and", "any", "are", "as", "at", "be",
            "been", "but", "by", "can", "could", "did", "do", "does", "for", "from", "had",
            "has", "have", "he", "her", "here", "him", "his", "how", "i", "if", "in", "into",
            "is", "it", "its", "may", "me", "might", "must", "my", "no", "not", "of", "on",
            "or", "our", "over", "please", "should", "so", "some", "such", "than", "that",
            "the", "their", "them", "then", "there", "these", "they", "this", "those", "to",
            "under", "was", "we", "were", "what", "when", "where", "which", "who", "whom",
            "why", "will", "with", "would", "you", "your",
        )

        /** Lowercase, split on anything that is not a letter or digit, drop noise. */
        fun tokenize(text: String): List<String> =
            text.lowercase()
                .split(NON_WORD)
                .filter { it.length >= 2 && it !in STOPWORDS }
    }
}

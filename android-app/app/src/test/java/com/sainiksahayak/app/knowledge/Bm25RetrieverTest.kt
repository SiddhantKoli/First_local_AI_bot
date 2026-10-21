package com.sainiksahayak.app.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Bm25RetrieverTest {

    private fun chunk(id: String, category: String, topic: String, text: String) =
        KnowledgeChunk(id = id, category = category, topic = topic, source = "test", text = text)

    private val sample = listOf(
        chunk(
            "tout", "Medical", "Tourniquet Application",
            "Apply the tourniquet two to three inches above the wound and tighten until bleeding stops.",
        ),
        chunk(
            "hape", "Medical", "High Altitude Pulmonary Edema (HAPE)",
            "HAPE symptoms include breathlessness at rest, cough and pink frothy sputum at high altitude.",
        ),
        chunk(
            "insas", "Weapons", "INSAS rifle",
            "The INSAS is a 5.56 mm rifle with a 30 round magazine and a cyclic rate of 650 rounds per minute.",
        ),
    )

    private fun build() = Bm25Retriever(sample)

    @Test
    fun ranksTheMatchingChunkFirst() {
        val hits = build().search("symptoms of HAPE", topK = 2)

        assertEquals("hape", hits.first().chunk.id)
        assertTrue(hits.first().score > 0.0)
        assertTrue(hits.size <= 2)
    }

    @Test
    fun topicWeightingBeatsASingleBodyMention() {
        // "wound" appears in the tourniquet text; "INSAS" also appears in a heading.
        val hits = build().search("INSAS rifle magazine")
        assertEquals("insas", hits.first().chunk.id)
    }

    @Test
    fun tokenizerLowercasesDropsStopwordsAndShortTokens() {
        assertEquals(listOf("tourniquet", "application"), Bm25Retriever.tokenize("The Tourniquet, a APPLICATION!"))
        assertEquals(emptyList<String>(), Bm25Retriever.tokenize("what is the of a"))
    }

    @Test
    fun unknownWordsScoreNothing() {
        assertTrue(build().search("chocolate cake recipe").isEmpty())
    }

    @Test
    fun topKIsRespected() {
        assertTrue(build().search("the rifle and the tourniquet and altitude", topK = 1).size == 1)
    }

    @Test
    fun ranksAreSequential() {
        val hits = build().search("rifle altitude tourniquet wound", topK = 3)
        assertEquals(hits.indices.map { it + 1 }, hits.map { it.rank })
        assertEquals(hits.map { it.score }.sortedDescending(), hits.map { it.score })
    }

    /**
     * Runs the real asset through the same code the app uses, so the thresholds
     * in Bm25Retriever are tested against actual knowledge base content.
     */
    private fun realRetriever(): Bm25Retriever {
        val file = listOf(
            File("src/main/assets/knowledge.json"),
            File("app/src/main/assets/knowledge.json"),
        ).firstOrNull { it.isFile }
        assertTrue(
            "knowledge.json not found; run tools/build_knowledge.py (looked from ${File(".").absolutePath})",
            file != null,
        )
        val chunks = KnowledgeBase.parse(file!!.readText())
        assertTrue("asset should hold the merged knowledge base", chunks.size > 200)
        return Bm25Retriever(chunks)
    }

    private fun assertAnswerable(retriever: Bm25Retriever, question: String) {
        val best = retriever.search(question).firstOrNull()
        assertTrue("no results for: $question", best != null)
        assertTrue(
            "'$question' should pass the gate (score=${best!!.score}, coverage=${best.coverage})",
            best.score >= Bm25Retriever.MIN_SCORE && best.coverage >= Bm25Retriever.MIN_COVERAGE,
        )
    }

    private fun assertRefused(retriever: Bm25Retriever, question: String) {
        val best = retriever.search(question).firstOrNull()
        val passes = best != null &&
            best.score >= Bm25Retriever.MIN_SCORE &&
            best.coverage >= Bm25Retriever.MIN_COVERAGE
        assertFalse(
            "'$question' should be refused (score=${best?.score}, coverage=${best?.coverage})",
            passes,
        )
    }

    @Test
    fun realKnowledgeBaseAnswersInScopeQuestions() {
        val retriever = realRetriever()
        listOf(
            "symptoms of HAPE",
            "how do I apply a tourniquet",
            "what is the INSAS rifle",
            "how to clear a room in urban combat",
            "INSAS vs AK-203",
            "what is a Subedar",
            "how should I treat a snake bite",
            "signs of hypothermia",
            "radio procedure",
        ).forEach { assertAnswerable(retriever, it) }
    }

    @Test
    fun realKnowledgeBaseRefusesUnrelatedQuestions() {
        val retriever = realRetriever()
        listOf(
            "how do I bake a chocolate cake",
            "what is the capital of France",
            "tell me a joke",
            "how do I install windows",
            "how do I make pasta",
            "who won the 2011 world cup",
            "best cricket player in the world",
        ).forEach { assertRefused(retriever, it) }
    }

    @Test
    fun realKnowledgeBaseFindsTheRightTopic() {
        val retriever = realRetriever()
        val top = retriever.search("symptoms of HAPE").first()
        assertTrue(
            "expected a HAPE chunk, got ${top.chunk.topic}",
            top.chunk.topic.contains("HAPE", ignoreCase = true) ||
                top.chunk.topic.contains("Altitude", ignoreCase = true),
        )
        assertTrue("expected a source reference", top.chunk.source.isNotBlank())
    }
}

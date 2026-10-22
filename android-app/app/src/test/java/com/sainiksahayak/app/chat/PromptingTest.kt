package com.sainiksahayak.app.chat

import com.sainiksahayak.app.knowledge.KnowledgeChunk
import com.sainiksahayak.app.knowledge.ScoredChunk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptingTest {

    @Test
    fun onlyFollowUpQuestionsNeedCondensation() {
        assertFalse(Prompting.needsCondensation("What is a Subedar?"))
        assertFalse(Prompting.needsCondensation("How do I prevent hypothermia?"))
        assertTrue(Prompting.needsCondensation("What about their ranks?"))
        assertTrue(Prompting.needsCondensation("How do I treat it?"))
    }

    @Test
    fun answerPromptLeavesSourceDisplayToTheUi() {
        val hit = ScoredChunk(
            chunk = KnowledgeChunk(
                id = "subedar",
                category = "General",
                topic = "JCO and NCO ranks",
                source = "test.csv",
                text = "Subedar is a Junior Commissioned Officer rank.",
            ),
            score = 7.0,
            rank = 1,
        )

        val systemPrompt = Prompting.answerTurns(listOf(hit), "What is a Subedar?").first().content

        assertTrue(systemPrompt.contains("Do not include source numbers"))
        assertFalse(systemPrompt.contains("End with one line listing the entry numbers"))
    }
}

package com.sainiksahayak.app.knowledge

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One searchable unit of the knowledge base, produced by
 * tools/build_knowledge.py from the CSVs in data/.
 */
@Serializable
data class KnowledgeChunk(
    val id: String,
    val category: String,
    val topic: String,
    val source: String,
    val text: String,
    val subcategory: String? = null,
    /** "2/3" when a long CSV row was split; null when the row fit in one chunk. */
    val part: String? = null,
)

@Serializable
private data class KnowledgeFile(
    val version: Int,
    val chunkCount: Int,
    val chunks: List<KnowledgeChunk>,
)

/** Loads the bundled knowledge.json (236 chunks, ~168 KB). */
object KnowledgeBase {
    const val ASSET_NAME = "knowledge.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): List<KnowledgeChunk> = json.decodeFromString<KnowledgeFile>(raw).chunks

    /** Must be called off the main thread; reading the asset takes a few ms. */
    fun load(context: Context): List<KnowledgeChunk> =
        context.assets.open(ASSET_NAME).bufferedReader().use { parse(it.readText()) }
}

package com.sainiksahayak.app.llm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The GGUF file lives in app-private storage (`filesDir/models/model.gguf`).
 *
 * We copy it out of the Storage Access Framework instead of keeping the content
 * URI: a permission grant can be revoked, and llama.cpp needs a real file path
 * for mmap. Copying costs one-time disk space (~2 GB for Q4_K_M 3B) and makes the
 * app work forever afterwards.
 */
class ModelStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir: File get() = File(context.filesDir, "models")

    val file: File get() = File(dir, "model.gguf")

    val displayName: String
        get() = prefs.getString(KEY_NAME, null) ?: file.name

    fun exists(): Boolean = file.isFile && file.length() > GGUF_HEADER_BYTES

    /** Copies the picked document, reporting 0f..1f as bytes land on disk. */
    suspend fun copyFromUri(uri: Uri, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val size = querySize(uri)
            val name = queryName(uri) ?: "model.gguf"
            dir.mkdirs()
            val temp = File(dir, "model.gguf.part")
            temp.delete()

            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected file")
            stream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 20) // 1 MiB
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (size > 0) {
                            val percent = (copied * 100 / size).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }

            if (!hasGgufHeader(temp)) {
                temp.delete()
                throw IOException("That file is not a GGUF model (missing GGUF header)")
            }

            file.delete()
            if (!temp.renameTo(file)) throw IOException("Could not move the model into app storage")
            prefs.edit().putString(KEY_NAME, name).apply()
            onProgress(1f)
            file
        }

    suspend fun delete() = withContext(Dispatchers.IO) {
        file.delete()
        prefs.edit().remove(KEY_NAME).apply()
    }

    private fun querySize(uri: Uri): Long =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
        }.getOrNull() ?: 0L

    private fun queryName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

    private fun hasGgufHeader(file: File): Boolean {
        if (!file.isFile || file.length() < GGUF_HEADER_BYTES) return false
        val header = ByteArray(GGUF_HEADER_BYTES)
        file.inputStream().use { it.read(header) }
        return String(header, Charsets.US_ASCII) == "GGUF"
    }

    private companion object {
        const val PREFS = "model_store"
        const val KEY_NAME = "display_name"
        const val GGUF_HEADER_BYTES = 4
    }
}

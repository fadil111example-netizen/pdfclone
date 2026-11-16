package com.lumina.pdfai.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Bookmark(
    val page: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
private data class BookmarkFile(val items: List<Bookmark> = emptyList())

/** Per-document bookmarks, stored as one small JSON file per document. */
class BookmarkStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private fun fileFor(uri: String): File {
        val dir = File(context.filesDir, "bookmarks").apply { mkdirs() }
        val name = uri.hashCode().toUInt().toString(16)
        return File(dir, "$name.json")
    }

    suspend fun list(uri: String): List<Bookmark> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val file = fileFor(uri)
                if (!file.exists()) emptyList()
                else json.decodeFromString<BookmarkFile>(file.readText()).items.sortedBy { it.page }
            }.getOrDefault(emptyList())
        }
    }

    suspend fun add(uri: String, page: Int, label: String): List<Bookmark> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = runCatching {
                val file = fileFor(uri)
                if (file.exists()) json.decodeFromString<BookmarkFile>(file.readText()).items else emptyList()
            }.getOrDefault(emptyList())

            val updated = (current.filterNot { it.page == page } + Bookmark(page, label))
                .sortedBy { it.page }
            runCatching {
                fileFor(uri).writeText(json.encodeToString(BookmarkFile.serializer(), BookmarkFile(updated)))
            }
            updated
        }
    }

    suspend fun remove(uri: String, page: Int): List<Bookmark> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = runCatching {
                val file = fileFor(uri)
                if (file.exists()) json.decodeFromString<BookmarkFile>(file.readText()).items else emptyList()
            }.getOrDefault(emptyList())
            val updated = current.filterNot { it.page == page }
            runCatching {
                fileFor(uri).writeText(json.encodeToString(BookmarkFile.serializer(), BookmarkFile(updated)))
            }
            updated
        }
    }
}

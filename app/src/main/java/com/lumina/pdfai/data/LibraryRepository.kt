package com.lumina.pdfai.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JSON backed library store.
 *
 * Small on purpose: a PDF reader needs a list, a few flags and the last read
 * position. Everything is persisted atomically (write temp file + rename) so a
 * kill during save cannot corrupt the library.
 */
class LibraryRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val file = File(context.filesDir, "library.json")
    private val mutex = Mutex()

    private val _entries = MutableStateFlow<List<PdfEntry>>(emptyList())
    val entries: StateFlow<List<PdfEntry>> = _entries.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = mutex.withLock {
        val read = runCatching {
            if (file.exists()) json.decodeFromString<LibraryFile>(file.readText()).entries else emptyList()
        }.onFailure { Log.w(TAG, "library read failed", it) }.getOrDefault(emptyList())

        // Drop entries whose permission has been revoked / file deleted.
        val alive = read.filter { canRead(it.uri) }
        _entries.value = alive.sortedByDescending { maxOf(it.openedAt, it.addedAt) }
        _loaded.value = true
        if (alive.size != read.size) persistLocked()
    }

    fun readEntries(): List<PdfEntry> = _entries.value

    fun find(uri: String): PdfEntry? = _entries.value.firstOrNull { it.uri == uri }

    /** Adds a document if new, returns the stored entry either way. */
    suspend fun add(uri: Uri, name: String? = null): PdfEntry = mutex.withLock {
        val key = uri.toString()
        _entries.value.firstOrNull { it.uri == key }?.let { return@withLock it }

        val entry = PdfEntry(
            uri = key,
            name = name ?: queryName(uri) ?: "Dokumen.pdf",
            sizeBytes = querySize(uri),
            addedAt = System.currentTimeMillis()
        )
        _entries.value = (listOf(entry) + _entries.value).sortedByDescending { maxOf(it.openedAt, it.addedAt) }
        persistLocked()
        entry
    }

    /** Bulk insert used by the device scanner. */
    suspend fun addAll(found: List<PdfEntry>) {
        if (found.isEmpty()) return
        mutex.withLock {
            val existing = _entries.value.associateBy { it.uri }.toMutableMap()
            found.forEach { candidate ->
                val current = existing[candidate.uri]
                existing[candidate.uri] = if (current == null) {
                    candidate
                } else {
                    // keep reading position, refresh metadata
                    current.copy(
                        name = candidate.name,
                        sizeBytes = candidate.sizeBytes,
                        pageCount = if (current.pageCount > 0) current.pageCount else candidate.pageCount
                    )
                }
            }
            _entries.value = existing.values.sortedByDescending { maxOf(it.openedAt, it.addedAt) }
            persistLocked()
        }
    }

    suspend fun update(uri: String, transform: (PdfEntry) -> PdfEntry) {
        mutex.withLock {
            var changed = false
            _entries.value = _entries.value.map { entry ->
                if (entry.uri == uri) {
                    transform(entry).also { changed = true }
                } else entry
            }
            if (changed) persistLocked()
        }
    }

    suspend fun updateProgress(uri: String, page: Int, pageCount: Int, offset: Float = 0f, seconds: Long = 0L) {
        update(uri) {
            it.copy(
                lastPage = page.coerceAtLeast(0),
                pageCount = if (pageCount > 0) pageCount else it.pageCount,
                scrollOffset = offset,
                openedAt = System.currentTimeMillis(),
                readSeconds = it.readSeconds + seconds,
                finished = pageCount > 0 && page >= pageCount - 1
            )
        }
    }

    suspend fun toggleFavorite(uri: String) {
        update(uri) { it.copy(favorite = !it.favorite) }
    }

    suspend fun setMetadata(uri: String, pageCount: Int, author: String, snippet: String) {
        update(uri) {
            it.copy(
                pageCount = if (pageCount > 0) pageCount else it.pageCount,
                author = author.ifBlank { it.author },
                snippet = snippet.ifBlank { it.snippet }
            )
        }
    }

    suspend fun remove(uri: String) {
        mutex.withLock {
            _entries.value = _entries.value.filterNot { it.uri == uri }
            persistLocked()
        }
    }

    /** Fire and forget persistence for callers outside a coroutine. */
    fun removeAsync(uri: String) {
        scope.launch { remove(uri) }
    }

    private fun persistLocked() {
        val snapshot = LibraryFile(entries = _entries.value)
        runCatching {
            val tmp = File(file.parentFile, "library.json.tmp")
            tmp.writeText(json.encodeToString(LibraryFile.serializer(), snapshot))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }.onFailure { Log.e(TAG, "library write failed", it) }
    }

    private fun canRead(uri: String): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
    }.getOrNull()

    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            } ?: 0L
    }.getOrDefault(0L)

    companion object {
        private const val TAG = "LibraryRepository"
    }
}

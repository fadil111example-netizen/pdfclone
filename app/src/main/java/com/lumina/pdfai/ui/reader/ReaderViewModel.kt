package com.lumina.pdfai.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.pdfai.data.Bookmark
import com.lumina.pdfai.data.PageFilter
import com.lumina.pdfai.data.PaperTone
import com.lumina.pdfai.data.ReaderSettings
import com.lumina.pdfai.di.Services
import com.lumina.pdfai.pdf.OutlineNode
import com.lumina.pdfai.pdf.PdfRenderEngine
import com.lumina.pdfai.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchHit(val page: Int, val snippet: String, val score: Int)

data class ReaderUiState(
    val uri: String = "",
    val title: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val zoom: Float = 1f,
    val settings: ReaderSettings = ReaderSettings(),
    val outline: List<OutlineNode> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val searchQuery: String = "",
    val searchHits: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val searchProgress: Float = 0f,
    val hasText: Boolean = true,
    val metadataAuthor: String = "",
    val showChrome: Boolean = true,
    val message: String? = null
)

/**
 * State holder for the PDF reading screen.
 *
 * Bitmaps are kept in a tiny LRU (5 entries): at typical phone resolution a
 * page is 3–6 MB, so keeping more than a handful would invite an OOM on
 * mid-range devices. Everything else (text, outline) comes from [Services].
 */
class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: PdfRenderEngine = Services.renderEngine()
    private val documents = Services.documents
    private val bookmarks = Services.bookmarks
    private val library = Services.library
    private val settingsRepo = Services.settings

    private val _state = MutableStateFlow(ReaderUiState(settings = settingsRepo.readerSettings()))
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val cache = object : LinkedHashMap<String, Bitmap>(6, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            if (size > MAX_CACHED_PAGES) {
                eldest?.value?.let { if (!it.isRecycled) it.recycle() }
                return true
            }
            return false
        }
    }

    private var searchJob: Job? = null
    private var openedAt = 0L
    private val aspectCache = mutableMapOf<Int, Float>()

    /** Height/width ratio of a page, used for placeholders before the bitmap loads. */
    suspend fun pageAspect(page: Int): Float {
        aspectCache[page]?.let { return it }
        val (w, h) = runCatching { engine.pageSize(page) }.getOrDefault(0 to 0)
        val aspect = if (w > 0 && h > 0) h.toFloat() / w else 1.414f
        aspectCache[page] = aspect
        return aspect
    }

    fun open(uri: String) {
        if (_state.value.uri == uri && _state.value.pageCount > 0) return
        openedAt = System.currentTimeMillis()
        _state.value = _state.value.copy(uri = uri, loading = true, error = null)

        viewModelScope.launch {
            val entry = library.find(uri)
            _state.value = _state.value.copy(
                title = entry?.name?.let { Format.fileStem(it) } ?: "Dokumen",
                currentPage = entry?.lastPage ?: 0
            )

            val pageCount = runCatching { engine.open(Uri.parse(uri)) }.getOrElse { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = "PDF tidak bisa dibuka. ${error.message ?: ""}".trim()
                )
                return@launch
            }

            _state.value = _state.value.copy(
                pageCount = pageCount,
                currentPage = (_state.value.currentPage).coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                loading = false
            )

            // Secondary work: outline, bookmarks, metadata, text availability.
            launch { loadOutline(uri) }
            launch { loadBookmarks(uri) }
            launch {
                val extraction = runCatching { documents.extraction(uri) }.getOrNull() ?: return@launch
                _state.value = _state.value.copy(
                    outline = if (_state.value.outline.isEmpty()) extraction.outline else _state.value.outline,
                    hasText = extraction.hasText,
                    metadataAuthor = extraction.metadata.author
                )
                library.setMetadata(
                    uri = uri,
                    pageCount = extraction.metadata.pageCount.takeIf { it > 0 } ?: pageCount,
                    author = extraction.metadata.author,
                    snippet = extraction.pages.firstOrNull()?.text
                        ?.let { com.lumina.pdfai.ai.TextNormalizer.snippet(it) }
                        .orEmpty()
                )
            }
        }
    }

    private suspend fun loadOutline(uri: String) {
        val extraction = runCatching { documents.extraction(uri) }.getOrNull() ?: return
        _state.value = _state.value.copy(outline = extraction.outline)
    }

    private suspend fun loadBookmarks(uri: String) {
        val list = bookmarks.list(uri)
        _state.value = _state.value.copy(bookmarks = list)
    }

    fun onPageChanged(page: Int) {
        if (page == _state.value.currentPage) return
        _state.value = _state.value.copy(currentPage = page)
        persistProgress()
    }

    /**
     * Slider drags land here: the page follows the finger immediately, but the
     * library JSON is only rewritten once the drag ends via [commitScrub].
     */
    fun onPageScrubbed(page: Int) {
        if (page == _state.value.currentPage) return
        _state.value = _state.value.copy(currentPage = page)
    }

    /** Persists the page after a slider drag has settled. */
    fun commitScrub() = persistProgress()

    fun setZoom(zoom: Float) {
        _state.value = _state.value.copy(zoom = zoom.coerceIn(1f, 5f))
    }

    fun toggleChrome() {
        _state.value = _state.value.copy(showChrome = !_state.value.showChrome)
    }

    fun setChrome(visible: Boolean) {
        if (_state.value.showChrome != visible) _state.value = _state.value.copy(showChrome = visible)
    }

    fun updateSettings(settings: ReaderSettings) {
        settingsRepo.saveReaderSettings(settings)
        val filterChanged = settings.filter != _state.value.settings.filter
        _state.value = _state.value.copy(settings = settings)
        if (filterChanged) clearCache()
    }

    fun setFilter(filter: PageFilter) =
        updateSettings(_state.value.settings.copy(filter = filter))

    fun setTone(tone: PaperTone) =
        updateSettings(_state.value.settings.copy(tone = tone))

    /** Renders a page, reusing the LRU cache. Safe to call from composition. */
    suspend fun pageBitmap(page: Int, widthPx: Int): Bitmap? {
        if (page < 0) return null
        val bucket = (widthPx / 120).coerceAtLeast(1) // quantise so scrolling reuses bitmaps
        val key = "$page@$bucket@${_state.value.settings.filter.ordinal}"
        synchronized(cache) { cache[key] }?.takeIf { !it.isRecycled }?.let { return it }

        val bitmap = withContext(Dispatchers.Default) {
            runCatching {
                engine.render(
                    pageIndex = page,
                    targetWidth = bucket * 120,
                    filter = _state.value.settings.filter
                )
            }.getOrNull()
        } ?: return null

        synchronized(cache) {
            cache[key]?.let { previous -> if (previous !== bitmap && !previous.isRecycled) previous.recycle() }
            cache[key] = bitmap
        }
        return bitmap
    }

    fun preloadAround(page: Int, widthPx: Int) {
        viewModelScope.launch {
            listOf(page + 1, page - 1, page + 2).forEach { candidate ->
                if (candidate in 0 until _state.value.pageCount) pageBitmap(candidate, widthPx)
            }
        }
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        if (query.trim().length < 2) {
            searchJob?.cancel()
            _state.value = _state.value.copy(searchHits = emptyList(), searching = false, searchProgress = 0f)
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(searching = true, searchProgress = 0f)
            val uri = _state.value.uri
            val extraction = runCatching { documents.extraction(uri) }.getOrNull()
            if (extraction == null) {
                _state.value = _state.value.copy(searching = false)
                return@launch
            }

            val needle = query.trim()
            val hits = mutableListOf<SearchHit>()
            extraction.pages.forEachIndexed { pageIndex, page ->
                val text = page.text
                if (text.isNotBlank()) {
                    val plain = com.lumina.pdfai.ai.TextNormalizer.collapseNewlines(text)
                    var from = 0
                    var count = 0
                    while (count < 3) {
                        val index = plain.indexOf(needle, from, ignoreCase = true)
                        if (index < 0) break
                        val start = (index - 60).coerceAtLeast(0)
                        val end = (index + needle.length + 80).coerceAtMost(plain.length)
                        val snippet = (if (start > 0) "…" else "") +
                            plain.substring(start, end).trim() +
                            (if (end < plain.length) "…" else "")
                        hits += SearchHit(pageIndex, snippet, scoreFor(plain, needle))
                        from = index + needle.length
                        count++
                    }
                }
                if (pageIndex % 8 == 0) {
                    _state.value = _state.value.copy(
                        searchProgress = (pageIndex + 1).toFloat() / extraction.pages.size.coerceAtLeast(1)
                    )
                }
            }

            _state.value = _state.value.copy(
                searchHits = hits.sortedByDescending { it.score }.take(200),
                searching = false,
                searchProgress = 1f
            )
        }
    }

    private fun scoreFor(text: String, needle: String): Int {
        // Prefer concentrated matches, they are usually actual mentions.
        val occurrences = text.split(needle, ignoreCase = true).size - 1
        return occurrences * 10
    }

    fun addBookmark(label: String) {
        val page = _state.value.currentPage
        val uri = _state.value.uri
        viewModelScope.launch {
            val list = bookmarks.add(uri, page, label.ifBlank { "Halaman ${page + 1}" })
            _state.value = _state.value.copy(bookmarks = list, message = "Penanda ditambahkan")
        }
    }

    fun removeBookmark(page: Int) {
        val uri = _state.value.uri
        viewModelScope.launch {
            val list = bookmarks.remove(uri, page)
            _state.value = _state.value.copy(bookmarks = list)
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun toggleFavorite() {
        val uri = _state.value.uri
        viewModelScope.launch { library.toggleFavorite(uri) }
    }

    private fun persistProgress() {
        val uri = _state.value.uri
        val page = _state.value.currentPage
        val count = _state.value.pageCount
        val seconds = if (openedAt == 0L) 0L else ((System.currentTimeMillis() - openedAt) / 1000).coerceAtLeast(0)
        openedAt = System.currentTimeMillis()
        viewModelScope.launch {
            library.updateProgress(uri, page, count, seconds = seconds)
        }
    }

    private fun clearCache() {
        synchronized(cache) {
            cache.values.forEach { if (!it.isRecycled) it.recycle() }
            cache.clear()
        }
    }

    fun close() {
        persistProgress()
        clearCache()
    }

    override fun onCleared() {
        super.onCleared()
        persistProgress()
        clearCache()
    }

    companion object {
        private const val MAX_CACHED_PAGES = 5
    }
}

package com.lumina.pdfai.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.pdfai.data.PdfEntry
import com.lumina.pdfai.di.Services
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibrarySort { RECENT, NAME, SIZE, PROGRESS }

data class LibraryUiState(
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val onlyFavorites: Boolean = false,
    val onlyFinished: Boolean = false,
    val searching: Boolean = false,
    val scanning: Boolean = false,
    val message: String? = null,
    val visible: List<PdfEntry> = emptyList(),
    val all: List<PdfEntry> = emptyList(),
    val recent: List<PdfEntry> = emptyList(),
    val totalPages: Int = 0,
    val totalListeningSeconds: Long = 0,
    val loaded: Boolean = false
)

/**
 * Owns the library list: filtering, sorting, imports and the "continue
 * reading" shelf. All persistence goes through the repository, so the UI is a
 * pure function of [state].
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val library = Services.library
    private val scanner = Services.scanner
    private val documents = Services.documents

    private val filters = MutableStateFlow(FilterState())

    private data class FilterState(
        val query: String = "",
        val sort: LibrarySort = LibrarySort.RECENT,
        val favorites: Boolean = false,
        val finished: Boolean = false,
        val scanning: Boolean = false,
        val message: String? = null
    )

    val state: StateFlow<LibraryUiState> =
        combine(library.entries, library.loaded, filters) { entries, loaded, filter ->
            val filtered = entries
                .filter { entry ->
                    (filter.query.isBlank() || entry.name.contains(filter.query, ignoreCase = true) ||
                        entry.author.contains(filter.query, ignoreCase = true)) &&
                        (!filter.favorites || entry.favorite) &&
                        (!filter.finished || entry.finished)
                }
                .let { list ->
                    when (filter.sort) {
                        LibrarySort.RECENT -> list.sortedByDescending { maxOf(it.openedAt, it.addedAt) }
                        LibrarySort.NAME -> list.sortedBy { it.name.lowercase() }
                        LibrarySort.SIZE -> list.sortedByDescending { it.sizeBytes }
                        LibrarySort.PROGRESS -> list.sortedByDescending { it.progressFraction }
                    }
                }

            LibraryUiState(
                query = filter.query,
                sort = filter.sort,
                onlyFavorites = filter.favorites,
                onlyFinished = filter.finished,
                scanning = filter.scanning,
                message = filter.message,
                visible = filtered,
                all = entries,
                recent = entries.filter { it.lastPage > 0 && !it.finished }
                    .sortedByDescending { it.openedAt }
                    .take(8),
                totalPages = entries.sumOf { it.pageCount },
                totalListeningSeconds = entries.sumOf { it.readSeconds },
                loaded = loaded
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) = filters.update { it.copy(query = value) }

    fun setSort(value: LibrarySort) = filters.update { it.copy(sort = value) }

    fun toggleFavorites() = filters.update { it.copy(favorites = !it.favorites) }

    fun toggleFinished() = filters.update { it.copy(finished = !it.finished) }

    fun consumeMessage() = filters.update { it.copy(message = null) }

    /** Registers a picked uri and returns the stored entry (through the callback). */
    fun import(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { library.add(uri) }
                .onSuccess { entry ->
                    filters.update { it.copy(message = "“${entry.name}” ditambahkan") }
                    onDone(entry.uri)
                }
                .onFailure {
                    filters.update { state -> state.copy(message = "Gagal menambahkan berkas: ${it.message}") }
                }
        }
    }

    fun scanDevice() {
        viewModelScope.launch {
            filters.update { it.copy(scanning = true) }
            val result = runCatching { scanner.scan() }
                .getOrElse { com.lumina.pdfai.data.DeviceScanner.ScanResult(emptyList(), false, it.message) }
            if (result.entries.isNotEmpty()) library.addAll(result.entries)
            filters.update {
                it.copy(
                    scanning = false,
                    message = when {
                        result.entries.isNotEmpty() -> "${result.entries.size} PDF ditemukan di perangkat"
                        result.note != null -> result.note
                        else -> "Tidak ada PDF baru yang ditemukan"
                    }
                )
            }
        }
    }

    fun remove(uri: String) {
        viewModelScope.launch {
            library.remove(uri)
            documents.invalidate(uri)
            filters.update { it.copy(message = "Dokumen dihapus dari pustaka") }
        }
    }

    fun toggleFavorite(uri: String) {
        viewModelScope.launch { library.toggleFavorite(uri) }
    }

    private fun MutableStateFlow<FilterState>.update(block: (FilterState) -> FilterState) {
        value = block(value)
    }
}

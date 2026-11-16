package com.lumina.pdfai.data

import kotlinx.serialization.Serializable

/**
 * One document in the library.
 *
 * The file itself is never copied nor modified: we keep the SAF [uri] the user
 * granted plus reading position and light metadata.
 */
@Serializable
data class PdfEntry(
    val uri: String,
    val name: String,
    val sizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val addedAt: Long = 0L,
    val openedAt: Long = 0L,
    val lastPage: Int = 0,
    val scrollOffset: Float = 0f,
    val favorite: Boolean = false,
    val author: String = "",
    /** Cached, human readable one-liner produced by the on-device analyser. */
    val snippet: String = "",
    val readSeconds: Long = 0L,
    val finished: Boolean = false
) {
    val progressFraction: Float
        get() = if (pageCount <= 1) 0f else (lastPage.toFloat() / (pageCount - 1)).coerceIn(0f, 1f)
}

@Serializable
data class LibraryFile(
    val version: Int = 1,
    val entries: List<PdfEntry> = emptyList()
)

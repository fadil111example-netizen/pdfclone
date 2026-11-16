package com.lumina.pdfai.pdf

import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RawPage(val index: Int, val text: String)

data class OutlineNode(
    val title: String,
    val pageIndex: Int,
    val depth: Int,
    val children: List<OutlineNode> = emptyList()
) {
    val isLeaf: Boolean get() = children.isEmpty()
}

data class PdfMetadata(
    val pageCount: Int = 0,
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = ""
) {
    val displayTitle: String
        get() = title.ifBlank { "Tanpa judul" }
}

data class ExtractionResult(
    val metadata: PdfMetadata,
    val pages: List<RawPage>,
    val outline: List<OutlineNode>,
    /** Non-fatal problems, surfaced in the reader as a hint instead of a crash. */
    val warnings: List<String> = emptyList()
) {
    val charCount: Int get() = pages.sumOf { it.text.length }
    val hasText: Boolean get() = charCount > 40
}

/**
 * Text/metadata extraction backed by PDFBox-Android.
 *
 * Runs entirely on the device; nothing is uploaded. All PDFBox objects are
 * confined to the IO dispatcher and the document is always closed.
 */
class PdfTextExtractor(private val resolver: PdfSourceResolver) {

    suspend fun extract(uri: Uri): ExtractionResult = withContext(Dispatchers.IO) {
        val file: File = try {
            resolver.resolve(uri)
        } catch (t: Throwable) {
            Log.w(TAG, "cannot resolve source", t)
            return@withContext ExtractionResult(
                metadata = PdfMetadata(),
                pages = emptyList(),
                outline = emptyList(),
                warnings = listOf("Berkas tidak dapat dibuka: ${t.message ?: "akses ditolak"}")
            )
        }

        var doc: PDDocument? = null
        try {
            doc = PDDocument.load(file)
            val pageCount = doc.numberOfPages
            val metadata = readMetadata(doc, pageCount)
            val pages = readPages(doc)
            val outline = runCatching { readOutline(doc) }
                .onFailure { Log.w(TAG, "outline failed", it) }
                .getOrDefault(emptyList())

            ExtractionResult(
                metadata = metadata,
                pages = pages,
                outline = outline,
                warnings = if (pages.sumOf { it.text.length } < 40) {
                    listOf("Dokumen ini tampaknya hasil pindai (gambar). Teks tidak ditemukan, jadi AI belum bisa membacanya.")
                } else emptyList()
            )
        } catch (t: Throwable) {
            Log.e(TAG, "extraction failed", t)
            ExtractionResult(
                metadata = PdfMetadata(),
                pages = emptyList(),
                outline = emptyList(),
                warnings = listOf("Gagal membaca PDF: ${t.message ?: t.javaClass.simpleName}")
            )
        } finally {
            runCatching { doc?.close() }
        }
    }

    /** Fast path used when a document is opened: page count + author only. */
    suspend fun quickInfo(uri: Uri): Pair<Int, String> = withContext(Dispatchers.IO) {
        var doc: PDDocument? = null
        try {
            doc = PDDocument.load(resolver.resolve(uri))
            val author = runCatching { doc.documentInformation?.author.orEmpty() }.getOrDefault("")
            doc.numberOfPages to author
        } catch (t: Throwable) {
            Log.w(TAG, "quickInfo failed", t)
            0 to ""
        } finally {
            runCatching { doc?.close() }
        }
    }

    private fun readMetadata(doc: PDDocument, pageCount: Int): PdfMetadata {
        val info = runCatching { doc.documentInformation }.getOrNull()
        return PdfMetadata(
            pageCount = pageCount,
            title = runCatching { info?.title.orEmpty() }.getOrDefault(""),
            author = runCatching { info?.author.orEmpty() }.getOrDefault(""),
            subject = runCatching { info?.subject.orEmpty() }.getOrDefault(""),
            keywords = runCatching { info?.keywords.orEmpty() }.getOrDefault("")
        )
    }

    private fun readPages(doc: PDDocument): List<RawPage> {
        val stripper = PDFTextStripper().apply {
            sortByPosition = true
            // \u000C (form feed) marks page boundaries so a single pass is enough.
            pageEnd = PAGE_MARK
            lineSeparator = "\n"
            wordSeparator = " "
        }

        val raw = runCatching { stripper.getText(doc) }.getOrElse { error ->
            Log.w(TAG, "text stripper failed, retrying page by page", error)
            val builder = StringBuilder()
            for (page in 1..doc.numberOfPages) {
                val single = PDFTextStripper().apply {
                    sortByPosition = true
                    startPage = page
                    endPage = page
                }
                builder.append(runCatching { single.getText(doc) }.getOrDefault(""))
                builder.append(PAGE_MARK)
            }
            builder.toString()
        }

        val pieces = raw.split(PAGE_MARK)
        return pieces.mapIndexed { index, text -> RawPage(index, text) }
            .filterIndexed { index, _ -> index < doc.numberOfPages }
    }

    private fun readOutline(doc: PDDocument): List<OutlineNode> {
        val catalog = doc.documentCatalog
        val outline: PDDocumentOutline = runCatching { catalog.documentOutline }.getOrNull() ?: return emptyList()
        val pages = runCatching { doc.pages }.getOrNull() ?: return emptyList()

        fun pageOf(item: PDOutlineItem): Int {
            val destination = runCatching { item.destination }.getOrNull() ?: return -1
            val resolved: PDPageDestination? = when (destination) {
                is PDPageDestination -> destination
                is PDNamedDestination -> runCatching { catalog.findNamedDestinationPage(destination) }.getOrNull()
                else -> null
            }
            if (resolved == null) return -1

            val number = runCatching { resolved.retrievePageNumber() }.getOrDefault(-1)
            if (number in 0 until doc.numberOfPages) return number

            val page = runCatching { resolved.page }.getOrNull() ?: return -1
            return runCatching { pages.indexOf(page) }.getOrDefault(-1)
        }

        fun convert(items: Iterable<PDOutlineItem>, depth: Int): List<OutlineNode> {
            if (depth > MAX_OUTLINE_DEPTH) return emptyList()
            val result = ArrayList<OutlineNode>()
            for (item in items) {
                val title = runCatching { item.title }.getOrNull()?.trim().orEmpty()
                if (title.isEmpty()) continue
                val kids: Iterable<PDOutlineItem> = runCatching { item.children() }.getOrNull() ?: emptyList()
                result.add(
                    OutlineNode(
                        title = title,
                        pageIndex = pageOf(item),
                        depth = depth,
                        children = convert(kids, depth + 1)
                    )
                )
            }
            return result
        }

        val roots: Iterable<PDOutlineItem> = runCatching { outline.children() }.getOrNull() ?: emptyList()
        return convert(roots, 0)
    }

    companion object {
        private const val TAG = "PdfTextExtractor"
        private const val PAGE_MARK = "\u000C"
        private const val MAX_OUTLINE_DEPTH = 4
    }
}

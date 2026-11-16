package com.lumina.pdfai.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.lumina.pdfai.data.OcrStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a scanned PDF into text: every page is rendered to a JPEG on the phone,
 * sent to OCR.space and the answer is stored page by page in [OcrStore].
 *
 * Modes:
 *  - [MODE_AUTO]    Engine 3 (most accurate); if a page fails or comes back
 *                   empty it is retried with Engine 2.
 *  - [MODE_ENGINE3] Engine 3 only.
 *  - [MODE_ENGINE2] Engine 2 only (faster).
 * (OCR.space has retired Engine 1 and redirects it to Engine 2.)
 */
class OcrPipeline(
    private val resolver: PdfSourceResolver,
    private val store: OcrStore,
    private val client: OcrSpaceClient = OcrSpaceClient()
) {

    /**
     * Recognises all pages that are not stored yet. Returns the page count.
     * Safe to cancel at any time; finished pages are kept.
     */
    suspend fun run(
        uri: String,
        apiKey: String,
        mode: Int,
        onProgress: (done: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val file = resolver.resolve(Uri.parse(uri))
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (t: Throwable) {
            runCatching { descriptor.close() }
            throw OcrException("PDF tidak bisa dirender: ${t.message ?: "berkas rusak atau terkunci"}")
        }

        try {
            val total = renderer.pageCount
            var done = store.countDone(uri, total)
            onProgress(done, total)

            for (index in 0 until total) {
                ensureActive()
                if (store.hasPage(uri, index)) continue

                val jpeg = renderPage(renderer, index)
                val text = recognize(jpeg, apiKey, mode)
                store.savePage(uri, index, text)

                done++
                onProgress(done, total)
            }
            total
        } finally {
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }

    // ---- recognition -------------------------------------------------------

    private suspend fun recognize(jpeg: ByteArray, apiKey: String, mode: Int): String {
        val engines = when (mode) {
            MODE_ENGINE3 -> listOf(3)
            MODE_ENGINE2 -> listOf(2)
            else -> listOf(3, 2)
        }

        var sawBlank = false
        var lastError: OcrException? = null
        for (engine in engines) {
            try {
                val text = withRetry { client.recognize(jpeg, apiKey, engine) }
                if (text.isNotBlank()) return clean(text)
                sawBlank = true
            } catch (e: OcrException) {
                if (e.fatal) throw e
                lastError = e
            }
        }
        if (sawBlank) return ""
        throw lastError ?: OcrException("OCR gagal")
    }

    private suspend fun withRetry(block: () -> String): String {
        var failure: OcrException? = null
        for (attempt in 1..3) {
            try {
                return block()
            } catch (e: OcrException) {
                if (e.fatal) throw e
                failure = e
            } catch (e: IOException) {
                failure = OcrException("Koneksi bermasalah: ${e.message ?: "tidak ada jaringan"}")
            }
            if (attempt < 3) delay(2_000L * attempt)
        }
        throw failure ?: OcrException("OCR gagal")
    }

    /** Engine 3 may answer with Markdown tables; make them plain readable text. */
    private fun clean(text: String): String {
        val separator = Regex("^[\\s|:\\-]+$")
        return text.replace("\r\n", "\n")
            .lines()
            .filterNot { line ->
                val t = line.trim()
                t.length >= 3 && '-' in t && separator.matches(t)
            }
            .joinToString("\n") { line ->
                if (line.trimStart().startsWith("|")) {
                    line.replace("|", " ").replace(Regex("\\s{2,}"), "  ").trim()
                } else line
            }
            .trim()
    }

    // ---- rendering ---------------------------------------------------------

    private fun renderPage(renderer: PdfRenderer, index: Int): ByteArray {
        val page = renderer.openPage(index)
        try {
            val longSide = max(page.width, page.height).toFloat().coerceAtLeast(1f)
            val scale = (TARGET_LONG_SIDE / longSide).coerceIn(0.5f, 6f)
            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return encodeUnderLimit(bitmap)
        } finally {
            page.close()
        }
    }

    /** OCR.space's free plan accepts at most 1 MB per image. */
    private fun encodeUnderLimit(source: Bitmap): ByteArray {
        var bitmap = source
        var best = ByteArray(0)
        try {
            while (true) {
                for (quality in intArrayOf(85, 75, 65, 55)) {
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    best = out.toByteArray()
                    if (best.size <= MAX_BYTES) return best
                }
                if (bitmap.width < 900) return best
                val smaller = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * 0.8f).roundToInt(),
                    (bitmap.height * 0.8f).roundToInt(),
                    true
                )
                if (bitmap !== source) bitmap.recycle()
                bitmap = smaller
            }
        } finally {
            if (bitmap !== source) bitmap.recycle()
            source.recycle()
        }
    }

    companion object {
        const val MODE_AUTO = 0
        const val MODE_ENGINE2 = 2
        const val MODE_ENGINE3 = 3

        private const val TARGET_LONG_SIDE = 2000f
        private const val MAX_BYTES = 950_000
    }
}

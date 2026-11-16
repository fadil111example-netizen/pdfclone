package com.lumina.pdfai.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.lumina.pdfai.data.PageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders PDF pages with the platform [PdfRenderer].
 *
 * PdfRenderer is not thread safe and can only keep one page open at a time, so
 * every call is funnelled through a [Mutex] and executed on a single background
 * thread. Bitmaps are recycled by callers through [releaseBitmap].
 */
class PdfRenderEngine(private val context: Context) {

    private val mutex = Mutex()
    private val master = Mutex()

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var currentKey: String? = null

    private val pageSizes = mutableMapOf<Int, Pair<Int, Int>>()

    val isOpen: Boolean get() = renderer != null
    val pageCount: Int get() = renderer?.pageCount ?: 0

    /**
     * Opens [uri] for rendering and returns the page count.
     * Re-opening the same document is a cheap no-op.
     */
    suspend fun open(uri: Uri): Int = master.withLock {
        val key = uri.toString()
        if (currentKey == key && renderer != null) return renderer!!.pageCount

        closeLocked()
        val file = PdfSourceResolver(context).resolve(uri)
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdf = try {
            PdfRenderer(fd)
        } catch (t: Throwable) {
            runCatching { fd.close() }
            throw t
        }
        descriptor = fd
        renderer = pdf
        currentKey = key
        pageSizes.clear()
        pdf.pageCount
    }

    /** Intrinsic page size in PDF points (1/72 inch), rotation applied. */
    suspend fun pageSize(pageIndex: Int): Pair<Int, Int> = mutex.withLock {
        pageSizes[pageIndex]?.let { return it }
        val pdf = renderer ?: return 0 to 0
        if (pageIndex !in 0 until pdf.pageCount) return 0 to 0
        pdf.openPage(pageIndex).use { page ->
            val size = page.width to page.height
            pageSizes[pageIndex] = size
            size
        }
    }

    /**
     * Renders [pageIndex] so its width is at least [targetWidth] px.
     * The bitmap is capped to [maxPixels] to avoid blowing up memory on
     * very large zooms.
     */
    suspend fun render(
        pageIndex: Int,
        targetWidth: Int,
        filter: PageFilter = PageFilter.NONE,
        maxPixels: Int = 6_000_000
    ): Bitmap? = mutex.withLock {
        withContext(Dispatchers.Default) {
            val pdf = renderer ?: return@withContext null
            if (pageIndex < 0 || pageIndex >= pdf.pageCount) return@withContext null

            val page = try {
                pdf.openPage(pageIndex)
            } catch (t: Throwable) {
                Log.w(TAG, "cannot open page $pageIndex", t)
                return@withContext null
            }

            page.use {
                val intrinsicW = max(1, page.width)
                val intrinsicH = max(1, page.height)
                var scale = max(1f, targetWidth.toFloat() / intrinsicW)

                var outW = (intrinsicW * scale).roundToInt().coerceAtLeast(1)
                var outH = (intrinsicH * scale).roundToInt().coerceAtLeast(1)
                val pixels = outW.toLong() * outH.toLong()
                if (pixels > maxPixels) {
                    val shrink = kotlin.math.sqrt(maxPixels.toDouble() / pixels).toFloat()
                    outW = (outW * shrink).roundToInt().coerceAtLeast(1)
                    outH = (outH * shrink).roundToInt().coerceAtLeast(1)
                }

                val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                if (filter != PageFilter.NONE) applyFilter(bitmap, filter)
                bitmap
            }
        }
    }

    suspend fun close() = master.withLock { closeLocked() }

    private fun closeLocked() {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
        currentKey = null
        pageSizes.clear()
    }

    private fun applyFilter(bitmap: Bitmap, filter: PageFilter) {
        val matrix = when (filter) {
            PageFilter.NONE -> return
            PageFilter.INVERT -> ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PageFilter.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
            PageFilter.HIGH_CONTRAST -> ColorMatrix(
                floatArrayOf(
                    1.7f, 0f, 0f, 0f, -90f,
                    0f, 1.7f, 0f, 0f, -90f,
                    0f, 0f, 1.7f, 0f, -90f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        val src = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        val dst = Rect(0, 0, bitmap.width, bitmap.height)
        canvas.drawBitmap(src, null, dst, paint)
        src.recycle()
    }

    fun releaseBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }

    /** Small helper for the library grid: thumbnail of page 0. */
    suspend fun thumbnail(uri: Uri, width: Int = 220): Bitmap? {
        return try {
            open(uri)
            render(0, width, PageFilter.NONE, maxPixels = 400_000)
        } catch (t: Throwable) {
            Log.w(TAG, "thumbnail failed", t)
            null
        }
    }

    companion object {
        private const val TAG = "PdfRenderEngine"

        /** Render scale that keeps text crisp without huge memory. */
        fun renderWidth(availableWidthPx: Int, zoom: Float, maxPx: Int = 2400): Int =
            (availableWidthPx * zoom).roundToInt().coerceIn(240, maxPx)

        fun fitScale(containerWidth: Int, containerHeight: Int, pageWidth: Int, pageHeight: Int): Float {
            if (pageWidth <= 0 || pageHeight <= 0) return 1f
            val sx = containerWidth.toFloat() / pageWidth
            val sy = containerHeight.toFloat() / pageHeight
            return min(sx, sy).coerceAtMost(1.6f)
        }
    }
}

/** Utility that returns a seekable [File] for any content:// or file:// uri. */
class PdfSourceResolver(private val context: Context) {

    suspend fun resolve(uri: Uri): File = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            val direct = File(uri.path ?: throw IllegalArgumentException("bad uri"))
            if (direct.exists()) return@withContext direct
        }

        val cacheDir = File(context.cacheDir, "pdfcache").apply { mkdirs() }
        prune(cacheDir)

        val key = (uri.toString() + "_" + fileSizeHint(uri)).hashCode().toUInt().toString(16)
        val target = File(cacheDir, "doc_$key.pdf")
        if (target.exists() && target.length() > 0) {
            target.setLastModified(System.currentTimeMillis())
            return@withContext target
        }

        val tmp = File(cacheDir, "doc_$key.part")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        } ?: throw IllegalStateException("Tidak bisa membuka berkas")

        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        target
    }

    private fun fileSizeHint(uri: Uri): Long = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }.getOrDefault(0L)

    private fun prune(dir: File) {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }
    }
}

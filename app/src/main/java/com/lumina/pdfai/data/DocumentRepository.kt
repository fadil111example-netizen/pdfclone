package com.lumina.pdfai.data

import android.util.Log
import com.lumina.pdfai.ai.ScriptBuilder
import com.lumina.pdfai.ai.SpeechScript
import com.lumina.pdfai.pdf.ExtractionResult
import com.lumina.pdfai.pdf.PdfTextExtractor
import com.lumina.pdfai.pdf.RawPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-wide cache of extracted documents and their narration scripts.
 *
 * Opening the AI reader right after the PDF view would otherwise re-parse the
 * whole file a second time. Two documents are kept (enough for "recent" jumps)
 * and very large documents are not cached to protect memory.
 */
class DocumentRepository(
    private val extractor: PdfTextExtractor,
    private val ocrStore: OcrStore
) {

    private data class Cached(
        val extraction: ExtractionResult,
        val script: SpeechScript?,
        val keepHeaders: Boolean
    )

    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, Cached>(2, 0.75f, true)
    private val inFlight = mutableMapOf<String, ExtractionResult>()

    suspend fun extraction(uri: String, force: Boolean = false): ExtractionResult {
        if (!force) {
            mutex.withLock { cache[uri] }?.let { return it.extraction }
            inFlight[uri]?.let { return it }
        }

        val extracted = withContext(Dispatchers.IO) { extractor.extract(android.net.Uri.parse(uri)) }
        val result = withContext(Dispatchers.IO) { mergeOcr(uri, extracted) }
        mutex.withLock {
            inFlight.remove(uri)
            put(uri, Cached(result, cache[uri]?.script, cache[uri]?.keepHeaders ?: true))
        }
        return result
    }

    suspend fun script(
        uri: String,
        skipHeaders: Boolean = true,
        naturalPauses: Boolean = true
    ): SpeechScript {
        mutex.withLock {
            val cached = cache[uri]
            if (cached?.script != null && cached.keepHeaders == skipHeaders) return cached.script
        }

        val extraction = extraction(uri)
        val script = withContext(Dispatchers.Default) {
            ScriptBuilder().build(
                rawPages = extraction.pages.map { it.text },
                outline = extraction.outline,
                options = ScriptBuilder.Options(
                    skipHeaders = skipHeaders,
                    naturalPauses = naturalPauses
                )
            )
        }

        mutex.withLock {
            put(uri, Cached(extraction, script, skipHeaders))
        }
        return script
    }

    /** A scanned PDF has no text layer; use the stored OCR text when it is complete. */
    private fun mergeOcr(uri: String, raw: ExtractionResult): ExtractionResult {
        if (raw.hasText) return raw
        val total = raw.metadata.pageCount
        if (total <= 0) return raw
        val texts = ocrStore.loadComplete(uri, total) ?: return raw
        return raw.copy(
            pages = texts.mapIndexed { index, text -> RawPage(index, text) },
            warnings = emptyList()
        )
    }

    suspend fun quickInfo(uri: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        extractor.quickInfo(android.net.Uri.parse(uri))
    }

    fun invalidate(uri: String) {
        cache.remove(uri)
    }

    private fun put(key: String, value: Cached) {
        cache[key] = value
        while (cache.size > 2) {
            val eldest = cache.keys.firstOrNull() ?: break
            cache.remove(eldest)
            Log.d("DocumentRepository", "evicted $eldest from cache")
        }
    }
}

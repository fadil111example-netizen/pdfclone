package com.lumina.pdfai.di

import android.content.Context
import com.lumina.pdfai.LuminaApp
import com.lumina.pdfai.ai.NarrationEngine
import com.lumina.pdfai.ai.TtsEngine
import com.lumina.pdfai.data.BookmarkStore
import com.lumina.pdfai.data.DeviceScanner
import com.lumina.pdfai.data.DocumentRepository
import com.lumina.pdfai.data.OcrStore
import com.lumina.pdfai.pdf.OcrPipeline
import com.lumina.pdfai.pdf.PdfRenderEngine
import com.lumina.pdfai.pdf.PdfSourceResolver
import com.lumina.pdfai.pdf.PdfTextExtractor

/**
 * Minimal hand-rolled service locator.
 *
 * The app has a handful of long-lived objects; a DI framework would be more
 * ceremony than value. Everything here is created lazily and tied to the
 * application context.
 */
object Services {

    @Volatile
    private var appRef: LuminaApp? = null

    fun init(context: Context) {
        if (appRef == null) {
            appRef = context.applicationContext as LuminaApp
        }
    }

    private val app: LuminaApp
        get() = appRef ?: (LuminaApp.instance.also { appRef = it })

    val settings get() = app.settingsRepository
    val library get() = app.libraryRepository

    val sourceResolver: PdfSourceResolver by lazy { PdfSourceResolver(app) }
    val textExtractor: PdfTextExtractor by lazy { PdfTextExtractor(sourceResolver) }
    val ocrStore: OcrStore by lazy { OcrStore(app) }
    val ocr: OcrPipeline by lazy { OcrPipeline(sourceResolver, ocrStore) }
    val documents: DocumentRepository by lazy { DocumentRepository(textExtractor, ocrStore) }
    val bookmarks: BookmarkStore by lazy { BookmarkStore(app) }
    val scanner: DeviceScanner by lazy { DeviceScanner(app) }

    private var renderEngineRef: PdfRenderEngine? = null

    /** A render engine is expensive (holds an open PdfRenderer), so it is shared. */
    @Synchronized
    fun renderEngine(): PdfRenderEngine {
        return renderEngineRef ?: PdfRenderEngine(app).also { renderEngineRef = it }
    }

    fun newTtsEngine(): TtsEngine = TtsEngine(app)

    fun newNarrationEngine(tts: TtsEngine): NarrationEngine = NarrationEngine(app, tts)
}

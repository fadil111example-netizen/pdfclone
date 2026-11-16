package com.lumina.pdfai

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.lumina.pdfai.data.LibraryRepository
import com.lumina.pdfai.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Responsibilities:
 *  - bootstrap PDFBox-Android (needs an Android Context before any font/resource is used)
 *  - hold the singletons that must survive Activity re-creation
 */
class LuminaApp : Application() {

    /** Survives configuration changes; used for fire-and-forget persistence work. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var libraryRepository: LibraryRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // PDFBox-Android reads its asset fonts/metadata through a static Context.
        PDFBoxResourceLoader.init(applicationContext)

        settingsRepository = SettingsRepository(applicationContext)
        libraryRepository = LibraryRepository(applicationContext, appScope)

        appScope.launch {
            libraryRepository.load()
        }
    }

    companion object {
        lateinit var instance: LuminaApp
            private set
    }
}

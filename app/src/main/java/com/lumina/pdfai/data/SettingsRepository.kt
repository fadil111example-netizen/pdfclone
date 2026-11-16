package com.lumina.pdfai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Paper tone used by the PDF page renderer and the AI reader. */
enum class PaperTone {
    LIGHT, SEPIA, DIM, BLACK;

    val isDark: Boolean get() = this == DIM || this == BLACK
}

/** Colour filter applied while rendering pages (helps at night / low vision). */
enum class PageFilter {
    NONE, INVERT, GRAYSCALE, HIGH_CONTRAST
}

enum class ReadingLayout {
    PAGED, CONTINUOUS
}

data class ReaderSettings(
    val tone: PaperTone = PaperTone.LIGHT,
    val filter: PageFilter = PageFilter.NONE,
    val layout: ReadingLayout = ReadingLayout.CONTINUOUS,
    val keepScreenOn: Boolean = true,
    val volumeKeysTurnPages: Boolean = true,
    val cropBorders: Boolean = false
)

/**
 * Preferences that are read often but written rarely.
 *
 * Stored with [SharedPreferences] instead of DataStore because every read
 * happens synchronously from Compose during layout; the writes are tiny and
 * driven by user taps only.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)

    /** The OCR API key lives in its own file so it can be excluded from backups. */
    private val secretPrefs: SharedPreferences =
        context.getSharedPreferences("lumina_secret", Context.MODE_PRIVATE)

    // ---- Appearance ---------------------------------------------------------

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, THEME_SYSTEM)
        set(value) = prefs.edit { putInt(KEY_THEME, value) }

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, false)
        set(value) = prefs.edit { putBoolean(KEY_DYNAMIC, value) }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN, true)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_SCREEN, value) }

    var volumeKeysTurnPages: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_PAGES, true)
        set(value) = prefs.edit { putBoolean(KEY_VOLUME_PAGES, value) }

    fun readerSettings(): ReaderSettings = ReaderSettings(
        tone = PaperTone.entries.getOrElse(prefs.getInt(KEY_TONE, 0)) { PaperTone.LIGHT },
        filter = PageFilter.entries.getOrElse(prefs.getInt(KEY_FILTER, 0)) { PageFilter.NONE },
        layout = ReadingLayout.entries.getOrElse(prefs.getInt(KEY_LAYOUT, 1)) { ReadingLayout.CONTINUOUS },
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN, true),
        volumeKeysTurnPages = prefs.getBoolean(KEY_VOLUME_PAGES, true),
        cropBorders = prefs.getBoolean(KEY_CROP, false)
    )

    fun saveReaderSettings(settings: ReaderSettings) {
        prefs.edit {
            putInt(KEY_TONE, settings.tone.ordinal)
            putInt(KEY_FILTER, settings.filter.ordinal)
            putInt(KEY_LAYOUT, settings.layout.ordinal)
            putBoolean(KEY_KEEP_SCREEN, settings.keepScreenOn)
            putBoolean(KEY_VOLUME_PAGES, settings.volumeKeysTurnPages)
            putBoolean(KEY_CROP, settings.cropBorders)
        }
    }

    // ---- OCR (scanned PDFs) -------------------------------------------------

    var ocrApiKey: String
        get() = secretPrefs.getString(KEY_OCR_API, "") ?: ""
        set(value) = secretPrefs.edit { putString(KEY_OCR_API, value) }

    /** 0 = automatic (Engine 3, fallback Engine 2), 3 = Engine 3, 2 = Engine 2. */
    var ocrMode: Int
        get() = prefs.getInt(KEY_OCR_MODE, 0)
        set(value) = prefs.edit { putInt(KEY_OCR_MODE, value) }

    // ---- AI narration -------------------------------------------------------

    /** Rate multiplier, 0.5 .. 2.0. Default 0.98 = unhurried, natural. */
    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, 0.98f)
        set(value) = prefs.edit { putFloat(KEY_RATE, value) }

    /** Pitch multiplier, 0.5 .. 1.6. Slight downward bias sounds less robotic. */
    var speechPitch: Float
        get() = prefs.getFloat(KEY_PITCH, 0.98f)
        set(value) = prefs.edit { putFloat(KEY_PITCH, value) }

    var voiceKey: String
        get() = prefs.getString(KEY_VOICE, "") ?: ""
        set(value) = prefs.edit { putString(KEY_VOICE, value) }

    var autoScrollFollow: Boolean
        get() = prefs.getBoolean(KEY_FOLLOW, true)
        set(value) = prefs.edit { putBoolean(KEY_FOLLOW, value) }

    var highlightWords: Boolean
        get() = prefs.getBoolean(KEY_HIGHLIGHT, true)
        set(value) = prefs.edit { putBoolean(KEY_HIGHLIGHT, value) }

    var naturalPauses: Boolean
        get() = prefs.getBoolean(KEY_PAUSES, true)
        set(value) = prefs.edit { putBoolean(KEY_PAUSES, value) }

    var skipHeaders: Boolean
        get() = prefs.getBoolean(KEY_SKIP_HEADERS, true)
        set(value) = prefs.edit { putBoolean(KEY_SKIP_HEADERS, value) }

    var readerFontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1f).coerceIn(0.8f, 1.8f)
        set(value) = prefs.edit { putFloat(KEY_FONT_SCALE, value.coerceIn(0.8f, 1.8f)) }

    var readerSerif: Boolean
        get() = prefs.getBoolean(KEY_SERIF, false)
        set(value) = prefs.edit { putBoolean(KEY_SERIF, value) }

    var autoPlayOnOpen: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTOPLAY, value) }

    var sleepTimerMinutes: Int
        get() = prefs.getInt(KEY_SLEEP, 0)
        set(value) = prefs.edit { putInt(KEY_SLEEP, value) }

    fun markTtsReady() = prefs.edit { putBoolean(KEY_TTS_INTRO, true) }

    val hasSeenTtsIntro: Boolean get() = prefs.getBoolean(KEY_TTS_INTRO, false)

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_KEEP_SCREEN = "keep_screen_on"
        private const val KEY_TONE = "paper_tone"
        private const val KEY_FILTER = "page_filter"
        private const val KEY_LAYOUT = "reading_layout"
        private const val KEY_VOLUME_PAGES = "volume_pages"
        private const val KEY_CROP = "crop_borders"
        private const val KEY_OCR_API = "ocr_api_key"
        private const val KEY_OCR_MODE = "ocr_mode"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "speech_pitch"
        private const val KEY_VOICE = "speech_voice"
        private const val KEY_FOLLOW = "auto_follow"
        private const val KEY_HIGHLIGHT = "highlight_words"
        private const val KEY_PAUSES = "natural_pauses"
        private const val KEY_SKIP_HEADERS = "skip_headers"
        private const val KEY_FONT_SCALE = "reader_font_scale"
        private const val KEY_SERIF = "reader_serif"
        private const val KEY_AUTOPLAY = "autoplay_open"
        private const val KEY_SLEEP = "sleep_timer"
        private const val KEY_TTS_INTRO = "tts_intro_seen"
    }
}

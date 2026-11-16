package com.lumina.pdfai.ai

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.lumina.pdfai.ai.LanguageDetector.localeFor
import java.util.Locale

/** A TTS voice with the metadata the UI needs, plus a naturalness score. */
data class VoiceOption(
    val id: String,
    val name: String,
    val locale: Locale,
    val quality: Int,
    val latency: Int,
    val needsNetwork: Boolean,
    val installed: Boolean,
    val score: Int
) {
    val displayName: String
        get() = buildString {
            append(name.ifBlank { locale.displayName })
            if (needsNetwork) append(" · online")
            if (!installed) append(" · belum diunduh")
        }
}

/**
 * Thin, well-behaved wrapper over the platform TextToSpeech engine.
 *
 * Two things matter for a pleasant listen:
 *  - picking the best available voice for the detected language (an offline
 *    "compact" voice sounds robotic, the network voices are the natural ones)
 *  - speaking sentence sized utterances with an explicit pause between them,
 *    because a single giant utterance flattens all the prosody.
 */
class TtsEngine(private val context: Context) {

    interface Callback {
        fun onReady(ok: Boolean, message: String?)
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onError(utteranceId: String, message: String?)
        fun onRange(utteranceId: String, start: Int, end: Int)
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingLanguage: String? = null
    private var callback: Callback? = null
    var currentVoice: Voice? = null
        private set

    val isReady: Boolean get() = ready

    fun attach(callback: Callback) {
        this.callback = callback
    }

    fun init(onReady: (Boolean) -> Unit) {
        if (tts != null) {
            onReady(ready)
            return
        }
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                Log.w(TAG, "TTS init failed with status $status")
                callback?.onReady(false, "Mesin suara tidak tersedia di perangkat ini")
                onReady(false)
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.let { callback?.onStart(it) }
                }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { callback?.onDone(it) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { callback?.onError(it, null) }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    utteranceId?.let { callback?.onError(it, describeError(errorCode)) }
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        utteranceId?.let { callback?.onRange(it, start, end) }
                    }
                }
            })
            runCatching {
                engine.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            pendingLanguage?.let { selectVoiceFor(it, preferOnline = true) }
            callback?.onReady(true, null)
            onReady(true)
        }
    }

    /** Chooses the most natural sounding voice for [language]. */
    fun selectVoiceFor(language: String, preferOnline: Boolean = true, preferredId: String? = null): Voice? {
        val engine = tts ?: run { pendingLanguage = language; return null }
        val locale = localeFor(language)

        val voices = runCatching { engine.voices?.toList().orEmpty() }
            .onFailure { Log.w(TAG, "voices unavailable", it) }
            .getOrDefault(emptyList())

        if (voices.isEmpty()) {
            runCatching {
                engine.language = locale
                engine.setSpeechRate(0.98f)
                engine.setPitch(1f)
            }
            return null
        }

        val online = isNetworkAvailable()

        val ranked = voices.map { voice -> voice to scoreVoice(voice, locale, preferOnline, online) }
            .filter { it.second > Int.MIN_VALUE / 2 }
            .sortedByDescending { it.second }

        val chosen = preferredId?.let { id -> voices.firstOrNull { it.name == id } }
            ?: ranked.firstOrNull()?.first

        if (chosen != null) {
            appliers(engine, chosen, locale)
        } else {
            runCatching { engine.language = locale }
        }
        return chosen
    }

    private fun appliers(engine: TextToSpeech, voice: Voice, locale: Locale) {
        runCatching {
            engine.voice = voice
            // Some engines quietly reset the locale when the voice changes.
            if (engine.voice?.locale?.language != locale.language) {
                engine.language = locale
            }
        }.onFailure { Log.w(TAG, "cannot apply voice ${voice.name}", it) }
        currentVoice = voice
    }

    /**
     * Naturalness ranking. Higher is better; voices that cannot work at all are
     * filtered out with [Int.MIN_VALUE].
     */
    private fun scoreVoice(voice: Voice, locale: Locale, preferOnline: Boolean, online: Boolean): Int {
        val features = voice.features.orEmpty()
        val needsNetwork = features.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS) ||
            features.contains("networkTimeoutMs") ||
            features.any { it.contains("network", ignoreCase = true) }
        val notInstalled = features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

        if (voice.locale.language != locale.language) return Int.MIN_VALUE
        if (notInstalled) return Int.MIN_VALUE
        if (needsNetwork && !online && !preferOnline) return Int.MIN_VALUE

        var score = 0
        if (voice.locale.country.equals(locale.country, ignoreCase = true) && locale.country.isNotEmpty()) score += 45
        if (voice.locale.variant.equals(locale.variant, ignoreCase = true) && locale.variant.isNotEmpty()) score += 10

        score += when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> 40
            Voice.QUALITY_HIGH -> 32
            Voice.QUALITY_NORMAL -> 16
            Voice.QUALITY_VERY_LOW -> 0
            else -> 6
        }
        score += when (voice.latency) {
            Voice.LATENCY_VERY_LOW -> 12
            Voice.LATENCY_LOW -> 10
            Voice.LATENCY_NORMAL -> 6
            Voice.LATENCY_HIGH -> 2
            else -> 0
        }
        // Network voices from Google are the natural, neural ones.
        if (needsNetwork) score += if (preferOnline) 26 else -30
        if (!needsNetwork) score += if (preferOnline) 6 else 22
        if (needsNetwork && !online) score -= 60

        // Names such as "id-id-x-idd-network" or "...-local" tell us a lot.
        val name = voice.name.lowercase(Locale.ROOT)
        when {
            name.contains("network") -> score += 8
            name.contains("local") -> score -= 4
            name.contains("wavenet") || name.contains("neural") -> score += 18
        }
        return score
    }

    fun availableVoices(language: String): List<VoiceOption> {
        val engine = tts ?: return emptyList()
        val locale = localeFor(language)
        val online = isNetworkAvailable()
        return runCatching {
            engine.voices.orEmpty()
                .filter { it.locale.language == locale.language }
                .map { voice ->
                    val features = voice.features.orEmpty()
                    VoiceOption(
                        id = voice.name,
                        name = voice.name,
                        locale = voice.locale,
                        quality = voice.quality,
                        latency = voice.latency,
                        needsNetwork = features.any { it.contains("network", true) },
                        installed = !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED),
                        score = scoreVoice(voice, locale, preferOnline = true, online = online)
                    )
                }
                .sortedByDescending { it.score }
        }.getOrDefault(emptyList())
    }

    fun speak(
        utteranceId: String,
        text: String,
        rate: Float = 0.98f,
        pitch: Float = 1f,
        flush: Boolean = true
    ): Boolean {
        val engine = tts ?: return false
        if (!ready) return false
        engine.setSpeechRate(rate.coerceIn(0.4f, 2.2f))
        engine.setPitch(pitch.coerceIn(0.4f, 1.8f))
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
        }
        val result = engine.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        ready = false
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        currentVoice = null
    }

    fun engineLabel(): String? = runCatching {
        tts?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) it.defaultEngine else null }
    }.getOrNull()

    fun openTtsSettings() {
        val candidates = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        candidates.forEach { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }

    fun openInstallTtsData() {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun isNetworkAvailable(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    private fun describeError(code: Int): String = when (code) {
        TextToSpeech.ERROR_SYNTHESIS -> "Mesin suara gagal mengubah teks menjadi suara."
        TextToSpeech.ERROR_SERVICE -> "Layanan suara berhenti."
        TextToSpeech.ERROR_OUTPUT -> "Tidak ada jalur audio aktif."
        TextToSpeech.ERROR_NETWORK -> "Suara online butuh koneksi internet."
        TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Koneksi suara online habis waktu."
        TextToSpeech.ERROR_INVALID_REQUEST -> "Permintaan suara tidak valid."
        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Data suara belum terunduh."
        else -> "Kesalahan mesin suara ($code)."
    }

    companion object {
        private const val TAG = "TtsEngine"
    }
}

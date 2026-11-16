package com.lumina.pdfai.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.pdfai.ai.LanguageDetector
import com.lumina.pdfai.ai.NarrationEngine
import com.lumina.pdfai.ai.SpeechChunk
import com.lumina.pdfai.ai.SpeechScript
import com.lumina.pdfai.ai.TtsEngine
import com.lumina.pdfai.ai.VoiceOption
import com.lumina.pdfai.di.Services
import com.lumina.pdfai.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the listen screen.
 *
 * [narration] is the live engine state; the other fields describe the document
 * around it. Keeping them separate avoids copying a rapidly changing flow into a
 * slower one sixty times a second.
 */
data class AiReaderUiState(
    val uri: String = "",
    val title: String = "",
    val pageCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val chunkCount: Int = 0,
    val estimatedMinutes: Int = 0,
    val hasText: Boolean = true,
    val languageLabel: String = "",
    val currentChunk: SpeechChunk? = null,
    val previousChunk: SpeechChunk? = null,
    val nextChunk: SpeechChunk? = null,
    val voiceLabel: String = "",
    val voices: List<VoiceOption> = emptyList(),
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val sleepTimerMinutes: Int = 0,
    val autoFollow: Boolean = true,
    val highlightWords: Boolean = true,
    val message: String? = null
)

/**
 * Drives the AI reader.
 *
 * The view model owns the [TtsEngine] because voices and the utterance
 * callbacks are tied to the Reader screen's lifetime: leaving the screen must
 * free the engine, and re-entering must not leave a zombie voice talking.
 */
class AiReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val tts: TtsEngine = Services.newTtsEngine()
    private val narration: NarrationEngine = Services.newNarrationEngine(tts)
    private val documents = Services.documents
    private val library = Services.library
    private val settingsRepo = Services.settings

    private val _ui = MutableStateFlow(AiReaderUiState())
    val ui: StateFlow<AiReaderUiState> = _ui.asStateFlow()

    val narrationState: StateFlow<NarrationEngine.State> = narration.state

    /** Set when the reader should page the PDF view to follow the voice. */
    var onPageFollow: ((Int) -> Unit)? = null

    private var loadedUri: String = ""
    private var script: SpeechScript = SpeechScript.EMPTY

    init {
        narration.onPageRequested = { page ->
            if (page >= 0) {
                onPageFollow?.invoke(page)
                persist(page)
            }
            refreshAround()
        }
        narration.onFinished = {
            viewModelScope.launch {
                val uri = loadedUri
                if (uri.isNotEmpty()) {
                    library.update(uri) { it.copy(finished = true, lastPage = it.pageCount - 1) }
                }
            }
        }
    }

    fun open(uri: String) {
        if (uri == loadedUri && script.chunks.isNotEmpty()) return
        loadedUri = uri
        _ui.value = _ui.value.copy(uri = uri, loading = true, error = null)

        viewModelScope.launch {
            val entry = library.find(uri)
            val speed = settingsRepo.speechRate
            val pitch = settingsRepo.speechPitch
            val autoPlayOnOpen = settingsRepo.autoPlayOnOpen
            _ui.value = _ui.value.copy(
                title = entry?.name?.let { Format.fileStem(it) } ?: "Dokumen",
                pageCount = entry?.pageCount ?: 0,
                speed = speed,
                pitch = pitch,
                autoFollow = settingsRepo.autoScrollFollow,
                highlightWords = settingsRepo.highlightWords,
                sleepTimerMinutes = settingsRepo.sleepTimerMinutes
            )

            val extraction = runCatching { documents.extraction(uri) }.getOrNull()
            if (extraction == null) {
                _ui.value = _ui.value.copy(
                    loading = false,
                    error = "Teks dokumen tidak bisa dibaca. Berkas mungkin rusak atau dilindungi kata sandi."
                )
                return@launch
            }

            if (!extraction.hasText) {
                _ui.value = _ui.value.copy(
                    loading = false,
                    hasText = false,
                    pageCount = extraction.pages.size,
                    error = "Dokumen ini tidak punya lapisan teks (hasil pindai/gambar). AI belum bisa membacakannya."
                )
                return@launch
            }

            val built = runCatching {
                documents.script(
                    uri = uri,
                    skipHeaders = settingsRepo.skipHeaders,
                    naturalPauses = settingsRepo.naturalPauses
                )
            }.getOrNull()

            if (built == null || built.isEmpty) {
                _ui.value = _ui.value.copy(
                    loading = false,
                    hasText = false,
                    error = "Tidak ada kalimat yang bisa dibacakan."
                )
                return@launch
            }

            script = built
            val startChunk = built.firstChunkOfPage(entry?.lastPage ?: 0)
            narration.load(
                script = built,
                startChunk = startChunk.coerceAtLeast(0),
                speed = speed,
                pitch = pitch
            )

            _ui.value = _ui.value.copy(
                loading = false,
                pageCount = extraction.pages.size.coerceAtLeast(entry?.pageCount ?: 0),
                chunkCount = built.chunks.size,
                estimatedMinutes = built.estimatedMinutes,
                languageLabel = LanguageDetector.label(built.language),
                currentChunk = built.chunkAt(startChunk),
                previousChunk = built.chunkAt(startChunk - 1),
                nextChunk = built.chunkAt(startChunk + 1),
                message = null
            )

            // Voices only exist once the engine finished initialising, which
            // load() triggers; a short delay is the cheapest reliable hook.
            kotlinx.coroutines.delay(350)
            refreshVoices()

            // Hands-free mode: start reading as soon as the screen has content.
            if (autoPlayOnOpen) play()
        }
    }

    private fun refreshVoices() {
        val language = script.language
        val voices = runCatching { tts.availableVoices(language) }.getOrDefault(emptyList())
        _ui.value = _ui.value.copy(
            voices = voices,
            voiceLabel = narration.state.value.voiceLabel.ifBlank {
                voices.firstOrNull()?.name.orEmpty()
            }
        )
    }

    private fun refreshAround() {
        val index = narration.state.value.chunkIndex
        if (index < 0) return
        _ui.value = _ui.value.copy(
            currentChunk = script.chunkAt(index),
            previousChunk = script.chunkAt(index - 1),
            nextChunk = script.chunkAt(index + 1)
        )
    }

    private fun persist(page: Int) {
        val uri = loadedUri
        if (uri.isEmpty() || page < 0) return
        viewModelScope.launch {
            library.updateProgress(
                uri = uri,
                page = page,
                pageCount = _ui.value.pageCount
            )
        }
    }

    // ---- Transport ----------------------------------------------------------

    fun toggle() {
        narration.toggle()
        if (narration.state.value.isPlaying) refreshAround()
    }

    fun play() {
        narration.play()
        refreshAround()
    }

    fun pause() = narration.pause()

    fun next() {
        narration.next()
        refreshAround()
    }

    fun previous() {
        narration.previous()
        refreshAround()
    }

    fun seekToChunk(index: Int) {
        narration.seekToChunk(index)
        refreshAround()
    }

    fun seekToFraction(fraction: Float) {
        if (script.chunks.isEmpty()) return
        val target = (fraction * script.chunks.size).toInt().coerceIn(0, script.chunks.lastIndex)
        seekToChunk(target)
    }

    fun setSpeed(value: Float) {
        val clamped = value.coerceIn(0.5f, 2.0f)
        narration.setSpeed(clamped)
        settingsRepo.speechRate = clamped
        _ui.value = _ui.value.copy(speed = clamped)
    }

    fun setPitch(value: Float) {
        val clamped = value.coerceIn(0.5f, 1.6f)
        narration.setPitch(clamped)
        settingsRepo.speechPitch = clamped
        _ui.value = _ui.value.copy(pitch = clamped)
    }

    fun setVoice(voice: VoiceOption) {
        settingsRepo.voiceKey = voice.id
        // selectVoiceFor honours a preferred id; call the engine directly so the
        // choice applies to the very next sentence, not only the next session.
        runCatching { tts.selectVoiceFor(script.language, preferOnline = true, preferredId = voice.id) }
        _ui.value = _ui.value.copy(voiceLabel = voice.name, message = "Suara: ${voice.name}")
        if (narration.state.value.isPlaying) narration.setSpeed(_ui.value.speed)
    }

    fun setLanguage(language: String) {
        narration.setLanguage(language)
        _ui.value = _ui.value.copy(languageLabel = LanguageDetector.label(language))
        viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            refreshVoices()
        }
    }

    fun startSleepTimer(minutes: Int) {
        narration.startSleepTimer(minutes)
        settingsRepo.sleepTimerMinutes = minutes
        _ui.value = _ui.value.copy(
            sleepTimerMinutes = minutes,
            message = if (minutes > 0) "Tidur dalam $minutes menit" else "Pewaktu tidur dimatikan"
        )
    }

    fun toggleAutoFollow() {
        val next = !_ui.value.autoFollow
        settingsRepo.autoScrollFollow = next
        _ui.value = _ui.value.copy(autoFollow = next)
    }

    fun toggleHighlight() {
        val next = !_ui.value.highlightWords
        settingsRepo.highlightWords = next
        _ui.value = _ui.value.copy(highlightWords = next)
    }

    fun openTtsSettings() = tts.openTtsSettings()

    fun openTtsDataInstaller() = tts.openInstallTtsData()

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    /** Drops the transient error raised by a failed utterance. */
    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun onPauseScreen() = narration.pause()

    fun shutdown() {
        narration.shutdown()
    }

    override fun onCleared() {
        super.onCleared()
        shutdown()
    }
}

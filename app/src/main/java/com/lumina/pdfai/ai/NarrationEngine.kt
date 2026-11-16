package com.lumina.pdfai.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The "AI reads it to you" core.
 *
 * Responsibilities:
 *  - walk the [SpeechScript] sentence by sentence
 *  - keep a natural rhythm (lead-in/tail silence, slight rate easing at
 *    sentence starts) instead of firing text at the engine as fast as possible
 *  - expose the exact word range currently being spoken so the UI can highlight
 *    it and auto-scroll
 *  - survive interruptions: pause keeps the position, phone calls or a lost
 *    audio focus simply stop and can resume
 *  - optional sleep timer for listening in bed
 */
class NarrationEngine(
    private val context: Context,
    private val tts: TtsEngine
) {

    data class State(
        val isPlaying: Boolean = false,
        val isPreparing: Boolean = false,
        val chunkIndex: Int = -1,
        val pageIndex: Int = 0,
        val charOffsetInChunk: Int = 0,
        val wordRange: IntRange? = null,
        val progress: Float = 0f,
        val spokenChars: Int = 0,
        val speed: Float = 1f,
        val language: String = "id",
        val voiceLabel: String = "",
        val error: String? = null,
        val sleepRemainingMs: Long = 0L,
        val finished: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    private var script: SpeechScript = SpeechScript.EMPTY
    private var speedFactor: Float = 1f
    private var pitchFactor: Float = 0.98f
    private var resumeAfterInterruption = false
    private var startedAt = 0L
    private var sleepDeadline = 0L
    private var sleepRunnable: Runnable? = null

    /** Called when the engine needs to move the visible page. */
    var onPageRequested: ((Int) -> Unit)? = null
    /** Called once when the script finishes. */
    var onFinished: (() -> Unit)? = null

    private val utterancePrefix = "lumina"
    private var activeUtterance: String? = null

    init {
        tts.attach(object : TtsEngine.Callback {
            override fun onReady(ok: Boolean, message: String?) {
                if (!ok) _state.value = _state.value.copy(error = message, isPreparing = false)
            }

            override fun onStart(utteranceId: String) {
                if (utteranceId != activeUtterance) return
                _state.value = _state.value.copy(isPreparing = false)
            }

            override fun onDone(utteranceId: String) {
                if (utteranceId != activeUtterance) return
                main.postDelayed({ advance() }, tailDelay())
            }

            override fun onError(utteranceId: String, message: String?) {
                if (utteranceId != activeUtterance) return
                Log.w(TAG, "utterance failed: $message")
                // Never stall: report once, then keep going with the next chunk.
                _state.value = _state.value.copy(error = message ?: "Suara terhenti")
                main.postDelayed({ advance() }, 220)
            }

            override fun onRange(utteranceId: String, start: Int, end: Int) {
                if (utteranceId != activeUtterance) return
                _state.value = _state.value.copy(
                    wordRange = start.coerceAtLeast(0)..end.coerceAtLeast(start),
                    charOffsetInChunk = start.coerceAtLeast(0)
                )
            }
        })
    }

    fun load(script: SpeechScript, startChunk: Int = 0, speed: Float = 1f, pitch: Float = 1f) {
        stopInternal(keepPosition = false)
        this.script = script
        this.speedFactor = speed
        tts.init { }
        tts.selectVoiceFor(script.language, preferOnline = true)
        val safeStart = startChunk.coerceIn(0, (script.chunks.size - 1).coerceAtLeast(0))
        _state.value = State(
            chunkIndex = if (script.isEmpty) -1 else safeStart,
            pageIndex = script.chunkAt(safeStart)?.pageIndex ?: 0,
            speed = speed,
            language = script.language,
            voiceLabel = tts.currentVoice?.name.orEmpty(),
            progress = progressFor(safeStart)
        )
        pitchFactor = pitch.coerceIn(0.5f, 1.6f)
    }

    fun play(fromStart: Boolean = false) {
        if (script.isEmpty) {
            _state.value = _state.value.copy(error = "Belum ada teks yang bisa dibaca.")
            return
        }
        tts.init { ok ->
            if (!ok) {
                _state.value = _state.value.copy(
                    error = "Mesin suara belum siap. Pasang Google Text-to-Speech lalu coba lagi.",
                    isPlaying = false
                )
                return@init
            }
            if (tts.currentVoice == null || tts.currentVoice?.locale?.language != script.language) {
                tts.selectVoiceFor(script.language, preferOnline = true)
            }
            val start = if (fromStart) 0 else _state.value.chunkIndex.coerceAtLeast(0)
            _state.value = _state.value.copy(
                isPlaying = true,
                finished = false,
                chunkIndex = start,
                pageIndex = script.chunkAt(start)?.pageIndex ?: 0,
                voiceLabel = tts.currentVoice?.name.orEmpty()
            )
            startedAt = SystemClock.elapsedRealtime()
            speakCurrent()
        }
    }

    fun pause() {
        stopInternal(keepPosition = true)
        _state.value = _state.value.copy(isPlaying = false, isPreparing = false, wordRange = null)
    }

    fun toggle() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun next() {
        val wasPlaying = _state.value.isPlaying
        stopInternal(keepPosition = true)
        val target = (_state.value.chunkIndex + 1).coerceAtMost(script.chunks.lastIndex)
        moveTo(target, resume = wasPlaying)
    }

    fun previous() {
        val wasPlaying = _state.value.isPlaying
        stopInternal(keepPosition = true)
        val current = _state.value.chunkIndex
        val offset = _state.value.charOffsetInChunk
        // Standard player behaviour: restart the sentence unless we are near its start.
        val target = if (offset > 30 && current >= 0) current else (current - 1).coerceAtLeast(0)
        moveTo(target, resume = wasPlaying)
    }

    fun seekToChunk(index: Int, resume: Boolean? = null) {
        val shouldResume = resume ?: _state.value.isPlaying
        stopInternal(keepPosition = true)
        moveTo(index.coerceIn(0, (script.chunks.size - 1).coerceAtLeast(0)), resume = shouldResume)
    }

    fun seekToPage(page: Int) {
        if (script.isEmpty) return
        val target = script.firstChunkOfPage(page)
        seekToChunk(target)
    }

    fun setSpeed(value: Float) {
        speedFactor = value.coerceIn(0.5f, 2.0f)
        _state.value = _state.value.copy(speed = speedFactor)
        if (_state.value.isPlaying) {
            // Re-speak the current sentence at the new pace so the change is heard now.
            speakCurrent()
        }
    }

    fun setPitch(pitch: Float) {
        pitchFactor = pitch.coerceIn(0.5f, 1.6f)
        if (_state.value.isPlaying) speakCurrent()
    }

    fun setLanguage(language: String) {
        tts.selectVoiceFor(language, preferOnline = true)
        _state.value = _state.value.copy(language = language, voiceLabel = tts.currentVoice?.name.orEmpty())
        if (_state.value.isPlaying) speakCurrent()
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        sleepDeadline = SystemClock.elapsedRealtime() + minutes * 60_000L

        val runnable = object : Runnable {
            override fun run() {
                val left = sleepDeadline - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    _state.value = _state.value.copy(sleepRemainingMs = 0)
                    pause()
                    sleepDeadline = 0
                    return
                }
                _state.value = _state.value.copy(sleepRemainingMs = left)
                main.postDelayed(this, 1000)
            }
        }
        sleepRunnable = runnable
        main.postDelayed(runnable, 1000)
    }

    fun cancelSleepTimer() {
        sleepRunnable?.let { main.removeCallbacks(it) }
        sleepRunnable = null
        sleepDeadline = 0
        _state.value = _state.value.copy(sleepRemainingMs = 0)
    }

    /** Total listening time in this session, for the library's reading stats. */
    fun sessionSeconds(): Long =
        if (startedAt == 0L) 0L else ((SystemClock.elapsedRealtime() - startedAt) / 1000).coerceAtLeast(0)

    fun shutdown() {
        stopInternal(keepPosition = false)
        cancelSleepTimer()
        tts.shutdown()
    }

    val currentScript: SpeechScript get() = script

    private fun moveTo(index: Int, resume: Boolean) {
        _state.value = _state.value.copy(
            chunkIndex = index,
            pageIndex = script.chunkAt(index)?.pageIndex ?: _state.value.pageIndex,
            progress = progressFor(index),
            wordRange = null,
            charOffsetInChunk = 0,
            error = null,
            finished = false
        )
        onPageRequested?.invoke(script.chunkAt(index)?.pageIndex ?: 0)
        if (resume) {
            _state.value = _state.value.copy(isPlaying = true)
            speakCurrent()
        }
    }

    private fun speakCurrent() {
        val chunk = script.chunkAt(_state.value.chunkIndex)
        if (chunk == null) {
            finish()
            return
        }
        val id = "$utterancePrefix-${chunk.index}-${SystemClock.elapsedRealtime()}"
        activeUtterance = id
        _state.value = _state.value.copy(
            pageIndex = chunk.pageIndex,
            isPlaying = true,
            isPreparing = true,
            wordRange = null,
            charOffsetInChunk = 0
        )
        onPageRequested?.invoke(chunk.pageIndex)

        val leadIn = chunk.leadInMs.toLong()
        val speak = {
            val ok = tts.speak(
                utteranceId = id,
                text = chunk.text,
                rate = ttsRate(),
                pitch = ttsPitch(),
                flush = true
            )
            if (!ok) {
                _state.value = _state.value.copy(
                    isPreparing = false,
                    error = "Mesin suara menolak permintaan. Coba pilih suara lain di Pengaturan."
                )
                main.postDelayed({ advance() }, 300)
            }
        }
        if (leadIn > 0) main.postDelayed(speak, leadIn) else speak()
    }

    private fun advance() {
        if (!_state.value.isPlaying) return
        val nextIndex = _state.value.chunkIndex + 1
        if (nextIndex >= script.chunks.size) {
            finish()
            return
        }
        _state.value = _state.value.copy(
            chunkIndex = nextIndex,
            pageIndex = script.chunkAt(nextIndex)?.pageIndex ?: _state.value.pageIndex,
            progress = progressFor(nextIndex),
            spokenChars = script.chunks.take(nextIndex).sumOf { it.charCount },
            wordRange = null,
            charOffsetInChunk = 0
        )
        speakCurrent()
    }

    private fun finish() {
        _state.value = _state.value.copy(
            isPlaying = false,
            isPreparing = false,
            wordRange = null,
            finished = true,
            progress = 1f
        )
        activeUtterance = null
        onFinished?.invoke()
    }

    private fun stopInternal(keepPosition: Boolean) {
        main.removeCallbacksAndMessages(null)
        activeUtterance = null
        tts.stop()
        if (!keepPosition) {
            _state.value = _state.value.copy(
                isPlaying = false, isPreparing = false, chunkIndex = -1, wordRange = null
            )
        }
    }

    private fun ttsRate(): Float {
        // A hair slower than the user setting at sentence level sounds warmer,
        // and punctuation gives the engine its own natural pauses.
        val base = 0.98f * speedFactor
        return base.coerceIn(0.45f, 2.1f)
    }

    private fun ttsPitch(): Float = pitchFactor.coerceIn(0.5f, 1.8f)

    private fun tailDelay(): Long {
        val chunk = script.chunkAt(_state.value.chunkIndex)
        val tail = chunk?.tailMs ?: 220
        // Faster playback should also shorten the silence, but never cut it out.
        return (tail / speedFactor).toLong().coerceIn(120L, 900L)
    }

    private fun progressFor(index: Int): Float {
        if (script.chunks.isEmpty()) return 0f
        if (index < 0) return 0f
        val spoken = script.chunks.take(index).sumOf { it.charCount }
        return (spoken.toFloat() / script.totalChars.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /** Pauses for an incoming call / focus loss; remembers to resume. */
    fun onAudioFocusLost() {
        resumeAfterInterruption = _state.value.isPlaying
        if (_state.value.isPlaying) pause()
    }

    fun onAudioFocusGained() {
        if (resumeAfterInterruption && !_state.value.isPlaying) {
            resumeAfterInterruption = false
            play()
        }
    }

    companion object {
        private const val TAG = "NarrationEngine"
    }
}

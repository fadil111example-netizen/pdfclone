package com.lumina.pdfai.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumina.pdfai.ai.NarrationEngine
import com.lumina.pdfai.ai.SpeechChunk
import com.lumina.pdfai.ui.components.SpeakingBars
import com.lumina.pdfai.ui.theme.BrandGradient
import com.lumina.pdfai.ui.theme.Ember400
import com.lumina.pdfai.ui.theme.Mint400
import com.lumina.pdfai.ui.theme.Violet400
import com.lumina.pdfai.util.Format
import kotlinx.coroutines.delay

/**
 * The "karaoke" panel: the sentence being spoken, in a large typeface, with the
 * exact word lit up. Surrounding sentences are dimmed but visible so the eye can
 * keep context.
 */
@Composable
internal fun ReadAlongPanel(
    ui: AiReaderUiState,
    narration: NarrationEngine.State,
    onSeekChunk: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val current = ui.currentChunk
    Column(modifier.padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (current != null) "Halaman ${current.pageIndex + 1}" else "—",
                style = MaterialTheme.typography.labelMedium,
                color = Mint400
            )
            Spacer(Modifier.width(8.dp))
            if (narration.isPlaying) {
                SpeakingBars(active = true, color = Mint400)
            }
            Spacer(Modifier.weight(1f))
            if (ui.voiceLabel.isNotBlank()) {
                Text(
                    ui.voiceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(140.dp),
                    textAlign = TextAlign.End
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Previous / current / next, stacked so the eyes never lose the place.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            ui.previousChunk?.let { chunk ->
                Text(
                    chunk.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.34f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekChunk(chunk.index) }
                )
                Spacer(Modifier.height(18.dp))
            }

            if (current != null) {
                val highlighted = remember(ui.highlightWords) { ui.highlightWords }
                Text(
                    text = current.highlighted(narration.wordRange, highlighted),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 26.sp,
                        lineHeight = 38.sp
                    ),
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            } else if (ui.chunkCount > 0) {
                Text(
                    "Tekan putar untuk mulai mendengarkan.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            ui.nextChunk?.let { chunk ->
                Spacer(Modifier.height(18.dp))
                Text(
                    chunk.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.34f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekChunk(chunk.index) }
                )
            }
        }

        if (narration.sleepRemainingMs > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = Violet400,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Tidur dalam ${Format.countdown(narration.sleepRemainingMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Violet400
                )
            }
        } else if (narration.finished) {
            Text(
                "Selesai. Dokumen sudah dibacakan sampai akhir.",
                style = MaterialTheme.typography.labelSmall,
                color = Mint400
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** Builds the annotated sentence with the spoken word highlighted. */
private fun SpeechChunk.highlighted(range: IntRange?, enabled: Boolean): AnnotatedString {
    if (!enabled || range == null) return AnnotatedString(text)
    val start = range.first.coerceIn(0, text.length)
    val end = (range.last + 1).coerceIn(start, text.length)
    if (start >= end) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = Ember400, fontWeight = FontWeight.Bold)) {
            append(text.substring(start, end))
        }
        append(text.substring(end))
    }
}

@Composable
internal fun TransportBar(
    ui: AiReaderUiState,
    narration: NarrationEngine.State,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeed: (Float) -> Unit,
    onSleep: (Int) -> Unit,
    onOpenVoices: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onMessageShown: () -> Unit,
    message: String?,
    modifier: Modifier = Modifier
) {
    var sleepMenuOpen by remember { mutableStateOf(false) }

    Column(modifier.padding(horizontal = 20.dp)) {
        Slider(
            value = narration.progress.coerceIn(0f, 1f),
            onValueChange = onSeek,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Violet400,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f)
            )
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                Format.percent(narration.progress),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
            Text(
                "${narration.pageIndex + 1}/${ui.pageCount.coerceAtLeast(1)} hal.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onOpenVoices) {
                Icon(
                    Icons.Filled.RecordVoiceOver,
                    contentDescription = "Pilih suara",
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Kalimat sebelumnya",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
            PlayButton(
                playing = narration.isPlaying,
                preparing = narration.isPreparing,
                finished = narration.finished,
                onClick = onToggle
            )
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Kalimat berikutnya",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
            Box {
                IconButton(onClick = { sleepMenuOpen = true }) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = "Pewaktu tidur",
                        tint = if (narration.sleepRemainingMs > 0) Violet400
                        else Color.White.copy(alpha = 0.85f)
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = sleepMenuOpen,
                    onDismissRequest = { sleepMenuOpen = false }
                ) {
                    listOf(0, 5, 10, 15, 30, 45, 60).forEach { minutes ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(if (minutes == 0) "Matikan pewaktu" else "Tidur dalam $minutes menit")
                            },
                            onClick = { sleepMenuOpen = false; onSleep(minutes) }
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Kecepatan ${Format.speechRateLabel(ui.speed)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            Text(
                if (narration.sleepRemainingMs > 0) Format.countdown(narration.sleepRemainingMs)
                else "Ketuk dua ikon untuk jeda & kecepatan",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        if (message != null) {
            LaunchedEffect(message) {
                delay(2200)
                onMessageShown()
            }
            Text(
                message,
                style = MaterialTheme.typography.labelMedium,
                color = Mint400,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (narration.error != null) {
            Text(
                narration.error,
                style = MaterialTheme.typography.labelMedium,
                color = Ember400,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (ui.voices.isEmpty() && !ui.loading) {
            Text(
                "Suara belum terdeteksi. Ketuk ikon mikrofon untuk memilih, atau pasang suara Google.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClick = onOpenTtsSettings)
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PlayButton(
    playing: Boolean,
    preparing: Boolean,
    finished: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(BrandGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            preparing && playing -> androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                strokeWidth = 2.5.dp,
                color = Color.White
            )
            playing -> Icon(
                Icons.Filled.Pause,
                contentDescription = "Jeda",
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
            else -> Icon(
                if (finished) Icons.Filled.SkipPrevious else Icons.Filled.PlayArrow,
                contentDescription = if (finished) "Putar ulang" else "Putar",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

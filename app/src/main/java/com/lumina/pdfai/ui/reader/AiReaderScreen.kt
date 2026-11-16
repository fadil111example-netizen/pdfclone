package com.lumina.pdfai.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumina.pdfai.pdf.OcrPipeline
import com.lumina.pdfai.ui.components.ThinkingDots
import com.lumina.pdfai.ui.theme.Ember400
import com.lumina.pdfai.ui.theme.Ink850
import com.lumina.pdfai.ui.theme.Ink900
import com.lumina.pdfai.ui.theme.Violet400

/**
 * The listening screen: one big play button, the sentence being spoken, and the
 * controls that make a synthetic voice bearable for an hour.
 */
@Composable
fun AiReaderScreen(
    documentUri: String,
    onBack: () -> Unit,
    onOpenPdf: (uri: String, page: Int) -> Unit
) {
    val viewModel: AiReaderViewModel = viewModel()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val narration by viewModel.narrationState.collectAsStateWithLifecycle()

    var showVoices by remember { mutableStateOf(false) }
    var showTuning by remember { mutableStateOf(false) }

    LaunchedEffect(documentUri) { viewModel.open(documentUri) }

    // Leave the screen and the voice stops: no background zombie narrator.
    DisposableEffect(Unit) {
        onDispose { viewModel.shutdown() }
    }

    AiReaderContent(
        ui = ui,
        narration = narration,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onSeek = viewModel::seekToFraction,
        onSeekChunk = viewModel::seekToChunk,
        onSpeed = viewModel::setSpeed,
        onPitch = viewModel::setPitch,
        onSleep = viewModel::startSleepTimer,
        onOpenVoices = { showVoices = true },
        onOpenTuning = { showTuning = true },
        onOpenPdf = { onOpenPdf(ui.uri, narration.pageIndex) },
        onOpenTtsSettings = viewModel::openTtsSettings,
        onConsumeMessage = viewModel::consumeMessage,
        onStartOcr = viewModel::startOcr,
        onCancelOcr = viewModel::cancelOcr,
        uiMessage = ui.message
    )

    if (showVoices) {
        VoiceSheet(
            voices = ui.voices,
            current = ui.voiceLabel,
            languageLabel = ui.languageLabel,
            onSelect = { viewModel.setVoice(it); showVoices = false },
            onOpenTtsSettings = { viewModel.openTtsSettings(); showVoices = false },
            onDismiss = { showVoices = false }
        )
    }

    if (showTuning) {
        TuningSheet(
            speed = ui.speed,
            pitch = ui.pitch,
            autoFollow = ui.autoFollow,
            highlight = ui.highlightWords,
            onSpeed = viewModel::setSpeed,
            onPitch = viewModel::setPitch,
            onToggleFollow = viewModel::toggleAutoFollow,
            onToggleHighlight = viewModel::toggleHighlight,
            onDismiss = { showTuning = false }
        )
    }
}

@Composable
private fun AiReaderContent(
    ui: AiReaderUiState,
    narration: com.lumina.pdfai.ai.NarrationEngine.State,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekChunk: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onSleep: (Int) -> Unit,
    onOpenVoices: () -> Unit,
    onOpenTuning: () -> Unit,
    onOpenPdf: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onConsumeMessage: () -> Unit,
    onStartOcr: (String, Int) -> Unit,
    onCancelOcr: () -> Unit,
    uiMessage: String?
) {
    val view = LocalView.current

    Box(
        Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Ink900, Ink850, Color(0xFF191436))
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        ui.title.ifBlank { "Dokumen" },
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOfNotNull(
                                ui.languageLabel.takeIf { it.isNotBlank() },
                                if (ui.estimatedMinutes > 0) "±${ui.estimatedMinutes} mnt" else null
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                IconButton(onClick = onOpenPdf) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Lihat teks PDF",
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }
                IconButton(onClick = onOpenTuning) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = "Pengaturan suara",
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            when {
                ui.loading -> PreparingBlock()
                ui.needsOcr -> OcrPanel(
                    ui = ui,
                    onStart = onStartOcr,
                    onCancel = onCancelOcr,
                    onBack = onBack
                )
                ui.error != null && !ui.hasText -> BlockingError(
                    title = "Tidak bisa membacakan",
                    message = ui.error,
                    onOpenTtsSettings = onOpenTtsSettings,
                    onBack = onBack
                )
                else -> Column(Modifier.weight(1f)) {
                    ReadAlongPanel(
                        ui = ui,
                        narration = narration,
                        onSeekChunk = onSeekChunk,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    TransportBar(
                        ui = ui,
                        narration = narration,
                        onToggle = onToggle,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onSpeed = onSpeed,
                        onSleep = onSleep,
                        onOpenVoices = onOpenVoices,
                        onOpenTtsSettings = onOpenTtsSettings,
                        onMessageShown = onConsumeMessage,
                        message = uiMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    )
                }
            }
        }
    }

    // Word-level highlighting is what makes listening feel "AI", so keep the
    // screen on while it speaks.
    DisposableEffect(narration.isPlaying) {
        view.keepScreenOn = narration.isPlaying
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun PreparingBlock() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Violet400, strokeWidth = 2.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Menyiapkan suara dan memecah teks menjadi kalimat…",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        ThinkingDots(color = Ember400)
    }
}

@Composable
private fun BlockingError(
    title: String,
    message: String,
    onOpenTtsSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White,
                modifier = Modifier.clickable(onClick = onBack)
            ) {
                Text(
                    "Kembali",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.White,
                contentColor = Ink900,
                modifier = Modifier.clickable(onClick = onOpenTtsSettings)
            ) {
                Text(
                    "Buka pengaturan suara",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
                )
            }
        }
    }
}

private val OcrError = Color(0xFFFF8A80)

@Composable
private fun OcrPanel(
    ui: AiReaderUiState,
    onStart: (String, Int) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit
) {
    var apiKey by remember(ui.ocrApiKey) { mutableStateOf(ui.ocrApiKey) }
    var mode by remember(ui.ocrMode) { mutableStateOf(ui.ocrMode) }
    var showKey by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Dokumen ini hasil pindai",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))

        if (ui.ocrRunning) {
            val fraction = if (ui.ocrTotal > 0) ui.ocrDone.toFloat() / ui.ocrTotal else 0f
            Text(
                "Membaca teks dari gambar halaman (OCR)…",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = Violet400,
                trackColor = Color.White.copy(alpha = 0.16f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Halaman ${ui.ocrDone} dari ${ui.ocrTotal}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Biarkan layar ini terbuka. Hasil tiap halaman langsung disimpan di HP, " +
                    "jadi kalau terhenti, OCR bisa dilanjutkan dari halaman terakhir.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            OcrButton("Batalkan", filled = false, onClick = onCancel)
        } else {
            Text(
                "PDF ini hanya berisi gambar, jadi teksnya perlu dikenali dulu lewat OCR " +
                    "sebelum bisa dibacakan. Gambar tiap halaman dikirim ke OCR.space. " +
                    "Hasilnya disimpan di HP, jadi cukup sekali per dokumen.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim() },
                label = { Text("API key OCR.space") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Text(
                        if (showKey) "Sembunyi" else "Lihat",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .clickable { showKey = !showKey }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Violet400,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = Violet400,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))

            OcrModeOption(
                title = "Otomatis (disarankan)",
                subtitle = "Engine 3 yang paling akurat; kalau gagal atau kosong, otomatis dicoba Engine 2",
                selected = mode == OcrPipeline.MODE_AUTO,
                onClick = { mode = OcrPipeline.MODE_AUTO }
            )
            Spacer(Modifier.height(8.dp))
            OcrModeOption(
                title = "Engine 3",
                subtitle = "Akurasi tertinggi, lebih lambat",
                selected = mode == OcrPipeline.MODE_ENGINE3,
                onClick = { mode = OcrPipeline.MODE_ENGINE3 }
            )
            Spacer(Modifier.height(8.dp))
            OcrModeOption(
                title = "Engine 2",
                subtitle = "Lebih cepat dan kuotanya lebih besar",
                selected = mode == OcrPipeline.MODE_ENGINE2,
                onClick = { mode = OcrPipeline.MODE_ENGINE2 }
            )

            if (ui.ocrError != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    ui.ocrError,
                    style = MaterialTheme.typography.bodySmall,
                    color = OcrError,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))
            val remaining = (ui.ocrTotal - ui.ocrDone).coerceAtLeast(0)
            OcrButton(
                label = if (ui.ocrDone > 0) "Lanjutkan OCR ($remaining halaman tersisa)"
                else "Mulai OCR (${ui.ocrTotal} halaman)",
                filled = true,
                onClick = { onStart(apiKey, mode) }
            )
            Spacer(Modifier.height(10.dp))
            OcrButton("Kembali", filled = false, onClick = onBack)
        }
    }
}

@Composable
private fun OcrButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (filled) Color.White else Color.White.copy(alpha = 0.12f),
        contentColor = if (filled) Ink900 else Color.White,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun OcrModeOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Violet400.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f)
            )
        }
    }
}

package com.lumina.pdfai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumina.pdfai.data.PageFilter
import com.lumina.pdfai.data.PaperTone
import com.lumina.pdfai.data.ReadingLayout
import com.lumina.pdfai.data.SettingsRepository
import com.lumina.pdfai.di.Services
import com.lumina.pdfai.ui.components.AuroraBackground
import com.lumina.pdfai.ui.theme.Ember500
import com.lumina.pdfai.ui.theme.Mint400
import com.lumina.pdfai.ui.theme.Violet500
import com.lumina.pdfai.ui.theme.isDark
import com.lumina.pdfai.util.Format

/**
 * All preferences in one scroll, grouped the way a reader actually thinks:
 * how it looks, how it sounds, and what happens when you leave.
 *
 * Values are written straight to [SettingsRepository] and mirrored into local
 * Compose state so switches move immediately without a round trip.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val settings = remember { Services.settings }
    val tts = remember { Services.newTtsEngine() }

    var themeMode by remember { mutableIntStateOf(settings.themeMode) }
    var dynamicColor by remember { mutableStateOf(settings.dynamicColor) }
    var keepScreenOn by remember { mutableStateOf(settings.keepScreenOn) }
    var volumeKeys by remember { mutableStateOf(settings.volumeKeysTurnPages) }

    var tone by remember { mutableStateOf(settings.readerSettings().tone) }
    var filter by remember { mutableStateOf(settings.readerSettings().filter) }
    var layout by remember { mutableStateOf(settings.readerSettings().layout) }

    var rate by remember { mutableStateOf(settings.speechRate) }
    var pitch by remember { mutableStateOf(settings.speechPitch) }
    var autoFollow by remember { mutableStateOf(settings.autoScrollFollow) }
    var highlight by remember { mutableStateOf(settings.highlightWords) }
    var naturalPauses by remember { mutableStateOf(settings.naturalPauses) }
    var skipHeaders by remember { mutableStateOf(settings.skipHeaders) }
    var autoPlay by remember { mutableStateOf(settings.autoPlayOnOpen) }
    var sleepMinutes by remember { mutableIntStateOf(settings.sleepTimerMinutes) }

    val engineName = remember { runCatching { tts.engineLabel() }.getOrNull() }

    AuroraBackground(dark = MaterialTheme.colorScheme.background.isDark()) {
        Scaffold(containerColor = Color.Transparent) { insets ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(insets)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                    Text(
                        "Pengaturan",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    item {
                        GroupTitle("Tampilan")
                        SettingsCard {
                            ChoiceRow(
                                title = "Tema aplikasi",
                                options = listOf("Ikuti sistem", "Terang", "Gelap"),
                                selected = themeMode,
                                onSelect = { themeMode = it; settings.themeMode = it }
                            )
                            Divider()
                            SwitchRow(
                                title = "Warna dinamis",
                                subtitle = "Ikuti palet wallpaper Android 12+",
                                checked = dynamicColor,
                                onToggle = { dynamicColor = !dynamicColor; settings.dynamicColor = dynamicColor }
                            )
                            Divider()
                            SwitchRow(
                                title = "Layar tetap menyala saat membaca",
                                checked = keepScreenOn,
                                onToggle = { keepScreenOn = !keepScreenOn; settings.keepScreenOn = keepScreenOn }
                            )
                            Divider()
                            SwitchRow(
                                title = "Tombol volume membalik halaman",
                                checked = volumeKeys,
                                onToggle = { volumeKeys = !volumeKeys; settings.volumeKeysTurnPages = volumeKeys }
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(20.dp))
                        GroupTitle("Halaman PDF")
                        SettingsCard {
                            ChoiceRow(
                                title = "Warna kertas",
                                options = listOf("Terang", "Sepia", "Redup", "Hitam"),
                                selected = tone.ordinal,
                                onSelect = {
                                    tone = PaperTone.entries[it]
                                    saveReader(settings) { it.copy(tone = tone) }
                                }
                            )
                            Divider()
                            ChoiceRow(
                                title = "Filter gambar",
                                options = listOf("Normal", "Invert", "Abu-abu", "Kontras tinggi"),
                                selected = filter.ordinal,
                                onSelect = {
                                    filter = PageFilter.entries[it]
                                    saveReader(settings) { it.copy(filter = filter) }
                                }
                            )
                            Divider()
                            ChoiceRow(
                                title = "Tata letak",
                                options = listOf("Per halaman", "Gulir terus"),
                                selected = if (layout == ReadingLayout.PAGED) 0 else 1,
                                onSelect = {
                                    layout = if (it == 0) ReadingLayout.PAGED else ReadingLayout.CONTINUOUS
                                    saveReader(settings) { it.copy(layout = layout) }
                                }
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(20.dp))
                        GroupTitle("AI Auto-Baca")
                        SettingsCard {
                            SliderRow(
                                title = "Kecepatan bicara",
                                valueLabel = Format.speechRateLabel(rate),
                                value = rate,
                                range = 0.5f..2f,
                                steps = 14,
                                onChange = { rate = it },
                                onCommit = { settings.speechRate = rate }
                            )
                            Divider()
                            SliderRow(
                                title = "Nada suara",
                                valueLabel = String.format(java.util.Locale.US, "%.2f", pitch),
                                value = pitch,
                                range = 0.5f..1.6f,
                                steps = 10,
                                onChange = { pitch = it },
                                onCommit = { settings.speechPitch = pitch }
                            )
                            Divider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // A throwaway engine is enough to launch the system
                                        // intent; the real one belongs to the reader screen.
                                        runCatching { tts.openTtsSettings() }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.SettingsVoice,
                                    contentDescription = null,
                                    tint = Violet500,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Mesin suara Android", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        engineName ?: "Belum terdeteksi — pasang Google Text-to-Speech",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Divider()
                            SwitchRow(
                                title = "Jeda alami antar kalimat",
                                subtitle = "Menambah napas pendek di akhir kalimat",
                                checked = naturalPauses,
                                onToggle = {
                                    naturalPauses = !naturalPauses
                                    settings.naturalPauses = naturalPauses
                                }
                            )
                            Divider()
                            SwitchRow(
                                title = "Lewati header & footer berulang",
                                subtitle = "Tidak membacakan judul bab di setiap halaman",
                                checked = skipHeaders,
                                onToggle = {
                                    skipHeaders = !skipHeaders
                                    settings.skipHeaders = skipHeaders
                                }
                            )
                            Divider()
                            SwitchRow(
                                title = "Sorot kata yang sedang dibaca",
                                checked = highlight,
                                onToggle = {
                                    highlight = !highlight
                                    settings.highlightWords = highlight
                                }
                            )
                            Divider()
                            SwitchRow(
                                title = "Ikuti bacaan otomatis",
                                checked = autoFollow,
                                onToggle = {
                                    autoFollow = !autoFollow
                                    settings.autoScrollFollow = autoFollow
                                }
                            )
                            Divider()
                            SwitchRow(
                                title = "Langsung bacakan saat dibuka",
                                checked = autoPlay,
                                onToggle = {
                                    autoPlay = !autoPlay
                                    settings.autoPlayOnOpen = autoPlay
                                }
                            )
                            Divider()
                            ChoiceRow(
                                title = "Pewaktu tidur bawaan",
                                options = listOf("Mati", "5 mnt", "10 mnt", "15 mnt", "30 mnt", "60 mnt"),
                                selected = sleepPresets.indexOf(sleepMinutes).coerceAtLeast(0),
                                onSelect = {
                                    sleepMinutes = sleepPresets[it]
                                    settings.sleepTimerMinutes = sleepMinutes
                                }
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(20.dp))
                        GroupTitle("Tentang")
                        SettingsCard {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenAbout)
                                    .padding(horizontal = 14.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Ember500,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Tentang Lumina PDF", modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Semua pemrosesan terjadi di perangkat. Tidak ada dokumen yang diunggah ke internet.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

private val sleepPresets = listOf(0, 5, 10, 15, 30, 60)

private fun saveReader(
    repo: SettingsRepository,
    transform: (com.lumina.pdfai.data.ReaderSettings) -> com.lumina.pdfai.data.ReaderSettings
) {
    repo.saveReaderSettings(transform(repo.readerSettings()))
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
        content = content
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

/** Segmented chips rather than a dropdown: no extra tap, everything visible. */
@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { index, option ->
                val active = index == selected
                Surface(
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                    contentColor = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable { onSelect(index) }
                ) {
                    Box(
                        Modifier.padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            option,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = Mint400)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onCommit,
            valueRange = range,
            steps = steps
        )
    }
}

package com.lumina.pdfai.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumina.pdfai.ai.VoiceOption
import com.lumina.pdfai.ui.theme.Ember500
import com.lumina.pdfai.ui.theme.Mint400
import com.lumina.pdfai.util.Format

/**
 * Voice picker.
 *
 * The list is sorted by the engine's naturalness score, so the first entry is
 * already the best available voice — the user only has to intervene if they
 * dislike it.
 */
@Composable
internal fun VoiceSheet(
    voices: List<VoiceOption>,
    current: String,
    languageLabel: String,
    onSelect: (VoiceOption) -> Unit,
    onOpenTtsSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pilih suara", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (languageLabel.isBlank()) "Urut dari yang paling natural"
                        else "Bahasa terdeteksi: $languageLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (voices.isEmpty()) {
                Text(
                    "Perangkat belum menyediakan suara untuk bahasa ini. Pasang paket suara Google Text-to-Speech, lalu kembali ke sini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onOpenTtsSettings) {
                    Icon(Icons.Filled.SettingsVoice, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Buka pengaturan mesin suara")
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(voices, key = { it.id }) { voice ->
                        val selected = voice.name == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable { onSelect(voice) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    voice.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    QualityBadge(voice)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        voice.locale.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Terpilih",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = onOpenTtsSettings) {
                    Icon(Icons.Filled.SettingsVoice, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kelola mesin suara di pengaturan Android")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QualityBadge(voice: VoiceOption) {
    val (label, tint) = when {
        voice.quality >= 400 -> "Sangat natural" to Mint400
        voice.quality >= 300 -> "Natural" to Mint400
        voice.needsNetwork -> "Online" to Ember500
        else -> "Hemat data" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.14f),
        contentColor = tint
    ) {
        Row(
            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (voice.needsNetwork) {
                Icon(
                    Icons.Filled.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
        }
    }
}

/** Speed, pitch, follow and highlight: everything that shapes the voice. */
@Composable
internal fun TuningSheet(
    speed: Float,
    pitch: Float,
    autoFollow: Boolean,
    highlight: Boolean,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onToggleFollow: () -> Unit,
    onToggleHighlight: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localSpeed by remember { mutableStateOf(speed) }
    var localPitch by remember { mutableStateOf(pitch) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text("Nada & tempo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sedikit lebih lambat dengan pitch rendah terdengar paling manusiawi.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kecepatan", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Format.speechRateLabel(localSpeed), style = MaterialTheme.typography.labelLarge)
            }
            Slider(
                value = localSpeed,
                onValueChange = { localSpeed = it },
                onValueChangeFinished = { onSpeed(localSpeed) },
                valueRange = 0.5f..2f,
                steps = 14
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0,5× santai", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("2× cepat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nada suara", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(String.format(java.util.Locale.US, "%.2f", localPitch), style = MaterialTheme.typography.labelLarge)
            }
            Slider(
                value = localPitch,
                onValueChange = { localPitch = it },
                onValueChangeFinished = { onPitch(localPitch) },
                valueRange = 0.5f..1.6f
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Berat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ringan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(18.dp))
            SwitchRow(
                title = "Ikuti bacaan otomatis",
                subtitle = "Sorotan pindah halaman mengikuti suara",
                checked = autoFollow,
                onToggle = onToggleFollow
            )
            SwitchRow(
                title = "Sorot kata yang dibaca",
                subtitle = "Warna berbeda pada kata yang sedang diucapkan",
                checked = highlight,
                onToggle = onToggleHighlight
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

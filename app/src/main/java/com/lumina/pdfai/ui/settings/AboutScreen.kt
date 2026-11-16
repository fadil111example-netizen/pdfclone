package com.lumina.pdfai.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumina.pdfai.ui.components.AuroraBackground
import com.lumina.pdfai.ui.theme.BrandGradient
import com.lumina.pdfai.ui.theme.Ember500
import com.lumina.pdfai.ui.theme.Mint400
import com.lumina.pdfai.ui.theme.Violet500
import com.lumina.pdfai.ui.theme.isDark

/** Credits, privacy posture and an honest list of what the app does *not* do. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
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
                    Text("Tentang", style = MaterialTheme.typography.titleLarge)
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(BrandGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Lumina PDF",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Baca. Dengar. Pahami.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Versi ${appVersion()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(22.dp))
                    }

                    item {
                        FeatureCard(
                            icon = Icons.Filled.AutoAwesome,
                            tint = Violet500,
                            title = "AI Auto-Baca",
                            body = "Teks PDF dipecah menjadi kalimat, dibersihkan dari header berulang, " +
                                "lalu dibacakan dengan jeda yang wajar. Kata yang sedang diucapkan " +
                                "disorot, dan halaman mengikuti suara."
                        )
                    }
                    item {
                        Spacer(Modifier.height(12.dp))
                        FeatureCard(
                            icon = Icons.Filled.RecordVoiceOver,
                            tint = Ember500,
                            title = "Suara yang dipilih, bukan bawaan",
                            body = "Aplikasi memilih suara paling natural yang tersedia di perangkat " +
                                "Anda (biasanya suara neural Google), lalu membiarkan Anda menggantinya " +
                                "kapan saja."
                        )
                    }
                    item {
                        Spacer(Modifier.height(12.dp))
                        FeatureCard(
                            icon = Icons.Filled.Lock,
                            tint = Mint400,
                            title = "Privasi penuh",
                            body = "Berkas, teks, penanda buku, dan riwayat bacaan tetap di perangkat. " +
                                "Tidak ada akun, tidak ada unggahan, tidak ada pelacakan."
                        )
                    }
                    item {
                        Spacer(Modifier.height(12.dp))
                        FeatureCard(
                            icon = Icons.Filled.WifiOff,
                            tint = MaterialTheme.colorScheme.primary,
                            title = "Bisa dipakai tanpa internet",
                            body = "Pembacaan PDF dan pemrosesan teks bekerja luring. Hanya suara " +
                                "online yang membutuhkan koneksi, dan itu pun pilihan Anda."
                        )
                    }

                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Dibangun dengan Kotlin dan Jetpack Compose. " +
                                "Pembacaan PDF memakai Android PdfRenderer, ekstraksi teks memakai PDFBox-Android.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Lumina PDF tidak mengubah, menggabungkan, atau menandatangani berkas PDF. " +
                                "Dokumen hasil pindai tanpa lapisan teks belum bisa dibacakan.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(36.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    body: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.15f),
            contentColor = tint
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun appVersion(): String = runCatching {
    val app = com.lumina.pdfai.LuminaApp.instance
    val info = app.packageManager.getPackageInfo(app.packageName, 0)
    info.versionName ?: "1.0"
}.getOrDefault("1.0")

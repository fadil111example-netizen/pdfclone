package com.lumina.pdfai.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumina.pdfai.data.PdfEntry
import com.lumina.pdfai.ui.theme.Ember500
import com.lumina.pdfai.ui.theme.paperGradient
import com.lumina.pdfai.util.Format

/**
 * Generated book cover: deterministic gradient + initials + page count.
 * No network, no bundled artwork, and every document still looks distinct.
 */
@Composable
fun DocumentCover(
    entry: PdfEntry,
    modifier: Modifier = Modifier,
    corner: Int = 16
) {
    val shape = RoundedCornerShape(corner.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(paperGradient(Format.coverSeed(entry.uri)))
    ) {
        // spine highlight
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        startX = 0f,
                        endX = 90f
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Format.initials(entry.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.94f),
                maxLines = 1
            )
            Column {
                if (entry.pageCount > 0) {
                    Text(
                        "${entry.pageCount} hal.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                if (entry.progressFraction > 0.02f) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { entry.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.28f),
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
            }
        }
        if (entry.readSeconds > 0) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.28f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

/** Wide library row with cover, progress and an AI badge. */
@Composable
fun DocumentRow(
    entry: PdfEntry,
    onClick: () -> Unit,
    onListen: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.66f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocumentCover(entry, Modifier.size(width = 56.dp, height = 74.dp), corner = 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(Format.bytes(entry.sizeBytes))
                    if (entry.pageCount > 0) append(" · ${entry.pageCount} hal.")
                    if (entry.lastPage > 0) append(" · hal. ${entry.lastPage + 1}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (entry.snippet.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier
                    .size(38.dp)
                    .clickable(onClick = onListen)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AutoStories,
                        contentDescription = "Bacakan dengan AI",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            if (entry.favorite) {
                Spacer(Modifier.height(6.dp))
                Icon(
                    Icons.Filled.Bookmark,
                    contentDescription = "Favorit",
                    tint = Ember500,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** Compact tile used by the "continue reading" carousel. */
@Composable
fun ContinueCard(
    entry: PdfEntry,
    onOpen: () -> Unit,
    onListen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(148.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
    ) {
        DocumentCover(entry, Modifier.fillMaxWidth().height(186.dp), corner = 18)
        Spacer(Modifier.height(8.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (entry.pageCount > 0) "${Format.percent(entry.progressFraction)} selesai" else "PDF",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Outlined.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
            modifier = Modifier.clickable(onClick = onListen)
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Dengarkan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumina.pdfai.data.Bookmark
import com.lumina.pdfai.data.PageFilter
import com.lumina.pdfai.data.PaperTone
import com.lumina.pdfai.data.ReadingLayout
import com.lumina.pdfai.pdf.OutlineNode
import com.lumina.pdfai.ui.theme.Ember500
import com.lumina.pdfai.ui.theme.Violet500
import com.lumina.pdfai.util.Format

@Composable
internal fun ReaderTopBar(
    state: ReaderUiState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOutline: () -> Unit,
    onListen: () -> Unit,
    onMenu: () -> Unit,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onPickTone: () -> Unit,
    toneMenuOpen: Boolean,
    onToneDismiss: () -> Unit,
    onTone: (PaperTone) -> Unit,
    onCycleFilter: () -> Unit
) {
    val bookmarked = state.bookmarks.any { it.page == state.currentPage }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        Format.pageLabel(state.currentPage, state.pageCount) +
                            if (state.zoom > 1.01f) " · " + Format.zoomLabel(state.zoom) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Cari")
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (bookmarked) "Hapus penanda" else "Tandai halaman",
                        tint = if (bookmarked) Ember500 else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                }
            }
        }
    }

    DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
        DropdownMenuItem(
            text = { Text("Daftar isi") },
            leadingIcon = { Icon(Icons.Filled.FormatListBulleted, contentDescription = null) },
            onClick = { onMenuDismiss(); onOutline() }
        )
        DropdownMenuItem(
            text = { Text("Warna kertas") },
            leadingIcon = { Icon(Icons.Filled.Brightness6, contentDescription = null) },
            onClick = { onMenuDismiss(); onPickTone() }
        )
        DropdownMenuItem(
            text = {
                Text(
                    when (state.settings.filter) {
                        PageFilter.NONE -> "Filter: normal"
                        PageFilter.INVERT -> "Filter: gelap (invert)"
                        PageFilter.GRAYSCALE -> "Filter: abu-abu"
                        PageFilter.HIGH_CONTRAST -> "Filter: kontras tinggi"
                    }
                )
            },
            leadingIcon = { Icon(Icons.Filled.Contrast, contentDescription = null) },
            onClick = { onMenuDismiss(); onCycleFilter() }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Bacakan dengan AI") },
            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Violet500) },
            onClick = { onMenuDismiss(); onListen() }
        )
    }

    DropdownMenu(expanded = toneMenuOpen, onDismissRequest = onToneDismiss) {
        PaperTone.entries.forEach { tone ->
            DropdownMenuItem(
                text = {
                    Text(
                        when (tone) {
                            PaperTone.LIGHT -> "Terang"
                            PaperTone.SEPIA -> "Sepia"
                            PaperTone.DIM -> "Redup"
                            PaperTone.BLACK -> "Hitam"
                        },
                        fontWeight = if (tone == state.settings.tone) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { onTone(tone); onToneDismiss() }
            )
        }
    }
}

@Composable
internal fun ReaderBottomBar(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onListen: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.pageCount > 0) "${state.currentPage + 1}" else "—",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = state.currentPage.toFloat(),
                    onValueChange = { viewModel.onPageScrubbed(it.toInt()) },
                    onValueChangeFinished = { viewModel.commitScrub() },
                    valueRange = 0f..(state.pageCount - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    if (state.pageCount > 0) "${state.pageCount}" else "—",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = { viewModel.setZoom(state.zoom - 0.25f) }) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "Perkecil")
                    }
                    IconButton(onClick = { viewModel.setZoom(state.zoom + 0.25f) }) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Perbesar")
                    }
                    IconButton(onClick = {
                        viewModel.updateSettings(
                            state.settings.copy(
                                layout = if (state.settings.layout == ReadingLayout.CONTINUOUS)
                                    ReadingLayout.PAGED else ReadingLayout.CONTINUOUS
                            )
                        )
                    }) {
                        Icon(
                            if (state.settings.layout == ReadingLayout.CONTINUOUS)
                                Icons.Filled.ViewAgenda else Icons.Filled.MenuBook,
                            contentDescription = "Ganti mode baca"
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.clickable(onClick = onListen)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Bacakan", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchSheet(
    query: String,
    hits: List<SearchHit>,
    searching: Boolean,
    progress: Float,
    onQuery: (String) -> Unit,
    onSelect: (SearchHit) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Cari di dokumen", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Kata atau kalimat") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Bersihkan")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (searching) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    searching -> "Mencari…"
                    query.length < 2 -> "Ketik minimal 2 huruf"
                    hits.isEmpty() -> "Tidak ditemukan"
                    else -> "${hits.size} hasil"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .padding(top = 8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(hits) { hit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(hit) }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            "Halaman ${hit.page + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            hit.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun OutlineSheet(
    outline: List<OutlineNode>,
    bookmarks: List<Bookmark>,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Navigasi", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .height(460.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                if (bookmarks.isNotEmpty()) {
                    item {
                        Text(
                            "Penanda",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(bookmarks, key = { "bm-${it.page}" }) { bookmark ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onJump(bookmark.page) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = null,
                                tint = Ember500,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bookmark.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Halaman ${bookmark.page + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onRemoveBookmark(bookmark.page) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Hapus", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }

                if (outline.isEmpty()) {
                    item {
                        Text(
                            "Dokumen ini tidak punya daftar isi (outline). Gunakan penggeser halaman di bawah, atau cari kata kunci.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                } else {
                    val rows = outline.flattenForList()
                    // A chapter is "active" while the current page sits between its
                    // start and the start of the next chapter at the same depth.
                    val activeIndex = rows.indexOfLast { it.pageIndex in 0..currentPage }
                    itemsIndexed(rows, key = { _, node -> "${node.title}-${node.pageIndex}-${node.depth}" }) { index, node ->
                        val active = index == activeIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable(enabled = node.pageIndex >= 0) { onJump(node.pageIndex) }
                                .padding(
                                    vertical = 8.dp,
                                    horizontal = (node.depth * 14).dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                node.title,
                                style = if (node.depth == 0) MaterialTheme.typography.bodyMedium
                                else MaterialTheme.typography.bodySmall,
                                fontWeight = when {
                                    active -> FontWeight.Bold
                                    node.depth == 0 -> FontWeight.SemiBold
                                    else -> FontWeight.Normal
                                },
                                color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (node.pageIndex >= 0) {
                                Text(
                                    "${node.pageIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Flattens the outline tree into drawable rows. */
internal fun List<OutlineNode>.flattenForList(): List<OutlineNode> {
    val out = mutableListOf<OutlineNode>()
    fun walk(nodes: List<OutlineNode>) {
        nodes.forEach { node ->
            out += node
            if (node.children.isNotEmpty()) walk(node.children)
        }
    }
    walk(this)
    return out
}

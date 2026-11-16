package com.lumina.pdfai.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumina.pdfai.data.PdfEntry
import com.lumina.pdfai.ui.components.AuroraBackground
import com.lumina.pdfai.ui.components.ContinueCard
import com.lumina.pdfai.ui.components.DocumentRow
import com.lumina.pdfai.ui.components.EmptyState
import com.lumina.pdfai.ui.theme.GradientSurface
import com.lumina.pdfai.ui.components.SectionHeader
import com.lumina.pdfai.ui.components.StatChip
import com.lumina.pdfai.ui.theme.BrandGradient
import com.lumina.pdfai.ui.theme.Ember500
import com.lumina.pdfai.ui.theme.Mint400
import com.lumina.pdfai.ui.theme.Violet500
import com.lumina.pdfai.ui.theme.isDark
import com.lumina.pdfai.ui.viewmodel.LibrarySort
import com.lumina.pdfai.ui.viewmodel.LibraryViewModel
import com.lumina.pdfai.util.Format
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: LibraryViewModel,
    onOpenDocument: (String) -> Unit,
    onListenDocument: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<PdfEntry?>(null) }
    var sortMenu by remember { mutableStateOf(false) }

    val pickSingle = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.import(uri) { key -> onOpenDocument(key) }
        }
    }

    // One-tap explanation the first time the library is empty.
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.consumeMessage()
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus dari pustaka?") },
            text = {
                Text(
                    "“${entry.name}” akan dihapus dari daftar. Berkas aslinya di perangkat Anda tidak disentuh."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(entry.uri)
                    pendingDelete = null
                    scope.launch { snackbar.showSnackbar("Dihapus dari pustaka") }
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Batal") }
            }
        )
    }

    AuroraBackground(dark = MaterialTheme.colorScheme.background.isDark()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) }
        ) { insets ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    Header(
                        state = state,
                        onOpenSettings = onOpenSettings,
                        onOpenAbout = onOpenAbout,
                        onRescan = { viewModel.scanDevice() }
                    )
                }
                item { Spacer(Modifier.height(14.dp)) }
                item {
                    AiHeroCard(
                        documentCount = state.all.size,
                        onListenLast = {
                            val target = state.recent.firstOrNull() ?: state.all.firstOrNull()
                            if (target != null) onListenDocument(target.uri)
                        },
                        onScan = { viewModel.scanDevice() },
                        onPick = { pickSingle.launch(arrayOf("application/pdf")) },
                        scanning = state.scanning,
                        hasDocuments = state.all.isNotEmpty()
                    )
                }

                if (state.recent.isNotEmpty()) {
                    item { Spacer(Modifier.height(22.dp)) }
                    item {
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            SectionHeader(
                                title = "Lanjutkan mendengarkan",
                                subtitle = "AI menyimpan posisi terakhir Anda",
                                actionLabel = "Semua",
                                onAction = { viewModel.setSort(LibrarySort.RECENT) }
                            )
                        }
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(state.recent, key = { it.uri }) { entry ->
                                ContinueCard(
                                    entry = entry,
                                    onOpen = { onOpenDocument(entry.uri) },
                                    onListen = { onListenDocument(entry.uri) }
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(10.dp)) }
                item {
                    SearchAndSort(
                        query = state.query,
                        onQueryChange = viewModel::setQuery,
                        sort = state.sort,
                        onSort = viewModel::setSort,
                        onlyFavorites = state.onlyFavorites,
                        onToggleFavorites = viewModel::toggleFavorites,
                        menuOpen = sortMenu,
                        onMenuOpenChange = { sortMenu = it }
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }

                when {
                    !state.loaded -> item { LoadingBlock() }
                    state.visible.isEmpty() -> item {
                        EmptyLibrary(
                            hasAny = state.all.isNotEmpty(),
                            query = state.query,
                            onPick = { pickSingle.launch(arrayOf("application/pdf")) },
                            onScan = { viewModel.scanDevice() }
                        )
                    }
                    else -> items(state.visible, key = { it.uri }) { entry ->
                        DocumentRow(
                            entry = entry,
                            onClick = { onOpenDocument(entry.uri) },
                            onListen = { onListenDocument(entry.uri) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        )
                    }
                }

                if (state.all.isNotEmpty()) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item { LibraryFootprint(state) }
                }
            }
        }

        if (state.all.isNotEmpty()) {
            FloatingBar(
                onPick = { pickSingle.launch(arrayOf("application/pdf")) },
                onScan = { viewModel.scanDevice() }
            )
        }
    }
}

@Composable
private fun Header(
    state: com.lumina.pdfai.ui.viewmodel.LibraryUiState,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onRescan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Lumina",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Baca, atau biarkan AI yang membacakan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Pengaturan")
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Lainnya")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Tentang aplikasi") },
                    onClick = { menu = false; onOpenAbout() }
                )
                DropdownMenuItem(
                    text = { Text(if (state.scanning) "Memindai…" else "Pindai ulang perangkat") },
                    enabled = !state.scanning,
                    onClick = { menu = false; onRescan() }
                )
            }
        }
    }
}

@Composable
private fun AiHeroCard(
    documentCount: Int,
    hasDocuments: Boolean,
    scanning: Boolean,
    onListenLast: () -> Unit,
    onScan: () -> Unit,
    onPick: () -> Unit
) {
    GradientSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(26.dp),
        brush = BrandGradient
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "AI Auto-Baca",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        "Suara natural, bukan robot",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (hasDocuments) {
                    "Ketuk putar, lalu dengarkan $documentCount dokumen Anda dibacakan dengan jeda dan intonasi yang wajar."
                } else {
                    "Tambahkan PDF, tekan putar, dan biarkan AI membacakannya untuk Anda. Mata boleh istirahat."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.94f)
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    contentColor = Color(0xFF3B1E8C),
                    modifier = Modifier.clickable(onClick = if (hasDocuments) onListenLast else onPick)
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (hasDocuments) Icons.Filled.PlayArrow else Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (hasDocuments) "Putar sekarang" else "Pilih PDF",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                    modifier = Modifier.clickable(onClick = onScan)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (scanning) "Memindai…" else "Pindai HP",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAndSort(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: LibrarySort,
    onSort: (LibrarySort) -> Unit,
    onlyFavorites: Boolean,
    onToggleFavorites: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Cari judul atau penulis") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Bersihkan")
                        }
                    }
                    Box {
                        IconButton(onClick = { onMenuOpenChange(true) }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Urutkan")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { onMenuOpenChange(false) }
                        ) {
                            LibrarySort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (option) {
                                                LibrarySort.RECENT -> "Terakhir dibuka"
                                                LibrarySort.NAME -> "Nama A–Z"
                                                LibrarySort.SIZE -> "Ukuran terbesar"
                                                LibrarySort.PROGRESS -> "Paling banyak dibaca"
                                            },
                                            fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = { onSort(option); onMenuOpenChange(false) }
                                )
                            }
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                label = "Favorit",
                selected = onlyFavorites,
                icon = Icons.Filled.Bookmark,
                onClick = onToggleFavorites
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@Composable
private fun LoadingBlock() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text("Menyiapkan pustaka…", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyLibrary(
    hasAny: Boolean,
    query: String,
    onPick: () -> Unit,
    onScan: () -> Unit
) {
    if (hasAny && query.isNotBlank()) {
        EmptyState(
            icon = Icons.Filled.Search,
            title = "Tidak ditemukan",
            message = "Tidak ada dokumen yang cocok dengan “$query”."
        )
        return
    }
    Column {
        EmptyState(
            icon = Icons.Filled.PictureAsPdf,
            title = "Belum ada dokumen",
            message = "Tambahkan PDF dari perangkat Anda. AI akan membacakannya dengan suara yang enak didengar.",
            actionLabel = "Pilih PDF",
            onAction = onPick
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onScan) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cari PDF di perangkat ini")
            }
        }
    }
}

@Composable
private fun LibraryFootprint(state: com.lumina.pdfai.ui.viewmodel.LibraryUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(Icons.Filled.PictureAsPdf, "${state.all.size} dokumen", tint = Violet500)
            StatChip(Icons.Filled.AutoAwesome, "${state.totalPages} halaman", tint = Ember500)
            StatChip(
                Icons.Filled.Timer,
                Format.duration(state.totalListeningSeconds) + " didengar",
                tint = Mint400
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Semua proses — pembacaan PDF maupun suara AI — terjadi di perangkat Anda.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FloatingBar(onPick: () -> Unit, onScan: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 10.dp,
            modifier = Modifier.clickable(onClick = onPick)
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah PDF")
                Spacer(Modifier.width(8.dp))
                Text("Tambah PDF", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}


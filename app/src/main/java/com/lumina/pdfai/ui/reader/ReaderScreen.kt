package com.lumina.pdfai.ui.reader

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumina.pdfai.data.PageFilter
import com.lumina.pdfai.data.PaperTone
import com.lumina.pdfai.data.ReadingLayout
import com.lumina.pdfai.ui.theme.Ember500
import com.lumina.pdfai.ui.theme.PaperBlack
import com.lumina.pdfai.ui.theme.PaperDim
import com.lumina.pdfai.ui.theme.PaperLight
import com.lumina.pdfai.ui.theme.PaperSepia
import com.lumina.pdfai.util.VolumeKeyBus

/**
 * Route entry point: owns the view model, opens the document, keeps the screen
 * awake and maps the volume rocker to page turns.
 *
 * The shared render engine is deliberately *not* closed here — [ReaderViewModel]
 * owns the bitmaps and releases them, while the engine itself outlives the
 * screen so returning to a document is instant.
 */
@Composable
fun ReaderRoute(
    documentUri: String,
    onBack: () -> Unit,
    onListen: (String) -> Unit
) {
    val viewModel: ReaderViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(documentUri) { viewModel.open(documentUri) }

    DisposableEffect(Unit) {
        onDispose { viewModel.close() }
    }

    // Keep-awake while reading a page, never while the chrome is hidden in the
    // middle of a menu.
    DisposableEffect(state.settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (state.settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Volume keys as page turns: the single most requested reader shortcut.
    // The handler lives in a process-wide bus because a View tag would never be
    // consulted: key events are dispatched by the Activity, not the window.
    DisposableEffect(state.settings.volumeKeysTurnPages) {
        if (!state.settings.volumeKeysTurnPages) {
            onDispose { }
        } else {
            val handler: (Int) -> Boolean = { keyCode ->
                val value = viewModel.state.value
                when {
                    keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP -> false
                    value.pageCount == 0 || value.loading -> false
                    keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        viewModel.onPageChanged((value.currentPage + 1).coerceAtMost(value.pageCount - 1))
                        true
                    }
                    else -> {
                        viewModel.onPageChanged((value.currentPage - 1).coerceAtLeast(0))
                        true
                    }
                }
            }
            VolumeKeyBus.register(handler)
            onDispose { VolumeKeyBus.unregister(handler) }
        }
    }

    ReaderScreen(
        state = state,
        viewModel = viewModel,
        onBack = onBack,
        onListen = { onListen(documentUri) }
    )
}

/** Paper colour behind the rendered pages. */
internal fun toneColor(tone: PaperTone): Color = when (tone) {
    PaperTone.LIGHT -> PaperLight
    PaperTone.SEPIA -> PaperSepia
    PaperTone.DIM -> PaperDim
    PaperTone.BLACK -> PaperBlack
}

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onListen: () -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showToneMenu by remember { mutableStateOf(false) }

    val paper = toneColor(state.settings.tone)
    val chromeVisible = state.showChrome && !state.loading && state.error == null

    Box(
        Modifier
            .fillMaxSize()
            .background(paper)
    ) {
        when {
            state.loading -> LoadingOverlay()
            state.error != null -> ErrorOverlay(message = state.error, onBack = onBack)
            state.settings.layout == ReadingLayout.PAGED -> PagedReader(state, viewModel)
            else -> ContinuousReader(state, viewModel)
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                state = state,
                onBack = onBack,
                onSearch = { showSearch = true },
                onOutline = { showOutline = true },
                onListen = onListen,
                onMenu = { showMenu = true },
                menuOpen = showMenu,
                onMenuDismiss = { showMenu = false },
                onToggleBookmark = {
                    if (state.bookmarks.any { it.page == state.currentPage }) {
                        viewModel.removeBookmark(state.currentPage)
                    } else {
                        viewModel.addBookmark("Halaman ${state.currentPage + 1}")
                    }
                },
                onPickTone = { showToneMenu = true },
                toneMenuOpen = showToneMenu,
                onToneDismiss = { showToneMenu = false },
                onTone = viewModel::setTone,
                onCycleFilter = {
                    viewModel.setFilter(
                        when (state.settings.filter) {
                            PageFilter.NONE -> PageFilter.INVERT
                            PageFilter.INVERT -> PageFilter.GRAYSCALE
                            PageFilter.GRAYSCALE -> PageFilter.HIGH_CONTRAST
                            PageFilter.HIGH_CONTRAST -> PageFilter.NONE
                        }
                    )
                }
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                state = state,
                viewModel = viewModel,
                onListen = onListen
            )
        }
    }

    if (showSearch) {
        SearchSheet(
            query = state.searchQuery,
            hits = state.searchHits,
            searching = state.searching,
            progress = state.searchProgress,
            onQuery = viewModel::search,
            onSelect = { hit ->
                viewModel.onPageChanged(hit.page)
                showSearch = false
            },
            onDismiss = { showSearch = false }
        )
    }

    if (showOutline) {
        OutlineSheet(
            outline = state.outline,
            bookmarks = state.bookmarks,
            currentPage = state.currentPage,
            onJump = { page ->
                viewModel.onPageChanged(page)
                showOutline = false
            },
            onRemoveBookmark = viewModel::removeBookmark,
            onDismiss = { showOutline = false }
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let { viewModel.consumeMessage() }
    }
}

@Composable
private fun LoadingOverlay() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, color = Ember500)
        Spacer(Modifier.height(14.dp))
        Text(
            "Membuka dokumen…",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun ErrorOverlay(message: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Gagal membuka", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        Spacer(Modifier.height(18.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .pointerInput(Unit) { detectTapGestures { onBack() } }
        ) {
            Text(
                "Kembali",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp)
            )
        }
    }
}

package com.lumina.pdfai.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/** Continuous vertical scrolling, the default reading mode. */
@Composable
internal fun ContinuousReader(
    state: ReaderUiState,
    viewModel: ReaderViewModel
) {
    if (state.pageCount == 0) return
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.currentPage.coerceIn(0, state.pageCount - 1)
    )
    var reported by remember { mutableIntStateOf(state.currentPage) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val page = index.coerceIn(0, state.pageCount - 1)
                reported = page
                viewModel.onPageChanged(page)
            }
    }

    // Jumps coming from search, the outline or the slider must move the list.
    LaunchedEffect(state.currentPage) {
        if (state.currentPage != reported) {
            reported = state.currentPage
            runCatching { listState.scrollToItem(state.currentPage) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 74.dp, bottom = 108.dp, start = 10.dp, end = 10.dp)
    ) {
        items(
            count = state.pageCount,
            key = { index -> "page-$index" }
        ) { index ->
            PdfPage(
                state = state,
                viewModel = viewModel,
                page = index,
                modifier = Modifier.fillMaxWidth(),
                onTap = { viewModel.toggleChrome() }
            )
        }
    }
}

/** One page per screen, horizontally swipeable, with pinch to zoom. */
@Composable
internal fun PagedReader(
    state: ReaderUiState,
    viewModel: ReaderViewModel
) {
    if (state.pageCount == 0) return
    val pagerState = rememberPagerState(
        initialPage = state.currentPage.coerceIn(0, state.pageCount - 1),
        pageCount = { state.pageCount }
    )
    var reported by remember { mutableIntStateOf(state.currentPage) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                reported = page
                viewModel.onPageChanged(page)
            }
    }

    LaunchedEffect(state.currentPage) {
        if (state.currentPage != reported) {
            reported = state.currentPage
            runCatching { pagerState.animateScrollToPage(state.currentPage) }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 84.dp),
        pageSpacing = 12.dp
    ) { page ->
        PdfPage(
            state = state,
            viewModel = viewModel,
            page = page,
            modifier = Modifier.fillMaxSize(),
            zoomable = true,
            onTap = { viewModel.toggleChrome() }
        )
    }
}

/**
 * A single rendered page with its own zoom/pan state.
 *
 * While the bitmap is being produced a placeholder with the correct aspect ratio
 * is shown, so the scroll position never jumps when the image lands.
 */
@Composable
internal fun PdfPage(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    page: Int,
    modifier: Modifier = Modifier,
    zoomable: Boolean = false,
    onTap: () -> Unit = {}
) {
    var widthPx by remember { mutableStateOf(0) }
    var aspect by remember(page) { mutableStateOf(1.414f) }
    var bitmap by remember(page) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(page) { mutableStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(page) { aspect = viewModel.pageAspect(page) }

    LaunchedEffect(page, widthPx, state.settings.filter) {
        if (widthPx <= 0) return@LaunchedEffect
        bitmap = viewModel.pageBitmap(page, widthPx)
    }

    val pageScale = scale * state.zoom

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width }
            .aspectRatio(1f / aspect)
            .clip(RoundedCornerShape(if (pageScale > 1.01f) 0.dp else 6.dp))
            .background(Color.White)
            .then(
                if (zoomable) {
                    // Pinch with two fingers zooms; a single finger is left to the
                    // pager so swiping between pages keeps working.
                    Modifier.pointerInput(page) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var active = true
                            while (active) {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2 || scale > 1.01f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val next = (scale * zoom).coerceIn(1f, 5f)
                                    scale = next
                                    offset = if (next <= 1.01f) Offset.Zero else offset + pan
                                    event.changes.forEach { it.consume() }
                                }
                                active = event.changes.any { it.pressed }
                            }
                        }
                    }
                } else Modifier
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .graphicsLayer {
                scaleX = pageScale
                scaleY = pageScale
                translationX = offset.x
                translationY = offset.y
                clip = pageScale > 1.01f
            }
    ) {
        val current = bitmap
        if (current != null && !current.isRecycled) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Halaman ${page + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 1.5.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Hal. ${page + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

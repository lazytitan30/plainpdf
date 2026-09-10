package com.leaf.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.leaf.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.leaf.app.data.pdf.read.PdfHandle
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Page-by-page horizontal reading. Renders through [PdfHandle] rather than the androidx
 * view, which only scrolls vertically. Each page is a bitmap at viewport width; pinch zoom
 * transforms it and re-renders sharper once the gesture settles.
 */
@Composable
fun PagedReader(
    handle: PdfHandle,
    startPage: Int,
    targetPage: Int?,
    onTargetConsumed: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onCentreTap: () -> Unit,
    doubleTapZoom: Boolean,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier,
) {
    val pageCount = handle.pageCount
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) { pageCount }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { onPageChanged(it) }
    }
    LaunchedEffect(targetPage) {
        val target = targetPage ?: return@LaunchedEffect
        if (target != pager.currentPage) pager.animateScrollToPage(target.coerceIn(0, pageCount - 1))
        onTargetConsumed()
    }

    HorizontalPager(
        state = pager,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it },
    ) { page ->
        ZoomablePage(
            handle = handle,
            page = page,
            onCentreTap = onCentreTap,
            doubleTapZoom = doubleTapZoom,
            colorFilter = colorFilter,
        )
    }
}

private const val MAX_ZOOM = 4f
private const val MAX_RENDER_WIDTH = 2400

@Composable
private fun ZoomablePage(
    handle: PdfHandle,
    page: Int,
    onCentreTap: () -> Unit,
    doubleTapZoom: Boolean,
    colorFilter: ColorFilter?,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.roundToPx() }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var settledScale by remember { mutableFloatStateOf(1f) }

        // Render at viewport width; when zoom settles above 1.5x, re-render sharper.
        val renderWidth = (viewportWidthPx * settledScale.coerceIn(1f, 2.5f)).toInt().coerceAtMost(MAX_RENDER_WIDTH)
        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = page, key2 = renderWidth) {
            value = runCatching { handle.renderPage(page, renderWidth) }
                .onFailure { android.util.Log.w("PagedReader", "render page $page at $renderWidth failed", it) }
                .getOrNull() ?: value
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(doubleTapZoom) {
                    detectTapGestures(
                        onTap = { pos ->
                            val third = size.width / 3f
                            if (pos.x in third..third * 2) onCentreTap()
                        },
                        onDoubleTap = { pos ->
                            if (!doubleTapZoom) return@detectTapGestures
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                val centre = Offset(size.width / 2f, size.height / 2f)
                                offset = (centre - pos) * (scale - 1f)
                            }
                            settledScale = scale
                        },
                    )
                }
                .pointerInput(Unit) {
                    // Claim drags only when pinching or already zoomed in; otherwise the
                    // pager keeps them so a plain swipe turns the page.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed > 1 || scale > 1.01f) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                val centre = Offset(size.width / 2f, size.height / 2f)
                                // Keep the point under the fingers fixed while zooming.
                                offset = (offset + (centroid - centre)) * (newScale / scale) - (centroid - centre) + pan
                                scale = newScale
                                if (scale <= 1.01f) offset = Offset.Zero
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        settledScale = scale
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.progress_loading_page),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
        }
    }
}

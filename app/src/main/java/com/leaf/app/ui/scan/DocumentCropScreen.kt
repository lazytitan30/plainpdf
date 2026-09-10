package com.leaf.app.ui.scan

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leaf.app.R
import com.leaf.app.ui.common.LoadingState
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.theme.Spacing
import com.leaf.app.ui.tools.common.ChoiceChips
import com.leaf.app.util.scan.DocumentImageProcessor
import com.leaf.app.util.scan.Quad
import com.leaf.app.util.scan.ScanLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Full-screen adjust step after a photo: drag the four corners onto the page edges, pick a
 * look, tap Use. The page outline is guessed automatically first. Rotate turns the photo.
 */
@Composable
fun DocumentCropScreen(
    source: Uri,
    outputFile: File,
    onDone: (File) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val processor = remember { DocumentImageProcessor(context.contentResolver) }
    val scope = rememberCoroutineScope()

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var quad by remember { mutableStateOf<Quad?>(null) }
    var look by remember { mutableStateOf(ScanLook.COLOR) }
    var rotation by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(source, rotation) {
        working = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                var bmp = processor.decode(source, maxEdge = 1200)
                if (rotation != 0) {
                    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true)
                    if (rotated !== bmp) { bmp.recycle(); bmp = rotated }
                }
                bmp to processor.detectQuad(bmp)
            }
        }
        result.onSuccess { (bmp, q) -> preview = bmp; quad = q; failed = false }.onFailure { Log.e(TAG, "Preview failed", it); failed = true }
        working = false
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF111111))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel), tint = Color.White) }
                Text(stringResource(R.string.crop_title), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { rotation = (rotation + 90) % 360 }, enabled = !working) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.crop_rotate), tint = Color.White)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = preview
                val q = quad
                when {
                    failed -> Text(stringResource(R.string.ops_error_corrupt), color = Color.White, modifier = Modifier.padding(Spacing.section))
                    bmp == null || q == null -> LoadingState(
                        stringResource(R.string.progress_preparing_photo),
                        color = Color.White,
                        textColor = Color(0xFFBBBBBB),
                    )
                    else -> CornerEditor(bitmap = bmp, quad = q, onQuadChange = { quad = it })
                }
            }
            Text(
                stringResource(R.string.crop_help),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFBBBBBB),
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                ChoiceChips(
                    options = ScanLook.entries,
                    selected = look,
                    label = { stringResource(when (it) { ScanLook.COLOR -> R.string.crop_look_color; ScanLook.GRAY -> R.string.crop_look_gray; ScanLook.BLACK_WHITE -> R.string.crop_look_bw }) },
                    onSelect = { look = it },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = 12.dp)) {
                QuireButton(
                    onClick = {
                        val q = quad ?: return@QuireButton
                        working = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { processor.process(source, q, look, rotation, outputFile) } }
                            working = false
                            result.onSuccess(onDone).onFailure { Log.e(TAG, "Processing failed", it); failed = true }
                        }
                    },
                    enabled = quad != null && !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (working) stringResource(R.string.crop_working) else stringResource(R.string.crop_use)) }
            }
        }
    }
}

private const val TAG = "Leaf/Crop"

/**
 * The photo fitted into the box with a draggable quadrilateral over it. A corner drags on
 * its own; a side drags both of its corners together, which is how you square up an edge
 * without walking two handles. While dragging, a loupe in a free corner of the picture
 * shows what is under the finger at 2.5x.
 */
@Composable
private fun CornerEditor(bitmap: Bitmap, quad: Quad, onQuadChange: (Quad) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 14.dp.toPx() }
    val grabRadiusPx = with(density) { 36.dp.toPx() }
    val sideGrabPx = with(density) { 28.dp.toPx() }
    val loupeRadiusPx = with(density) { 64.dp.toPx() }
    val loupeMarginPx = with(density) { 12.dp.toPx() }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        val aspect = bitmap.width.toFloat() / bitmap.height
        val boxW = maxWidth
        val boxH = maxHeight
        val imgW = if (boxW / boxH > aspect) boxH * aspect else boxW
        val imgH = imgW / aspect
        val imgWpx = with(density) { imgW.toPx() }
        val imgHpx = with(density) { imgH.toPx() }
        var drag by remember { mutableStateOf<DragTarget?>(null) }
        var finger by remember { mutableStateOf(Offset.Unspecified) }
        // The gesture must survive recomposition while corners move, so it reads the latest
        // quad through state instead of being keyed on it.
        val currentQuad by rememberUpdatedState(quad)
        val currentOnChange by rememberUpdatedState(onQuadChange)

        Box(Modifier.width(imgW).height(imgH)) {
            Image(image, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(imgWpx, imgHpx) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                val pts = currentQuad.points().map { Offset(it.x * imgWpx, it.y * imgHpx) }
                                drag = hitTest(pts, pos, grabRadiusPx, sideGrabPx)
                                finger = pos
                            },
                            onDrag = { change, delta ->
                                change.consume()
                                val target = drag ?: return@detectDragGestures
                                finger = change.position
                                val pts = currentQuad.points().map { PointF(it.x, it.y) }
                                val dx = delta.x / imgWpx
                                val dy = delta.y / imgHpx
                                for (i in target.corners) {
                                    pts[i].x = (pts[i].x + dx).coerceIn(0f, 1f)
                                    pts[i].y = (pts[i].y + dy).coerceIn(0f, 1f)
                                }
                                currentOnChange(Quad(pts[0], pts[1], pts[2], pts[3]))
                            },
                            onDragEnd = { drag = null; finger = Offset.Unspecified },
                            onDragCancel = { drag = null; finger = Offset.Unspecified },
                        )
                    },
            ) {
                val pts = quad.points().map { Offset(it.x * size.width, it.y * size.height) }
                val stroke = 3.dp.toPx()
                // Dim everything outside the page.
                val outside = Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                    op(this, polygon(pts), androidx.compose.ui.graphics.PathOperation.Difference)
                }
                drawPath(outside, Color.Black.copy(alpha = 0.45f))
                drawPath(polygon(pts), accent, style = Stroke(width = stroke))
                val active = drag
                if (active != null && active.corners.size == 2) {
                    val a = pts[active.corners[0]]
                    val b = pts[active.corners[1]]
                    drawLine(Color.White, a, b, strokeWidth = stroke * 2.5f, alpha = 0.6f)
                }
                pts.forEachIndexed { i, p ->
                    drawCircle(Color.White, handleRadiusPx, p)
                    drawCircle(accent, handleRadiusPx, p, style = Stroke(width = stroke))
                    if (active != null && i in active.corners) drawCircle(accent.copy(alpha = 0.3f), handleRadiusPx * 2f, p)
                }
                // Loupe: the region under the finger, magnified, in whichever corner of the
                // picture is furthest from the finger so it never sits under the hand.
                if (active != null && finger.isSpecified) {
                    val focus = if (active.corners.size == 1) pts[active.corners[0]] else finger
                    val zoom = 2.5f
                    val left = finger.x > size.width / 2f
                    val top = finger.y > size.height / 2f
                    val centre = Offset(
                        if (left) loupeRadiusPx + loupeMarginPx else size.width - loupeRadiusPx - loupeMarginPx,
                        if (top) loupeRadiusPx + loupeMarginPx else size.height - loupeRadiusPx - loupeMarginPx,
                    )
                    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(centre - Offset(loupeRadiusPx, loupeRadiusPx), androidx.compose.ui.geometry.Size(loupeRadiusPx * 2, loupeRadiusPx * 2))) }
                    clipPath(clip) {
                        drawRect(Color.Black)
                        // Source pixels around the focus point; the picture is displayed at size / bitmap scale.
                        val sx = image.width / size.width
                        val sy = image.height / size.height
                        val halfW = loupeRadiusPx / zoom * sx
                        val halfH = loupeRadiusPx / zoom * sy
                        val srcLeft = (focus.x * sx - halfW).roundToInt()
                        val srcTop = (focus.y * sy - halfH).roundToInt()
                        drawImage(
                            image = image,
                            srcOffset = IntOffset(srcLeft, srcTop),
                            srcSize = IntSize((halfW * 2).roundToInt(), (halfH * 2).roundToInt()),
                            dstOffset = IntOffset((centre.x - loupeRadiusPx).roundToInt(), (centre.y - loupeRadiusPx).roundToInt()),
                            dstSize = IntSize((loupeRadiusPx * 2).roundToInt(), (loupeRadiusPx * 2).roundToInt()),
                        )
                        // The outline as it appears around the focus point.
                        val mapped = pts.map { centre + (it - focus) * zoom }
                        drawPath(polygon(mapped), accent, style = Stroke(width = stroke))
                        mapped.forEach { drawCircle(Color.White, handleRadiusPx * 0.6f, it); drawCircle(accent, handleRadiusPx * 0.6f, it, style = Stroke(width = stroke)) }
                        // Crosshair at the focus.
                        val arm = loupeRadiusPx * 0.35f
                        drawLine(Color.White, centre - Offset(arm, 0f), centre + Offset(arm, 0f), strokeWidth = 1.5.dp.toPx(), alpha = 0.8f)
                        drawLine(Color.White, centre - Offset(0f, arm), centre + Offset(0f, arm), strokeWidth = 1.5.dp.toPx(), alpha = 0.8f)
                    }
                    drawCircle(Color.White, loupeRadiusPx, centre, style = Stroke(width = stroke))
                    drawCircle(Color.Black.copy(alpha = 0.4f), loupeRadiusPx + stroke, centre, style = Stroke(width = stroke))
                }
            }
        }
    }
}

/** What a drag moves: one corner, or the two corners of a side. */
private class DragTarget(val corners: List<Int>)

private fun polygon(pts: List<Offset>): Path = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (k in 1 until pts.size) lineTo(pts[k].x, pts[k].y)
    close()
}

/** Corners win over sides, and the nearest side within reach wins over nothing. */
private fun hitTest(pts: List<Offset>, pos: Offset, cornerRadius: Float, sideRadius: Float): DragTarget? {
    var best: DragTarget? = null
    var bestD = cornerRadius
    pts.forEachIndexed { i, p ->
        val d = (p - pos).getDistance()
        if (d < bestD) { bestD = d; best = DragTarget(listOf(i)) }
    }
    if (best != null) return best
    bestD = sideRadius
    for (i in pts.indices) {
        val j = (i + 1) % pts.size
        val d = distanceToSegment(pos, pts[i], pts[j])
        if (d < bestD) { bestD = d; best = DragTarget(listOf(i, j)) }
    }
    return best
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    if (len2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

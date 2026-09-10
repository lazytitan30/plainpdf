package com.leaf.app.ui.tools.sign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.leaf.app.ui.theme.QuireShape

/** Points normalised to 0..1 of the canvas so the same strokes render at any size. */
typealias InkStroke = List<Offset>

/** 3:1 drawing surface. Strokes are reported as normalised points; the caller owns the list. */
@Composable
fun SignatureCanvas(
    strokes: List<InkStroke>,
    ink: Color,
    onStrokeStart: (Offset) -> Unit,
    onStrokePoint: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    borderColor: Color = Color.Gray,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(SIGNATURE_ASPECT)
            .clip(QuireShape.Card)
            .background(background)
            .border(1.dp, borderColor, QuireShape.Card)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos -> onStrokeStart(Offset(pos.x / size.width, pos.y / size.height)) },
                    onDrag = { change, _ ->
                        change.consume()
                        onStrokePoint(Offset(change.position.x / size.width, change.position.y / size.height))
                    },
                )
            },
    ) {
        val strokeWidth = size.width * STROKE_WIDTH_FRACTION
        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            val path = Path()
            val first = stroke.first()
            path.moveTo(first.x * size.width, first.y * size.height)
            if (stroke.size == 1) path.lineTo(first.x * size.width + 0.1f, first.y * size.height)
            for (p in stroke.drop(1)) path.lineTo(p.x * size.width, p.y * size.height)
            drawPath(path, ink, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

const val SIGNATURE_ASPECT = 3f
/** Stroke width as a fraction of canvas width; identical on screen and in the exported PNG. */
const val STROKE_WIDTH_FRACTION = 0.008f

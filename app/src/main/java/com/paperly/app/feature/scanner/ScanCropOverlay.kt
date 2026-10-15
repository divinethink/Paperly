package com.paperly.app.feature.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paperly.app.domain.scanner.ScanPoint
import com.paperly.app.domain.scanner.ScanQuad

private const val HANDLES = 8 // 0..3 corners, 4..7 edge midpoints (edge i-4 runs corner i-4 -> corner i-3)
private const val CORNER_COUNT = 4
private val HIT_RADIUS = 44.dp
private val CORNER_HANDLE = 11.dp
private val MID_HANDLE = 7.dp
private val FRAME_STROKE = 3.dp
private val HANDLE_STROKE = 2.dp

/** Where the image sits inside the canvas (letterboxed). */
private class Fit(val left: Float, val top: Float, val width: Float, val height: Float)

private fun fit(box: IntSize, image: ImageBitmap): Fit {
    val scale = minOf(box.width.toFloat() / image.width, box.height.toFloat() / image.height)
    val w = image.width * scale
    val h = image.height * scale
    return Fit((box.width - w) / 2f, (box.height - h) / 2f, w, h)
}

private fun handlePoint(quad: ScanQuad, handle: Int): ScanPoint {
    val p = quad.points
    if (handle < CORNER_COUNT) return p[handle]
    val a = p[handle - CORNER_COUNT]
    val b = p[(handle - CORNER_COUNT + 1) % CORNER_COUNT]
    return ScanPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}

private fun moved(p: ScanPoint, dx: Float, dy: Float) =
    ScanPoint((p.x + dx).coerceIn(0f, 1f), (p.y + dy).coerceIn(0f, 1f))

/** Corner handles move one corner; edge handles move both corners of that edge. */
private fun moveHandle(quad: ScanQuad, handle: Int, dx: Float, dy: Float): ScanQuad {
    val p = quad.points.toMutableList()
    if (handle < CORNER_COUNT) {
        p[handle] = moved(p[handle], dx, dy)
    } else {
        val i = handle - CORNER_COUNT
        val j = (i + 1) % CORNER_COUNT
        p[i] = moved(p[i], dx, dy)
        p[j] = moved(p[j], dx, dy)
    }
    return ScanQuad.of(p)
}

/** The page image with a draggable 4-corner crop frame. Moves that would make the frame invalid are ignored. */
@Composable
fun ScanCropOverlay(
    image: ImageBitmap,
    quad: ScanQuad,
    onChange: (ScanQuad) -> Unit,
    modifier: Modifier = Modifier,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    var active by remember { mutableIntStateOf(-1) }
    val latest by rememberUpdatedState(quad)
    val hitRadius = with(LocalDensity.current) { HIT_RADIUS.toPx() }
    val cornerRadius = with(LocalDensity.current) { CORNER_HANDLE.toPx() }
    val midRadius = with(LocalDensity.current) { MID_HANDLE.toPx() }
    val line = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .onSizeChanged { box = it }
            .pointerInput(image) {
                detectDragGestures(
                    onDragStart = { start ->
                        val f = fit(box, image)
                        fun dist(h: Int): Float {
                            val pt = handlePoint(latest, h)
                            return (Offset(f.left + pt.x * f.width, f.top + pt.y * f.height) - start).getDistance()
                        }
                        active = (0 until HANDLES).minByOrNull(::dist)?.takeIf { dist(it) <= hitRadius } ?: -1
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                ) { change, drag ->
                    change.consume()
                    val f = fit(box, image)
                    if (active >= 0 && f.width > 0f && f.height > 0f) {
                        onChange(moveHandle(latest, active, drag.x / f.width, drag.y / f.height))
                    }
                }
            },
    ) {
        val f = fit(IntSize(size.width.toInt(), size.height.toInt()), image)
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(f.left.toInt(), f.top.toInt()),
            dstSize = IntSize(f.width.toInt(), f.height.toInt()),
        )
        fun at(pt: ScanPoint) = Offset(f.left + pt.x * f.width, f.top + pt.y * f.height)
        val frame = Path().apply {
            val pts = quad.points.map(::at)
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(frame, line, style = Stroke(width = FRAME_STROKE.toPx()))
        for (h in 0 until HANDLES) {
            val radius = if (h < CORNER_COUNT) cornerRadius else midRadius
            drawCircle(Color.White, radius, at(handlePoint(quad, h)))
            drawCircle(line, radius, at(handlePoint(quad, h)), style = Stroke(width = HANDLE_STROKE.toPx()))
        }
    }
}

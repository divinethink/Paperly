package com.paperly.app.feature.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paperly.app.core.ui.theme.CropGreen
import com.paperly.app.domain.scanner.ScanPoint
import com.paperly.app.domain.scanner.ScanQuad
import kotlin.math.hypot

private const val HANDLES = 8 // 0..3 corners, 4..7 edge midpoints (edge i-4 runs corner i-4 -> corner i-3)
private const val CORNER_COUNT = 4
private val HIT_RADIUS = 44.dp
private val CORNER_HANDLE = 11.dp
private val MID_HANDLE = 7.dp
private val FRAME_STROKE = 3.dp
private val HANDLE_STROKE = 2.dp
private val LOUPE_WIDTH = 120.dp
private val LOUPE_HEIGHT = 96.dp
private val LOUPE_GAP = 28.dp
private val LOUPE_CORNER = 12.dp
private val LOUPE_BORDER = 2.dp
private val LOUPE_SHADOW_OFFSET = 2.dp
private val CROSS_ARM = 10.dp
private const val LOUPE_MAGNIFY = 2.5f
private const val FIT_MARGIN = 0.06f
private const val LOUPE_SHADOW_ALPHA = 0.25f

/** Where the image sits inside the canvas (letterboxed). */
private class Fit(val left: Float, val top: Float, val width: Float, val height: Float)

private fun fit(box: IntSize, image: ImageBitmap): Fit {
    val keep = 1f - 2f * FIT_MARGIN // free space around the picture so the edge handles stay easy to grab
    val scale = minOf(box.width * keep / image.width, box.height * keep / image.height)
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

/**
 * Corner handles move one corner freely; edge handles slide that edge straight (parallel to itself), so a
 * rectangular crop stays rectangular. [drag] is a fraction of the image, [size] the image size in pixels.
 */
private fun moveHandle(quad: ScanQuad, handle: Int, drag: Offset, size: Size): ScanQuad {
    val p = quad.points.toMutableList()
    if (handle < CORNER_COUNT) {
        p[handle] = moved(p[handle], drag.x, drag.y)
    } else {
        val i = handle - CORNER_COUNT
        val j = (i + 1) % CORNER_COUNT
        val straight = straightDrag(p[i], p[j], drag, size)
        p[i] = moved(p[i], straight.x, straight.y)
        p[j] = moved(p[j], straight.x, straight.y)
    }
    return ScanQuad.of(p)
}

/** The part of [drag] that is perpendicular to the edge a-b (measured in pixels), as image fractions again. */
private fun straightDrag(a: ScanPoint, b: ScanPoint, drag: Offset, size: Size): Offset {
    val ex = (b.x - a.x) * size.width
    val ey = (b.y - a.y) * size.height
    val length = hypot(ex, ey)
    if (length == 0f) return Offset.Zero
    val nx = -ey / length
    val ny = ex / length
    val along = drag.x * size.width * nx + drag.y * size.height * ny
    return Offset(along * nx / size.width, along * ny / size.height)
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
    val line = CropGreen
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
                        val moveBy = Offset(drag.x / f.width, drag.y / f.height)
                        onChange(moveHandle(latest, active, moveBy, Size(f.width, f.height)))
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
        drawCropFrame(image, f, quad, active, line)
    }
}

/** The crop frame with its 8 handles; while one is dragged ([active] >= 0) the loupe shows its surroundings. */
private fun DrawScope.drawCropFrame(
    image: ImageBitmap,
    f: Fit,
    quad: ScanQuad,
    active: Int,
    line: Color,
) {
    fun at(pt: ScanPoint) = Offset(f.left + pt.x * f.width, f.top + pt.y * f.height)
    val frame = Path().apply {
        val pts = quad.points.map(::at)
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(frame, line, style = Stroke(width = FRAME_STROKE.toPx()))
    val cornerRadius = CORNER_HANDLE.toPx()
    val midRadius = MID_HANDLE.toPx()
    val stroke = Stroke(width = HANDLE_STROKE.toPx())
    for (h in 0 until HANDLES) {
        val centre = at(handlePoint(quad, h))
        if (h < CORNER_COUNT) {
            drawCircle(Color.White, cornerRadius, centre)
            drawCircle(line, cornerRadius, centre, style = stroke)
        } else {
            val corner = centre - Offset(midRadius, midRadius)
            val square = Size(midRadius * 2, midRadius * 2)
            drawRect(Color.White, corner, square)
            drawRect(line, corner, square, style = stroke)
        }
    }
    if (active >= 0) drawCropLoupe(image, f, at(handlePoint(quad, active)), line)
}

/**
 * Magnifier for placing a handle precisely: the page around [point] at [LOUPE_MAGNIFY]x in a box that floats above
 * the finger (below it near the top edge), with a cross-hair on the exact spot.
 */
private fun DrawScope.drawCropLoupe(image: ImageBitmap, f: Fit, point: Offset, line: Color) {
    val w = LOUPE_WIDTH.toPx()
    val h = LOUPE_HEIGHT.toPx()
    val lift = h / 2 + LOUPE_GAP.toPx()
    val above = point.y - lift
    val centreY = if (above - h / 2 < 0f) point.y + lift else above
    val centreX = point.x.coerceIn(w / 2, maxOf(w / 2, size.width - w / 2))
    val box = Rect(Offset(centreX - w / 2, centreY - h / 2), Size(w, h))
    val corner = CornerRadius(LOUPE_CORNER.toPx())
    val shadowAt = box.topLeft + Offset(0f, LOUPE_SHADOW_OFFSET.toPx())
    drawRoundRect(Color.Black.copy(alpha = LOUPE_SHADOW_ALPHA), shadowAt, box.size, corner)
    drawRoundRect(Color.White, box.topLeft, box.size, corner)
    clipPath(Path().apply { addRoundRect(RoundRect(box, corner)) }) {
        withTransform({
            translate(centreX, centreY)
            scale(LOUPE_MAGNIFY, LOUPE_MAGNIFY, Offset.Zero)
            translate(-point.x, -point.y)
        }) {
            drawImage(
                image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(f.left.toInt(), f.top.toInt()),
                dstSize = IntSize(f.width.toInt(), f.height.toInt()),
            )
        }
        val arm = CROSS_ARM.toPx()
        val centre = Offset(centreX, centreY)
        drawLine(line, centre - Offset(arm, 0f), centre + Offset(arm, 0f), strokeWidth = HANDLE_STROKE.toPx())
        drawLine(line, centre - Offset(0f, arm), centre + Offset(0f, arm), strokeWidth = HANDLE_STROKE.toPx())
    }
    drawRoundRect(line, box.topLeft, box.size, corner, style = Stroke(LOUPE_BORDER.toPx()))
}

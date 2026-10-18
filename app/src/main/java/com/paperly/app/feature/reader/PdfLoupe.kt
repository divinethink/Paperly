package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val LoupeWidth = 240.dp
private val LoupeHeight = 88.dp
private val LoupeCorner = 20.dp
private val LoupeBorder = 1.5.dp
private val LoupeGap = 32.dp // clear space between the loupe's edge and the fingertip
private val LoupeShadow = 3.dp
private val LoupeBorderColor = Color(0xFF8A8A8A)
private const val LOUPE_MAGNIFY = 1.8f
private const val SHADOW_ALPHA = 0.16f

/**
 * Xodo-style magnifier for precise handle/edge placement: a wide rounded box (about two text lines) showing the page
 * bitmap around [finger] at [LOUPE_MAGNIFY]x, with the page's own overlays ([overlay]: selection, marks, highlights)
 * redrawn on top at the same scale. It floats above the fingertip (below it near the page top). Sizes are divided by
 * [zoom] so the loupe keeps its on-screen size.
 */
internal fun DrawScope.drawLoupe(
    image: ImageBitmap,
    finger: Offset,
    zoom: Float,
    filter: ColorFilter?,
    overlay: DrawScope.() -> Unit,
) {
    val z = maxOf(zoom, MIN_ZOOM)
    val w = LoupeWidth.toPx() / z
    val h = LoupeHeight.toPx() / z
    val lift = h / 2 + LoupeGap.toPx() / z
    val above = finger.y - lift
    val centreY = if (above - h / 2 < 0f) finger.y + lift else above
    val centreX = finger.x.coerceIn(w / 2, maxOf(w / 2, size.width - w / 2))
    val box = Rect(Offset(centreX - w / 2, centreY - h / 2), Size(w, h))
    val corner = CornerRadius(LoupeCorner.toPx() / z)

    // soft shadow, then white backing so the page shows on a clean ground
    drawRoundRect(
        Color.Black.copy(alpha = SHADOW_ALPHA),
        box.topLeft + Offset(0f, LoupeShadow.toPx() / z),
        box.size,
        corner,
    )
    drawRoundRect(Color.White, box.topLeft, box.size, corner)

    val scale = image.width / size.width
    val srcW = w / LOUPE_MAGNIFY * scale
    val srcH = h / LOUPE_MAGNIFY * scale
    clipPath(Path().apply { addRoundRect(RoundRect(box, corner)) }) {
        drawImage(
            image,
            srcOffset = IntOffset(
                (finger.x * scale - srcW / 2).roundToInt(),
                (finger.y * scale - srcH / 2).roundToInt(),
            ),
            srcSize = IntSize(srcW.roundToInt(), srcH.roundToInt()),
            dstOffset = IntOffset(box.left.roundToInt(), box.top.roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            colorFilter = filter,
        )
        // same overlay code as the page, mapped so that the finger point lands on the loupe centre
        withTransform({
            translate(centreX, centreY)
            scale(LOUPE_MAGNIFY, LOUPE_MAGNIFY, Offset.Zero)
            translate(-finger.x, -finger.y)
        }) { overlay() }
    }
    drawRoundRect(LoupeBorderColor, box.topLeft, box.size, corner, style = Stroke(LoupeBorder.toPx() / z))
}

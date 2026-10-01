package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val LoupeRadius = 48.dp
private val LoupeBorder = 2.dp
private val LoupeDot = 2.dp
private const val LOUPE_MAGNIFY = 2f
private const val LOUPE_LIFT = 1.6f // loupe centre sits this many radii above the finger (below it near the page top)

/**
 * Magnifier bubble for precise handle/edge placement: the page bitmap around [finger] drawn [LOUPE_MAGNIFY]x in a
 * circle that stays clear of the fingertip. Radius is divided by [zoom] so it keeps its on-screen size.
 */
internal fun DrawScope.drawLoupe(image: ImageBitmap, finger: Offset, zoom: Float, filter: ColorFilter?) {
    val z = maxOf(zoom, MIN_ZOOM)
    val radius = LoupeRadius.toPx() / z
    val above = finger.y - radius * LOUPE_LIFT
    val centreY = if (above - radius < 0f) finger.y + radius * LOUPE_LIFT else above
    val centre = Offset(finger.x.coerceIn(radius, maxOf(radius, size.width - radius)), centreY)
    val scale = image.width / size.width
    val half = radius / LOUPE_MAGNIFY * scale
    drawCircle(Color.White, radius, centre)
    clipPath(Path().apply { addOval(Rect(centre, radius)) }) {
        drawImage(
            image,
            srcOffset = IntOffset((finger.x * scale - half).roundToInt(), (finger.y * scale - half).roundToInt()),
            srcSize = IntSize((half * 2).roundToInt(), (half * 2).roundToInt()),
            dstOffset = IntOffset((centre.x - radius).roundToInt(), (centre.y - radius).roundToInt()),
            dstSize = IntSize((radius * 2).roundToInt(), (radius * 2).roundToInt()),
            colorFilter = filter,
        )
    }
    drawCircle(PendingStrokeColor, radius, centre, style = Stroke(LoupeBorder.toPx() / z))
    drawCircle(PendingStrokeColor, LoupeDot.toPx() / z, centre)
}

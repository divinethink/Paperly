package com.paperly.app.domain.reader

import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

/** EPUB reading fonts (P3-C). Stored by [key]; unknown/missing values fall back to [SERIF]. */
enum class EpubFont(val key: String) {
    SERIF("serif"),
    SANS("sans"),
    ;

    companion object {
        fun fromKey(key: String?): EpubFont = entries.firstOrNull { it.key == key } ?: SERIF
    }
}

/** Global EPUB typography (multipliers, like Readium). Steps clamp, so stored junk never breaks rendering. */
data class EpubTypography(
    val font: EpubFont = EpubFont.SERIF,
    val fontScale: Float = DEFAULT_SCALE,
    val lineSpacing: Float = DEFAULT_LINE,
    val margin: Float = DEFAULT_MARGIN,
) {
    fun coerced() = copy(
        fontScale = fontScale.coerceIn(MIN_SCALE, MAX_SCALE),
        lineSpacing = lineSpacing.coerceIn(MIN_LINE, MAX_LINE),
        margin = margin.coerceIn(MIN_MARGIN, MAX_MARGIN),
    )

    fun stepScale(up: Boolean) = copy(fontScale = step(fontScale, SCALE_STEP, up)).coerced()
    fun stepLine(up: Boolean) = copy(lineSpacing = step(lineSpacing, LINE_STEP, up)).coerced()
    fun stepMargin(up: Boolean) = copy(margin = step(margin, MARGIN_STEP, up)).coerced()

    companion object {
        const val DEFAULT_SCALE = 1f
        const val DEFAULT_LINE = 1.4f
        const val DEFAULT_MARGIN = 1f
        const val MIN_SCALE = 0.7f
        const val MAX_SCALE = 2.5f
        const val MIN_LINE = 1f
        const val MAX_LINE = 2.2f
        const val MIN_MARGIN = 0.5f
        const val MAX_MARGIN = 2.5f
        private const val SCALE_STEP = 0.1f
        private const val LINE_STEP = 0.1f
        private const val MARGIN_STEP = 0.25f
        private const val ROUND = 100f

        // Rounded to 2 decimals so repeated steps never drift (0.1f + 0.1f ...).
        private fun step(value: Float, delta: Float, up: Boolean): Float =
            ((value + if (up) delta else -delta) * ROUND).roundToInt() / ROUND
    }
}

interface EpubTypographyStore {
    val typography: Flow<EpubTypography>

    suspend fun save(typography: EpubTypography)
}

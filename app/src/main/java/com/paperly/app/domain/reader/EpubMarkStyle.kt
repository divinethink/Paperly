package com.paperly.app.domain.reader

private const val HIGHLIGHT_ALPHA = "0.4"
private const val LIGHT_MIX = 0.6f
private const val FULL = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

// Same colours as the app's colour picker.
private val RGB = mapOf(
    AnnotationColor.AMBER to 0xFFB300,
    AnnotationColor.RED to 0xD32F2F,
    AnnotationColor.GREEN to 0x388E3C,
    AnnotationColor.BLUE to 0x1E88E5,
    AnnotationColor.PURPLE to 0x8E24AA,
    AnnotationColor.BLACK to 0x212121,
)

private fun channels(rgb: Int) = listOf(rgb shr RED_SHIFT and FULL, rgb shr GREEN_SHIFT and FULL, rgb and FULL)

private fun hex(rgb: Int) = "#%06X".format(rgb)

/** Light version of the colour for readers that do not know `rgba()`. */
private fun lighter(rgb: Int): Int {
    val mixed = channels(rgb).map { FULL - ((FULL - it) * (1 - LIGHT_MIX)).toInt() }
    return (mixed[0] shl RED_SHIFT) or (mixed[1] shl GREEN_SHIFT) or mixed[2]
}

/** The inline CSS an EPUB mark gets: same colours as in the app (Note = blue highlight). */
internal fun epubMarkStyle(type: AnnotationType, color: AnnotationColor?, stroke: AnnotationStroke? = null): String {
    val chosen = if (type == AnnotationType.NOTE) AnnotationColor.BLUE else color ?: AnnotationColor.defaultFor(type)
    val tint = RGB.getValue(chosen)
    val thickness = stroke?.let { ";text-decoration-thickness:${it.dp}px" }.orEmpty()
    val line = "text-decoration-color:${hex(tint)}$thickness"
    return when (type) {
        AnnotationType.HIGHLIGHT, AnnotationType.NOTE -> {
            val rgba = "rgba(${channels(tint).joinToString(",")},$HIGHLIGHT_ALPHA)"
            "background-color:${hex(lighter(tint))};background-color:$rgba"
        }
        AnnotationType.UNDERLINE -> "text-decoration:underline;$line"
        AnnotationType.SQUIGGLY -> "text-decoration:underline wavy;$line"
        AnnotationType.STRIKETHROUGH -> "text-decoration:line-through;$line"
    }
}

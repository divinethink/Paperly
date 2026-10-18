package com.paperly.app.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Small Material-style glyphs from path data (the bundled icon set has no download/sort/chevron).
private const val ICON_SIZE = 24f

private const val DOWNLOAD_PATH = "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z"
private const val SORT_PATH = "M3,18h6v-2H3v2zM3,6v2h18V6H3zm0,7h12v-2H3v2z"
private const val CHEVRON_PATH = "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z"

private fun glyph(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = ICON_SIZE.dp,
        defaultHeight = ICON_SIZE.dp,
        viewportWidth = ICON_SIZE,
        viewportHeight = ICON_SIZE,
    ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()

val DownloadGlyph: ImageVector by lazy { glyph("Download", DOWNLOAD_PATH) }
val SortGlyph: ImageVector by lazy { glyph("Sort", SORT_PATH) }
val ChevronGlyph: ImageVector by lazy { glyph("Chevron", CHEVRON_PATH) }

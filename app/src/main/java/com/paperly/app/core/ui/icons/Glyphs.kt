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

private const val REFLOW_PATH = "M3,18h13v-2H3v2zM3,6v2h18V6H3zm0,7h18v-2H3v2z"
private const val FIT_HEIGHT_PATH = "M8.01,3L4,7h3v7h2V7h3L8.01,3zM16,17.01V10h-2v7.01h-3L15,21l4,-3.99h-3z"
private const val FIT_WIDTH_PATH = "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z"
private const val BRIGHTNESS_PATH = "M12,7a5,5 0 1,0 0.001,0zM11,1h2v3h-2zM11,20h2v3h-2zM1,11h3v2H1zM20,11h3v2h-3z" +
    "M4.2,5.6l1.4,-1.4 2.1,2.1 -1.4,1.4zM16.3,17.7l1.4,-1.4 2.1,2.1 -1.4,1.4z" +
    "M17.7,4.2l1.4,1.4 -2.1,2.1 -1.4,-1.4zM5.6,19.8l-1.4,-1.4 2.1,-2.1 1.4,1.4z"
private const val SHARE_PATH = "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7" +
    "s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3" +
    " -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3" +
    "c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92" +
    " 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"
private const val PRINT_PATH = "M19,8H5c-1.66,0 -3,1.34 -3,3v6h4v4h12v-4h4v-6c0,-1.66 -1.34,-3 -3,-3zM16,19H8v-5h8v5z" +
    "M19,12c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1zM18,3H6v4h12V3z"
private const val ARTICLE_PATH = "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18" +
    "c1.1,0 2,-0.9 2,-2V8l-6,-6z" +
    "M16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z"

private const val FOLDER_PATH =
    "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"
private const val IMAGE_PATH = "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14" +
    "c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z"
private const val CAMERA_PATH = "M12,12m-3.2,0a3.2,3.2 0 1,0 6.4,0a3.2,3.2 0 1,0 -6.4,0" +
    "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9z" +
    "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"
private const val BOOKMARK_PATH = "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z"

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
val ReflowGlyph: ImageVector by lazy { glyph("Reflow", REFLOW_PATH) }
val FitHeightGlyph: ImageVector by lazy { glyph("FitHeight", FIT_HEIGHT_PATH) }
val FitWidthGlyph: ImageVector by lazy { glyph("FitWidth", FIT_WIDTH_PATH) }
val BrightnessGlyph: ImageVector by lazy { glyph("Brightness", BRIGHTNESS_PATH) }
val ShareGlyph: ImageVector by lazy { glyph("Share", SHARE_PATH) }
val PrintGlyph: ImageVector by lazy { glyph("Print", PRINT_PATH) }
val ArticleGlyph: ImageVector by lazy { glyph("Article", ARTICLE_PATH) }
val FolderGlyph: ImageVector by lazy { glyph("Folder", FOLDER_PATH) }
val BookmarkGlyph: ImageVector by lazy { glyph("Bookmark", BOOKMARK_PATH) }
val GalleryGlyph: ImageVector by lazy { glyph("Gallery", IMAGE_PATH) }
val CameraGlyph: ImageVector by lazy { glyph("Camera", CAMERA_PATH) }

package com.paperly.app.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Simple 24dp Material-style glyphs drawn from path data: the bundled icon set (icons-core) has no
// folder/document/book, and the extended set would add a very large dependency.
private const val ICON_SIZE = 24f

private const val FOLDER_PATH =
    "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"
private const val DOCUMENT_PATH =
    "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6z" +
        "M16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z"
private const val BOOK_PATH =
    "M18,2H6c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z" +
        "M6,4h5v8l-2.5,-1.5L6,12V4z"

private fun glyph(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = ICON_SIZE.dp,
        defaultHeight = ICON_SIZE.dp,
        viewportWidth = ICON_SIZE,
        viewportHeight = ICON_SIZE,
    ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()

internal val ScannedIcon: ImageVector by lazy { glyph("Scanned", FOLDER_PATH) }
internal val PdfIcon: ImageVector by lazy { glyph("Pdf", DOCUMENT_PATH) }
internal val EpubIcon: ImageVector by lazy { glyph("Epub", BOOK_PATH) }

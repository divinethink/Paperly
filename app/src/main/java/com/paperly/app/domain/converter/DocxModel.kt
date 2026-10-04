package com.paperly.app.domain.converter

import java.io.InputStream

/** Source of image bytes for the EPUB writer; [part] is a normalised package part name (`word/media/image1.png`). */
interface ImageSource {
    fun canRead(part: String): Boolean

    fun open(part: String): InputStream
}

/** Inline content of one paragraph. */
sealed interface Inline {
    data class Text(val text: String, val bold: Boolean, val italic: Boolean, val underline: Boolean) : Inline

    data class Image(val part: String, val alt: String) : Inline

    data object Break : Inline
}

/** Block-level content. Tables are flattened to paragraphs (text is kept, layout is not). */
sealed interface Block {
    val inlines: List<Inline>

    data class Heading(val level: Int, override val inlines: List<Inline>) : Block

    data class Paragraph(override val inlines: List<Inline>) : Block

    data class ListItem(val ordered: Boolean, val level: Int, override val inlines: List<Inline>) : Block
}

/** What the converter could not carry over, so the UI can say so instead of silently dropping content. */
data class DocxFlags(
    val tablesFlattened: Boolean = false,
    val footnotesSkipped: Boolean = false,
    val textBoxesSkipped: Boolean = false,
)

data class DocxDocument(val blocks: List<Block>, val flags: DocxFlags)

internal fun List<Inline>.plainText(): String = filterIsInstance<Inline.Text>().joinToString("") { it.text }.trim()

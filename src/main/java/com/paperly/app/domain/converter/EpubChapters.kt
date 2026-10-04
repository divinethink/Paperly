package com.paperly.app.domain.converter

/** One XHTML file of the EPUB. [inToc] is false for size-based continuation files of the same chapter. */
internal data class EpubFile(val title: String, val blocks: List<Block>, val inToc: Boolean)

/** Splits a document into XHTML files: at the top heading level (H1, or H2 if there is no H1), then by size. */
internal object EpubChapters {
    private const val MAX_BLOCKS_PER_FILE = 400
    private const val MAX_SPLIT_LEVEL = 2

    fun split(blocks: List<Block>, fallbackTitle: String): List<EpubFile> {
        val topLevel = blocks.filterIsInstance<Block.Heading>().minOfOrNull { it.level }
        val splitLevel = topLevel?.takeIf { it <= MAX_SPLIT_LEVEL }
        val groups = ArrayList<List<Block>>()
        var current = ArrayList<Block>()
        for (block in blocks) {
            // A heading directly after another heading (no body between) stays in the same chapter.
            val startsChapter = splitLevel != null && block is Block.Heading && block.level == splitLevel &&
                current.any { it !is Block.Heading }
            if (startsChapter) {
                groups += current
                current = ArrayList()
            }
            current += block
        }
        if (current.isNotEmpty()) groups += current
        return groups.flatMap { toFiles(it, fallbackTitle) }
    }

    private fun toFiles(group: List<Block>, fallbackTitle: String): List<EpubFile> {
        val title = group.filterIsInstance<Block.Heading>().firstOrNull()?.inlines?.plainText()
            ?.takeIf { it.isNotBlank() } ?: fallbackTitle
        return group.chunked(MAX_BLOCKS_PER_FILE).mapIndexed { i, part -> EpubFile(title, part, inToc = i == 0) }
    }
}

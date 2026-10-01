package com.paperly.app.domain.reader

/** One annotation as exported text. [heading] groups entries (EPUB: chapter, PDF: "Page N"); [quote] = text. */
data class AnnotationExportEntry(
    val heading: String,
    val type: AnnotationType,
    val quote: String?,
    val note: String?,
)

/** Pure (no Android/Readium): Markdown for sharing. Groups by heading in first-seen order. */
fun buildAnnotationMarkdown(title: String, entries: List<AnnotationExportEntry>): String = buildString {
    appendLine("# ${title.ifBlank { "Annotations" }}")
    entries.groupBy { it.heading }.forEach { (heading, group) ->
        appendLine()
        if (heading.isNotBlank()) appendLine("## $heading")
        group.forEach { entry ->
            appendLine()
            entry.quote?.trim()?.takeIf { it.isNotEmpty() }?.lines()?.forEach { appendLine("> ${it.trim()}") }
            val label = entry.type.name.lowercase().replaceFirstChar { it.uppercase() }
            val note = entry.note?.trim()?.takeIf { it.isNotEmpty() }
            appendLine(if (note != null) "**$label** — $note" else "**$label**")
        }
    }
}

package com.paperly.app.domain.ocr

private val bengali = Regex("[\u0980-\u09FF]")
private val shortLatin = Regex("(?<![A-Za-z0-9])[A-Za-z]{1,3}(?![A-Za-z0-9])")
private val manySpaces = Regex("[ \t]{2,}")
private val keepWords = setOf(
    "a", "an", "the", "is", "to", "of", "in", "on", "at", "it", "he", "she", "we", "i", "you", "and", "or", "but",
    "not", "no", "yes", "ok", "for", "are", "was", "be", "by", "my", "me", "so", "if", "as", "do", "up", "us", "all",
    "can", "had", "has", "his", "her", "how", "now", "one", "out", "see", "who", "why", "tv", "pc", "cd", "dvd",
    "atm", "sms", "dna", "usa", "uk", "id", "mr", "dr", "ms",
)

/**
 * Removes the stray 1-3 letter Latin fragments (such as "fag", "AYA") that OCR invents from ornaments on a page
 * that is otherwise Bengali. Pages with no Bengali are left as they are; common short English words are kept.
 */
object OcrTextCleaner {
    fun clean(text: String): String =
        if (bengali.containsMatchIn(text)) text.lines().mapNotNull(::cleanLine).joinToString("\n") else text

    private fun cleanLine(line: String): String? {
        if (line.isBlank()) return line
        val kept = shortLatin.replace(line) { if (it.value.lowercase() in keepWords) it.value else "" }
        return kept.replace(manySpaces, " ").trim().ifEmpty { null }
    }
}

package com.paperly.app.domain.reader

/**
 * Decides whether a PDF's text layer is usable for search. Legacy Bijoy/SutonnyMJ Bengali PDFs expose
 * Latin-1 symbols (`†`, `÷`, `©`...) instead of Unicode, so their extracted text is garbage (Checklist Decision Log).
 * The suspicious-character threshold is a proposed heuristic: verify on a real Bijoy gazette PDF.
 */
object TextReadability {
    private const val MIN_CHARS = 20
    private const val MAX_SUSPICIOUS_RATIO = 0.12f
    private val BENGALI = 0x0980..0x09FF
    private val LATIN1 = 0x0080..0x00FF
    private val EXTRA_SUSPICIOUS = setOf(0x2020, 0x2021, 0x2022, 0x2030, 0x0192, 0x02C6, 0x0152, 0x0153, 0x0160, 0x0161)

    fun isReadable(text: String): Boolean {
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.length < MIN_CHARS) return false // empty / scanned / image-only
        val suspicious = chars.count { it.code in LATIN1 || it.code in EXTRA_SUSPICIOUS }
        return suspicious.toFloat() / chars.length <= MAX_SUSPICIOUS_RATIO
    }

    /** Unicode Bengali: search works but extraction can drop vowel signs/conjuncts, so matches may be partial. */
    fun hasBengali(text: String): Boolean = text.any { it.code in BENGALI }
}

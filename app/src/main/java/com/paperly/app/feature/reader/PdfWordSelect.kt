package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import com.paperly.app.domain.reader.ReaderEngine
import com.paperly.app.domain.reader.TextSelection

private const val WORD_STEP = 0.01f // page-fraction nudge, about one character wide
private const val MAX_STEPS = 14
private const val MAX_STALL = 3

/**
 * The word under a long-press (EPUB-style): seed from the press point, then widen one nudge at a time until the
 * next character is not part of a word. Null when there is no text there.
 */
internal suspend fun pickWordAt(engine: ReaderEngine, page: Int, at: Offset): TextSelection? {
    val seed = engine.selectText(page, at.x, at.y, at.x, at.y)?.takeIf { it.text.isNotBlank() }
        ?: engine.selectText(page, at.x - WORD_STEP, at.y, at.x + WORD_STEP, at.y)?.takeIf { it.text.isNotBlank() }
        ?: return null
    return grow(engine, page, at.y, grow(engine, page, at.y, seed, toRight = true), toRight = false)
}

private suspend fun grow(
    engine: ReaderEngine,
    page: Int,
    y: Float,
    start: TextSelection,
    toRight: Boolean,
): TextSelection {
    val left = start.rects.minOfOrNull { it.left } ?: return start
    val right = start.rects.maxOf { it.right }
    var best = start
    var stall = 0
    var step = 1
    var open = true
    while (open && step <= MAX_STEPS && stall < MAX_STALL) {
        val reach = step * WORD_STEP
        step++
        val from = if (toRight) left else left - reach
        val to = if (toRight) right + reach else right
        val cand = engine.selectText(page, from, y, to, y)
        if (cand == null || cand.text.isBlank() || !edgeIsWord(cand.text, toRight)) {
            open = false // word boundary (or nothing there): keep the last good span
        } else if (cand.text.length > best.text.length) {
            best = cand
            stall = 0
        } else {
            stall++
        }
    }
    return best
}

private fun edgeIsWord(text: String, toRight: Boolean): Boolean {
    val c = if (toRight) text.last() else text.first()
    return c.isLetterOrDigit() || c == '\''
}

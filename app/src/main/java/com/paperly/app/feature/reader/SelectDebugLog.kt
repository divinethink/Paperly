package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import com.paperly.app.domain.reader.MatchRect

private const val MAX_LINES = 800
private const val SKIP_FRAMES = 2
private const val SHOW_FRAMES = 4
private const val NANOS_PER_MS = 1_000_000L

/**
 * TEMPORARY diagnostics for the PDF text-select bugs (handle drag, collapse on lift). Memory only; geometry and
 * counts only, never document text. Copied from the Reader menu. Remove once the root cause is fixed.
 */
object SelectDebugLog {
    private val lines = ArrayDeque<String>()
    private val t0 = System.nanoTime()

    fun log(message: String) {
        val at = (System.nanoTime() - t0) / NANOS_PER_MS
        synchronized(lines) {
            if (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast("$at $message")
        }
    }

    /** Everything logged so far; the buffer is emptied so the next copy holds only newer lines. */
    fun takeAll(): String = synchronized(lines) {
        val all = lines.joinToString("\n")
        lines.clear()
        all
    }

    fun f(v: Float): String = "%.3f".format(v)

    fun pt(o: Offset): String = "(${f(o.x)},${f(o.y)})"

    /** Count plus first and last rect (l,t,r,b), enough to see where a selection starts and ends. */
    fun rs(list: List<MatchRect>): String {
        val first = list.firstOrNull() ?: return "n=0"
        val last = list.last()
        return "n=${list.size} first=${box(first)} last=${box(last)}"
    }

    /** Who called the function that logs this (file:line of the nearest frames). */
    fun caller(): String = Throwable("select-log caller").stackTrace.drop(SKIP_FRAMES).take(SHOW_FRAMES)
        .joinToString("<") { "${it.fileName}:${it.lineNumber}" }

    private fun box(r: MatchRect) = "[${f(r.left)},${f(r.top)},${f(r.right)},${f(r.bottom)}]"
}

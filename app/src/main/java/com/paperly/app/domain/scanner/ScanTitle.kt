package com.paperly.app.domain.scanner

import com.paperly.app.domain.document.MAX_TITLE_LENGTH
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NEW_TITLE_PREFIX = "Scan "
private const val NEW_TITLE_PATTERN = "yyyy-MM-dd HH-mm"

/** Name of a new scan document made by Move/Copy ("Scan 2026-10-20 14-05", same style as a fresh scan). */
fun newScanTitle(now: Date = Date()): String =
    NEW_TITLE_PREFIX + SimpleDateFormat(NEW_TITLE_PATTERN, Locale.US).format(now)

/** User-typed scan name: trimmed and capped; blank falls back to [default] (a scan is never lost over its name). */
fun scanTitle(input: String, default: String): String = input.trim().take(MAX_TITLE_LENGTH).trim().ifBlank { default }

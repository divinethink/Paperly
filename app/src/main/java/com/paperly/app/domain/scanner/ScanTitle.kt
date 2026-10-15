package com.paperly.app.domain.scanner

import com.paperly.app.domain.document.MAX_TITLE_LENGTH

/** User-typed scan name: trimmed and capped; blank falls back to [default] (a scan is never lost over its name). */
fun scanTitle(input: String, default: String): String = input.trim().take(MAX_TITLE_LENGTH).trim().ifBlank { default }

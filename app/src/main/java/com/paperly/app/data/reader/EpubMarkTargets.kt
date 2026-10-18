package com.paperly.app.data.reader

import android.net.Uri
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.XhtmlMark
import com.paperly.app.domain.reader.epubMarkStyle
import org.json.JSONObject

/** Chapter file name (as in the zip) for a Readium href: no leading slash, query/fragment cut, %-decoded. */
internal fun chapterName(href: String): String =
    Uri.decode(href.substringBefore('#').substringBefore('?')).trimStart('/')

/** The mark's chapter and text; null when the locator has no selected text (nothing to find in the book). */
internal fun toXhtmlMark(annotation: EpubAnnotation, id: String): Pair<String, XhtmlMark>? = runCatching {
    val json = JSONObject(annotation.locatorJson)
    val text = json.optJSONObject("text")
    val highlight = text?.optString("highlight").orEmpty()
    val href = json.optString("href")
    if (highlight.isBlank() || href.isEmpty()) {
        null
    } else {
        chapterName(href) to XhtmlMark(
            id = id,
            before = text?.optString("before").orEmpty(),
            highlight = highlight,
            after = text?.optString("after").orEmpty(),
            style = epubMarkStyle(annotation.type, annotation.color),
            title = annotation.noteText,
        )
    }
}.getOrNull()

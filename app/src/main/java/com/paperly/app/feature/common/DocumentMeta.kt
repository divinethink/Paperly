package com.paperly.app.feature.common

import android.content.Context
import android.text.format.Formatter
import com.paperly.app.domain.document.Document

fun documentMeta(context: Context, doc: Document): String =
    "${doc.type.uppercase()} · ${Formatter.formatShortFileSize(context, doc.sizeBytes)}"

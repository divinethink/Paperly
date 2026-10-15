package com.paperly.app.domain.settings

import kotlinx.coroutines.flow.Flow

/** L3: put highlights into the Library PDF file itself (so other PDF apps show them). ON by default. */
interface PdfFileWriteSettings {
    val writeHighlights: Flow<Boolean>

    suspend fun setWriteHighlights(enabled: Boolean)
}

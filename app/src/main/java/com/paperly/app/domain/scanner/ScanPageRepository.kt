package com.paperly.app.domain.scanner

import kotlinx.coroutines.flow.Flow

data class ScanPageInfo(val title: String = "", val note: String = "")

const val MAX_PAGE_TITLE = 100
const val MAX_PAGE_NOTE = 2000

interface ScanPageRepository {
    fun observe(documentId: String, pageIndex: Int): Flow<ScanPageInfo>

    /** Blank title and note = row removed (nothing stored for an untouched page). */
    suspend fun save(documentId: String, pageIndex: Int, info: ScanPageInfo)
}

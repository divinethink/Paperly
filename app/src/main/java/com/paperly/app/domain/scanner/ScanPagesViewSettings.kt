package com.paperly.app.domain.scanner

import kotlinx.coroutines.flow.Flow

/** How a scanned document's pages are listed: thumbnail grid (default) or list; chosen once, kept until changed. */
interface ScanPagesViewSettings {
    val grid: Flow<Boolean>

    suspend fun setGrid(grid: Boolean)
}

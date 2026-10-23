package com.paperly.app.domain.scanner

import kotlinx.coroutines.flow.Flow

/** Size/quality of saved scans, stored scan quality (default MEDIUM; no menu any more - Share / Save choose their own file size). */
interface ScanOutputSettings {
    val size: Flow<ScanOutputSize>

    suspend fun setSize(size: ScanOutputSize)
}

/** Never throws: a missing or unknown stored name (older/newer data) means MEDIUM. */
fun scanOutputSizeOf(name: String?): ScanOutputSize =
    ScanOutputSize.entries.firstOrNull { it.name == name } ?: ScanOutputSize.MEDIUM

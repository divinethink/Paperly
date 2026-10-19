package com.paperly.app.domain.scanner

import kotlinx.coroutines.flow.Flow

/** Size/quality of saved scans, chosen once in the Scanned tab's menu and kept until changed (default MEDIUM). */
interface ScanOutputSettings {
    val size: Flow<ScanOutputSize>

    suspend fun setSize(size: ScanOutputSize)
}

/** Never throws: a missing or unknown stored name (older/newer data) means MEDIUM. */
fun scanOutputSizeOf(name: String?): ScanOutputSize =
    ScanOutputSize.entries.firstOrNull { it.name == name } ?: ScanOutputSize.MEDIUM

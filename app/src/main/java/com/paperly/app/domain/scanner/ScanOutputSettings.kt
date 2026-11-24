package com.paperly.app.domain.scanner

import kotlinx.coroutines.flow.Flow

/** Quality of stored scans: the best the device's memory allows; Share / Save choose a smaller file size themselves. */
interface ScanOutputSettings {
    val size: Flow<ScanOutputSize>
}

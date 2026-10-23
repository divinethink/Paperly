package com.paperly.app.domain.scanner

import kotlinx.coroutines.flow.Flow

/** Quality of stored scans: always the best (LARGE); Share / Save choose a smaller file size themselves. */
interface ScanOutputSettings {
    val size: Flow<ScanOutputSize>
}

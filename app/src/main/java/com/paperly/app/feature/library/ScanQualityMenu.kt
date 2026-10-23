package com.paperly.app.feature.library

import androidx.annotation.StringRes
import com.paperly.app.R
import com.paperly.app.domain.scanner.ExportSize

// Kept as a file name only: the old scan-quality menu is gone; this holds the size-choice labels used by the dialog.
@get:StringRes
internal val ExportSize.label: Int
    get() = when (this) {
        ExportSize.ACTUAL -> R.string.export_size_actual
        ExportSize.MEDIUM -> R.string.export_size_medium
        ExportSize.SMALL -> R.string.export_size_small
        ExportSize.SMALLEST -> R.string.export_size_smallest
    }

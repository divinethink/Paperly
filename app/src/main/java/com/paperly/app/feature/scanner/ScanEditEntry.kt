package com.paperly.app.feature.scanner

import androidx.compose.runtime.staticCompositionLocalOf

/** Opens the page editor of a scanned document (provided by navigation; the Reader menu calls it). */
val LocalOpenScanEditor = staticCompositionLocalOf<(String) -> Unit> { {} }

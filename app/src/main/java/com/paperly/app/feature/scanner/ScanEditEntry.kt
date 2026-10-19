package com.paperly.app.feature.scanner

import androidx.compose.runtime.staticCompositionLocalOf

/** Opens the page editor of a scanned document (provided by navigation; the Reader menu calls it). */
val LocalOpenScanEditor = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Opens the page editor of a scanned document and starts "Add scan" there (provided by the Library route). */
val LocalAddScanPages = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Opens the page grid of a scanned document (provided by the Library route; the Scanned list calls it). */
val LocalOpenScanPages = staticCompositionLocalOf<(String) -> Unit> { {} }

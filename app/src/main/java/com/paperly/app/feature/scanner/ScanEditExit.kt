package com.paperly.app.feature.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Leaves the editor when it says so: saved, nothing added, or the new scan was thrown away. */
@Composable
internal fun EditExitEffects(state: ScanEditUi, onDone: () -> Unit, onCancelAdd: () -> Unit, onDiscarded: () -> Unit) {
    LaunchedEffect(state.done) { if (state.done) onDone() }
    LaunchedEffect(state.cancelled) { if (state.cancelled) onCancelAdd() }
    LaunchedEffect(state.discarded) { if (state.discarded) onDiscarded() }
}

package com.paperly.app.feature.reader

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.paperly.app.domain.reader.countedSeconds

/** Counts foreground time while a reader is on screen (RESUMED -> PAUSED/leaving). Local only, no content. */
@Composable
internal fun ReadingTimeEffect(viewModel: ReadingTimeViewModel = hiltViewModel()) {
    // Activity lifecycle (same approach as FlushOnPause): no Compose-local needed.
    val lifecycle = (LocalContext.current as FragmentActivity).lifecycle
    DisposableEffect(lifecycle, viewModel) {
        var startedAt = NOT_RUNNING
        fun start() {
            if (startedAt == NOT_RUNNING) startedAt = SystemClock.elapsedRealtime()
        }
        fun stop() {
            if (startedAt != NOT_RUNNING) {
                viewModel.record(countedSeconds(SystemClock.elapsedRealtime() - startedAt))
                startedAt = NOT_RUNNING
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
}

private const val NOT_RUNNING = -1L

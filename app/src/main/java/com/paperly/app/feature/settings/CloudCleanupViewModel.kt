package com.paperly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.sync.CleanupBlock
import com.paperly.app.domain.sync.CleanupPreview
import com.paperly.app.domain.sync.CleanupResult
import com.paperly.app.domain.sync.CloudCleanup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CleanupUi {
    data object Idle : CleanupUi

    data object Working : CleanupUi

    /** Waiting for the user's yes: nothing has been deleted yet. */
    data class Confirm(val count: Int, val bytes: Long) : CleanupUi

    data object NothingToClean : CleanupUi

    data class Blocked(val reason: CleanupBlock) : CleanupUi

    data class Done(val deleted: Int, val remaining: Int) : CleanupUi
}

/** Settings -> Sync -> "Clean up cloud storage": check first, delete only after an explicit confirmation. */
@HiltViewModel
class CloudCleanupViewModel @Inject constructor(private val cleanup: CloudCleanup) : ViewModel() {
    private val _ui = MutableStateFlow<CleanupUi>(CleanupUi.Idle)
    val ui: StateFlow<CleanupUi> = _ui

    fun check() = start {
        when (val preview = cleanup.preview()) {
            is CleanupPreview.Blocked -> CleanupUi.Blocked(preview.reason)
            is CleanupPreview.Found ->
                if (preview.count == 0) CleanupUi.NothingToClean else CleanupUi.Confirm(preview.count, preview.bytes)
        }
    }

    fun confirm() {
        if (_ui.value !is CleanupUi.Confirm) return
        start {
            when (val result = cleanup.clean()) {
                is CleanupResult.Blocked -> CleanupUi.Blocked(result.reason)
                is CleanupResult.Done -> CleanupUi.Done(result.deleted, result.remaining)
            }
        }
    }

    fun dismiss() {
        if (_ui.value is CleanupUi.Confirm) _ui.value = CleanupUi.Idle
    }

    /** Double-tap guard: a second request while one is running is ignored. */
    private fun start(block: suspend () -> CleanupUi) {
        if (_ui.value is CleanupUi.Working) return
        _ui.value = CleanupUi.Working
        viewModelScope.launch {
            _ui.value = runCatching { block() }.getOrElse {
                if (it is CancellationException) throw it
                CleanupUi.Blocked(CleanupBlock.UNAVAILABLE)
            }
        }
    }
}

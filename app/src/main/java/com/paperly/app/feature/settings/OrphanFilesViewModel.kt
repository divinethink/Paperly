package com.paperly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.storage.LocalOrphanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface OrphanUi {
    data object Idle : OrphanUi
    data object Busy : OrphanUi
    data class Found(val count: Int, val bytes: Long) : OrphanUi
    data class Done(val deleted: Int, val failed: Int) : OrphanUi
    data object Failed : OrphanUi
}

/** Manual only: [scan] shows what is there; nothing is deleted until the user taps [clean]. */
@HiltViewModel
class OrphanFilesViewModel @Inject constructor(private val repository: LocalOrphanRepository) : ViewModel() {
    private val _state = MutableStateFlow<OrphanUi>(OrphanUi.Idle)
    val state: StateFlow<OrphanUi> = _state.asStateFlow()

    fun scan() = launchOp { repository.scan().let { OrphanUi.Found(it.count, it.bytes) } }

    /** Only after a scan that found something; otherwise ignored. */
    fun clean() {
        if ((_state.value as? OrphanUi.Found)?.count?.let { it > 0 } != true) return
        launchOp { repository.clean().let { OrphanUi.Done(it.deleted, it.failed) } }
    }

    /** Ignored while busy (double-tap safe). */
    private fun launchOp(block: suspend () -> OrphanUi) {
        if (_state.value == OrphanUi.Busy) return
        _state.value = OrphanUi.Busy
        viewModelScope.launch {
            _state.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OrphanUi.Failed
            }
        }
    }
}

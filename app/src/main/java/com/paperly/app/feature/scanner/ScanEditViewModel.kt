package com.paperly.app.feature.scanner

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.data.scanner.ScanPageRenderer
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.ScanEditRepository
import com.paperly.app.domain.scanner.ScanEditResult
import com.paperly.app.domain.scanner.ScanOutputSize
import com.paperly.app.domain.scanner.ScanQuad
import com.paperly.app.domain.scanner.canDeleteAt
import com.paperly.app.domain.scanner.moved
import com.paperly.app.domain.scanner.withQuad
import com.paperly.app.domain.scanner.withoutAt
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [source] = current page image (crop view), [preview] = current page with its edits applied. */
data class ScanEditUi(
    val loading: Boolean = true,
    val notEditable: Boolean = false,
    val pages: List<ScanEditPage> = emptyList(),
    val current: Int = 0,
    val cropping: Boolean = false,
    val source: ImageBitmap? = null,
    val preview: ImageBitmap? = null,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val failed: ScanEditResult? = null,
    val addFailed: Boolean = false,
    val done: Boolean = false,
) {
    val page: ScanEditPage? get() = pages.getOrNull(current)
    val canDelete: Boolean get() = pages.canDeleteAt(current)
}

private const val SOURCE_SIDE = 1400
private const val PREVIEW_SIDE = 1000

@HiltViewModel
class ScanEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScanEditRepository,
) : ViewModel() {
    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _state = MutableStateFlow(ScanEditUi())
    val state: StateFlow<ScanEditUi> = _state.asStateFlow()
    private var sourceBitmap: Bitmap? = null
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val pages = repository.load(documentId)
            if (pages == null) {
                _state.value = ScanEditUi(loading = false, notEditable = true)
            } else {
                _state.value = ScanEditUi(loading = false, pages = pages)
                showPage(0)
            }
        }
    }

    fun goTo(index: Int) {
        if (index in _state.value.pages.indices && !_state.value.saving) showPage(index)
    }

    /** Filter / rotate / reset-crop; the page rules (locked pages keep crop and rotation) live in the domain. */
    fun edit(transform: (ScanEditPage) -> ScanEditPage) {
        val page = _state.value.page ?: return
        val changed = transform(page)
        if (changed == page) return
        _state.update { it.withPage(changed) }
        refreshPreview()
    }

    /** Crop-frame drag: no preview render until the crop view is left. */
    fun setQuad(quad: ScanQuad) {
        val page = _state.value.page ?: return
        val changed = page.withQuad(quad)
        if (changed != page) _state.update { it.withPage(changed) }
    }

    fun toggleCrop() {
        val state = _state.value
        if (state.page?.locked != false) return
        _state.update { it.copy(cropping = !it.cropping) }
        if (state.cropping) refreshPreview() // leaving the crop view: show the flattened result
    }

    fun onAddUnavailable() = _state.update { it.copy(addFailed = true) }

    fun dismissError() = _state.update { it.copy(failed = null, addFailed = false) }

    /** S4: moves the shown page one place earlier (-1) or later (+1); the view follows it. */
    fun move(delta: Int) {
        val state = _state.value
        val to = state.current + delta
        if (state.saving || to !in state.pages.indices) return
        _state.update { it.copy(pages = it.pages.moved(it.current, to), current = to, dirty = true) }
    }

    /** S4: removes the shown page (the screen asks first). A highlighted page or the last page stays. */
    fun deleteCurrent() {
        val state = _state.value
        if (state.saving || !state.canDelete) return
        val next = state.current.coerceAtMost(state.pages.size - 2)
        _state.update { it.copy(pages = it.pages.withoutAt(it.current), dirty = true) }
        showPage(next)
    }

    /** S4: adds pictures (scanner pages or gallery picks) at the end of the scan. */
    fun addPages(uris: List<String>) {
        if (uris.isEmpty() || _state.value.saving) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val added = repository.stageAdded(documentId, uris)
            if (added == null) {
                _state.update { it.copy(loading = false, addFailed = true) }
            } else {
                val first = _state.value.pages.size
                _state.update { it.copy(pages = it.pages + added, dirty = true) }
                showPage(first)
            }
        }
    }

    override fun onCleared() {
        repository.discardStaged(documentId) // pictures staged for adding but never saved
    }

    fun save(size: ScanOutputSize) {
        val state = _state.value
        if (state.saving) return
        _state.update { it.copy(saving = true, failed = null) }
        viewModelScope.launch {
            val result = repository.apply(documentId, state.pages, size)
            _state.update {
                if (result == ScanEditResult.SAVED) {
                    it.copy(saving = false, done = true)
                } else {
                    it.copy(saving = false, failed = result)
                }
            }
        }
    }

    private fun showPage(index: Int) {
        previewJob?.cancel()
        _state.update { it.copy(current = index, cropping = false, loading = true) }
        previewJob = viewModelScope.launch {
            val file = _state.value.page?.let { repository.pageFile(documentId, it) }
            val source = file?.let { decodeOrNull(it) }
            sourceBitmap = source
            _state.update { it.copy(source = source?.asImageBitmap(), loading = false) }
            renderInto(_state)
        }
    }

    private fun refreshPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch { renderInto(_state) }
    }

    private suspend fun renderInto(target: MutableStateFlow<ScanEditUi>) {
        val page = target.value.page
        val source = sourceBitmap
        if (page == null || source == null) return
        val rendered = withContext(Dispatchers.Default) {
            try {
                ScanPageRenderer.render(source, page.quad, page.rotation, page.filter, PREVIEW_SIDE)
            } catch (ignored: OutOfMemoryError) {
                null // keep the previous preview
            }
        }
        if (rendered != null) target.update { it.copy(preview = rendered.asImageBitmap()) }
    }
}

private fun ScanEditUi.withPage(page: ScanEditPage) =
    copy(pages = pages.mapIndexed { position, old -> if (position == current) page else old }, dirty = true)

private suspend fun decodeOrNull(file: File): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            ScanPageRenderer.decode(file, SOURCE_SIDE)
        } catch (ignored: IOException) {
            null
        } catch (ignored: OutOfMemoryError) {
            null
        }
    }

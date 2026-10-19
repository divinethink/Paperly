package com.paperly.app.feature.scanner

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.data.scanner.ScanPageRenderer
import com.paperly.app.domain.scanner.SCAN_FILTER_CHOICES
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.ScanEditRepository
import com.paperly.app.domain.scanner.ScanEditResult
import com.paperly.app.domain.scanner.ScanFilter
import com.paperly.app.domain.scanner.ScanOcrRepository
import com.paperly.app.domain.scanner.ScanOutputSettings
import com.paperly.app.domain.scanner.ScanQuad
import com.paperly.app.domain.scanner.canDeleteAt
import com.paperly.app.domain.scanner.moved
import com.paperly.app.domain.scanner.toggledBw
import com.paperly.app.domain.scanner.withFilter
import com.paperly.app.domain.scanner.withFilterAll
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
import kotlinx.coroutines.flow.first
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
    val thumbs: Map<ScanFilter, ImageBitmap> = emptyMap(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val failed: ScanEditResult? = null,
    val addFailed: Boolean = false,
    val done: Boolean = false,
    /** Opened from the card menu "Add pages": the scanner starts once the pages are loaded. */
    val autoAdd: Boolean = false,
    /** Opened from the grid's "Add pictures": the gallery picker starts once the pages are loaded. */
    val autoPick: Boolean = false,
    /** Opened to add pages: the old page must not show while the picker/scanner is up or its result is staged. */
    val awaitingAdd: Boolean = false,
    /** The picker/scanner was closed with nothing added: the screen goes back to where it came from. */
    val cancelled: Boolean = false,
    /** Opened to delete one page, but it carries highlights or is the last page: nothing was deleted. */
    val deleteBlocked: Boolean = false,
) {
    val page: ScanEditPage? get() = pages.getOrNull(current)
    val canDelete: Boolean get() = pages.canDeleteAt(current)
}

private const val SOURCE_SIDE = 1400
private const val PREVIEW_SIDE = 1000
private const val THUMB_SIDE = 160

@HiltViewModel
@Suppress("TooManyFunctions") // one small action per editor operation
class ScanEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScanEditRepository,
    private val ocr: ScanOcrRepository,
    private val outputSettings: ScanOutputSettings,
) : ViewModel() {
    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val isNewScan: Boolean = savedStateHandle.get<Boolean>("new") == true
    private val addAtStart: Boolean = savedStateHandle.get<Boolean>("add") == true
    private val pickAtStart: Boolean = savedStateHandle.get<Boolean>("pick") == true
    private val startPage: Int = savedStateHandle.get<Int>("page") ?: -1 // opened from the Reader's top bar
    private val cropAtStart: Boolean = savedStateHandle.get<Boolean>("crop") == true
    private val deleteAtStart: Boolean = savedStateHandle.get<Boolean>("delete") == true

    /** A fresh scan opens its document when the editor is left (saved or skipped); otherwise back to the Library. */
    val exitDocumentId: String? = documentId.takeIf { isNewScan }
    private val _state = MutableStateFlow(ScanEditUi())
    val state: StateFlow<ScanEditUi> = _state.asStateFlow()
    private var sourceBitmap: Bitmap? = null
    private var previewJob: Job? = null
    private var thumbKey: ThumbKey? = null
    private val filterBeforeBw = HashMap<String, ScanFilter>() // per page (source index / added name), not saved

    init {
        viewModelScope.launch {
            val pages = repository.load(documentId)
            if (pages == null) {
                _state.value = ScanEditUi(loading = false, notEditable = true)
            } else {
                _state.value = ScanEditUi(
                    loading = false,
                    pages = pages,
                    autoAdd = addAtStart,
                    autoPick = pickAtStart,
                    awaitingAdd = addAtStart || pickAtStart,
                )
                openAtStart(pages)
            }
        }
    }

    /** First page shown: page 1, or the page the Reader's top bar asked for (crop step / delete at once). */
    private fun openAtStart(pages: List<ScanEditPage>) {
        val start = startPage.takeIf { it in pages.indices }
        showPage(start ?: 0, cropFirst = cropAtStart && start != null && !pages[start].locked)
        if (isNewScan && pages.firstOrNull()?.locked == false) _state.update { it.copy(cropping = true) }
        if (deleteAtStart && start != null) removeAtStart(start)
    }

    /** Reader top-bar delete (already confirmed there): removes the page and saves; a locked/last page stays. */
    private fun removeAtStart(index: Int) {
        if (!_state.value.pages.canDeleteAt(index)) {
            _state.update { it.copy(deleteBlocked = true) }
            return
        }
        _state.update {
            it.copy(pages = it.pages.withoutAt(index), current = index.coerceAtMost(it.pages.size - 2), dirty = true)
        }
        save()
    }

    fun dismissDeleteBlocked() = _state.update { it.copy(deleteBlocked = false) }

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

    /** B/W button: on = BW (the page remembers its old filter), off = that filter again. */
    fun toggleBw() {
        val page = _state.value.page ?: return
        val key = page.added ?: page.index.toString()
        if (page.filter != ScanFilter.BW) filterBeforeBw[key] = page.filter
        edit { it.withFilter(toggledBw(it.filter, filterBeforeBw[key])) }
    }

    /** "Apply to all": the shown page's filter goes onto every page (nothing is written until Save). */
    fun applyFilterToAll() {
        val state = _state.value
        val filter = state.page?.filter ?: return
        if (state.saving) return
        _state.update { it.copy(pages = it.pages.withFilterAll(filter), dirty = true) }
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

    /** Recognised text of the SAVED scan (null = nothing read yet, e.g. the Settings switch is OFF). */
    suspend fun recognisedText(): String? = ocr.text(documentId)

    /** The "Add pages" start is used up (also after a rotation). */
    fun consumeAutoAdd() = _state.update { it.copy(autoAdd = false) }

    /** The gallery picker start is used up. */
    fun consumeAutoPick() = _state.update { it.copy(autoPick = false) }

    fun onAddUnavailable() = _state.update { it.copy(addFailed = true, awaitingAdd = false) }

    /** Picker/scanner closed without a result: only a screen opened just to add pages goes back. */
    fun onAddCancelled() = _state.update { if (it.awaitingAdd) it.copy(awaitingAdd = false, cancelled = true) else it }

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

    /**
     * S4: adds pictures (scanner pages or gallery picks) at the end of the scan and shows the first new one.
     * [crop] (gallery picks, which are not cropped yet) opens it in the crop step; "Next" then leads to the filters.
     */
    fun addPages(uris: List<String>, crop: Boolean) {
        if (uris.isEmpty() || _state.value.saving) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val added = repository.stageAdded(documentId, uris)
            if (added == null) {
                _state.update { it.copy(loading = false, addFailed = true, awaitingAdd = false) }
            } else {
                val first = _state.value.pages.size
                _state.update { it.copy(pages = it.pages + added, dirty = true, awaitingAdd = false) }
                showPage(first, cropFirst = crop)
            }
        }
    }

    override fun onCleared() {
        repository.discardStaged(documentId) // pictures staged for adding but never saved
    }

    /** Saves with the size chosen once in the Scanned tab's menu (no question each time). */
    fun save() {
        val state = _state.value
        if (state.saving) return
        _state.update { it.copy(saving = true, failed = null) }
        viewModelScope.launch {
            val size = outputSettings.size.first()
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

    private fun showPage(index: Int, cropFirst: Boolean = false) {
        previewJob?.cancel()
        thumbKey = null
        _state.update {
            // cropFirst: the crop frame must not flash over the previous page's image while the new one loads
            it.copy(
                current = index,
                cropping = cropFirst,
                loading = true,
                source = if (cropFirst) null else it.source,
                preview = if (cropFirst) null else it.preview,
            )
        }
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
                ScanPageRenderer.render(source, page, PREVIEW_SIDE)
            } catch (ignored: OutOfMemoryError) {
                null // keep the previous preview
            }
        }
        if (rendered != null) target.update { it.copy(preview = rendered.asImageBitmap()) }
        renderThumbs(target, source, page)
    }

    /** Small preview of every filter on the current page; redone only when the page, crop or rotation changed. */
    private suspend fun renderThumbs(target: MutableStateFlow<ScanEditUi>, source: Bitmap, page: ScanEditPage) {
        val key = ThumbKey(target.value.current, page.quad, page.rotation)
        if (key == thumbKey) return
        val thumbs = withContext(Dispatchers.Default) {
            try {
                SCAN_FILTER_CHOICES.associateWith {
                    val look = page.copy(filter = it, brightness = 0, contrast = 0)
                    ScanPageRenderer.render(source, look, THUMB_SIDE).asImageBitmap()
                }
            } catch (ignored: OutOfMemoryError) {
                null // keep the previous thumbnails
            }
        }
        if (thumbs != null) {
            thumbKey = key
            target.update { it.copy(thumbs = thumbs) }
        }
    }
}

private data class ThumbKey(val page: Int, val quad: ScanQuad, val rotation: Int)

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

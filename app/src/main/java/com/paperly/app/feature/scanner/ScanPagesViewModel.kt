package com.paperly.app.feature.scanner

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.reader.ReaderEngine
import com.paperly.app.domain.reader.ReaderStateRepository
import com.paperly.app.feature.reader.PageBitmapCache
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ScanPagesUi {
    data object Loading : ScanPagesUi
    data object Failed : ScanPagesUi

    /** [aspect] = width / height of page 1; the thumbnail boxes use it. */
    data class Ready(val document: Document, val pageCount: Int, val aspect: Float) : ScanPagesUi
}

private class OpenedPdf(val engine: ReaderEngine, val pageCount: Int)

private const val DEFAULT_ASPECT = 0.7f
private const val CACHE_KB = 24 * 1024

/**
 * Page grid of one scanned document. Read-only: thumbnails come from the document's PDF (what the Reader shows),
 * nothing is written except the reading position when a page is tapped.
 */
@HiltViewModel
class ScanPagesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val readerState: ReaderStateRepository,
    private val fileStore: DocumentFileStore,
    // Provider = lazy: the engine is created only when the grid is opened.
    private val engineProvider: Provider<ReaderEngine>,
) : ViewModel() {

    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _state = MutableStateFlow<ScanPagesUi>(ScanPagesUi.Loading)
    val state: StateFlow<ScanPagesUi> = _state.asStateFlow()
    private var engine: ReaderEngine? = null
    private val cache = PageBitmapCache(CACHE_KB)
    private val lock = Mutex() // one open/render/close at a time

    init {
        reload()
    }

    fun reload() {
        _state.value = ScanPagesUi.Loading
        viewModelScope.launch { _state.value = lock.withLock { load() } }
    }

    private suspend fun load(): ScanPagesUi {
        val document = repository.getDocument(documentId) ?: return ScanPagesUi.Failed
        val opened = openEngine() ?: return ScanPagesUi.Failed
        engine?.close()
        engine = opened.engine
        return ScanPagesUi.Ready(document, opened.pageCount, opened.engine.pageAspect(0) ?: DEFAULT_ASPECT)
    }

    /** A fresh engine on the document file; null (engine closed again) if it cannot be opened. */
    private suspend fun openEngine(): OpenedPdf? {
        val file = fileStore.resolve(documentId) ?: return null
        val pdf = engineProvider.get()
        var pages = 0
        try {
            val result = pdf.open(file)
            if (result is OpenResult.Success) pages = result.pageCount
        } finally {
            if (pages < 1) pdf.close()
        }
        return if (pages < 1) null else OpenedPdf(pdf, pages)
    }

    private suspend fun liveEngine(): ReaderEngine? =
        engine ?: openEngine()?.also { engine = it.engine }?.engine

    /** Thumbnail of page [index] (0-based), at most [widthPx] wide; null if it cannot be drawn. */
    suspend fun thumbnail(index: Int, widthPx: Int): Bitmap? {
        cache.get(index, widthPx)?.let { return it }
        return lock.withLock {
            cache.get(index, widthPx)
                ?: liveEngine()?.renderPage(index, widthPx)?.also { cache.put(index, widthPx, it) }
        }
    }

    /** Screen stopped (e.g. the Reader is on top): hand the PDF back; the next thumbnail reopens it. */
    fun release() {
        viewModelScope.launch {
            lock.withLock {
                engine?.close()
                engine = null
            }
        }
    }

    /** Saves [index] as the reading position of this document, then [onReady] (the Reader reads it on open). */
    fun openPage(index: Int, onReady: () -> Unit) {
        val count = (_state.value as? ScanPagesUi.Ready)?.pageCount ?: return
        viewModelScope.launch {
            runCatching { readerState.saveProgress(documentId, index, count) }
            onReady()
        }
    }

    override fun onCleared() {
        engine?.close()
        engine = null
    }
}

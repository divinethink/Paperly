package com.paperly.app.feature.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.R
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.core.intent.ReaderRedirects
import com.paperly.app.core.model.DocumentType
import com.paperly.app.core.perf.PerfTrace
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import com.paperly.app.domain.ocr.PageOcr
import com.paperly.app.domain.reader.AnnotateStyleStore
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationRepository
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.reader.PdfHighlightFileSync
import com.paperly.app.domain.reader.ReaderEngine
import com.paperly.app.domain.reader.ReaderPreferences
import com.paperly.app.domain.reader.ReaderStateRepository
import com.paperly.app.domain.reader.ReaderTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ReaderError { NONE, PASSWORD_REQUIRED, UNSUPPORTED, FAILED }

data class ReaderUiState(
    val loading: Boolean = true,
    val document: Document? = null,
    val pageCount: Int = 0,
    val pageAspect: Float = 0f,
    val startPage: Int = 0,
    val error: ReaderError = ReaderError.NONE,
)

@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions") // Hilt DI collaborators; 11 small state/event handlers
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val readerState: ReaderStateRepository,
    private val annotationRepository: AnnotationRepository,
    annotateStyleStore: AnnotateStyleStore,
    private val preferences: ReaderPreferences,
    private val fileStore: DocumentFileStore,
    // Provider = lazy: the engine is created only when a PDF is actually opened (Rule #10).
    private val engineProvider: Provider<ReaderEngine>,
    private val pageOcr: PageOcr,
    private val quickStore: QuickViewStore,
    private val redirects: ReaderRedirects,
    private val highlightFileSync: PdfHighlightFileSync,
) : ViewModel() {

    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()

    /** Quick view: a PDF opened from outside and not (yet) in the Library — no progress, bookmarks or annotations. */
    val isQuick: Boolean = QuickViewStore.isToken(documentId)
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    private var engine: ReaderEngine? = null
    private val cache = PageBitmapCache()
    private val renderLock = Mutex() // one render at a time keeps peak memory bounded
    private val currentPage = MutableStateFlow<Int?>(null)
    private var lastSaved = -1

    /** L3: a highlight changed in this session, so on close it is offered to the PDF-file writer. */
    private var annotationsChanged = false

    val search = ReaderSearchController(
        scope = viewModelScope,
        engine = { engine },
        pageCount = { _uiState.value.pageCount },
        currentPage = { currentPage.value ?: 0 },
    )

    val textSelect = PdfTextSelectController(viewModelScope) { engine }

    val reflowOcr = ReflowOcrController(viewModelScope, pageOcr, { engine }, documentId)

    /** Quick view only: highlights, bookmarks and position live here until "Save to Library". */
    private val quick = QuickSession()

    private val _keepPrompt = MutableStateFlow(false)

    /** Quick view: true once, after the first highlight, to ask whether to keep the PDF in the Library. */
    val keepPrompt: StateFlow<Boolean> = _keepPrompt.asStateFlow()

    val annotations = AnnotationController(
        viewModelScope,
        if (isQuick) quick.annotations else annotationRepository,
        annotateStyleStore,
        documentId,
    )

    val theme: StateFlow<ReaderTheme> = preferences.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReaderTheme.AUTO)

    private val bookmarkSource: Flow<Set<Int>> = when {
        isQuick -> quick.bookmarks
        documentId.isEmpty() -> emptyFlow()
        else -> readerState.observeBookmarkedPages(documentId)
    }

    val bookmarkedPages: StateFlow<Set<Int>> =
        bookmarkSource.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    init {
        textSelect.onMiss = { page, from, to -> annotations.quickMiss(page, from, to) }
        if (isQuick) {
            viewModelScope.launch {
                annotations.items.first { it.isNotEmpty() }
                _keepPrompt.value = true // first highlight: offer to keep the PDF (with it) in the Library
            }
        }
        observeAnnotationChanges()
        observeProgress()
        viewModelScope.launch {
            val document = PerfTrace.span("reader.open") { loadDocument() }
            if (document == null) {
                _uiState.value = ReaderUiState(loading = false)
                return@launch
            }
            if (!isQuick) repository.markOpened(documentId)
            _uiState.value = if (document.type == DocumentType.PDF || document.type == DocumentType.SCANNED_PDF) {
                openPdf(document)
            } else {
                failed(document, ReaderError.UNSUPPORTED)
            }
            if (_uiState.value.pageCount > 0) search.probe() // after state is set: probe reads pageCount
        }
    }

    private suspend fun loadDocument(): Document? =
        if (isQuick) quickStore.document(documentId) else repository.getDocument(documentId)

    private suspend fun openPdf(document: Document): ReaderUiState {
        val file = (if (isQuick) quickStore.resolve(document.id) else fileStore.resolve(document.id))
            ?: return failed(document, ReaderError.FAILED)
        val pdf = engineProvider.get().also { engine = it }
        return try {
            when (val result = PerfTrace.span("reader.pdf.open") { pdf.open(file) }) {
                is OpenResult.Success -> {
                    val start = (readerState.getSavedPage(document.id) ?: 0).coerceIn(0, result.pageCount - 1)
                    lastSaved = start
                    val aspect = PerfTrace.span("reader.pdf.page0") { pdf.pageAspect(0) }
                    if (aspect == null) {
                        failed(document, ReaderError.FAILED)
                    } else {
                        ReaderUiState(
                            loading = false,
                            document = document,
                            pageCount = result.pageCount,
                            pageAspect = aspect,
                            startPage = start,
                        )
                    }
                }
                OpenResult.PasswordRequired -> failed(document, ReaderError.PASSWORD_REQUIRED)
                OpenResult.Failed -> failed(document, ReaderError.FAILED)
            }
        } catch (e: CancellationException) {
            pdf.close()
            throw e
        }
    }

    /** Debounced (no DB write per scroll frame); [flush] persists immediately on pause. */
    @OptIn(FlowPreview::class)
    private fun observeProgress() {
        viewModelScope.launch {
            currentPage.filterNotNull().debounce(PROGRESS_DEBOUNCE_MS).distinctUntilChanged().collect { save(it) }
        }
    }

    private suspend fun save(page: Int) {
        val count = _uiState.value.pageCount
        if (isQuick || count <= 0 || page == lastSaved) return
        readerState.saveProgress(documentId, page, count)
        lastSaved = page
    }

    fun onPageChanged(page: Int) {
        if (_uiState.value.pageCount > 0) currentPage.value = page
        if (isQuick) quick.page = page
    }

    fun flush() {
        if (isQuick) return
        viewModelScope.launch {
            currentPage.value?.let { save(it) }
            readerState.flushSync(documentId)
        }
    }

    private val _reflow = MutableStateFlow(false)

    /** Reflow (text) view of the PDF instead of page images; not persisted, never touches saved progress. */
    val reflow: StateFlow<Boolean> = _reflow.asStateFlow()

    fun toggleReflow() {
        _reflow.value = !_reflow.value
    }

    fun setTheme(theme: ReaderTheme) {
        viewModelScope.launch { preferences.setTheme(theme) }
    }

    fun toggleBookmark(page: Int) {
        if (isQuick) {
            quick.toggleBookmark(page)
            return
        }
        if (_uiState.value.pageCount <= 0) return
        viewModelScope.launch { readerState.toggleBookmark(documentId, page) }
    }

    private fun failed(document: Document, error: ReaderError) =
        ReaderUiState(loading = false, document = document, error = error)

    /** Cached page bitmap (LRU), keyed by page + [widthPx] so a zoom-driven higher resolution re-renders. */
    suspend fun pageBitmap(index: Int, widthPx: Int): Bitmap? {
        val pdf = engine ?: return null
        return cache.get(index, widthPx) ?: renderLock.withLock {
            cache.get(index, widthPx) ?: pdf.renderPage(index, widthPx)?.also { cache.put(index, widthPx, it) }
        }
    }

    private val _saved = MutableStateFlow(false)

    /** Quick view only: the PDF has been added to the Library (the menu item then disappears). */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()
    private val _notice = MutableStateFlow<Int?>(null)

    /** One-shot message (string resource) for the UI to show once. */
    val notice: StateFlow<Int?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    fun dismissKeepPrompt() {
        _keepPrompt.value = false
    }

    /** Quick view -> Library: imports the PDF, copies highlights/bookmarks/position, then reopens the Library copy. */
    fun saveToLibrary() {
        _keepPrompt.value = false
        val file = quickStore.resolve(documentId) ?: return
        val title = _uiState.value.document?.title ?: "Document"
        viewModelScope.launch {
            val result = repository.importQuick(Uri.fromFile(file).toString(), title, DocumentType.PDF)
            val target = when (result) {
                is ImportResult.Success -> result.documentId
                is ImportResult.Duplicate -> result.existingId
                else -> null
            }
            if (target == null) {
                _notice.value = R.string.quick_save_failed
                return@launch
            }
            quick.commitTo(
                target,
                annotationRepository,
                readerState,
                _uiState.value.pageCount,
                fresh = result is ImportResult.Success,
            )
            _saved.value = true
            redirects.post(target)
        }
    }

    override fun onCleared() {
        if (isQuick) quickStore.delete(documentId) // the temporary copy dies with the Reader
        pageOcr.release()
        engine?.close()
        cache.clear()
        // After the engine closed the file: writes the highlights into it if the setting is on (app scope).
        if (annotationsChanged) highlightFileSync.schedule(documentId)
    }

    /** First emission = what was already there; any later difference means a highlight was added/edited/removed. */
    private fun observeAnnotationChanges() {
        if (isQuick || documentId.isEmpty()) return
        viewModelScope.launch {
            var baseline: List<Annotation>? = null
            annotationRepository.observe(documentId).collect { now ->
                val before = baseline
                if (before == null) baseline = now else if (now != before) annotationsChanged = true
            }
        }
    }

    private companion object {
        const val PROGRESS_DEBOUNCE_MS = 800L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

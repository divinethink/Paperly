package com.paperly.app.feature.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.model.DocumentType
import com.paperly.app.core.perf.PerfTrace
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.reader.AnnotationRepository
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.reader.ReaderEngine
import com.paperly.app.domain.reader.ReaderPreferences
import com.paperly.app.domain.reader.ReaderStateRepository
import com.paperly.app.domain.reader.ReaderTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
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
@Suppress("LongParameterList") // Hilt constructor injection; each dependency is a distinct collaborator
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val readerState: ReaderStateRepository,
    annotationRepository: AnnotationRepository,
    private val preferences: ReaderPreferences,
    private val fileStore: DocumentFileStore,
    // Provider = lazy: the engine is created only when a PDF is actually opened (Rule #10).
    private val engineProvider: Provider<ReaderEngine>,
) : ViewModel() {

    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    private var engine: ReaderEngine? = null
    private val cache = PageBitmapCache()
    private val renderLock = Mutex() // one render at a time keeps peak memory bounded
    private val currentPage = MutableStateFlow<Int?>(null)
    private var lastSaved = -1

    val search = ReaderSearchController(
        scope = viewModelScope,
        engine = { engine },
        pageCount = { _uiState.value.pageCount },
        currentPage = { currentPage.value ?: 0 },
    )

    val annotations = AnnotationController(viewModelScope, annotationRepository, documentId)

    val theme: StateFlow<ReaderTheme> = preferences.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReaderTheme.AUTO)

    val bookmarkedPages: StateFlow<Set<Int>> =
        (if (documentId.isEmpty()) emptyFlow() else readerState.observeBookmarkedPages(documentId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    init {
        observeProgress()
        viewModelScope.launch {
            val document = PerfTrace.span("reader.open") { repository.getDocument(documentId) }
            if (document == null) {
                _uiState.value = ReaderUiState(loading = false)
                return@launch
            }
            repository.markOpened(documentId)
            _uiState.value = if (document.type == DocumentType.PDF) {
                openPdf(document)
            } else {
                failed(document, ReaderError.UNSUPPORTED)
            }
            if (_uiState.value.pageCount > 0) search.probe() // after state is set: probe reads pageCount
        }
    }

    private suspend fun openPdf(document: Document): ReaderUiState {
        val file = fileStore.resolve(document.id) ?: return failed(document, ReaderError.FAILED)
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
        if (count <= 0 || page == lastSaved) return
        readerState.saveProgress(documentId, page, count)
        lastSaved = page
    }

    fun onPageChanged(page: Int) {
        if (_uiState.value.pageCount > 0) currentPage.value = page
    }

    fun flush() {
        currentPage.value?.let { page -> viewModelScope.launch { save(page) } }
    }

    fun setTheme(theme: ReaderTheme) {
        viewModelScope.launch { preferences.setTheme(theme) }
    }

    fun toggleBookmark(page: Int) {
        if (_uiState.value.pageCount > 0) viewModelScope.launch { readerState.toggleBookmark(documentId, page) }
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

    override fun onCleared() {
        engine?.close()
        cache.clear()
    }

    private companion object {
        const val PROGRESS_DEBOUNCE_MS = 800L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

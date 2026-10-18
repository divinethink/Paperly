package com.paperly.app.feature.reader

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.R
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.core.model.DocumentType
import com.paperly.app.core.perf.PerfTrace
import com.paperly.app.data.reader.EpubPublicationOpener
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import com.paperly.app.domain.reader.ReaderStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

data class EpubUiState(
    val loading: Boolean = true,
    val isEpub: Boolean = false,
    val title: String = "",
    val publication: Publication? = null,
    val initialLocator: Locator? = null,
    val searchable: Boolean = false,
)

private const val PROGRESS_DEBOUNCE_MS = 800L
private const val EPUB_EXTENSION = "epub"

/** Decides PDF vs EPUB for the reader route; for EPUB opens the book, resumes, saves progress, bookmarks. */
@OptIn(FlowPreview::class)
@HiltViewModel
@Suppress("TooManyFunctions") // small state/event handlers; the quick-view ones are the newest
class EpubReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val opener: EpubPublicationOpener,
    private val readerState: ReaderStateRepository,
    annotationFactory: EpubAnnotationControllerFactory,
    private val deps: EpubQuickDeps,
) : ViewModel() {
    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _uiState = MutableStateFlow(EpubUiState())
    val uiState: StateFlow<EpubUiState> = _uiState.asStateFlow()

    /** Quick view: a book opened from outside, not in the Library — marks are remembered by file checksum. */
    val isQuick: Boolean = QuickViewStore.isToken(documentId)
    private val quick = QuickEpubSession()
    val annotations = annotationFactory.create(viewModelScope, documentId, if (isQuick) quick.annotations else null)

    private val _quickDocument = MutableStateFlow<Document?>(null)

    /** Quick view only: the in-memory document (for Share / Save a copy). */
    val quickDocument: StateFlow<Document?> = _quickDocument.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()
    private val _notice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = _notice.asStateFlow()
    private val latest = MutableStateFlow<Locator?>(null)
    private var quickChecksum: String? = null
    private var persistJob: Job? = null
    private var searchJob: Job? = null
    private val _searchState = MutableStateFlow(EpubSearchState())
    val searchState: StateFlow<EpubSearchState> = _searchState.asStateFlow()

    /** Live reading position (chapter title + progress) for the bottom bar. */
    val position: StateFlow<Locator?> = latest.asStateFlow()

    private val bookmarkSource: Flow<List<String>> = when {
        isQuick -> quick.bookmarks
        documentId.isEmpty() -> emptyFlow()
        else -> readerState.observeBookmarkLocators(documentId)
    }

    val bookmarks: StateFlow<List<String>> =
        bookmarkSource
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isBookmarked: StateFlow<Boolean> = combine(bookmarks.map { it.toSet() }, latest) { saved, locator ->
        locator != null && bookmarkKey(locator) in saved
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch { load() }
        // Debounced like PDF progress: no write on every scroll tick.
        viewModelScope.launch { latest.filterNotNull().debounce(PROGRESS_DEBOUNCE_MS).collect { save(it) } }
    }

    private suspend fun load() {
        val document = if (isQuick) deps.quickStore.document(documentId) else repository.getDocument(documentId)
        if (document == null || document.type != DocumentType.EPUB) {
            _uiState.value = EpubUiState(loading = false)
            return
        }
        if (isQuick) _quickDocument.value = document else repository.markOpened(documentId)
        val file = if (isQuick) deps.quickStore.resolve(documentId) else deps.fileStore.resolve(documentId)
        val publication = file?.let { PerfTrace.span("reader.epub.open") { opener.open(it) } }
        _uiState.value = EpubUiState(
            loading = false,
            isEpub = true,
            title = document.title,
            publication = publication,
            initialLocator = if (isQuick) restoreQuick() else decodeLocator(readerState.getSavedLocator(documentId)),
            searchable = publication?.let(::canSearch) == true,
        )
    }

    /** Quick view: loads the marks remembered for this exact file, starts remembering changes; returns the position. */
    private suspend fun restoreQuick(): Locator? {
        val checksum = deps.quickStore.source(documentId)?.checksum ?: return null
        val saved = deps.quickMarks.load(checksum)
        if (saved != null) quick.restore(saved)
        quickChecksum = checksum
        // started only after the restore, so the first write can never replace remembered marks with an empty list
        persistJob = viewModelScope.launch {
            combine(annotations.items, quick.bookmarks, latest.debounce(PROGRESS_DEBOUNCE_MS)) { _, _, _ -> }
                .collect { rememberQuick() }
        }
        return decodeLocator(saved?.locator)
    }

    private suspend fun rememberQuick() {
        val checksum = quickChecksum ?: return
        deps.quickMarks.save(checksum, quick.snapshot(latest.value?.let(::encodeLocator)))
    }

    fun onLocator(locator: Locator) {
        latest.value = withBookEnd(locator, _uiState.value.publication?.readingOrder?.lastOrNull()?.url())
    }

    /** Immediate save (called on ON_PAUSE) so the last position survives app kill. */
    fun flushProgress() {
        if (isQuick) {
            viewModelScope.launch { rememberQuick() }
            return
        }
        viewModelScope.launch {
            latest.value?.let { save(it) }
            readerState.flushSync(documentId)
        }
    }

    fun toggleBookmark() {
        val locator = latest.value ?: return
        if (isQuick) quick.toggleBookmark(bookmarkKey(locator)) else toggleStored(bookmarkKey(locator))
    }

    private fun toggleStored(key: String) {
        viewModelScope.launch { readerState.toggleBookmarkLocator(documentId, key) }
    }

    /** Stored bookmark JSON is its own identity, so toggling it removes it. */
    fun removeBookmark(json: String) {
        if (isQuick) quick.toggleBookmark(json) else toggleStored(json)
    }

    /** Cancels any running search; a blank query just clears the results. */
    fun search(query: String) {
        searchJob?.cancel()
        val publication = _uiState.value.publication
        val text = query.trim()
        if (publication == null || text.isEmpty()) {
            _searchState.value = EpubSearchState(query = query)
            return
        }
        _searchState.value = EpubSearchState(query = text, searching = true)
        searchJob = viewModelScope.launch {
            val hits = searchPublication(publication, text).orEmpty()
            _searchState.value = EpubSearchState(query = text, hits = hits, searched = true)
        }
    }

    private suspend fun save(locator: Locator) {
        if (isQuick) return
        readerState.saveLocator(documentId, encodeLocator(locator), progressOf(locator))
    }

    fun clearNotice() {
        _notice.value = null
    }

    /** Quick view -> Library: imports the book, copies highlights/bookmarks/position, then reopens the Library copy. */
    fun saveToLibrary() {
        val file = deps.quickStore.resolve(documentId) ?: return
        val title = _uiState.value.title.ifBlank { "Document" }
        viewModelScope.launch {
            val result = repository.importQuick(Uri.fromFile(file).toString(), title, DocumentType.EPUB)
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
                deps.annotationRepository,
                readerState,
                latest.value,
                fresh = result is ImportResult.Success,
            )
            persistJob?.cancel() // the marks now live in the Library; stop remembering them by file
            quickChecksum?.let { deps.quickMarks.delete(it) }
            quickChecksum = null
            _saved.value = true
            deps.redirects.post(target)
        }
    }

    override fun onCleared() {
        _uiState.value.publication?.close()
        // Only an EPUB's temporary copy dies here. This ViewModel also exists for a quick PDF (same route); that copy
        // belongs to the PDF reader, whose write-back into the original needs it after this ViewModel is gone.
        if (isQuick && deps.quickStore.resolve(documentId)?.extension == EPUB_EXTENSION) {
            deps.quickStore.delete(documentId)
        }
    }
}

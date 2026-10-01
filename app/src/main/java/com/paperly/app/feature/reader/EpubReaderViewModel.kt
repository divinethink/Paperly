package com.paperly.app.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.model.DocumentType
import com.paperly.app.core.perf.PerfTrace
import com.paperly.app.data.reader.EpubPublicationOpener
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.reader.ReaderStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
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
)

private const val PROGRESS_DEBOUNCE_MS = 800L

/** Decides PDF vs EPUB for the reader route; for EPUB opens the book, resumes, saves progress, bookmarks. */
@OptIn(FlowPreview::class)
@HiltViewModel
class EpubReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val fileStore: DocumentFileStore,
    private val opener: EpubPublicationOpener,
    private val readerState: ReaderStateRepository,
) : ViewModel() {
    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _uiState = MutableStateFlow(EpubUiState())
    val uiState: StateFlow<EpubUiState> = _uiState.asStateFlow()
    private val latest = MutableStateFlow<Locator?>(null)

    val isBookmarked: StateFlow<Boolean> = combine(
        (if (documentId.isEmpty()) emptyFlow() else readerState.observeBookmarkLocators(documentId)).map { it.toSet() },
        latest,
    ) { bookmarks, locator -> locator != null && bookmarkKey(locator) in bookmarks }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch { load() }
        // Debounced like PDF progress: no write on every scroll tick.
        viewModelScope.launch { latest.filterNotNull().debounce(PROGRESS_DEBOUNCE_MS).collect { save(it) } }
    }

    private suspend fun load() {
        val document = repository.getDocument(documentId)
        if (document == null || document.type != DocumentType.EPUB) {
            _uiState.value = EpubUiState(loading = false)
            return
        }
        repository.markOpened(documentId)
        val file = fileStore.resolve(documentId)
        val publication = file?.let { PerfTrace.span("reader.epub.open") { opener.open(it) } }
        _uiState.value = EpubUiState(
            loading = false,
            isEpub = true,
            title = document.title,
            publication = publication,
            initialLocator = decodeLocator(readerState.getSavedLocator(documentId)),
        )
    }

    fun onLocator(locator: Locator) {
        latest.value = locator
    }

    /** Immediate save (called on ON_PAUSE) so the last position survives app kill. */
    fun flushProgress() {
        latest.value?.let { viewModelScope.launch { save(it) } }
    }

    fun toggleBookmark() {
        latest.value?.let { viewModelScope.launch { readerState.toggleBookmarkLocator(documentId, bookmarkKey(it)) } }
    }

    private suspend fun save(locator: Locator) {
        readerState.saveLocator(documentId, encodeLocator(locator), progressOf(locator))
    }

    override fun onCleared() {
        _uiState.value.publication?.close()
    }
}

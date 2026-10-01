package com.paperly.app.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.model.DocumentType
import com.paperly.app.core.perf.PerfTrace
import com.paperly.app.data.reader.EpubPublicationOpener
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.reader.EpubTypography
import com.paperly.app.domain.reader.EpubTypographyStore
import com.paperly.app.domain.reader.ReaderPreferences
import com.paperly.app.domain.reader.ReaderTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Publication

data class EpubUiState(
    val loading: Boolean = true,
    val isEpub: Boolean = false,
    val title: String = "",
    val publication: Publication? = null,
)

/** Decides PDF vs EPUB for the reader route and, for EPUB, opens the publication (P3-B). */
@HiltViewModel
class EpubReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val fileStore: DocumentFileStore,
    private val opener: EpubPublicationOpener,
    private val typographyStore: EpubTypographyStore,
    private val readerPreferences: ReaderPreferences,
) : ViewModel() {
    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _uiState = MutableStateFlow(EpubUiState())
    val uiState: StateFlow<EpubUiState> = _uiState.asStateFlow()
    val typography: StateFlow<EpubTypography> =
        typographyStore.typography.stateIn(viewModelScope, SharingStarted.Eagerly, EpubTypography())
    val theme: StateFlow<ReaderTheme> =
        readerPreferences.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTheme.AUTO)

    init {
        viewModelScope.launch {
            val document = repository.getDocument(documentId)
            if (document == null || document.type != DocumentType.EPUB) {
                _uiState.value = EpubUiState(loading = false)
                return@launch
            }
            repository.markOpened(documentId)
            val file = fileStore.resolve(documentId)
            val publication = file?.let { PerfTrace.span("reader.epub.open") { opener.open(it) } }
            _uiState.value = EpubUiState(
                loading = false,
                isEpub = true,
                title = document.title,
                publication = publication,
            )
        }
    }

    fun updateTypography(value: EpubTypography) {
        viewModelScope.launch { typographyStore.save(value) }
    }

    fun setTheme(value: ReaderTheme) {
        viewModelScope.launch { readerPreferences.setTheme(value) }
    }

    override fun onCleared() {
        _uiState.value.publication?.close()
    }
}

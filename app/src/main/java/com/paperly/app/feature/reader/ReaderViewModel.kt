package com.paperly.app.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.core.perf.PerfTrace
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReaderUiState(val loading: Boolean = true, val document: Document? = null)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: DocumentRepository,
) : ViewModel() {

    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val document = PerfTrace.span("reader.open") { repository.getDocument(documentId) }
            _uiState.value = ReaderUiState(loading = false, document = document)
        }
    }
}

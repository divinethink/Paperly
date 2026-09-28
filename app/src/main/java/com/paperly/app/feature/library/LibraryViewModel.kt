package com.paperly.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ImportError { Unsupported, Failed }

data class LibraryUiState(
    val documents: List<Document> = emptyList(),
    val isImporting: Boolean = false,
    val error: ImportError? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DocumentRepository,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val error = MutableStateFlow<ImportError?>(null)

    val uiState: StateFlow<LibraryUiState> =
        combine(repository.observeDocuments(), importing, error) { docs, isImporting, err ->
            LibraryUiState(docs, isImporting, err)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun importDocument(uri: String) {
        if (importing.value) return // double-tap guard
        importing.value = true
        error.value = null
        viewModelScope.launch {
            try {
                error.value = when (repository.importDocument(uri)) {
                    is ImportResult.Success -> null
                    ImportResult.UnsupportedType -> ImportError.Unsupported
                    ImportResult.Failed -> ImportError.Failed
                }
            } finally {
                importing.value = false
            }
        }
    }
}

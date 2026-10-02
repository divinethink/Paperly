package com.paperly.app.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.trash.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: TrashRepository,
) : ViewModel() {

    /** null = still loading (so "Trash is empty" is never shown before the first emission). */
    val documents: StateFlow<List<Document>?> = repository.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _deleteFailed = MutableStateFlow(false)
    val deleteFailed: StateFlow<Boolean> = _deleteFailed.asStateFlow()

    fun restore(doc: Document) {
        viewModelScope.launch { repository.restore(doc.id) }
    }

    fun deleteForever(doc: Document) {
        viewModelScope.launch { _deleteFailed.value = !repository.deleteForever(doc.id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { _deleteFailed.value = !repository.emptyTrash() }
    }
}

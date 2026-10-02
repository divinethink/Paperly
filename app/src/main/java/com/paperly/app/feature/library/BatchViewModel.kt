package com.paperly.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.folder.FolderRepository
import com.paperly.app.domain.folder.parseTags
import com.paperly.app.domain.trash.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Multi-select actions. Each is a loop of the existing idempotent single-document calls; Trash stays restorable. */
@HiltViewModel
class BatchViewModel @Inject constructor(
    private val documents: DocumentRepository,
    private val trash: TrashRepository,
    private val folders: FolderRepository,
) : ViewModel() {

    fun setFavorite(docs: List<Document>, favorite: Boolean) {
        viewModelScope.launch { docs.forEach { documents.setFavorite(it.id, favorite) } }
    }

    fun moveToTrash(docs: List<Document>) {
        viewModelScope.launch { docs.forEach { trash.trash(it.id) } }
    }

    /** [folderId] null = remove from folder. */
    fun move(docs: List<Document>, folderId: String?) {
        viewModelScope.launch { docs.forEach { folders.moveDocument(it.id, folderId) } }
    }

    /** Adds to each document's existing tags (same clean-up and cap as single-document tag editing). */
    fun addTags(docs: List<Document>, rawTags: String) {
        viewModelScope.launch {
            docs.forEach { folders.setTags(it.id, parseTags((it.tags + rawTags).joinToString(","))) }
        }
    }
}

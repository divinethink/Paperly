package com.paperly.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.folder.FolderRepository
import com.paperly.app.domain.folder.parseTags
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Folder/tag write actions, kept apart from LibraryViewModel (list + import state). */
@HiltViewModel
class OrganizeViewModel @Inject constructor(
    private val folders: FolderRepository,
) : ViewModel() {

    fun createFolder(name: String) {
        viewModelScope.launch { folders.createFolder(name) }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch { folders.deleteFolder(id) }
    }

    fun moveDocument(doc: Document, folderId: String?) {
        viewModelScope.launch { folders.moveDocument(doc.id, folderId) }
    }

    fun setTags(doc: Document, rawTags: String) {
        viewModelScope.launch { folders.setTags(doc.id, parseTags(rawTags)) }
    }
}

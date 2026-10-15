package com.paperly.app.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.core.model.DocumentType
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.scanner.ScanEditRepository
import com.paperly.app.domain.scanner.ScanEditorSettings
import com.paperly.app.domain.scanner.ScanPageInfo
import com.paperly.app.domain.scanner.ScanPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** P4-B: per-page title/note, only for "scanned-pdf" documents (the menu item is hidden otherwise). */
@HiltViewModel
class ScanPageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    documents: DocumentRepository,
    private val pages: ScanPageRepository,
    private val editor: ScanEditRepository,
    private val editorSettings: ScanEditorSettings,
) : ViewModel() {
    val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _isScan = MutableStateFlow(false)
    val isScan: StateFlow<Boolean> = _isScan

    private val _canEditPages = MutableStateFlow(false)

    /** S2: the page editor is offered only for scans whose pages were kept, with the feature flag on. */
    val canEditPages: StateFlow<Boolean> = _canEditPages

    init {
        viewModelScope.launch {
            _isScan.value = documents.getDocument(documentId)?.type == DocumentType.SCANNED_PDF
            _canEditPages.value = _isScan.value && editorSettings.enabled.first() && editor.load(documentId) != null
        }
    }

    fun observe(page: Int): Flow<ScanPageInfo> = pages.observe(documentId, page)

    fun save(page: Int, info: ScanPageInfo) {
        viewModelScope.launch { pages.save(documentId, page, info) }
    }
}

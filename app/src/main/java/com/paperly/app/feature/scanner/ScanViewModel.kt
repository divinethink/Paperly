package com.paperly.app.feature.scanner

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.core.model.DocumentType
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import com.paperly.app.domain.folder.Folder
import com.paperly.app.domain.folder.FolderRepository
import com.paperly.app.domain.folder.visibleIn
import com.paperly.app.domain.scanner.ScanEditRepository
import com.paperly.app.domain.scanner.ScanEditorSettings
import com.paperly.app.domain.scanner.ScanOcrRepository
import com.paperly.app.domain.scanner.scanTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanError { Unavailable, Save }

/** [naming] = a scan result is waiting for its name/folder (dialog open); nothing is saved until the user answers. */
data class ScanUiState(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: ScanError? = null,
    val naming: Boolean = false,
    val defaultTitle: String = "",
    /** Set with [saved] after a gallery import: the new document opens in the page editor. */
    val editDocumentId: String? = null,
)

private const val TITLE_PREFIX = "Scan "
private const val TITLE_PATTERN = "yyyy-MM-dd HH-mm"
private val PENDING_KEY = stringPreferencesKey("pending_scan_uri")

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val folderRepository: FolderRepository,
    private val store: DataStore<Preferences>,
    private val editRepository: ScanEditRepository,
    private val ocr: ScanOcrRepository,
    settings: ScanEditorSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    val folders: StateFlow<List<Folder>> = folderRepository.observeFolders()
        .map { it.visibleIn(DocumentType.SCANNED_PDF) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** S2 flag: OFF = the previous behaviour (ML Kit PDF only, no gallery button, no editor). */
    val editorEnabled: StateFlow<Boolean> =
        settings.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var pendingUri: String? = null
    private var pendingPages: List<String> = emptyList()
    private var pendingGallery = false

    init {
        // Unfinished-session recovery: a scan whose save never completed (process death, even with the name dialog
        // open) is re-imported once with the default name and no folder.
        viewModelScope.launch {
            val pending = store.data.first()[PENDING_KEY] ?: return@launch
            val alive = withContext(Dispatchers.IO) {
                val uri = Uri.parse(pending)
                uri.scheme != "file" || uri.path?.let { File(it).exists() } == true
            }
            if (alive) save(Saving(pending, defaultTitle(), null)) else store.edit { it.remove(PENDING_KEY) }
        }
    }

    fun onUnavailable() = _state.update { it.copy(error = ScanError.Unavailable) }

    fun consumeSaved() = _state.update { it.copy(saved = false, editDocumentId = null) }

    /**
     * [pdfUri] = ML Kit's result PDF, [pageUris] = its page JPEGs (kept for the page editor; may be empty).
     * Double delivery is ignored while a save runs or the dialog is open.
     */
    fun onScanned(pdfUri: String, pageUris: List<String> = emptyList()) {
        val current = _state.value
        if (current.saving || current.naming) return
        beginNaming(pdfUri, pageUris, gallery = false)
    }

    /** Gallery pictures: build the unedited PDF first, then the normal name/folder step; the editor opens after. */
    fun onGalleryPicked(uris: List<String>) {
        val current = _state.value
        if (current.saving || current.naming || uris.isEmpty()) return
        _state.value = ScanUiState(saving = true)
        viewModelScope.launch {
            val staged = editRepository.stageGallery(uris)
            if (staged == null) {
                _state.value = ScanUiState(error = ScanError.Save)
            } else {
                beginNaming(staged.pdfUri, staged.pageUris, gallery = true)
            }
        }
    }

    private fun beginNaming(pdfUri: String, pageUris: List<String>, gallery: Boolean) {
        pendingUri = pdfUri
        pendingPages = pageUris
        pendingGallery = gallery
        _state.value = ScanUiState(naming = true, defaultTitle = defaultTitle())
        // pointer survives a crash while the dialog is open
        viewModelScope.launch { store.edit { it[PENDING_KEY] = pdfUri } }
    }

    /** Dialog answer: [title] blank = default name; [folderId] null = no folder. */
    fun confirmName(title: String, folderId: String?) {
        val uri = pendingUri ?: return
        pendingUri = null
        save(Saving(uri, scanTitle(title, _state.value.defaultTitle), folderId, pendingPages, pendingGallery))
    }

    private fun defaultTitle() = TITLE_PREFIX + SimpleDateFormat(TITLE_PATTERN, Locale.US).format(Date())

    private class Saving(
        val pdfUri: String,
        val title: String,
        val folderId: String?,
        val pageUris: List<String> = emptyList(),
        val openEditor: Boolean = false,
    )

    private fun save(job: Saving) {
        _state.value = ScanUiState(saving = true)
        viewModelScope.launch {
            store.edit { it[PENDING_KEY] = job.pdfUri } // pointer survives a crash until the save is done
            val result = repository.importScan(job.pdfUri, job.title)
            if (result is ImportResult.Success) {
                if (job.folderId != null) folderRepository.moveDocument(result.documentId, job.folderId)
                if (job.pageUris.isNotEmpty()) editRepository.storeSources(result.documentId, job.pageUris)
                ocr.schedule(result.documentId) // background text recognition when the Settings switch is ON
            }
            store.edit { it.remove(PENDING_KEY) } // one attempt per pointer: no endless retry loop
            // Duplicate = an identical scan is already in the Library
            val ok = result is ImportResult.Success || result is ImportResult.Duplicate
            val editId = (result as? ImportResult.Success)?.documentId?.takeIf { job.openEditor }
            _state.value = if (ok) {
                ScanUiState(saved = true, editDocumentId = editId)
            } else {
                ScanUiState(error = ScanError.Save)
            }
        }
    }
}

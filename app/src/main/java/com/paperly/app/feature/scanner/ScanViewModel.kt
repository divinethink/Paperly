package com.paperly.app.feature.scanner

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import com.paperly.app.domain.folder.Folder
import com.paperly.app.domain.folder.FolderRepository
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
)

private const val TITLE_PREFIX = "Scan "
private const val TITLE_PATTERN = "yyyy-MM-dd HH-mm"
private val PENDING_KEY = stringPreferencesKey("pending_scan_uri")

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val folderRepository: FolderRepository,
    private val store: DataStore<Preferences>,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    val folders: StateFlow<List<Folder>> =
        folderRepository.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var pendingUri: String? = null

    init {
        // Unfinished-session recovery: a scan whose save never completed (process death, even with the name dialog
        // open) is re-imported once with the default name and no folder.
        viewModelScope.launch {
            val pending = store.data.first()[PENDING_KEY] ?: return@launch
            val alive = withContext(Dispatchers.IO) {
                val uri = Uri.parse(pending)
                uri.scheme != "file" || uri.path?.let { File(it).exists() } == true
            }
            if (alive) save(pending, defaultTitle(), null) else store.edit { it.remove(PENDING_KEY) }
        }
    }

    fun onUnavailable() = _state.update { it.copy(error = ScanError.Unavailable) }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    /** [pdfUri] = ML Kit's result PDF. Double delivery is ignored while a save runs or the dialog is open. */
    fun onScanned(pdfUri: String) {
        val current = _state.value
        if (current.saving || current.naming) return
        pendingUri = pdfUri
        _state.value = ScanUiState(naming = true, defaultTitle = defaultTitle())
        viewModelScope.launch { store.edit { it[PENDING_KEY] = pdfUri } } // pointer survives a crash while the dialog is open
    }

    /** Dialog answer: [title] blank = default name; [folderId] null = no folder. */
    fun confirmName(title: String, folderId: String?) {
        val uri = pendingUri ?: return
        pendingUri = null
        save(uri, scanTitle(title, _state.value.defaultTitle), folderId)
    }

    private fun defaultTitle() = TITLE_PREFIX + SimpleDateFormat(TITLE_PATTERN, Locale.US).format(Date())

    private fun save(pdfUri: String, title: String, folderId: String?) {
        _state.value = ScanUiState(saving = true)
        viewModelScope.launch {
            store.edit { it[PENDING_KEY] = pdfUri } // pointer survives a crash until the save is done
            val result = repository.importScan(pdfUri, title)
            if (result is ImportResult.Success && folderId != null) folderRepository.moveDocument(result.documentId, folderId)
            store.edit { it.remove(PENDING_KEY) } // one attempt per pointer: no endless retry loop
            val ok = result is ImportResult.Success || result is ImportResult.Duplicate // identical scan already in Library
            _state.value = if (ok) ScanUiState(saved = true) else ScanUiState(error = ScanError.Save)
        }
    }
}

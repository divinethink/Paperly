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
import com.paperly.app.domain.folder.FolderRepository
import com.paperly.app.domain.scanner.ScanEditRepository
import com.paperly.app.domain.scanner.ScanEditorSettings
import com.paperly.app.domain.scanner.ScanOcrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** S2 flag: OFF = the previous behaviour (ML Kit PDF only, no gallery button, no editor). */
    val editorEnabled: StateFlow<Boolean> =
        settings.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** The save/import that is running now (null or finished = nothing is running). */
    private var work: Job? = null

    init {
        // Unfinished-session recovery: a scan whose save never completed (process death) is re-imported once with
        // the default name and no folder.
        viewModelScope.launch {
            val pending = store.data.first()[PENDING_KEY] ?: return@launch
            val alive = withContext(Dispatchers.IO) {
                val uri = Uri.parse(pending)
                uri.scheme != "file" || uri.path?.let { File(it).exists() } == true
            }
            if (alive) save(Saving(pending)) else store.edit { it.remove(PENDING_KEY) }
        }
    }

    /** Scan screen opened: clears anything an earlier visit left behind (e.g. a stuck "saving"). */
    fun onScreenOpened() = _state.update { it.onScreenEnter(workRunning = work?.isActive == true) }

    fun onUnavailable() = _state.update { it.copy(error = ScanError.Unavailable) }

    /** A new scan attempt starts: the old error message goes away. */
    fun dismissError() = _state.update { it.copy(error = null) }

    fun consumeSaved() = _state.update { it.copy(saved = false, duplicate = false, editDocumentId = null) }

    /**
     * [pdfUri] = ML Kit's result PDF, [pageUris] = its page JPEGs (kept for the page editor; may be empty).
     * No name question: the scan is saved at once with a date-time name (rename later from the card menu).
     * Double delivery is ignored while a save runs.
     */
    fun onScanned(pdfUri: String, pageUris: List<String> = emptyList()) {
        if (_state.value.saving) return
        save(Saving(pdfUri, pageUris))
    }

    /** Gallery pictures: build the unedited PDF first, then the normal save; the editor opens after. */
    fun onGalleryPicked(uris: List<String>) {
        if (_state.value.saving || uris.isEmpty()) return
        startWork {
            val staged = editRepository.stageGallery(uris)
            if (staged == null) {
                ScanUiState(error = ScanError.Save)
            } else {
                persist(Saving(staged.pdfUri, staged.pageUris, openEditor = true))
            }
        }
    }

    private fun defaultTitle() = TITLE_PREFIX + SimpleDateFormat(TITLE_PATTERN, Locale.US).format(Date())

    private class Saving(
        val pdfUri: String,
        val pageUris: List<String> = emptyList(),
        val openEditor: Boolean = false,
        val folderId: String? = null,
    )

    private fun save(job: Saving) = startWork { persist(job) }

    /**
     * Runs [block] as the one running job. Whatever happens (an exception included) the state ends as saved or as an
     * error, never stuck on "saving", so the next scan can always start.
     */
    private fun startWork(block: suspend () -> ScanUiState) {
        _state.value = ScanUiState(saving = true)
        work = viewModelScope.launch {
            val result = try {
                block()
            } catch (ignored: Exception) {
                currentCoroutineContext().ensureActive() // cancellation is not a save error
                ScanUiState(error = ScanError.Save)
            }
            _state.value = result
        }
    }

    private suspend fun persist(job: Saving): ScanUiState {
        store.edit { it[PENDING_KEY] = job.pdfUri } // pointer survives a crash until the save is done
        try {
            val result = repository.importScan(job.pdfUri, defaultTitle())
            if (result is ImportResult.Success) {
                if (job.folderId != null) folderRepository.moveDocument(result.documentId, job.folderId)
                if (job.pageUris.isNotEmpty()) editRepository.storeSources(result.documentId, job.pageUris)
                ocr.schedule(result.documentId) // background text recognition when the Settings switch is ON
            }
            return scanOutcome(result, job.openEditor)
        } finally {
            // one attempt per pointer: no endless retry loop
            withContext(NonCancellable) { store.edit { it.remove(PENDING_KEY) } }
        }
    }
}

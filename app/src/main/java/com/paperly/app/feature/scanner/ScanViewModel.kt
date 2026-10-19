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
import com.paperly.app.domain.scanner.ScanEditRepository
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TITLE_PREFIX = "Scan "
private const val TITLE_PATTERN = "yyyy-MM-dd HH-mm"
private val PENDING_KEY = stringPreferencesKey("pending_scan_uri")

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val folderTarget: ScanFolderTarget,
    private val store: DataStore<Preferences>,
    private val editRepository: ScanEditRepository,
    private val ocr: ScanOcrRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /** The save/import that is running now (null or finished = nothing is running). */
    private var work: Job? = null

    /** Decides whether the Scan screen starts the scanner at once when it opens (no extra tap). */
    val launchGate = ScanLaunchGate()

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

    fun onUnavailable() {
        launchGate.arm()
        _state.update { it.copy(error = ScanError.Unavailable) }
    }

    /** A new scan attempt starts: the old error message goes away. */
    fun dismissError() = _state.update { it.copy(error = null) }

    fun consumeSaved() {
        launchGate.arm()
        _state.update { it.copy(saved = false, duplicate = false, editDocumentId = null, openDocumentId = null) }
    }

    /** Camera or gallery pictures: build the unedited PDF first, then the normal save; the crop editor opens after. */
    fun onGalleryPicked(uris: List<String>) {
        if (_state.value.saving || uris.isEmpty()) return
        startWork {
            val staged = editRepository.stageGallery(uris)
            if (staged == null) {
                ScanUiState(error = ScanError.Save)
            } else {
                persist(Saving(staged.pdfUri, staged.pageUris, openEditor = true, folderId = folderTarget.folderId))
            }
        }
    }

    private fun defaultTitle() = TITLE_PREFIX + SimpleDateFormat(TITLE_PATTERN, Locale.US).format(Date())

    private class Saving(
        val pdfUri: String,
        val pageUris: List<String> = emptyList(),
        val openEditor: Boolean = false,
        val openDocument: Boolean = false,
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
            if (!result.saved) launchGate.arm() // an error: leaving and coming back starts the scanner again
            _state.value = result
        }
    }

    private suspend fun persist(job: Saving): ScanUiState {
        store.edit { it[PENDING_KEY] = job.pdfUri } // pointer survives a crash until the save is done
        try {
            val result = repository.importScan(job.pdfUri, defaultTitle())
            if (result is ImportResult.Success) {
                folderTarget.moveInto(result.documentId, job.folderId)
                if (job.pageUris.isNotEmpty()) editRepository.storeSources(result.documentId, job.pageUris)
                ocr.schedule(result.documentId) // background text recognition when the Settings switch is ON
            }
            return scanOutcome(result, job.openEditor, job.openDocument)
        } finally {
            // one attempt per pointer: no endless retry loop
            withContext(NonCancellable) { store.edit { it.remove(PENDING_KEY) } }
        }
    }
}

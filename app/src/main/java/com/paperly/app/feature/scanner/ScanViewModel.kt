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
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanError { Unavailable, Save }

data class ScanUiState(val saving: Boolean = false, val saved: Boolean = false, val error: ScanError? = null)

private const val TITLE_PREFIX = "Scan "
private const val TITLE_PATTERN = "yyyy-MM-dd HH-mm"
private val PENDING_KEY = stringPreferencesKey("pending_scan_uri")

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val store: DataStore<Preferences>,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    init {
        // Unfinished-session recovery: a scan whose save never completed (process death) is re-imported once.
        viewModelScope.launch {
            val pending = store.data.first()[PENDING_KEY] ?: return@launch
            val alive = withContext(Dispatchers.IO) {
                val uri = Uri.parse(pending)
                uri.scheme != "file" || uri.path?.let { File(it).exists() } == true
            }
            if (alive) onScanned(pending) else store.edit { it.remove(PENDING_KEY) }
        }
    }

    fun onUnavailable() = _state.update { it.copy(error = ScanError.Unavailable) }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    /** [pdfUri] = ML Kit's result PDF. Double-delivery is ignored while a save runs (idempotent). */
    fun onScanned(pdfUri: String) {
        if (_state.value.saving) return
        _state.value = ScanUiState(saving = true)
        viewModelScope.launch {
            store.edit { it[PENDING_KEY] = pdfUri } // pointer survives a crash until the save is done
            val title = TITLE_PREFIX + SimpleDateFormat(TITLE_PATTERN, Locale.US).format(Date())
            val ok = when (repository.importScan(pdfUri, title)) {
                is ImportResult.Success, is ImportResult.Duplicate -> true // identical scan already in Library
                else -> false
            }
            store.edit { it.remove(PENDING_KEY) } // one attempt per pointer: no endless retry loop
            _state.value = if (ok) ScanUiState(saved = true) else ScanUiState(error = ScanError.Save)
        }
    }
}

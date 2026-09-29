package com.paperly.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.core.intent.IncomingImportRequests
import com.paperly.app.core.perf.PerfTrace
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import com.paperly.app.domain.folder.Folder
import com.paperly.app.domain.folder.FolderRepository
import com.paperly.app.domain.trash.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImportError { Unsupported, Failed }

enum class LibraryFilter { All, Favorites, Recent }

private const val RECENT_LIMIT = 20

/**
 * Pure list filtering (unit-tested): folder -> search (title/tag, case-insensitive) -> kind.
 * Recent = opened docs, newest first, capped (a preview list, not an aggregate).
 */
internal fun List<Document>.filterFor(
    kind: LibraryFilter,
    query: String = "",
    folderId: String? = null,
): List<Document> {
    val needle = query.trim()
    val base = filter { (folderId == null || it.folderId == folderId) && it.matches(needle) }
    return when (kind) {
        LibraryFilter.All -> base
        LibraryFilter.Favorites -> base.filter { it.isFavorite }
        LibraryFilter.Recent -> base.filter { it.lastOpenedAt != null }
            .sortedByDescending { it.lastOpenedAt }
            .take(RECENT_LIMIT)
    }
}

private fun Document.matches(needle: String): Boolean =
    needle.isEmpty() || title.contains(needle, ignoreCase = true) || tags.any { it.contains(needle, ignoreCase = true) }

private data class Selection(
    val kind: LibraryFilter = LibraryFilter.All,
    val query: String = "",
    val folderId: String? = null,
)

private class Transient(val importing: Boolean, val error: ImportError?, val duplicate: DuplicatePrompt?)

/** Import paused on a checksum match, waiting for the owner's Skip / Keep Both / Open Existing choice. */
data class DuplicatePrompt(val existingId: String, val existingTitle: String, val sourceUri: String)

data class LibraryUiState(
    val documents: List<Document> = emptyList(),
    val totalCount: Int = 0,
    val filter: LibraryFilter = LibraryFilter.All,
    val isImporting: Boolean = false,
    val error: ImportError? = null,
    val duplicate: DuplicatePrompt? = null,
    val query: String = "",
    val folders: List<Folder> = emptyList(),
    val selectedFolderId: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val trashRepository: TrashRepository,
    folderRepository: FolderRepository,
    private val incomingImports: IncomingImportRequests,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val error = MutableStateFlow<ImportError?>(null)
    private val duplicate = MutableStateFlow<DuplicatePrompt?>(null)
    private val selection = MutableStateFlow(Selection())
    private val transient = combine(importing, error, duplicate) { i, e, d -> Transient(i, e, d) }

    val uiState: StateFlow<LibraryUiState> =
        combine(
            repository.observeDocuments(),
            folderRepository.observeFolders(),
            selection,
            transient,
        ) { docs, folders, sel, t ->
            // A deleted folder silently falls back to "all folders".
            val folderId = sel.folderId?.takeIf { id -> folders.any { it.id == id } }
            LibraryUiState(
                documents = docs.filterFor(sel.kind, sel.query, folderId),
                totalCount = docs.size,
                filter = sel.kind,
                isImporting = t.importing,
                error = t.error,
                duplicate = t.duplicate,
                query = sel.query,
                folders = folders,
                selectedFolderId = folderId,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        // Open-With / Share: wait for any running import (the double-tap guard would drop it), then import.
        viewModelScope.launch {
            incomingImports.pending.filterNotNull().collect { uri ->
                importing.first { !it }
                importDocument(uri)
                incomingImports.consume(uri)
            }
        }
    }

    fun importDocument(uri: String, allowDuplicate: Boolean = false) {
        if (importing.value) return // double-tap guard
        importing.value = true
        error.value = null
        viewModelScope.launch {
            try {
                val result = PerfTrace.span("library.import") { repository.importDocument(uri, allowDuplicate) }
                error.value = when (result) {
                    is ImportResult.Success -> null
                    is ImportResult.Duplicate -> {
                        duplicate.value = DuplicatePrompt(result.existingId, result.existingTitle, uri)
                        null
                    }
                    ImportResult.UnsupportedType -> ImportError.Unsupported
                    ImportResult.Failed -> ImportError.Failed
                }
            } finally {
                importing.value = false
            }
        }
    }

    /** Skip: drop the pending import; nothing was copied. */
    fun dismissDuplicate() {
        duplicate.value = null
    }

    /** Keep Both: explicit re-import that bypasses the duplicate check. */
    fun keepBoth() {
        val pending = duplicate.value ?: return
        duplicate.value = null
        importDocument(pending.sourceUri, allowDuplicate = true)
    }

    fun setFilter(value: LibraryFilter) {
        selection.update { it.copy(kind = value) }
    }

    fun setQuery(value: String) {
        selection.update { it.copy(query = value) }
    }

    fun selectFolder(id: String?) {
        selection.update { it.copy(folderId = id) }
    }

    fun toggleFavorite(doc: Document) {
        viewModelScope.launch { repository.setFavorite(doc.id, !doc.isFavorite) }
    }

    fun moveToTrash(doc: Document) {
        viewModelScope.launch { trashRepository.trash(doc.id) }
    }

    fun rename(doc: Document, newTitle: String) {
        viewModelScope.launch { repository.renameDocument(doc.id, newTitle) }
    }
}

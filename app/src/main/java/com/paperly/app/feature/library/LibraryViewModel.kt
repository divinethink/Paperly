package com.paperly.app.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.core.intent.IncomingImportRequests
import com.paperly.app.core.perf.PerfTrace
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import com.paperly.app.domain.document.LibrarySort
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.domain.document.LibraryView
import com.paperly.app.domain.document.LibraryViewStore
import com.paperly.app.domain.folder.Folder
import com.paperly.app.domain.folder.FolderRepository
import com.paperly.app.domain.folder.pathTo
import com.paperly.app.domain.folder.visibleIn
import com.paperly.app.domain.reader.ReaderStateRepository
import com.paperly.app.domain.scanner.ScanOcrRepository
import com.paperly.app.domain.trash.TrashRepository
import com.paperly.app.feature.scanner.ScanFolderTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImportError { Unsupported, Failed }

enum class LibraryFilter { All, Favorites, Recent, Finished }

/** Loading until the first database emission, so an empty Library is never shown while still loading. */
enum class LibraryLoad { Loading, Ready, Failed }

private sealed interface DocsResult {
    data object Loading : DocsResult

    class Ready(val docs: List<Document>) : DocsResult

    data object Failed : DocsResult
}

private const val RECENT_LIMIT = 20

/** Navigation argument carrying the [LibraryType] key of a Library tab (Scanned / PDF / EPUB). */
const val LIBRARY_TYPE_ARG = "type"

/**
 * Pure list filtering (unit-tested): folder -> search (title/tag, case-insensitive) -> kind.
 * Recent = opened docs, newest first, capped (a preview list, not an aggregate).
 */
internal fun List<Document>.filterFor(
    kind: LibraryFilter,
    query: String = "",
    folderId: String? = null,
    view: LibraryView = LibraryView(),
    scanTextIds: Set<String> = emptySet(),
): List<Document> {
    val needle = query.trim()
    val base = filter { it.inFolder(folderId) && it.matches(needle, scanTextIds) && it.isType(view.type) }
    return when (kind) {
        LibraryFilter.All, LibraryFilter.Finished -> base.sortedFor(view) // Finished is narrowed by the caller
        LibraryFilter.Favorites -> base.filter { it.isFavorite }.sortedFor(view)
        LibraryFilter.Recent -> base.filter { it.lastOpenedAt != null }
            .sortedByDescending { it.lastOpenedAt }
            .take(RECENT_LIMIT)
    }
}

private fun Document.inFolder(folderId: String?): Boolean = folderId == null || this.folderId == folderId

private fun Document.isType(type: LibraryType?): Boolean = type == null || type.docType == this.type

/** Recent keeps its own newest-opened order, so only All/Favorites are sorted. */
internal fun List<Document>.sortedFor(view: LibraryView): List<Document> {
    val order: Comparator<Document> = when (view.sort) {
        LibrarySort.DEFAULT -> return this
        LibrarySort.NAME -> compareBy<Document, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        LibrarySort.ADDED -> compareBy<Document> { it.createdAt }
        LibrarySort.OPENED -> compareBy<Document> { it.lastOpenedAt ?: Long.MIN_VALUE }
        LibrarySort.SIZE -> compareBy<Document> { it.sizeBytes }
    }
    return sortedWith(if (view.ascending) order else order.reversed())
}

/** Title, tag, or (S3) the recognised text of a scan: [scanTextIds] = scans whose OCR text contains the query. */
private fun Document.matches(needle: String, scanTextIds: Set<String>): Boolean =
    needle.isEmpty() || title.contains(needle, ignoreCase = true) ||
        tags.any { it.contains(needle, ignoreCase = true) } || id in scanTextIds

private data class Selection(
    val kind: LibraryFilter = LibraryFilter.All,
    val query: String = "",
    val folderId: String? = null,
)

/** [progress] is 0..1, null when never read. */
data class ContinueInfo(val document: Document, val progress: Float?)

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
    /** Scanned tab: the folder rows shown above the documents, i.e. the subfolders of the open folder. */
    val scanFolders: List<ScanFolderItem> = emptyList(),
    /** Top-level folder first, the open folder last; empty at the root. */
    val folderPath: List<Folder> = emptyList(),
    val view: LibraryView = LibraryView(),
    val load: LibraryLoad = LibraryLoad.Loading,
    /** Documents read to the end: they get a check mark and the Finished filter. */
    val finishedIds: Set<String> = emptySet(),
)

@Suppress("LongParameterList", "TooManyFunctions") // Hilt DI collaborators; small state/event handlers
@HiltViewModel
class LibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val trashRepository: TrashRepository,
    folderRepository: FolderRepository,
    private val incomingImports: IncomingImportRequests,
    viewStore: LibraryViewStore,
    readerState: ReaderStateRepository,
    scanOcr: ScanOcrRepository,
    private val scanFolder: ScanFolderTarget,
) : ViewModel() {

    /** Each Library tab shows exactly one document type; the persisted type filter no longer applies. */
    val type: LibraryType = LibraryType.fromKey(savedStateHandle.get<String>(LIBRARY_TYPE_ARG)) ?: LibraryType.PDF

    private val importing = MutableStateFlow(false)
    private val shownTab = MutableSharedFlow<LibraryType>(extraBufferCapacity = 1)

    /** Fires after an import so the screen can jump to the tab that now holds the new document. */
    val showTab: SharedFlow<LibraryType> = shownTab.asSharedFlow()
    private val error = MutableStateFlow<ImportError?>(null)
    private val duplicate = MutableStateFlow<DuplicatePrompt?>(null)
    private val selection = MutableStateFlow(Selection())
    private val transient = combine(importing, error, duplicate) { i, e, d -> Transient(i, e, d) }
    private val reload = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val documentsResult: Flow<DocsResult> = reload.flatMapLatest {
        repository.observeDocuments()
            .map<List<Document>, DocsResult> { DocsResult.Ready(it) }
            .onStart { emit(DocsResult.Loading) }
            .catch { emit(DocsResult.Failed) }
    }

    /** S3: scans whose recognised text contains the typed query (empty set when there is no query). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scanTextHits: Flow<Set<String>> = selection.map { it.query.trim() }.distinctUntilChanged()
        .flatMapLatest { q -> if (q.isEmpty()) flowOf(emptySet<String>()) else scanOcr.observeMatchingIds(q) }

    private val docsAndHits = combine(documentsResult, scanTextHits, repository.finishedIds) { r, hits, done ->
        Triple(r, hits, done)
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(
            docsAndHits,
            folderRepository.observeFolders(),
            selection,
            transient,
            viewStore.view,
        ) { (result, hits, finished), folders, sel, t, view ->
            val docs = (result as? DocsResult.Ready)?.docs.orEmpty()
            val tabView = view.copy(type = type)
            val tabFolders = folders.visibleIn(type.docType)
            // A deleted folder silently falls back to "all folders".
            val folderId = sel.folderId?.takeIf { id -> tabFolders.any { it.id == id } }
            val scanBrowse = sel.query.isBlank() && sel.kind == LibraryFilter.All
            val found = docs.filterFor(sel.kind, sel.query, folderId, tabView, hits)
            val shown = if (sel.kind == LibraryFilter.Finished) found.filter { it.id in finished } else found
            LibraryUiState(
                documents = if (scanBrowse && folderId == null) shown.withoutFolder() else shown,
                scanFolders = if (scanBrowse) {
                    scanFolderItems(tabFolders, docs, type.docType, folderId)
                } else {
                    emptyList()
                },
                folderPath = tabFolders.pathTo(folderId),
                totalCount = docs.count { it.type == type.docType },
                filter = sel.kind,
                isImporting = t.importing,
                error = t.error,
                duplicate = t.duplicate,
                query = sel.query,
                folders = tabFolders,
                selectedFolderId = folderId,
                view = tabView,
                finishedIds = finished,
                load = when (result) {
                    DocsResult.Loading -> LibraryLoad.Loading
                    is DocsResult.Ready -> LibraryLoad.Ready
                    DocsResult.Failed -> LibraryLoad.Failed
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** Most recently opened document + its saved progress, for the "Continue reading" card. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val continueReading: StateFlow<ContinueInfo?> = repository.observeDocuments()
        .map { docs ->
            docs.filter { it.type == type.docType && it.lastOpenedAt != null }.maxByOrNull { it.lastOpenedAt ?: 0L }
        }
        .distinctUntilChanged()
        .flatMapLatest { doc ->
            if (doc == null) flowOf(null) else readerState.observeProgress(doc.id).map { ContinueInfo(doc, it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Open-With / Share: wait for any running import (the double-tap guard would drop it), then import.
     * Called only while this tab is on screen, so with several tabs alive one request is imported once.
     */
    suspend fun processIncomingImports() {
        incomingImports.pending.filterNotNull().collect { uri ->
            importing.first { !it }
            importDocument(uri)
            incomingImports.consume(uri)
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
                    is ImportResult.Success -> {
                        // Room can miss the change when the list was re-subscribed right as the file picker closed
                        // (the new row never appeared until restart); a fresh query is always correct.
                        reload.update { it + 1 }
                        announceTab(result.documentId)
                        null
                    }
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

    private suspend fun announceTab(documentId: String) {
        val docType = repository.getDocument(documentId)?.type
        LibraryType.entries.firstOrNull { it.docType == docType }?.let { shownTab.tryEmit(it) }
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

    fun retry() {
        reload.update { it + 1 }
    }

    fun setFilter(value: LibraryFilter) {
        selection.update { it.copy(kind = value) }
    }

    fun setQuery(value: String) {
        selection.update { it.copy(query = value) }
    }

    fun selectFolder(id: String?) {
        selection.update { it.copy(folderId = id) }
        if (type == LibraryType.SCAN) scanFolder.select(id) // new scans go into the open folder
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

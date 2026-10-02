package com.paperly.app.feature.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.storage.StorageUsage
import com.paperly.app.domain.storage.StorageUsageRepository
import com.paperly.app.domain.trash.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal const val LARGEST_COUNT = 5

data class TypeSize(val type: String, val count: Int, val bytes: Long)

/** Documents with identical content (2+). */
data class DuplicateGroup(val documents: List<Document>)

data class StorageUiState(
    val usage: StorageUsage? = null,
    val byType: List<TypeSize> = emptyList(),
    val largest: List<Document> = emptyList(),
    val duplicates: List<DuplicateGroup> = emptyList(),
)

/**
 * Pure builder (unit-tested). Type totals cover ALL documents; "largest" is a preview list (top [LARGEST_COUNT]).
 * Groups are resolved against the live document list, and any group left with fewer than 2 documents is dropped.
 */
internal fun buildStorageState(
    usage: StorageUsage?,
    docs: List<Document>,
    groups: List<List<String>>,
): StorageUiState {
    val byId = docs.associateBy { it.id }
    return StorageUiState(
        usage = usage,
        byType = docs.groupBy { it.type }
            .map { (type, list) -> TypeSize(type, list.size, list.sumOf { it.sizeBytes }) }
            .sortedByDescending { it.bytes },
        largest = docs.sortedByDescending { it.sizeBytes }.take(LARGEST_COUNT),
        duplicates = groups
            .map { ids -> ids.mapNotNull { byId[it] } }
            .filter { it.size >= 2 }
            .map { DuplicateGroup(it) },
    )
}

@HiltViewModel
class StorageViewModel @Inject constructor(
    usageRepository: StorageUsageRepository,
    documents: DocumentRepository,
    private val trash: TrashRepository,
) : ViewModel() {

    val state: StateFlow<StorageUiState> = combine(
        usageRepository.observeUsage(),
        documents.observeDocuments(),
        usageRepository.observeDuplicateGroups(),
    ) { usage, docs, groups -> buildStorageState(usage, docs, groups) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUiState())

    /** Keeps [keepId] and moves the other copies to Trash (restorable); nothing is deleted for good. */
    fun keepOnly(group: DuplicateGroup, keepId: String) {
        viewModelScope.launch { group.documents.filter { it.id != keepId }.forEach { trash.trash(it.id) } }
    }
}

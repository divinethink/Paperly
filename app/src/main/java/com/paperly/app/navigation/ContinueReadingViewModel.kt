package com.paperly.app.navigation

import androidx.lifecycle.ViewModel
import com.paperly.app.core.intent.ContinueReadingRequests
import com.paperly.app.domain.document.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

@HiltViewModel
class ContinueReadingViewModel @Inject constructor(
    private val requests: ContinueReadingRequests,
    private val repository: DocumentRepository,
) : ViewModel() {
    val pending: StateFlow<Boolean> = requests.pending

    /** Clears the request and returns the most recently opened (non-trashed) document, or null if none yet. */
    suspend fun consumeTarget(): String? {
        requests.consume()
        return repository.observeDocuments().first()
            .filter { it.lastOpenedAt != null }
            .maxByOrNull { it.lastOpenedAt ?: 0L }
            ?.id
    }
}

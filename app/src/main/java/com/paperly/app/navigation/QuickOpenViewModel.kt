package com.paperly.app.navigation

import androidx.lifecycle.ViewModel
import com.paperly.app.core.file.QuickStage
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.core.intent.IncomingImportRequests
import com.paperly.app.core.intent.QuickOpenRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class QuickOpenViewModel @Inject constructor(
    private val requests: QuickOpenRequests,
    private val store: QuickViewStore,
    private val imports: IncomingImportRequests,
) : ViewModel() {
    val pending: StateFlow<String?> = requests.pending

    /** Reader id to open for [uri] (a Library copy or a temporary quick copy); null = fell back to a normal import. */
    suspend fun stage(uri: String): String? {
        store.prune()
        return when (val result = store.stage(uri)) {
            is QuickStage.Library -> result.documentId
            is QuickStage.Temp -> result.token
            QuickStage.NotPdf, QuickStage.Failed -> {
                imports.post(uri) // the Library import flow shows its usual error/duplicate handling
                null
            }
        }
    }

    fun consume(uri: String) = requests.consume(uri)
}

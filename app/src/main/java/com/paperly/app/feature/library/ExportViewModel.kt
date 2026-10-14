package com.paperly.app.feature.library

import androidx.lifecycle.ViewModel
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.QuickViewStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val store: DocumentFileStore,
    private val quickViews: QuickViewStore,
) : ViewModel() {
    /** The stored file of a Library document, or the cache copy of a quick-view PDF. */
    fun file(documentId: String): File? =
        if (QuickViewStore.isToken(documentId)) quickViews.resolve(documentId) else store.resolve(documentId)
}

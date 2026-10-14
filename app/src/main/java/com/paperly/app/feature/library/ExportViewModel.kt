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
    private val quick: QuickViewStore,
) : ViewModel() {
    /** Library file, or the temporary copy of a quick-view PDF. */
    fun file(documentId: String): File? =
        if (QuickViewStore.isToken(documentId)) quick.resolve(documentId) else store.resolve(documentId)
}

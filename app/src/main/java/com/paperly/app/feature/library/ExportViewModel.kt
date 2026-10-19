package com.paperly.app.feature.library

import androidx.lifecycle.ViewModel
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.data.export.PdfJpgRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val store: DocumentFileStore,
    private val quick: QuickViewStore,
    private val jpg: PdfJpgRenderer,
) : ViewModel() {
    /** Library file, or the temporary copy of a quick-view PDF. */
    fun file(documentId: String): File? =
        if (QuickViewStore.isToken(documentId)) quick.resolve(documentId) else store.resolve(documentId)

    /** One JPG per page of the document (null = failed), ready to share or save. */
    suspend fun renderJpgs(documentId: String, base: String): List<File>? =
        file(documentId)?.let { jpg.render(it, base) }
}

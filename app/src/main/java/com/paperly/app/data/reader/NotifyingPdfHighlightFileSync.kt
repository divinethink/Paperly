package com.paperly.app.data.reader

import com.paperly.app.domain.reader.FileWriteNotice
import com.paperly.app.domain.reader.PdfHighlightFileSync
import com.paperly.app.domain.reader.PdfWriteResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [PdfHighlightFileSync] the app uses: runs the write and then tells the person how it ended. Only a real
 * outcome is reported (written, or tried and not possible); nothing to write or setting off stays silent.
 */
@Singleton
class NotifyingPdfHighlightFileSync @Inject constructor(
    private val sync: PdfHighlightFileSyncImpl,
    private val notice: FileWriteNotice,
) : PdfHighlightFileSync {
    override fun schedule(documentId: String, removedIds: Set<String>, notify: Boolean) {
        sync.schedule(documentId, removedIds) { result -> if (notify) tell(result) }
    }

    private fun tell(result: PdfWriteResult?) {
        when (result) {
            is PdfWriteResult.Written -> notice.show(true)
            is PdfWriteResult.Failed, is PdfWriteResult.Skipped -> notice.show(false)
            else -> Unit
        }
    }
}

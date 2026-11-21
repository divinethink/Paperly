package com.paperly.app.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

/** "Save a copy" picker that starts in the Downloads folder (not the last folder the system picker used). */
internal class CreateInDownloads(mime: String) : ActivityResultContracts.CreateDocument(mime) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS)

    private companion object {
        val DOWNLOADS: Uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
    }
}

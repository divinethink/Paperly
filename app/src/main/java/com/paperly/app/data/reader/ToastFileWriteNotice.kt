package com.paperly.app.data.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.paperly.app.R
import com.paperly.app.domain.reader.FileWriteNotice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Short on-screen message after a Library PDF was (or could not be) written; safe to call from any thread. */
class ToastFileWriteNotice @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileWriteNotice {
    override fun show(written: Boolean) {
        val message = if (written) R.string.pdf_write_ok else R.string.pdf_write_failed
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }
}

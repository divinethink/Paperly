package com.paperly.app.core.intent

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IncomingImportTest {
    private val content = Uri.parse("content://docs/book.pdf")

    @Test
    fun viewIntentWithContentUriIsAccepted() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(content, "application/pdf")
        assertEquals("content://docs/book.pdf", extractImportUri(intent))
    }

    @Test
    fun sendIntentUsesStreamExtra() {
        val intent = Intent(Intent.ACTION_SEND).setType("application/epub+zip").putExtra(Intent.EXTRA_STREAM, content)
        assertEquals("content://docs/book.pdf", extractImportUri(intent))
    }

    @Test
    fun fileSchemeIsRejected() {
        val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse("file:///data/data/other.app/x.pdf"))
        assertNull(extractImportUri(intent))
    }

    @Test
    fun otherActionsAndNullAreIgnored() {
        assertNull(extractImportUri(Intent(Intent.ACTION_MAIN)))
        assertNull(extractImportUri(Intent(Intent.ACTION_SEND)))
        assertNull(extractImportUri(null))
    }
}

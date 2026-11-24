package com.paperly.app.core.file

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentFileStoreDeleteTest {
    private val context = RuntimeEnvironment.getApplication()
    private val store = AppPrivateDocumentFileStore(context)

    private fun backup(id: String) = File(File(context.filesDir, PDF_BACKUP_DIR), "$id.bak")

    private fun seed(id: String) = runBlocking {
        store.storeFrom("pdf".byteInputStream(), id)
        backup(id).apply { parentFile?.mkdirs() }.writeText("copy")
    }

    @Test
    fun deleteRemovesDocumentAndItsSafetyCopy() {
        seed("d1")
        seed("d2")
        assertTrue(runBlocking { store.delete("d1") })
        assertFalse(backup("d1").exists())
        assertTrue(backup("d2").exists()) // another document's copy is untouched
    }

    @Test
    fun missingDocumentFileStillClearsItsSafetyCopy() {
        seed("d3")
        File(context.filesDir, "$DOCUMENTS_DIR/d3").delete()
        runBlocking { store.delete("d3") }
        assertFalse(backup("d3").exists())
    }
}

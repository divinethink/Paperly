package com.paperly.app.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupArchiveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun source(id: String, content: String): BackupSource {
        val bytes = content.toByteArray()
        val file = tmp.newFile(id).apply { writeBytes(bytes) }
        val doc = BackupDocument(id, "Title $id", "pdf", bytes.size.toLong(), sha(bytes), null, listOf("a"), true, 1L, 2L, null)
        return BackupSource(doc, file)
    }

    private fun archive(vararg s: BackupSource): ByteArray = ByteArrayOutputStream().also {
        BackupArchive.write(it, s.toList(), listOf(BackupFolder("f1", "Work", null, 5L)), BackupHeader(2, "1.0", 9L))
    }.toByteArray()

    @Test
    fun writtenArchiveVerifies() {
        assertTrue(BackupArchive.verify(ByteArrayInputStream(archive(source("d1", "hello"), source("d2", "world")))))
    }

    @Test
    fun corruptedBytesFailVerify() {
        val bytes = archive(source("d1", "hello hello hello"))
        bytes[bytes.size / 4] = (bytes[bytes.size / 4].toInt() xor 0x55).toByte()
        assertFalse(BackupArchive.verify(ByteArrayInputStream(bytes)))
    }

    @Test
    fun truncatedArchiveFailsVerify() {
        val bytes = archive(source("d1", "hello hello hello"))
        assertFalse(BackupArchive.verify(ByteArrayInputStream(bytes.copyOf(bytes.size / 2))))
    }

    @Test
    fun manifestRoundTripAndRestoreReadPath() {
        val file: File = tmp.newFile("b.zip").apply { writeBytes(archive(source("d1", "hello"))) }
        ZipFile(file).use { zip ->
            val m = BackupArchive.readManifest(zip)
            assertNotNull(m)
            assertEquals(1, m!!.documents.size)
            assertEquals("Work", m.folders.single().name)
            assertEquals(listOf("a"), m.documents.single().tags)
            assertEquals("hello", BackupArchive.openDocument(zip, "d1")!!.use { String(it.readBytes()) })
            assertNull(BackupArchive.openDocument(zip, "missing"))
        }
    }

    @Test
    fun decodeDropsUnsafeOrInvalidEntriesAndIgnoresUnknownKeys() {
        val good = "a".repeat(64)
        val json = """{"formatVersion":1,"future":"x","documents":[
            {"documentId":"../evil","title":"t","type":"pdf","sizeBytes":1,"checksum":"$good"},
            {"documentId":"ok1","title":"t","type":"exe","sizeBytes":1,"checksum":"$good"},
            {"documentId":"ok2","title":"t","type":"pdf","sizeBytes":1,"checksum":"nothex","extra":1},
            {"documentId":"ok3","title":"t","type":"epub","sizeBytes":1,"checksum":"$good","unknownField":true}
        ]}"""
        val m = BackupManifestCodec.decode(json)!!
        assertEquals(listOf("ok3"), m.documents.map { it.documentId })
        assertEquals(3, m.invalidEntries)
    }

    @Test
    fun malformedManifestIsNull() {
        assertNull(BackupManifestCodec.decode("not json"))
        assertNull(BackupManifestCodec.decode("""{"documents":[]}"""))
    }
}

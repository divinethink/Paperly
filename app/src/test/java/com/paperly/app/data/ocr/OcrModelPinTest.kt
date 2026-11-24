package com.paperly.app.data.ocr

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OcrModelPinTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val abc = ModelFingerprint(3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")

    private fun file(text: String): File = tmp.newFile().apply { writeText(text) }

    @Test
    fun `file with the pinned size and hash matches`() {
        assertTrue(OcrModelPin.matches(file("abc"), abc))
    }

    @Test
    fun `same size but different content is refused`() {
        assertFalse(OcrModelPin.matches(file("abd"), abc))
    }

    @Test
    fun `different size is refused`() {
        assertFalse(OcrModelPin.matches(file("abcd"), abc))
    }

    @Test
    fun `unknown language is refused`() {
        assertNull(OcrModelPin.fingerprint("xyz"))
        assertFalse(OcrModelPin.matches("xyz", file("abc")))
    }

    @Test
    fun `pinned values are well formed`() {
        assertTrue(OcrModelPin.COMMIT.matches(Regex("[0-9a-f]{40}")))
        listOf("ben", "eng").forEach {
            assertTrue(OcrModelPin.fingerprint(it)!!.sha256.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test
    fun `download address uses the pinned commit not a branch`() {
        val url = OcrModelPin.url("ben")
        assertTrue(url.contains("/${OcrModelPin.COMMIT}/"))
        assertEquals("ben.traineddata", url.substringAfterLast('/'))
        assertFalse(url.contains("/main/"))
    }
}

package com.paperly.app.data.reader

import com.paperly.app.data.backup.BackupAnnotation
import com.paperly.app.data.backup.BackupExtras
import com.paperly.app.data.backup.BackupExtrasCodec
import com.paperly.app.domain.reader.MatchRect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnnotationRectsTest {
    private val a = MatchRect(0.1f, 0.2f, 0.5f, 0.3f)
    private val b = MatchRect(0.2f, 0.6f, 0.9f, 0.7f)

    @Test
    fun singlePartIsNotEncodedAndMultiPartRoundTrips() {
        assertNull(AnnotationRects.encode(listOf(a)))
        assertEquals(listOf(a, b), AnnotationRects.decode(AnnotationRects.encode(listOf(a, b))))
    }

    @Test
    fun decodeDropsInvalidPartsAndSurvivesCorruptJson() {
        assertEquals(listOf(a), AnnotationRects.decode("[[0.1,0.2,0.5,0.3],[2,0,1,1],[0.1,0.2]]"))
        assertEquals(emptyList<MatchRect>(), AnnotationRects.decode("not json"))
        assertEquals(emptyList<MatchRect>(), AnnotationRects.decode(null))
    }

    @Test
    fun boundingBoxCoversAllParts() {
        assertEquals(MatchRect(0.1f, 0.2f, 0.9f, 0.7f), AnnotationRects.bounding(listOf(a, b)))
    }

    @Test
    fun backupCodecKeepsRectsAndOldEntriesWithoutRectsStayValid() {
        val note = BackupAnnotation(
            annotationId = "a1",
            documentId = "d1",
            locator = "0",
            type = "highlight",
            color = null,
            rectLeft = 0.1f,
            rectTop = 0.2f,
            rectRight = 0.9f,
            rectBottom = 0.7f,
            noteText = null,
            createdAt = 1L,
            updatedAt = 1L,
            rects = AnnotationRects.encode(listOf(a, b)),
        )
        val root = JSONObject()
        val both = listOf(note, note.copy(annotationId = "a2", rects = null))
        BackupExtrasCodec.encode(BackupExtras(annotations = both), root)
        val (decoded, bad) = BackupExtrasCodec.decode(root, setOf("d1"))
        assertEquals(0, bad)
        assertEquals(listOf(a, b), AnnotationRects.decode(decoded.annotations[0].rects))
        assertNull(decoded.annotations[1].rects)
    }
}

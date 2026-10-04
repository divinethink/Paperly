package com.paperly.app.core.file

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Plain JVM: export-cache pruning and orphan-file selection are pure file logic. */
class FileHousekeepingTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_000_000_000_000L
    private val old = now - 10_000L
    private val fresh = now + 10_000L

    private fun file(dir: File, name: String, modified: Long) = File(dir, name).apply {
        parentFile?.mkdirs()
        writeText("x")
        setLastModified(modified)
    }

    @Test
    fun pruneRemovesOnlyOldFilesAndEmptiedFolders() {
        val root = tmp.newFolder("exports")
        val oldDoc = file(root, "a.pdf", old)
        val freshDoc = file(root, "b.pdf", fresh)
        val oldImg = file(root, "images/p1.png", old)
        val mixedOld = file(root, "images2/p1.png", old)
        val mixedFresh = file(root, "images2/p2.png", fresh)
        val emptyAfter = File(root, "images")

        assertEquals(3, ExportFiles.pruneOlderThan(root, now))

        assertFalse(oldDoc.exists())
        assertTrue(freshDoc.exists())
        assertFalse(oldImg.exists())
        assertFalse(emptyAfter.exists())
        assertFalse(mixedOld.exists())
        assertTrue(mixedFresh.exists())
        assertTrue(root.exists())
    }

    @Test
    fun pruneOfMissingFolderIsZero() {
        assertEquals(0, ExportFiles.pruneOlderThan(File(tmp.root, "nope"), now))
    }

    @Test
    fun candidatesAreOldPlainFilesOnly() {
        val dir = tmp.newFolder("documents")
        val a = file(dir, "a", old)
        file(dir, "recent", fresh)
        File(dir, "sub").mkdirs()
        assertEquals(listOf(a), OrphanFiles.candidates(dir, now))
    }

    @Test
    fun unreferencedSkipsKnownIdsEvenInTrashButAlwaysTakesStaleTemp() {
        val dir = tmp.newFolder("documents")
        val known = file(dir, "known", old)
        val orphan = file(dir, "orphan", old)
        val tmpOfKnown = file(dir, "known.tmp", old)
        val result = OrphanFiles.unreferenced(listOf(known, orphan, tmpOfKnown), setOf("known"))
        assertEquals(setOf(orphan, tmpOfKnown), result.toSet())
        assertEquals(2, OrphanFiles.delete(result))
        assertTrue(known.exists())
    }
}

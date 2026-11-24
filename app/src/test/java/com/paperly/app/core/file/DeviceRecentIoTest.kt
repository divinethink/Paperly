package com.paperly.app.core.file

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class DeviceRecentIoTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(dir: File, page: Int?) =
        DeviceRecent(RecentKind.PDF, "Book", "abc", 5L, page, recentFile(dir, RecentKind.PDF))

    @Test
    fun copyThenPublishThenReadBack() {
        val dir = File(tmp.root, "recent")
        val source = tmp.newFile("a.pdf").apply { writeText("%PDF-1") }
        assertTrue(copyAtomic(source, dir, RecentKind.PDF))
        MutableStateFlow<DeviceRecent?>(null).publish(dir, entry(dir, 3))
        val read = readRecent(dir, RecentKind.PDF)
        assertEquals("Book", read?.title)
        assertEquals(3, read?.page)
        assertEquals("%PDF-1", read?.file?.readText())
    }

    @Test
    fun noPageMeansNull() {
        val dir = File(tmp.root, "recent")
        copyAtomic(tmp.newFile("a.pdf").apply { writeText("x") }, dir, RecentKind.PDF)
        MutableStateFlow<DeviceRecent?>(null).publish(dir, entry(dir, null))
        assertNull(readRecent(dir, RecentKind.PDF)?.page)
    }

    @Test
    fun missingFileGivesNothingAndDeleteClears() {
        val dir = File(tmp.root, "recent")
        assertNull(readRecent(dir, RecentKind.EPUB))
        copyAtomic(tmp.newFile("a.pdf").apply { writeText("x") }, dir, RecentKind.PDF)
        MutableStateFlow<DeviceRecent?>(null).publish(dir, entry(dir, 0))
        deleteRecent(dir, RecentKind.PDF)
        assertNull(readRecent(dir, RecentKind.PDF))
        assertFalse(recentFile(dir, RecentKind.PDF).exists())
    }
}

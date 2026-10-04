package com.paperly.app.core.database

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseBackupPruneTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun dirWith(vararg names: String): File = tmp.newFolder().also { d -> names.forEach { File(d, it).writeText("x") } }

    private fun names(d: File) = d.list().orEmpty().toSet()

    @Test
    fun listIsNewestVersionFirstAndIgnoresForeignFiles() {
        val d = dirWith("paperly.db.v2.bak", "paperly.db.v10.bak", "paperly.db.v7.bak", "notes.txt", "paperly.db.vX.bak")
        assertEquals(listOf(10, 7, 2), DatabaseBackup.listBackups(d).map { it.first })
    }

    @Test
    fun keepsTheThreeHighestVersionsAndNothingForeignIsTouched() {
        val d = dirWith(
            "paperly.db.v1.bak",
            "paperly.db.v2.bak",
            "paperly.db.v3.bak",
            "paperly.db.v4.bak",
            "paperly.db.v5.bak",
            "notes.txt",
            "other.bak",
        )
        assertEquals(2, DatabaseBackup.prune(d))
        assertEquals(setOf("paperly.db.v3.bak", "paperly.db.v4.bak", "paperly.db.v5.bak", "notes.txt", "other.bak"), names(d))
    }

    @Test
    fun theCopyJustMadeIsKeptEvenWhenItIsNotAmongTheHighest() {
        val d = dirWith("paperly.db.v1.bak", "paperly.db.v7.bak", "paperly.db.v8.bak", "paperly.db.v9.bak", "paperly.db.v10.bak")
        DatabaseBackup.prune(d, alsoKeep = 1)
        assertEquals(setOf("paperly.db.v1.bak", "paperly.db.v8.bak", "paperly.db.v9.bak", "paperly.db.v10.bak"), names(d))
    }

    @Test
    fun leftoverTempCopyIsRemovedAndFewBackupsAreAllKept() {
        val d = dirWith("paperly.db.v6.bak", "paperly.db.v7.bak.tmp")
        assertEquals(1, DatabaseBackup.prune(d))
        assertEquals(setOf("paperly.db.v6.bak"), names(d))
    }

    @Test
    fun missingFolderIsFine() {
        assertEquals(0, DatabaseBackup.prune(File(tmp.root, "nope")))
        assertFalse(File(tmp.root, "nope").exists())
        assertTrue(DatabaseBackup.listBackups(File(tmp.root, "nope")).isEmpty())
    }
}

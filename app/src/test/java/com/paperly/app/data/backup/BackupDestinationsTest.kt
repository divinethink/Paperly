package com.paperly.app.data.backup

import com.paperly.app.domain.backup.BackupScope
import com.paperly.app.domain.backup.BackupTarget
import com.paperly.app.domain.backup.LastBackup
import com.paperly.app.domain.backup.backupFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupDestinationsTest {
    @Test
    fun everyScopeHasItsOwnFixedFileName() {
        val names = BackupScope.entries.map { it.backupFileName() }
        assertEquals(listOf("Paperly-All.zip", "Paperly-Scan.zip", "Paperly-PDF.zip", "Paperly-EPUB.zip"), names)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun lastBackupSurvivesEncodeAndDecode() {
        val last = LastBackup(BackupTarget.DRIVE, 1_700_000_000_000L)
        assertEquals(last, decodeLastBackup(encodeLastBackup(last)))
        val device = LastBackup(BackupTarget.DEVICE, 5L)
        assertEquals(device, decodeLastBackup(encodeLastBackup(device)))
    }

    @Test
    fun brokenLastBackupValuesAreIgnored() {
        assertNull(decodeLastBackup(null))
        assertNull(decodeLastBackup(""))
        assertNull(decodeLastBackup("DRIVE"))
        assertNull(decodeLastBackup("DRIVE|x"))
        assertNull(decodeLastBackup("CLOUD|5"))
    }

    @Test
    fun folderLabelDropsTheVolumePrefix() {
        assertEquals("Documents/Backups", folderLabelOf("primary:Documents/Backups"))
        assertEquals("1234-ABCD", folderLabelOf("1234-ABCD"))
    }
}

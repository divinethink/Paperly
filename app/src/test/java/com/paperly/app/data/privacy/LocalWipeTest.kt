package com.paperly.app.data.privacy

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalWipeTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var db: PaperlyDatabase
    private val key = booleanPreferencesKey("k")
    private val storeFile = File(context.filesDir.parentFile, "wipe-test-${System.nanoTime()}.preferences_pb")
    private val store by lazy { PreferenceDataStoreFactory.create { storeFile } }
    private val marker get() = File(context.noBackupFilesDir, "delete_all.marker")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaperlyDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun fill() = runBlocking {
        db.documentDao().insert(DocumentEntity("d1", "T", "pdf", 1, "c", createdAt = 1, updatedAt = 1))
        File(context.filesDir, "documents").apply { mkdirs() }.resolve("d1").writeText("x")
        File(context.filesDir, "db-backups").apply { mkdirs() }.resolve("b").writeText("x")
        File(context.cacheDir, "exports").apply { mkdirs() }.resolve("e").writeText("x")
        store.edit { it[key] = true }
    }

    private fun wipe() = runBlocking { LocalWipeImpl(context, db, store).wipe() }

    @Test
    fun `wipe leaves no document file backup cache or setting`() {
        fill()
        wipe()
        assertEquals(0, File(context.filesDir, "documents").listFiles().orEmpty().size)
        assertEquals(0, File(context.filesDir, "db-backups").listFiles().orEmpty().size)
        assertEquals(0, context.cacheDir.listFiles().orEmpty().size)
        assertEquals(null, runBlocking { store.edit { }[key] })
        assertEquals(0, runBlocking { db.aggregateDao().getAllIds().size })
        assertFalse(marker.exists())
    }

    @Test
    fun `resume runs only when the marker is there`() {
        fill()
        runBlocking { LocalWipeImpl(context, db, store).resumeIfInterrupted() }
        assertTrue(File(context.filesDir, "documents/d1").exists())
        marker.createNewFile()
        runBlocking { LocalWipeImpl(context, db, store).resumeIfInterrupted() }
        assertFalse(File(context.filesDir, "documents/d1").exists())
        assertFalse(marker.exists())
    }
}

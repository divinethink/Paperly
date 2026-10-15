package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Annotation id + its page locator (projection). */
data class AnnotationLocatorRow(val annotationId: String, val locator: String)

/**
 * S4: moves everything keyed by page index of a scanned document to the new page order, in ONE transaction:
 * page title/note, editor rows, highlights, bookmarks and the reading position. No schema change.
 * A page that is gone (deleted) takes its title/note and bookmark with it; highlights never get that far
 * (a highlighted page cannot be deleted) and a stray one aborts the whole transaction.
 */
@Dao
abstract class ScanPageRemapDao {
    @Query("SELECT * FROM scan_pages WHERE documentId = :id")
    abstract suspend fun pages(id: String): List<ScanPageEntity>

    @Query("DELETE FROM scan_pages WHERE documentId = :id")
    abstract suspend fun clearPages(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putPages(rows: List<ScanPageEntity>)

    @Query("DELETE FROM scan_page_edit WHERE documentId = :id")
    abstract suspend fun clearEdits(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putEdits(rows: List<ScanPageEditEntity>)

    @Query("SELECT annotationId, locator FROM annotations WHERE documentId = :id")
    abstract suspend fun annotationLocators(id: String): List<AnnotationLocatorRow>

    @Query("UPDATE annotations SET locator = :locator WHERE annotationId = :annotationId")
    abstract suspend fun setAnnotationLocator(annotationId: String, locator: String)

    @Query("SELECT * FROM bookmarks WHERE documentId = :id")
    abstract suspend fun bookmarks(id: String): List<BookmarkEntity>

    @Query("DELETE FROM bookmarks WHERE documentId = :id")
    abstract suspend fun clearBookmarks(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putBookmarks(rows: List<BookmarkEntity>)

    @Query("SELECT * FROM reading_state WHERE documentId = :id")
    abstract suspend fun readingState(id: String): ReadingStateEntity?

    @Query("UPDATE reading_state SET locator = :locator WHERE documentId = :id")
    abstract suspend fun setReadingLocator(id: String, locator: String)

    /**
     * [positions] = old page index -> new position, [editRows] = the new editor rows (already in the new order),
     * [fileStep] runs LAST inside the transaction (the atomic file replace): if it throws, every row change rolls back.
     */
    @Transaction
    open suspend fun remap(
        documentId: String,
        positions: Map<Int, Int>,
        newCount: Int,
        editRows: List<ScanPageEditEntity>,
        fileStep: suspend () -> Unit,
    ) {
        val titles = pages(documentId)
        clearPages(documentId)
        putPages(titles.mapNotNull { row -> positions[row.pageIndex]?.let { row.copy(pageIndex = it) } })

        clearEdits(documentId)
        putEdits(editRows)

        annotationLocators(documentId).forEach { row ->
            val target = row.locator.toIntOrNull()?.let { positions[it] }
            // a highlight on a removed page: abort, the transaction rolls back
            val moved = checkNotNull(target) { "Highlight on a removed page" }.toString()
            if (moved != row.locator) setAnnotationLocator(row.annotationId, moved)
        }

        val marks = bookmarks(documentId)
        clearBookmarks(documentId)
        putBookmarks(
            marks.mapNotNull { row ->
                row.locator.toIntOrNull()?.let { positions[it] }?.let { row.copy(locator = it.toString()) }
            },
        )

        readingState(documentId)?.let { state ->
            val old = state.locator.toIntOrNull()
            if (old != null) {
                val moved = (positions[old] ?: old.coerceIn(0, (newCount - 1).coerceAtLeast(0))).toString()
                if (moved != state.locator) setReadingLocator(documentId, moved)
            }
        }
        fileStep()
    }
}

package com.paperly.app.core.file

import java.io.File

/**
 * Files in the documents folder that no database row points to (leftovers of an interrupted import/restore).
 * Pure file logic. Safety rules: only plain files older than a cutoff (so an import that has stored its file but not
 * yet inserted its row is never touched), and the folder is listed BEFORE the database is read.
 */
object OrphanFiles {
    private const val TMP_SUFFIX = ".tmp"

    /** Step 1 (call first): plain files in [dir] last modified before [cutoffMillis]. */
    fun candidates(dir: File, cutoffMillis: Long): List<File> =
        dir.listFiles().orEmpty().filter { it.isFile && it.lastModified() < cutoffMillis }

    /** Step 2: of [candidates], those no row in [knownIds] (Trash rows included) refers to; stale `.tmp` always. */
    fun unreferenced(candidates: List<File>, knownIds: Set<String>): List<File> =
        candidates.filter { it.name.endsWith(TMP_SUFFIX) || it.name !in knownIds }

    /** Returns how many were deleted. */
    fun delete(files: List<File>): Int = files.count { it.delete() }
}

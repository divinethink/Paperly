package com.paperly.app.domain.reader

import kotlinx.coroutines.flow.Flow

/** Per-document reading position and bookmarks. PDF locator = zero-based page index. */
interface ReaderStateRepository {
    /** Last saved page index, or null if none. */
    suspend fun getSavedPage(documentId: String): Int?

    /** Idempotent upsert. */
    suspend fun saveProgress(documentId: String, page: Int, pageCount: Int)

    /** Saved reading progress 0..1 (null = never read); emits on every change. */
    fun observeProgress(documentId: String): Flow<Float?>

    fun observeBookmarkedPages(documentId: String): Flow<Set<Int>>

    /** Returns true if the page is bookmarked after the call. */
    suspend fun toggleBookmark(documentId: String, page: Int): Boolean

    // String-locator API (EPUB: Readium locator JSON). Same tables as the PDF page API above.

    suspend fun getSavedLocator(documentId: String): String?

    /** Idempotent upsert; [progress] is clamped to 0..1. */
    suspend fun saveLocator(documentId: String, locator: String, progress: Float)

    fun observeBookmarkLocators(documentId: String): Flow<List<String>>

    /** Returns true if the locator is bookmarked after the call. */
    suspend fun toggleBookmarkLocator(documentId: String, locator: String): Boolean
}

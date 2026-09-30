package com.paperly.app.domain.reader

import kotlinx.coroutines.flow.Flow

/** Per-document reading position and bookmarks. PDF locator = zero-based page index. */
interface ReaderStateRepository {
    /** Last saved page index, or null if none. */
    suspend fun getSavedPage(documentId: String): Int?

    /** Idempotent upsert. */
    suspend fun saveProgress(documentId: String, page: Int, pageCount: Int)

    fun observeBookmarkedPages(documentId: String): Flow<Set<Int>>

    /** Returns true if the page is bookmarked after the call. */
    suspend fun toggleBookmark(documentId: String, page: Int): Boolean
}

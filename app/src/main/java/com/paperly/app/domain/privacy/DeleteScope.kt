package com.paperly.app.domain.privacy

import com.paperly.app.domain.document.LibraryType
import kotlinx.coroutines.flow.StateFlow

/**
 * What "Delete" in Settings removes. TRASH = everything in Trash (like Empty Trash). SCAN / PDF / EPUB = that tab's
 * documents from THIS phone only (cloud copies stay). ALL = the full "delete all my data" (cloud, account, phone).
 */
enum class DeleteScope(val type: LibraryType?) {
    TRASH(null),
    SCAN(LibraryType.SCAN),
    PDF(LibraryType.PDF),
    EPUB(LibraryType.EPUB),
    ALL(null),
}

sealed interface ScopedDeleteOutcome {
    data class Done(val deleted: Int) : ScopedDeleteOutcome

    /** Some documents could not be removed (file busy etc.); they are still in the Library and can be tried again. */
    data class Partial(val deleted: Int, val failed: Int) : ScopedDeleteOutcome

    data object Failed : ScopedDeleteOutcome
}

data class ScopedDeleteState(val running: Boolean = false, val outcome: ScopedDeleteOutcome? = null)

/** Removes every live document of one kind from this phone only. Nothing is sent to the cloud. */
interface LocalTypeWipe {
    suspend fun wipe(type: LibraryType): ScopedDeleteOutcome
}

/**
 * Runs a scoped delete in the app's own scope (leaving Settings never stops it halfway). Not for [DeleteScope.ALL]
 * (that is `DeleteAllDataRunner`). Does nothing while another delete or a backup is running.
 */
interface ScopedDeleteRunner {
    val state: StateFlow<ScopedDeleteState>

    fun start(scope: DeleteScope)

    /** Clears a finished result. No effect while running. */
    fun dismissOutcome()
}

package com.paperly.app.core.file

import android.content.Context
import com.paperly.app.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val DEVICE_RECENT_DIR = "device-recent"

/** The two kinds kept for "Continue from device": one file each. [key] and [ext] are fixed file names. */
enum class RecentKind(val key: String, val ext: String) { PDF("pdf", "pdf"), EPUB("epub", "epub") }

/** The last PDF / EPUB read straight from the phone (not in the Library). [page] is 0-based, PDF only. */
data class DeviceRecent(
    val kind: RecentKind,
    val title: String,
    val checksum: String,
    val openedAt: Long,
    val page: Int?,
    val file: File,
)

private class Link(val kind: RecentKind, val inPlace: Boolean)

/**
 * Keeps the last PDF and the last EPUB opened from outside the Library (Open-With), one copy each in app-private
 * storage, so "Continue from device" survives the app being closed. The original file is never touched here.
 * A quick-view session is tied to its entry by its token; only the newest session of a kind may change the entry.
 */
@Singleton
class DeviceRecentStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, DEVICE_RECENT_DIR)
    private val state = RecentKind.entries.associateWith { MutableStateFlow(readRecent(dir, it)) }
    private val links = ConcurrentHashMap<String, Link>()
    private val owner = ConcurrentHashMap<RecentKind, String>()
    private val hidden = ConcurrentHashMap.newKeySet<String>()
    private val lock = Any()

    fun observe(kind: RecentKind): StateFlow<DeviceRecent?> = state.getValue(kind)

    /** A fresh Open-With file: [source] (the session's cache copy) replaces the kept one. Best-effort, blocking. */
    fun record(kind: RecentKind, token: String, source: File, title: String, checksum: String) {
        synchronized(lock) {
            val flow = state.getValue(kind)
            if (copyAtomic(source, dir, kind)) {
                val samePage = flow.value?.takeIf { it.checksum == checksum }?.page
                val now = System.currentTimeMillis()
                flow.publish(dir, DeviceRecent(kind, title, checksum, now, samePage, recentFile(dir, kind)))
                links[token] = Link(kind, inPlace = false)
                owner[kind] = token
            }
        }
    }

    /** A session opened from the kept copy itself: later writes go straight into that copy. */
    fun adopt(token: String, kind: RecentKind, checksum: String) {
        synchronized(lock) {
            links[token] = Link(kind, inPlace = true)
            owner[kind] = token
            val flow = state.getValue(kind)
            flow.value?.let { flow.publish(dir, it.copy(checksum = checksum, openedAt = System.currentTimeMillis())) }
        }
    }

    /** Where the PDF was left (0-based); null when unknown or this session no longer owns the entry. */
    fun page(token: String): Int? {
        val kind = links[token]?.kind ?: return null
        return state.getValue(kind).value?.takeIf { owner[kind] == token }?.page
    }

    fun savePage(token: String, page: Int) {
        val kind = links[token]?.kind?.takeIf { it == RecentKind.PDF } ?: return
        scope.launch(Dispatchers.IO) {
            synchronized(lock) {
                val flow = state.getValue(kind)
                val current = flow.value
                if (current != null && owner[kind] == token && current.page != page) {
                    flow.publish(dir, current.copy(page = page, openedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    /** The original changed (our marks went in): keep the entry in step. [written] is the finished file. */
    suspend fun onWritten(token: String, checksum: String, written: File) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val link = links[token] ?: return@synchronized
            val flow = state.getValue(link.kind)
            val current = flow.value
            if (current != null && owner[link.kind] == token) {
                val kept = link.inPlace || copyAtomic(written, dir, link.kind)
                if (kept) flow.publish(dir, current.copy(checksum = checksum))
            }
        }
    }

    /** The file now lives in the Library: hide the card at once, delete the kept copy when the session ends. */
    fun hide(token: String) {
        synchronized(lock) {
            val kind = links[token]?.kind
            if (kind != null && owner[kind] == token) {
                hidden.add(token)
                state.getValue(kind).value = null
            }
        }
    }

    /** The session is over (its cache copy is gone). */
    fun unlink(token: String) {
        synchronized(lock) {
            val link = links.remove(token)
            if (link != null && hidden.remove(token) && owner[link.kind] == token) {
                deleteRecent(dir, link.kind)
                owner.remove(link.kind)
            }
        }
    }

    /** Forgets the kept file of [kind] (it is in the Library, or it is gone). */
    fun drop(kind: RecentKind) {
        synchronized(lock) {
            deleteRecent(dir, kind)
            owner.remove(kind)
            state.getValue(kind).value = null
        }
    }
}

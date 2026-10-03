package com.paperly.app.domain.sync

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-document throttle for reading-position sync (in memory only: after a process restart the first change
 * simply goes through). [onChange] says whether this change should enter the queue now; a change held back is
 * remembered, and [onFlush] (reader paused/closed) releases it, so the final position is never left behind.
 */
class ReadingThrottle(private val now: () -> Long = System::currentTimeMillis) {
    private val lastEnqueued = ConcurrentHashMap<String, Long>()
    private val held: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun onChange(documentId: String): Boolean {
        val time = now()
        val enqueue = ReadingDebounce.shouldEnqueue(lastEnqueued[documentId], time)
        if (enqueue) {
            lastEnqueued[documentId] = time
            held.remove(documentId)
        } else {
            held.add(documentId)
        }
        return enqueue
    }

    fun onFlush(documentId: String): Boolean {
        val release = held.remove(documentId)
        if (release) lastEnqueued[documentId] = now()
        return release
    }
}

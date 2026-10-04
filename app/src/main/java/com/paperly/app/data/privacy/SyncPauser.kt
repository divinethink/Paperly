package com.paperly.app.data.privacy

import com.paperly.app.domain.sync.SyncQueue
import com.paperly.app.domain.sync.SyncSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Stops sync while the cloud is emptied (so nothing is re-uploaded) and puts it back as the user had it. */
@Singleton
class SyncPauser @Inject constructor(
    private val settings: SyncSettings,
    private val queue: SyncQueue,
) {
    /** Pauses sync (this also cancels a run in flight) and returns whether it was already paused. */
    suspend fun pause(): Boolean {
        val wasPaused = settings.paused.first()
        settings.setPaused(true)
        queue.kick()
        return wasPaused
    }

    suspend fun restore(wasPaused: Boolean) {
        settings.setPaused(wasPaused)
        if (!wasPaused) queue.kick()
    }
}

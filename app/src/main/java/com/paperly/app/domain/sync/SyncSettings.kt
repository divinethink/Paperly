package com.paperly.app.domain.sync

import kotlinx.coroutines.flow.Flow

/** User policy for sync. Defaults are the safe ones: Wi-Fi only, not paused. */
interface SyncSettings {
    val wifiOnly: Flow<Boolean>

    /** Paused = nothing is sent or received; the queue is kept and continues on resume. */
    val paused: Flow<Boolean>

    suspend fun setWifiOnly(value: Boolean)

    suspend fun setPaused(value: Boolean)
}

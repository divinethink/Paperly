package com.paperly.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.paperly.app.domain.sync.SyncSettings
import com.paperly.app.domain.sync.SyncStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore keys shared by the settings store, the queue scheduler and the processor. */
internal object SyncPrefs {
    val WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
    val PAUSED = booleanPreferencesKey("sync_paused")
}

@Singleton
class DataStoreSyncSettings @Inject constructor(
    private val store: DataStore<Preferences>,
) : SyncSettings {
    override val wifiOnly: Flow<Boolean> = store.data.map { it[SyncPrefs.WIFI_ONLY] ?: true }

    override val paused: Flow<Boolean> = store.data.map { it[SyncPrefs.PAUSED] ?: false }

    override suspend fun setWifiOnly(value: Boolean) {
        store.edit { it[SyncPrefs.WIFI_ONLY] = value }
    }

    override suspend fun setPaused(value: Boolean) {
        store.edit { it[SyncPrefs.PAUSED] = value }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncSettingsModule {
    @Binds
    abstract fun bindSyncSettings(impl: DataStoreSyncSettings): SyncSettings

    @Binds
    abstract fun bindSyncStatus(impl: RoomSyncStatus): SyncStatus
}

package com.paperly.app.data.scanner

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.paperly.app.domain.scanner.ScanPagesViewSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class ScanPagesViewSettingsImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : ScanPagesViewSettings {

    // Additive key; missing or unreadable = grid. No migration.
    override val grid: Flow<Boolean> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY] ?: true }

    override suspend fun setGrid(grid: Boolean) {
        store.edit { it[KEY] = grid }
    }

    private companion object {
        val KEY = booleanPreferencesKey("scan_pages_grid")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanPagesViewSettingsModule {
    @Binds
    abstract fun bindScanPagesViewSettings(impl: ScanPagesViewSettingsImpl): ScanPagesViewSettings
}

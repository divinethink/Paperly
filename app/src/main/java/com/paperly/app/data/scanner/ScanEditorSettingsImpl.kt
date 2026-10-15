package com.paperly.app.data.scanner

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.paperly.app.domain.scanner.ScanEditorSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

@Singleton
class ScanEditorSettingsImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : ScanEditorSettings {

    // Additive key; missing = true (ON by default). No migration.
    override val enabled: Flow<Boolean> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map { it[KEY] ?: true }

    override suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[KEY] = enabled }
    }

    private companion object {
        val KEY = booleanPreferencesKey("scan_editor_enabled")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanEditorSettingsModule {
    @Binds
    abstract fun bindScanEditorSettings(impl: ScanEditorSettingsImpl): ScanEditorSettings
}

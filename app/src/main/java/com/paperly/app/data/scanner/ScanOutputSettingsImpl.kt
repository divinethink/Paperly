package com.paperly.app.data.scanner

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.domain.scanner.ScanOutputSettings
import com.paperly.app.domain.scanner.ScanOutputSize
import com.paperly.app.domain.scanner.scanOutputSizeOf
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
class ScanOutputSettingsImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : ScanOutputSettings {

    // Additive key (stores the enum name); missing or unreadable = MEDIUM. No migration.
    override val size: Flow<ScanOutputSize> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { scanOutputSizeOf(it[KEY]) }

    override suspend fun setSize(size: ScanOutputSize) {
        store.edit { it[KEY] = size.name }
    }

    private companion object {
        val KEY = stringPreferencesKey("scan_output_size")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanOutputSettingsModule {
    @Binds
    abstract fun bindScanOutputSettings(impl: ScanOutputSettingsImpl): ScanOutputSettings
}

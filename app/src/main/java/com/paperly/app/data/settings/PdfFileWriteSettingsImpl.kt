package com.paperly.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.paperly.app.domain.settings.PdfFileWriteSettings
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
class PdfFileWriteSettingsImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : PdfFileWriteSettings {

    // Additive key; missing = true (ON by default). No migration.
    override val writeHighlights: Flow<Boolean> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map { it[KEY] ?: true }

    override suspend fun setWriteHighlights(enabled: Boolean) {
        store.edit { it[KEY] = enabled }
    }

    private companion object {
        val KEY = booleanPreferencesKey("write_highlights_to_pdf")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfFileWriteSettingsModule {
    @Binds
    abstract fun bindPdfFileWriteSettings(impl: PdfFileWriteSettingsImpl): PdfFileWriteSettings
}

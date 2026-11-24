package com.paperly.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.domain.settings.AppPalette
import com.paperly.app.domain.settings.AppThemeMode
import com.paperly.app.domain.settings.AppearanceStore
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
class AppearanceStoreImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : AppearanceStore {

    // Additive key; missing = false (brand palette). No migration.
    override val dynamicColor: Flow<Boolean> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map { it[DYNAMIC_COLOR_KEY] ?: false }

    // Additive key; missing or unknown = follow the phone.
    override val themeMode: Flow<AppThemeMode> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map { AppThemeMode.fromKey(it[THEME_MODE_KEY]) }

    // Additive key; missing or unknown = the brand palette.
    override val palette: Flow<AppPalette> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map { AppPalette.fromKey(it[PALETTE_KEY]) }

    override suspend fun setPalette(palette: AppPalette) {
        store.edit { it[PALETTE_KEY] = palette.key }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        store.edit { it[THEME_MODE_KEY] = mode.key }
    }

    private companion object {
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        val THEME_MODE_KEY = stringPreferencesKey("app_theme_mode")
        val PALETTE_KEY = stringPreferencesKey("app_palette")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppearanceModule {
    @Binds
    abstract fun bindAppearanceStore(impl: AppearanceStoreImpl): AppearanceStore
}

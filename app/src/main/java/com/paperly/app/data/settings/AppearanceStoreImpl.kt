package com.paperly.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    private companion object {
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppearanceModule {
    @Binds
    abstract fun bindAppearanceStore(impl: AppearanceStoreImpl): AppearanceStore
}

package com.paperly.app.data.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.paperly.app.domain.reader.ReaderComfort
import com.paperly.app.domain.reader.ReaderComfortStore
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
class ReaderComfortStoreImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : ReaderComfortStore {

    // Additive keys; missing values fall back to the defaults (no migration). Absent brightness = system.
    override val comfort: Flow<ReaderComfort> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map {
            ReaderComfort(
                brightness = it[BRIGHTNESS_KEY],
                blueLight = it[BLUE_LIGHT_KEY] ?: 0f,
                keepAwake = it[KEEP_AWAKE_KEY] ?: false,
                hideBars = it[HIDE_BARS_KEY] ?: false,
            ).clamped()
        }

    override suspend fun save(comfort: ReaderComfort) {
        val value = comfort.clamped()
        store.edit { prefs ->
            val brightness = value.brightness
            if (brightness == null) {
                prefs.remove(BRIGHTNESS_KEY)
            } else {
                prefs[BRIGHTNESS_KEY] = brightness
            }
            prefs[BLUE_LIGHT_KEY] = value.blueLight
            prefs[KEEP_AWAKE_KEY] = value.keepAwake
            prefs[HIDE_BARS_KEY] = value.hideBars
        }
    }

    private companion object {
        val BRIGHTNESS_KEY = floatPreferencesKey("reader_brightness")
        val BLUE_LIGHT_KEY = floatPreferencesKey("reader_blue_light")
        val KEEP_AWAKE_KEY = booleanPreferencesKey("reader_keep_awake")
        val HIDE_BARS_KEY = booleanPreferencesKey("reader_hide_bars")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderComfortModule {
    @Binds
    abstract fun bindReaderComfortStore(impl: ReaderComfortStoreImpl): ReaderComfortStore
}

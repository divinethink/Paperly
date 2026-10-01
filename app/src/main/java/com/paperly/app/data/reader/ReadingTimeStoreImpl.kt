package com.paperly.app.data.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.domain.reader.ReadingTimeStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

private const val SECONDS_PER_MINUTE = 60

/**
 * Additive DataStore keys (no DB change): the local day ("yyyy-MM-dd") + seconds read that day.
 * The day key is local on purpose: "today" means the user's calendar day, not a UTC day.
 */
@Singleton
class ReadingTimeStoreImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : ReadingTimeStore {

    override val todayMinutes: Flow<Int> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map {
            val seconds = if (it[DAY_KEY] == LocalDate.now().toString()) it[SECONDS_KEY] ?: 0L else 0L
            (seconds / SECONDS_PER_MINUTE).toInt()
        }

    override suspend fun addSeconds(seconds: Long) {
        if (seconds <= 0L) return
        store.edit {
            val today = LocalDate.now().toString()
            val current = if (it[DAY_KEY] == today) it[SECONDS_KEY] ?: 0L else 0L
            it[DAY_KEY] = today
            it[SECONDS_KEY] = current + seconds
        }
    }

    private companion object {
        val DAY_KEY = stringPreferencesKey("reading_day")
        val SECONDS_KEY = longPreferencesKey("reading_seconds")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReadingTimeModule {
    @Binds
    abstract fun bindReadingTimeStore(impl: ReadingTimeStoreImpl): ReadingTimeStore
}

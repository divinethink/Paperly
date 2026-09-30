package com.paperly.app.data.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.domain.reader.ReaderPreferences
import com.paperly.app.domain.reader.ReaderTheme
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
class ReaderPreferencesImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : ReaderPreferences {

    // A corrupt/unreadable store degrades to the default theme instead of crashing the Reader.
    override val theme: Flow<ReaderTheme> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map { ReaderTheme.fromKey(it[THEME_KEY]) }

    override suspend fun setTheme(theme: ReaderTheme) {
        store.edit { it[THEME_KEY] = theme.key }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("reader_theme")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderPreferencesModule {
    @Binds
    abstract fun bindReaderPreferences(impl: ReaderPreferencesImpl): ReaderPreferences
}

package com.paperly.app.data.document

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.domain.document.LibrarySort
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.domain.document.LibraryView
import com.paperly.app.domain.document.LibraryViewStore
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
class LibraryViewStoreImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : LibraryViewStore {

    // Additive keys; missing/unknown values fall back to the defaults (no migration).
    override val view: Flow<LibraryView> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map {
            LibraryView(
                sort = LibrarySort.fromKey(it[SORT_KEY]),
                ascending = it[ASC_KEY] ?: false,
                type = LibraryType.fromKey(it[TYPE_KEY]),
            )
        }

    override suspend fun save(view: LibraryView) {
        store.edit {
            it[SORT_KEY] = view.sort.key
            it[ASC_KEY] = view.ascending
            val type = view.type
            if (type == null) it.remove(TYPE_KEY) else it[TYPE_KEY] = type.key
        }
    }

    private companion object {
        val SORT_KEY = stringPreferencesKey("library_sort")
        val ASC_KEY = booleanPreferencesKey("library_sort_asc")
        val TYPE_KEY = stringPreferencesKey("library_type")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryViewModule {
    @Binds
    abstract fun bindLibraryViewStore(impl: LibraryViewStoreImpl): LibraryViewStore
}

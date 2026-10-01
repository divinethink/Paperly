package com.paperly.app.data.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotateStyleStore
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
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
class AnnotateStyleStoreImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : AnnotateStyleStore {

    // Additive keys; missing/unknown values fall back to the defaults (no migration).
    override val style: Flow<AnnotateStyle> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map {
            val defaults = AnnotateStyle()
            AnnotateStyle(
                type = it[TYPE_KEY]?.let(AnnotationType::fromKey) ?: defaults.type,
                color = AnnotationColor.fromKey(it[COLOR_KEY]) ?: defaults.color,
            )
        }

    override suspend fun save(style: AnnotateStyle) {
        store.edit {
            it[TYPE_KEY] = style.type.key
            it[COLOR_KEY] = style.color.key
        }
    }

    private companion object {
        val TYPE_KEY = stringPreferencesKey("annotate_type")
        val COLOR_KEY = stringPreferencesKey("annotate_color")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnnotateStyleModule {
    @Binds
    abstract fun bindAnnotateStyleStore(impl: AnnotateStyleStoreImpl): AnnotateStyleStore
}

package com.paperly.app.data.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotateStyleStore
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationStroke
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
                stroke = AnnotationStroke.fromKey(it[STROKE_KEY]) ?: defaults.stroke,
            )
        }

    // Per tool keys are additive too; a tool without them keeps the old single colour/thickness if it was the
    // current tool, else its default look.
    override val tools: Flow<Map<AnnotationType, AnnotateStyle>> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map { prefs -> TOOLS.associateWith { toolLook(prefs, it) } }

    override suspend fun save(style: AnnotateStyle) {
        store.edit {
            it[TYPE_KEY] = style.type.key
            it[COLOR_KEY] = style.color.key
            it[STROKE_KEY] = style.stroke.key
            it[stringPreferencesKey("annotate_color_${style.type.key}")] = style.color.key
            it[stringPreferencesKey("annotate_stroke_${style.type.key}")] = style.stroke.key
        }
    }

    private fun toolLook(prefs: Preferences, type: AnnotationType): AnnotateStyle {
        val fallback = if (prefs[TYPE_KEY] == type.key) {
            AnnotateStyle(
                type,
                AnnotationColor.fromKey(prefs[COLOR_KEY]) ?: AnnotationColor.defaultFor(type),
                AnnotationStroke.fromKey(prefs[STROKE_KEY]) ?: AnnotateStyle().stroke,
            )
        } else {
            AnnotateStyle.defaultFor(type)
        }
        return AnnotateStyle(
            type,
            AnnotationColor.fromKey(prefs[stringPreferencesKey("annotate_color_${type.key}")]) ?: fallback.color,
            AnnotationStroke.fromKey(prefs[stringPreferencesKey("annotate_stroke_${type.key}")]) ?: fallback.stroke,
        )
    }

    private companion object {
        val TYPE_KEY = stringPreferencesKey("annotate_type")
        val COLOR_KEY = stringPreferencesKey("annotate_color")
        val STROKE_KEY = stringPreferencesKey("annotate_stroke")
        val TOOLS = AnnotationType.entries - AnnotationType.NOTE
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnnotateStyleModule {
    @Binds
    abstract fun bindAnnotateStyleStore(impl: AnnotateStyleStoreImpl): AnnotateStyleStore
}

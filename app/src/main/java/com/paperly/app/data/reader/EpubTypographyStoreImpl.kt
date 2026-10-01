package com.paperly.app.data.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.domain.reader.EpubFont
import com.paperly.app.domain.reader.EpubTypography
import com.paperly.app.domain.reader.EpubTypographyStore
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
class EpubTypographyStoreImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : EpubTypographyStore {

    // Missing/corrupt values fall back to defaults (additive keys; no migration needed).
    override val typography: Flow<EpubTypography> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map {
            EpubTypography(
                font = EpubFont.fromKey(it[FONT_KEY]),
                fontScale = it[SCALE_KEY] ?: EpubTypography.DEFAULT_SCALE,
                lineSpacing = it[LINE_KEY] ?: EpubTypography.DEFAULT_LINE,
                margin = it[MARGIN_KEY] ?: EpubTypography.DEFAULT_MARGIN,
            ).coerced()
        }

    override suspend fun save(typography: EpubTypography) {
        val safe = typography.coerced()
        store.edit {
            it[FONT_KEY] = safe.font.key
            it[SCALE_KEY] = safe.fontScale
            it[LINE_KEY] = safe.lineSpacing
            it[MARGIN_KEY] = safe.margin
        }
    }

    private companion object {
        val FONT_KEY = stringPreferencesKey("epub_font")
        val SCALE_KEY = floatPreferencesKey("epub_font_scale")
        val LINE_KEY = floatPreferencesKey("epub_line_spacing")
        val MARGIN_KEY = floatPreferencesKey("epub_margin")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EpubTypographyModule {
    @Binds
    abstract fun bindEpubTypographyStore(impl: EpubTypographyStoreImpl): EpubTypographyStore
}

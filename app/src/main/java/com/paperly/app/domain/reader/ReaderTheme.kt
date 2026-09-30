package com.paperly.app.domain.reader

import kotlinx.coroutines.flow.Flow

/** Reading themes (Architecture §৬). Stored by [key]; unknown/missing values fall back to [AUTO]. */
enum class ReaderTheme(val key: String) {
    AUTO("auto"),
    LIGHT("light"),
    SEPIA("sepia"),
    WARM("warm"),
    DARK("dark"),
    AMOLED("amoled"),
    ;

    companion object {
        fun fromKey(key: String?): ReaderTheme = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

interface ReaderPreferences {
    val theme: Flow<ReaderTheme>

    suspend fun setTheme(theme: ReaderTheme)
}

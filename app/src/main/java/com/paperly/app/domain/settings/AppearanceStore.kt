package com.paperly.app.domain.settings

import kotlinx.coroutines.flow.Flow

/** Light/dark of the app screens; [SYSTEM] follows the phone. Reading themes are a separate choice. */
enum class AppThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        /** Unknown or missing = [SYSTEM], so an old or damaged value never breaks start-up. */
        fun fromKey(key: String?): AppThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** App-wide look. Dynamic Color (Material You) is optional and OFF by default; the brand palette is the default. */
interface AppearanceStore {
    val dynamicColor: Flow<Boolean>

    val themeMode: Flow<AppThemeMode>

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setThemeMode(mode: AppThemeMode)
}

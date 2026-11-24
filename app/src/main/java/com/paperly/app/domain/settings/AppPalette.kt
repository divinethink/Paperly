package com.paperly.app.domain.settings

/**
 * Colour palette of the app screens. [PAPERLY] is the brand palette and follows the App theme (light / dark / system);
 * the others bring their own tone ([forcedDark]) and replace System colours. Reading themes are separate.
 */
enum class AppPalette(val key: String, val forcedDark: Boolean?) {
    PAPERLY("paperly", null),
    MEADOW("meadow", false),
    GLACIER("glacier", false),
    NEON_NIGHT("neon_night", true),
    ;

    /** Dark or light for the app screens: a palette with its own tone decides, else the chosen mode ([modeDark]). */
    fun isDark(modeDark: Boolean): Boolean = forcedDark ?: modeDark

    companion object {
        /** Unknown or missing = [PAPERLY], so an old or damaged value never breaks start-up. */
        fun fromKey(key: String?): AppPalette = entries.firstOrNull { it.key == key } ?: PAPERLY
    }
}

package com.paperly.app.domain.reader

import kotlinx.coroutines.flow.Flow

const val MIN_BRIGHTNESS = 0.05f

/**
 * Reader-wide viewing comfort (PDF and EPUB share it). [brightness] null = follow the system;
 * [blueLight] 0 = off .. 1 = strongest warm tint. Global, not per-book.
 */
data class ReaderComfort(
    val brightness: Float? = null,
    val blueLight: Float = 0f,
    val keepAwake: Boolean = false,
    val hideBars: Boolean = false,
) {
    fun clamped(): ReaderComfort = copy(
        brightness = brightness?.coerceIn(MIN_BRIGHTNESS, 1f),
        blueLight = blueLight.coerceIn(0f, 1f),
    )
}

interface ReaderComfortStore {
    val comfort: Flow<ReaderComfort>

    suspend fun save(comfort: ReaderComfort)
}

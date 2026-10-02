package com.paperly.app.domain.settings

import kotlinx.coroutines.flow.Flow

/** App-wide look. Dynamic Color (Material You) is optional and OFF by default; the brand palette is the default. */
interface AppearanceStore {
    val dynamicColor: Flow<Boolean>

    suspend fun setDynamicColor(enabled: Boolean)
}

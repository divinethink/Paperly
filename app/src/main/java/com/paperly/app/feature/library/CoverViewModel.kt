package com.paperly.app.feature.library

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.paperly.app.domain.cover.CoverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** U4: one shared instance per Library screen; rows call [load] lazily from LaunchedEffect. */
@HiltViewModel
class CoverViewModel @Inject constructor(private val covers: CoverRepository) : ViewModel() {
    suspend fun load(id: String, type: String): Bitmap? = covers.cover(id, type)
}

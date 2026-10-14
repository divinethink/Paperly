package com.paperly.app.feature.library

import androidx.lifecycle.ViewModel
import com.paperly.app.core.file.DocumentFileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(private val store: DocumentFileStore) : ViewModel() {
    fun file(documentId: String): File? = store.resolve(documentId)
}

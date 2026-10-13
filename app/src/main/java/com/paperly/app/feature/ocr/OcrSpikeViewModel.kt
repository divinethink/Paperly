package com.paperly.app.feature.ocr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.data.ocr.OcrModel
import com.paperly.app.data.ocr.OcrSpike
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OcrSpikeUi(val modelReady: Boolean, val busy: Boolean, val message: String)

/** P10 spike (temporary): model download + a Bengali OCR trial; the result is plain text for the owner to read. */
@HiltViewModel
class OcrSpikeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val state = MutableStateFlow(OcrSpikeUi(OcrModel.isReady(context), false, ""))
    val ui: StateFlow<OcrSpikeUi> = state.asStateFlow()

    fun downloadModel() {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, message = "Downloading the Bengali model…")
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { OcrModel.download(context) }
            val note = if (ok) {
                "Model ready (${OcrModel.sizeKb(context)} KB)."
            } else {
                "Download failed — check the internet."
            }
            state.value = OcrSpikeUi(OcrModel.isReady(context), false, note)
        }
    }

    fun runOcr(pdf: Uri) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, message = "Reading 3 pages… this takes a while.")
        viewModelScope.launch {
            val report = OcrSpike.run(context, pdf)
            state.value = state.value.copy(busy = false, message = report.toText())
        }
    }
}

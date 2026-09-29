package com.paperly.app.feature.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.model.DocumentType
import com.paperly.app.core.perf.PerfTrace
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.reader.ReaderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReaderError { NONE, PASSWORD_REQUIRED, UNSUPPORTED, FAILED }

data class ReaderUiState(
    val loading: Boolean = true,
    val document: Document? = null,
    val pageCount: Int = 0,
    val firstPage: Bitmap? = null,
    val error: ReaderError = ReaderError.NONE,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
    private val fileStore: DocumentFileStore,
    // Provider = lazy: the engine is created only when a PDF is actually opened (Rule #10).
    private val engineProvider: Provider<ReaderEngine>,
) : ViewModel() {

    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    private var engine: ReaderEngine? = null

    init {
        viewModelScope.launch {
            val document = PerfTrace.span("reader.open") { repository.getDocument(documentId) }
            if (document == null) {
                _uiState.value = ReaderUiState(loading = false)
                return@launch
            }
            repository.markOpened(documentId)
            _uiState.value = if (document.type == DocumentType.PDF) {
                openPdf(document)
            } else {
                failed(document, ReaderError.UNSUPPORTED)
            }
        }
    }

    private suspend fun openPdf(document: Document): ReaderUiState {
        val file = fileStore.resolve(document.id) ?: return failed(document, ReaderError.FAILED)
        val pdf = engineProvider.get().also { engine = it }
        return try {
            when (val result = PerfTrace.span("reader.pdf.open") { pdf.open(file) }) {
                is OpenResult.Success -> {
                    val page = PerfTrace.span("reader.pdf.render") { pdf.renderPage(0, MAX_RENDER_WIDTH_PX) }
                    if (page == null) {
                        failed(document, ReaderError.FAILED)
                    } else {
                        ReaderUiState(
                            loading = false,
                            document = document,
                            pageCount = result.pageCount,
                            firstPage = page,
                        )
                    }
                }
                OpenResult.PasswordRequired -> failed(document, ReaderError.PASSWORD_REQUIRED)
                OpenResult.Failed -> failed(document, ReaderError.FAILED)
            }
        } catch (e: CancellationException) {
            pdf.close()
            throw e
        }
    }

    private fun failed(document: Document, error: ReaderError) =
        ReaderUiState(loading = false, document = document, error = error)

    override fun onCleared() {
        engine?.close()
    }

    private companion object {
        const val MAX_RENDER_WIDTH_PX = 1080 // P2-B sizes pages from the real screen width
    }
}

package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.EpubAnnotationRepository
import com.paperly.app.domain.reader.MatchRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [locatorJson] = the new selection (existing == null), or [existing] being edited. */
data class EpubAnnotationEditor(val locatorJson: String?, val existing: EpubAnnotation?)

/** Annotate-mode style: type + color applied straight to a text selection. */
data class EpubAnnotateStyle(
    val type: AnnotationType = AnnotationType.UNDERLINE,
    val color: AnnotationColor = AnnotationColor.RED,
)

/** Reader-side EPUB annotation state; persistence via [EpubAnnotationRepository]. */
class EpubAnnotationController(
    private val scope: CoroutineScope,
    private val repository: EpubAnnotationRepository,
    private val documentId: String,
) {
    val items: StateFlow<List<EpubAnnotation>> =
        (if (documentId.isEmpty()) emptyFlow() else repository.observe(documentId))
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _editor = MutableStateFlow<EpubAnnotationEditor?>(null)
    val editor: StateFlow<EpubAnnotationEditor?> = _editor.asStateFlow()

    private val _mode = MutableStateFlow(false)
    val mode: StateFlow<Boolean> = _mode.asStateFlow()
    private val _style = MutableStateFlow(EpubAnnotateStyle())
    val style: StateFlow<EpubAnnotateStyle> = _style.asStateFlow()

    fun toggleMode() {
        _mode.value = !_mode.value
    }

    fun setStyle(style: EpubAnnotateStyle) {
        _style.value = style
    }

    /** Annotate mode ON: save with the chosen style at once (Note still asks for text); OFF: open the dialog. */
    fun onSelection(locatorJson: String) {
        val style = _style.value
        if (!_mode.value || style.type == AnnotationType.NOTE) {
            startNew(locatorJson)
        } else {
            scope.launch { repository.add(documentId, locatorJson, AnnotationContent(style.type, style.color, null)) }
        }
    }

    fun startNew(locatorJson: String) {
        _editor.value = EpubAnnotationEditor(locatorJson, null)
    }

    fun startEdit(id: String) {
        items.value.firstOrNull { it.id == id }?.let { _editor.value = EpubAnnotationEditor(null, it) }
    }

    fun close() {
        _editor.value = null
    }

    fun save(editor: EpubAnnotationEditor, content: AnnotationContent) {
        scope.launch {
            val existing = editor.existing
            val locator = editor.locatorJson
            if (existing != null) {
                repository.update(existing.id, content)
            } else if (locator != null) {
                repository.add(documentId, locator, content)
            }
        }
    }

    fun delete(id: String) {
        scope.launch { repository.delete(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Adapter so the PDF annotation dialog can edit an EPUB annotation (page/rect are unused by the dialog). */
internal fun EpubAnnotation.toDialogModel() =
    Annotation(id, 0, type, MatchRect(0f, 0f, 0f, 0f), noteText, color)

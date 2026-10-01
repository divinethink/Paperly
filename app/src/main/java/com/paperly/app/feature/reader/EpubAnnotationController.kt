package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotateStyleStore
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.EpubAnnotationRepository
import com.paperly.app.domain.reader.MatchRect
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [locatorJson] = the new selection (existing == null), or [existing] being edited. */
data class EpubAnnotationEditor(val locatorJson: String?, val existing: EpubAnnotation?)

/** Injected into the ViewModel (keeps its constructor small); builds the per-document controller. */
class EpubAnnotationControllerFactory @Inject constructor(
    private val repository: EpubAnnotationRepository,
    private val styleStore: AnnotateStyleStore,
) {
    fun create(scope: CoroutineScope, documentId: String) =
        EpubAnnotationController(scope, repository, styleStore, documentId)
}

/** Reader-side EPUB annotation state; persistence via [EpubAnnotationRepository]. */
class EpubAnnotationController(
    private val scope: CoroutineScope,
    private val repository: EpubAnnotationRepository,
    private val styleStore: AnnotateStyleStore,
    private val documentId: String,
) {
    val items: StateFlow<List<EpubAnnotation>> =
        (if (documentId.isEmpty()) emptyFlow() else repository.observe(documentId))
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _editor = MutableStateFlow<EpubAnnotationEditor?>(null)
    val editor: StateFlow<EpubAnnotationEditor?> = _editor.asStateFlow()

    private val _mode = MutableStateFlow(false)
    val mode: StateFlow<Boolean> = _mode.asStateFlow()

    // Readium has no strikethrough decoration, so a style picked in the PDF reader reads as underline here.
    val style: StateFlow<AnnotateStyle> = styleStore.style
        .map { if (it.type == AnnotationType.STRIKETHROUGH) it.copy(type = AnnotationType.UNDERLINE) else it }
        .stateIn(scope, SharingStarted.Eagerly, AnnotateStyle())

    fun toggleMode() {
        _mode.value = !_mode.value
    }

    fun setStyle(style: AnnotateStyle) {
        scope.launch { styleStore.save(style) }
    }

    /** Menu "Annotate": save with the chosen style at once, never asking for a color (Note still asks for text). */
    fun onSelection(locatorJson: String) {
        val chosen = style.value
        if (chosen.type == AnnotationType.NOTE) {
            startNew(locatorJson)
        } else {
            val content = AnnotationContent(chosen.type, chosen.color, null)
            scope.launch { repository.add(documentId, locatorJson, content) }
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

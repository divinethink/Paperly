package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotateStyleStore
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.EpubAnnotationRepository
import com.paperly.app.domain.reader.MatchRect
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Readium decorations have only highlight and underline; these PDF-only types read as underline in EPUB.
private val PDF_ONLY_TYPES = setOf(AnnotationType.STRIKETHROUGH, AnnotationType.SQUIGGLY)

/** [locatorJson] = the new selection (existing == null), or [existing] being edited. */
data class EpubAnnotationEditor(val locatorJson: String?, val existing: EpubAnnotation?)

/** A tapped mark; [anchor] = where it is on screen (the popup sits next to it). */
data class EpubTap(val id: String, val anchor: EpubAnchor)

/** Injected into the ViewModel (keeps its constructor small); builds the per-document controller. */
class EpubAnnotationControllerFactory @Inject constructor(
    private val repository: EpubAnnotationRepository,
    private val styleStore: AnnotateStyleStore,
) {
    /** [quick] = an in-memory repository for a quick-view book (no Library row to attach highlights to). */
    fun create(scope: CoroutineScope, documentId: String, quick: EpubAnnotationRepository? = null) =
        EpubAnnotationController(scope, quick ?: repository, styleStore, documentId)
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

    /** True once this session added, edited or deleted a mark: the book file then needs the new marks. */
    @Volatile
    var changed: Boolean = false
        private set

    private val _editor = MutableStateFlow<EpubAnnotationEditor?>(null)
    val editor: StateFlow<EpubAnnotationEditor?> = _editor.asStateFlow()

    private val _tapped = MutableStateFlow<EpubTap?>(null)
    val tapped: StateFlow<EpubTap?> = _tapped.asStateFlow()

    private val _mode = MutableStateFlow(false)
    val mode: StateFlow<Boolean> = _mode.asStateFlow()

    // Readium has no strikethrough/squiggly decoration, so such a style picked in the PDF reader reads as underline.
    val style: StateFlow<AnnotateStyle> = styleStore.style
        .map { if (it.type in PDF_ONLY_TYPES) it.copy(type = AnnotationType.UNDERLINE) else it }
        .stateIn(scope, SharingStarted.Eagerly, AnnotateStyle())

    fun toggleMode() {
        _mode.value = !_mode.value
    }

    fun setStyle(style: AnnotateStyle) {
        scope.launch { styleStore.save(style) }
    }

    /** Selection bar: save [type] with the chosen colour at once (Note still asks for its text). */
    fun markSelection(locatorJson: String, type: AnnotationType) {
        if (type == AnnotationType.NOTE) {
            startNew(locatorJson)
        } else {
            val content = AnnotationContent(type, style.value.color, null)
            changed = true
            scope.launch { repository.add(documentId, locatorJson, content) }
        }
    }

    /** A mark was tapped: popup next to it, or (no position known) the full edit dialog. */
    fun onTapped(id: String, anchor: EpubAnchor?) {
        if (anchor == null) startEdit(id) else _tapped.value = EpubTap(id, anchor)
    }

    /** Closes the tap popup; true when one was open. */
    fun dismissTap(): Boolean {
        val wasOpen = _tapped.value != null
        _tapped.value = null
        return wasOpen
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
        changed = true
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
        changed = true
        scope.launch { repository.delete(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Adapter so the PDF annotation dialog can edit an EPUB annotation (page/rect are unused by the dialog). */
internal fun EpubAnnotation.toDialogModel() =
    Annotation(id, 0, type, MatchRect(0f, 0f, 0f, 0f), noteText, color)

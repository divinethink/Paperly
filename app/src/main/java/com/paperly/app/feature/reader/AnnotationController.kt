package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotateStyleStore
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationRepository
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect
import com.paperly.app.domain.reader.isFileMark
import com.paperly.app.domain.reader.lookFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the annotation dialog edits: a new annotation on [page] made of [rects] (existing == null; [rect] is the
 * first part) or an [existing] annotation.
 */
data class AnnotationEditor(
    val page: Int,
    val rect: MatchRect,
    val existing: Annotation?,
    val rects: List<MatchRect> = listOf(rect),
)

/**
 * Parts drawn on one [page] but not yet saved; committed together as one annotation. [editing] = instead the box of
 * a saved mark picked for moving / resizing (see [EditingMark]); nothing is committed from it.
 */
data class PendingParts(val page: Int, val rects: List<MatchRect>, val editing: Boolean = false)

/** Reader-side annotation state for one document; persistence goes through [AnnotationRepository]. */
@Suppress("TooManyFunctions") // small state handlers for pending parts, picked marks, selection marks, saved items
class AnnotationController(
    private val scope: CoroutineScope,
    private val repository: AnnotationRepository,
    private val styleStore: AnnotateStyleStore,
    private val documentId: String,
) {
    val items: StateFlow<List<Annotation>> =
        (if (documentId.isEmpty()) emptyFlow() else repository.observe(documentId))
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val annotateOn = MutableStateFlow(false)

    /** Annotate mode: one-finger drag draws an area (page scroll is off); off = normal reading. */
    val annotateMode: StateFlow<Boolean> = annotateOn.asStateFlow()

    private val pendingState = MutableStateFlow<PendingParts?>(null)

    /** Unsaved parts of the annotation being built (one page only); empty = null. Not persisted. */
    val pending: StateFlow<PendingParts?> = pendingState.asStateFlow()

    private val boxOn = MutableStateFlow(false)

    /** Image pages, annotate mode: a drag draws one resizable box with the floating mark menu (off = direct marks). */
    val boxMode: StateFlow<Boolean> = boxOn.asStateFlow()

    private val editingState = MutableStateFlow<EditingMark?>(null)

    /** The saved mark picked for editing (tap on it, not in annotate mode); null = none. */
    val editing: StateFlow<EditingMark?> = editingState.asStateFlow()

    fun toggleMode() {
        cancelPending() // also drops a quick box (long-press on an image page) or a picked mark
        boxOn.value = false
        annotateOn.update { !it }
    }

    fun toggleBox() {
        cancelPending()
        boxOn.update { !it }
    }

    /**
     * Picks saved [mark]: its box (with handles) becomes the only pending part of its page. False = not pickable
     * (annotate mode, or a mark that already lives in a Quick-view file, which is never rewritten): use the dialog.
     */
    fun beginEdit(mark: Annotation): Boolean {
        val frame = frameOf(mark.rects)
        if (annotateOn.value || frame == null || isFileMark(mark.id)) return false
        editingState.value = EditingMark(mark.id, mark.page, mark.rects, frame)
        pendingState.value = PendingParts(mark.page, listOf(frame), editing = true)
        return true
    }

    /**
     * A drag of the picked mark's box ended: the mark's parts follow the box (stored; the PDF file is updated
     * later).
     */
    fun finishEditDrag() {
        val edit = editingState.value ?: return
        val now = pendingState.value?.takeIf { it.editing }?.rects?.singleOrNull() ?: return
        if (now == edit.frame) return
        val moved = remapRects(edit.rects, edit.frame, now)
        editingState.value = edit.copy(rects = moved, frame = now)
        scope.launch { repository.setRects(edit.id, moved) }
    }

    /** Type + color chosen in the pencil strip; persisted, so no per-annotation color prompt. */
    val style: StateFlow<AnnotateStyle> =
        styleStore.style.stateIn(scope, SharingStarted.Eagerly, AnnotateStyle())

    /** Each tool's own remembered colour and thickness. */
    val tools: StateFlow<Map<AnnotationType, AnnotateStyle>> =
        styleStore.tools.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** [type] with the colour and thickness last used for that tool. */
    fun styleFor(type: AnnotationType): AnnotateStyle = tools.value.lookFor(type)

    fun setStyle(style: AnnotateStyle) {
        scope.launch { styleStore.save(style) }
    }

    /** Adds a part; ignored on another page than the pending one (finish or cancel that first) or beyond the cap. */
    fun addPending(page: Int, rect: MatchRect) {
        pendingState.update { cur ->
            when {
                cur == null -> PendingParts(page, listOf(rect))
                cur.page != page || cur.rects.size >= MAX_PARTS -> cur
                else -> cur.copy(rects = cur.rects + rect)
            }
        }
    }

    fun changePending(index: Int, rect: MatchRect) {
        pendingState.update { cur ->
            cur?.takeIf { index in it.rects.indices }
                ?.let { p -> p.copy(rects = p.rects.mapIndexed { i, r -> if (i == index) rect else r }) }
                ?: cur
        }
    }

    fun removePending(index: Int) {
        pendingState.update { cur ->
            cur?.copy(rects = cur.rects.filterIndexed { i, _ -> i != index })?.takeIf { it.rects.isNotEmpty() }
        }
        if (pendingState.value == null) editingState.value = null // the red cross also lets go of a picked mark
    }

    fun cancelPending() {
        editingState.value = null
        pendingState.value = null
    }

    /** Saves the pending parts as one annotation; a Note returns an editor (pending stays until its text is saved). */
    fun commitPending(chosen: AnnotateStyle = style.value): AnnotationEditor? {
        val parts = pendingState.value
        return when {
            parts == null -> null
            chosen.type == AnnotationType.NOTE -> AnnotationEditor(parts.page, parts.rects.first(), null, parts.rects)
            else -> {
                val content = AnnotationContent(chosen.type, chosen.color, null, chosen.stroke)
                scope.launch { repository.add(documentId, parts.page, parts.rects.first(), content, parts.rects) }
                pendingState.value = null
                null
            }
        }
    }

    /**
     * Saves selected text as marks in the chosen style (no dialog): one annotation per page part, since an annotation
     * lives on one page. A Note returns an editor for the first part instead. Independent of the annotate-mode
     * pending parts. [onSaved] gets the new annotations' ids (same order as [parts]) once all are stored, so the
     * selection can stay and restyle those same marks ([restyleMark]) instead of adding new ones.
     */
    fun annotateSelection(
        parts: List<SelectionPart>,
        type: AnnotationType? = null,
        onSaved: (List<String>) -> Unit = {},
    ): AnnotationEditor? {
        val pieces = parts.map { it.copy(rects = it.rects.take(MAX_PARTS)) }.filter { it.rects.isNotEmpty() }
        val chosen = type?.let(::styleFor) ?: style.value
        return when {
            pieces.isEmpty() -> null
            chosen.type == AnnotationType.NOTE -> pieces.first().let {
                AnnotationEditor(it.page, it.rects.first(), null, it.rects)
            }
            else -> {
                val content = AnnotationContent(chosen.type, chosen.color, null, chosen.stroke)
                scope.launch {
                    val known = items.value.mapTo(HashSet()) { it.id }
                    pieces.forEach { repository.add(documentId, it.page, it.rects.first(), content, it.rects) }
                    fun fresh(list: List<Annotation>, page: Int) =
                        list.firstOrNull { it.id !in known && it.page == page }
                    val saved = withTimeoutOrNull(SAVED_WAIT_MS) {
                        items.first { list -> pieces.all { fresh(list, it.page) != null } }
                    }
                    saved?.let { list -> onSaved(pieces.mapNotNull { fresh(list, it.page)?.id }) }
                }
                null
            }
        }
    }

    /** Changes the stored look of mark [id] (type, colour, thickness) in place; no-op when it no longer exists. */
    fun restyleMark(id: String, change: (AnnotationContent) -> AnnotationContent) {
        val cur = items.value.firstOrNull { it.id == id } ?: return
        val base = AnnotationContent(cur.type, cur.color, cur.noteText, cur.stroke)
        scope.launch { repository.update(id, change(base)) }
    }

    fun save(editor: AnnotationEditor, content: AnnotationContent) {
        scope.launch {
            val existing = editor.existing
            if (existing == null) {
                repository.add(documentId, editor.page, editor.rect, content, editor.rects)
                pendingState.value = null
            } else {
                repository.update(existing.id, content)
                if (editingState.value?.id == existing.id) cancelPending()
            }
        }
    }

    fun delete(id: String) {
        if (editingState.value?.id == id) cancelPending()
        scope.launch { repository.delete(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SAVED_WAIT_MS = 2_000L // how long a fresh mark may take to show up in [items]
        const val MAX_PARTS = 50 // same cap the repository enforces
    }
}

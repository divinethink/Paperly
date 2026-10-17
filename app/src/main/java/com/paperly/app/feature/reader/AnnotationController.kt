package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotateStyleStore
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationRepository
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect
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

/** Parts drawn on one [page] but not yet saved; committed together as one annotation. */
data class PendingParts(val page: Int, val rects: List<MatchRect>)

/** Reader-side annotation state for one document; persistence goes through [AnnotationRepository]. */
@Suppress("TooManyFunctions") // small state handlers for pending parts, selection marks and saved items
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

    fun toggleMode() {
        pendingState.value = null // also drops a quick box (long-press on an image page) when entering annotate mode
        annotateOn.update { !it }
    }

    /** Type + color chosen in the pencil strip; persisted, so no per-annotation color prompt. */
    val style: StateFlow<AnnotateStyle> =
        styleStore.style.stateIn(scope, SharingStarted.Eagerly, AnnotateStyle())

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
    }

    fun cancelPending() {
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
     * Saves selected text lines as one annotation in the chosen style (no dialog); a Note returns an editor instead.
     * Independent of the annotate-mode pending parts. [onSaved] gets the new annotation's id once it is stored, so the
     * selection can stay and restyle that same mark ([restyleMark]) instead of adding a second one.
     */
    fun annotateSelection(
        page: Int,
        rects: List<MatchRect>,
        type: AnnotationType? = null,
        onSaved: (String) -> Unit = {},
    ): AnnotationEditor? {
        val parts = rects.take(MAX_PARTS)
        val chosen = type?.let { style.value.copy(type = it) } ?: style.value
        return when {
            parts.isEmpty() -> null
            chosen.type == AnnotationType.NOTE -> AnnotationEditor(page, parts.first(), null, parts)
            else -> {
                val content = AnnotationContent(chosen.type, chosen.color, null, chosen.stroke)
                scope.launch {
                    val known = items.value.mapTo(HashSet()) { it.id }
                    repository.add(documentId, page, parts.first(), content, parts)
                    val saved = withTimeoutOrNull(SAVED_WAIT_MS) {
                        items.first { list -> list.any { it.id !in known && it.page == page } }
                    }
                    saved?.firstOrNull { it.id !in known && it.page == page }?.let { onSaved(it.id) }
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
            }
        }
    }

    fun delete(id: String) {
        scope.launch { repository.delete(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SAVED_WAIT_MS = 2_000L // how long a fresh mark may take to show up in [items]
        const val MAX_PARTS = 50 // same cap the repository enforces
    }
}

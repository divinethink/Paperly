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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        if (annotateOn.value) pendingState.value = null
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
    fun commitPending(): AnnotationEditor? {
        val parts = pendingState.value
        val chosen = style.value
        return when {
            parts == null -> null
            chosen.type == AnnotationType.NOTE -> AnnotationEditor(parts.page, parts.rects.first(), null, parts.rects)
            else -> {
                val content = AnnotationContent(chosen.type, chosen.color, null)
                scope.launch { repository.add(documentId, parts.page, parts.rects.first(), content, parts.rects) }
                pendingState.value = null
                null
            }
        }
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
        const val MAX_PARTS = 50 // same cap the repository enforces
    }
}

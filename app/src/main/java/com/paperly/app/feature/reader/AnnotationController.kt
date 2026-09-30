package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationRepository
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

/** What the annotation dialog edits: a new [rect] on [page] (existing == null) or an [existing] annotation. */
data class AnnotationEditor(val page: Int, val rect: MatchRect, val existing: Annotation?)

/** Reader-side annotation state for one document; persistence goes through [AnnotationRepository]. */
class AnnotationController(
    private val scope: CoroutineScope,
    private val repository: AnnotationRepository,
    private val documentId: String,
) {
    val items: StateFlow<List<Annotation>> =
        (if (documentId.isEmpty()) emptyFlow() else repository.observe(documentId))
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val annotateOn = MutableStateFlow(false)

    /** Annotate mode: one-finger drag draws an area (page scroll is off); off = normal reading. */
    val annotateMode: StateFlow<Boolean> = annotateOn.asStateFlow()

    fun toggleMode() = annotateOn.update { !it }

    fun save(editor: AnnotationEditor, content: AnnotationContent) {
        scope.launch {
            val existing = editor.existing
            if (existing == null) {
                repository.add(documentId, editor.page, editor.rect, content)
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
    }
}

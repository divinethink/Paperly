package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationRepository
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
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

    fun save(editor: AnnotationEditor, type: AnnotationType, note: String?) {
        scope.launch {
            val existing = editor.existing
            if (existing == null) {
                repository.add(documentId, editor.page, type, editor.rect, note)
            } else {
                repository.update(existing.id, type, note)
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

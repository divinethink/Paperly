package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationRepository
import com.paperly.app.domain.reader.MatchRect
import com.paperly.app.domain.reader.ReaderStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_PARTS = 50

/** Quick view: annotations kept in memory only (no Library row to attach them to); copied on "Save to Library". */
internal class QuickAnnotationRepository : AnnotationRepository {
    private val state = MutableStateFlow<List<Annotation>>(emptyList())
    private var seq = 0

    override fun observe(documentId: String): Flow<List<Annotation>> = state

    override suspend fun add(
        documentId: String,
        page: Int,
        rect: MatchRect,
        content: AnnotationContent,
        rects: List<MatchRect>,
    ) {
        val parts = rects.ifEmpty { listOf(rect) }.take(MAX_PARTS)
        val box = MatchRect(
            parts.minOf { it.left },
            parts.minOf { it.top },
            parts.maxOf { it.right },
            parts.maxOf { it.bottom },
        )
        val item = Annotation("quick-${seq++}", page, content.type, box, content.noteText, content.color, parts)
        state.update { it + item }
    }

    override suspend fun update(id: String, content: AnnotationContent) {
        state.update { list ->
            list.map { a ->
                if (a.id != id) a else a.copy(type = content.type, color = content.color, noteText = content.noteText)
            }
        }
    }

    override suspend fun delete(id: String) {
        state.update { list -> list.filter { it.id != id } }
    }

    fun snapshot(): List<Annotation> = state.value
}

/** In-memory state of one quick-view PDF: highlights, bookmarks and the page being read. */
internal class QuickSession {
    val annotations = QuickAnnotationRepository()
    private val marks = MutableStateFlow<Set<Int>>(emptySet())
    val bookmarks: StateFlow<Set<Int>> = marks.asStateFlow()

    @Volatile
    var page: Int = 0

    fun toggleBookmark(page: Int) {
        marks.update { if (page in it) it - page else it + page }
    }

    /**
     * Copies the session into a Library document. [fresh] = the document was just imported, so its bookmarks and
     * position are ours to set; an already-existing copy only gets the new highlights (nothing of its own is touched).
     */
    suspend fun commitTo(
        documentId: String,
        annotationRepository: AnnotationRepository,
        readerState: ReaderStateRepository,
        pageCount: Int,
        fresh: Boolean,
    ) {
        annotations.snapshot().forEach { a ->
            val content = AnnotationContent(a.type, a.color, a.noteText)
            annotationRepository.add(documentId, a.page, a.rect, content, a.rects)
        }
        if (!fresh) return
        marks.value.forEach { readerState.toggleBookmark(documentId, it) }
        if (pageCount > 0) readerState.saveProgress(documentId, page.coerceIn(0, pageCount - 1), pageCount)
    }
}

package com.paperly.app.feature.reader

import com.paperly.app.core.file.QuickMarks
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.EpubAnnotationRepository
import com.paperly.app.domain.reader.ReaderStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.readium.r2.shared.publication.Locator

/** Quick view EPUB: highlights kept in memory only (no Library row yet); copied on "Save to Library". */
internal class QuickEpubAnnotationRepository : EpubAnnotationRepository {
    private val state = MutableStateFlow<List<EpubAnnotation>>(emptyList())
    private var seq = 0

    override fun observe(documentId: String): Flow<List<EpubAnnotation>> = state

    override suspend fun add(documentId: String, locatorJson: String, content: AnnotationContent) {
        val item = EpubAnnotation(
            "quick-${seq++}",
            locatorJson,
            content.type,
            content.color,
            content.noteText,
            content.stroke,
        )
        state.update { it + item }
    }

    override suspend fun update(id: String, content: AnnotationContent) {
        state.update { list ->
            list.map { a ->
                if (a.id != id) {
                    a
                } else {
                    a.copy(
                        type = content.type,
                        color = content.color,
                        noteText = content.noteText,
                        stroke = content.stroke,
                    )
                }
            }
        }
    }

    override suspend fun delete(id: String) {
        state.update { list -> list.filter { it.id != id } }
    }

    fun snapshot(): List<EpubAnnotation> = state.value
}

/** In-memory state of one quick-view EPUB: highlights and bookmarks (position = the Reader's latest locator). */
internal class QuickEpubSession {
    val annotations = QuickEpubAnnotationRepository()
    private val marks = MutableStateFlow<List<String>>(emptyList())
    val bookmarks: StateFlow<List<String>> = marks.asStateFlow()

    /** Brings back what was remembered for this file (see `QuickMarksStore`); call before anything else is added. */
    suspend fun restore(saved: QuickMarks) {
        saved.annotations.forEach { a ->
            annotations.add("", a.locatorJson, AnnotationContent(a.type, a.color, a.noteText))
        }
        marks.value = saved.bookmarks
    }

    /** Everything to remember now; [locator] = the reading position as Locator JSON. */
    fun snapshot(locator: String?) = QuickMarks(annotations.snapshot(), marks.value, locator)

    /** [key] is a bookmark key (see `bookmarkKey`); toggling the same key removes it. */
    fun toggleBookmark(key: String) {
        marks.update { if (key in it) it - key else it + key }
    }

    /** Copies into a Library document; [fresh] = just imported, so its bookmarks and position are ours to set. */
    suspend fun commitTo(
        documentId: String,
        annotationRepository: EpubAnnotationRepository,
        readerState: ReaderStateRepository,
        latest: Locator?,
        fresh: Boolean,
    ) {
        annotations.snapshot().forEach { a ->
            annotationRepository.add(documentId, a.locatorJson, AnnotationContent(a.type, a.color, a.noteText))
        }
        if (!fresh) return
        marks.value.forEach { readerState.toggleBookmarkLocator(documentId, it) }
        latest?.let { readerState.saveLocator(documentId, encodeLocator(it), progressOf(it)) }
    }
}

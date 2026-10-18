package com.paperly.app.feature.reader

import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.QuickMarksStore
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.core.intent.ReaderRedirects
import com.paperly.app.domain.reader.EpubAnnotationRepository
import javax.inject.Inject

/** File / quick-view collaborators of [EpubReaderViewModel] (keeps its constructor small). */
class EpubQuickDeps @Inject constructor(
    val fileStore: DocumentFileStore,
    val quickStore: QuickViewStore,
    val quickMarks: QuickMarksStore,
    val redirects: ReaderRedirects,
    val annotationRepository: EpubAnnotationRepository,
)

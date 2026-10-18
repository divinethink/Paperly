package com.paperly.app.feature.reader

import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.QuickMarksStore
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.core.intent.ReaderRedirects
import com.paperly.app.domain.reader.EpubAnnotationRepository
import com.paperly.app.domain.reader.EpubHighlightFileSync
import com.paperly.app.domain.reader.QuickEpubWriteBack
import javax.inject.Inject

/** Writes into the EPUB file: Library sync (Reader closed) and the quick-view copy / replace. */
class EpubFileOps @Inject constructor(
    val fileSync: EpubHighlightFileSync,
    val quickWrite: QuickEpubWriteBack,
)

/** File / quick-view collaborators of [EpubReaderViewModel] (keeps its constructor small). */
class EpubQuickDeps @Inject constructor(
    val fileStore: DocumentFileStore,
    val quickStore: QuickViewStore,
    val quickMarks: QuickMarksStore,
    val redirects: ReaderRedirects,
    val annotationRepository: EpubAnnotationRepository,
    val fileOps: EpubFileOps,
)

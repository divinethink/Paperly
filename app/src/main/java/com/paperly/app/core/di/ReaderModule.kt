package com.paperly.app.core.di

import com.paperly.app.data.reader.EnginePdfOpenCheck
import com.paperly.app.data.reader.EpubFileMarkWriter
import com.paperly.app.data.reader.EpubHighlightFileSyncImpl
import com.paperly.app.data.reader.PdfBoxAnnotationReader
import com.paperly.app.data.reader.PdfBoxAnnotationWriter
import com.paperly.app.data.reader.PdfHighlightFileSyncImpl
import com.paperly.app.data.reader.PdfReaderEngine
import com.paperly.app.data.reader.QuickEpubWriteBackImpl
import com.paperly.app.data.reader.QuickPdfWriteBackImpl
import com.paperly.app.data.reader.ReadiumEpubOpenCheck
import com.paperly.app.domain.reader.EpubHighlightFileSync
import com.paperly.app.domain.reader.EpubMarkWriter
import com.paperly.app.domain.reader.EpubOpenCheck
import com.paperly.app.domain.reader.PdfAnnotationReader
import com.paperly.app.domain.reader.PdfAnnotationWriter
import com.paperly.app.domain.reader.PdfHighlightFileSync
import com.paperly.app.domain.reader.PdfOpenCheck
import com.paperly.app.domain.reader.QuickEpubWriteBack
import com.paperly.app.domain.reader.QuickPdfWriteBack
import com.paperly.app.domain.reader.ReaderEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Unscoped on purpose: every `Provider<ReaderEngine>.get()` is a fresh engine for one open document. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderModule {
    @Binds
    abstract fun bindReaderEngine(impl: PdfReaderEngine): ReaderEngine

    @Binds
    abstract fun bindPdfAnnotationWriter(impl: PdfBoxAnnotationWriter): PdfAnnotationWriter

    @Binds
    abstract fun bindPdfAnnotationReader(impl: PdfBoxAnnotationReader): PdfAnnotationReader

    @Binds
    abstract fun bindPdfHighlightFileSync(impl: PdfHighlightFileSyncImpl): PdfHighlightFileSync

    @Binds
    abstract fun bindQuickPdfWriteBack(impl: QuickPdfWriteBackImpl): QuickPdfWriteBack

    @Binds
    abstract fun bindPdfOpenCheck(impl: EnginePdfOpenCheck): PdfOpenCheck

    @Binds
    abstract fun bindEpubMarkWriter(impl: EpubFileMarkWriter): EpubMarkWriter

    @Binds
    abstract fun bindEpubOpenCheck(impl: ReadiumEpubOpenCheck): EpubOpenCheck

    @Binds
    abstract fun bindEpubHighlightFileSync(impl: EpubHighlightFileSyncImpl): EpubHighlightFileSync

    @Binds
    abstract fun bindQuickEpubWriteBack(impl: QuickEpubWriteBackImpl): QuickEpubWriteBack
}

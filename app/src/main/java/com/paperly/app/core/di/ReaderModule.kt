package com.paperly.app.core.di

import com.paperly.app.data.reader.PdfReaderEngine
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
}

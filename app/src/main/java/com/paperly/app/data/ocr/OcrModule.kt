package com.paperly.app.data.ocr

import com.paperly.app.domain.ocr.PageOcr
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds
    abstract fun bindPageOcr(impl: TesseractPageOcr): PageOcr
}

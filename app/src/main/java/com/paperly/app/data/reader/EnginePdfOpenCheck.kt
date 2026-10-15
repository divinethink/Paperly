package com.paperly.app.data.reader

import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.reader.PdfOpenCheck
import com.paperly.app.domain.reader.ReaderEngine
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

/** Opens the finished temp PDF with a fresh viewer engine (the same one the Reader uses) and closes it again. */
class EnginePdfOpenCheck @Inject constructor(
    private val engineProvider: Provider<ReaderEngine>,
) : PdfOpenCheck {
    override suspend fun opens(file: File): Boolean {
        val engine = engineProvider.get()
        return try {
            engine.open(file) is OpenResult.Success
        } finally {
            engine.close()
        }
    }
}

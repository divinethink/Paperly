package com.paperly.app.data.reader

import android.net.Uri
import com.paperly.app.core.file.QuickSource
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfAnnotationWriter
import com.paperly.app.domain.reader.PdfOpenCheck
import com.paperly.app.domain.reader.PdfWriteResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Highlights -> temporary copy (safe writer) -> verified copy replaces the original. Null = nothing new to write. */
@Singleton
class QuickOriginalStep @Inject constructor(
    private val writer: PdfAnnotationWriter,
    private val openCheck: PdfOpenCheck,
    private val original: OriginalFileWriter,
) {
    suspend fun run(
        token: String,
        source: QuickSource,
        file: File,
        items: List<PdfAnnotationItem>,
    ): OriginalWriteResult? {
        // Ids made unique per session, so a later session on the same original never collides with these /NM keys.
        val unique = items.map { it.copy(id = "$token-${it.id}") }
        return when (val result = writer.write(file, unique) { openCheck.opens(it) }) {
            is PdfWriteResult.NothingToWrite -> null
            is PdfWriteResult.Written ->
                original.overwrite(Uri.parse(source.uri), source.checksum, file, result.checksum, token)
            else -> OriginalWriteResult.FAILED
        }
    }
}

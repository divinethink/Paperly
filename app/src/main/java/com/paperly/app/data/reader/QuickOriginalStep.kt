package com.paperly.app.data.reader

import android.net.Uri
import com.paperly.app.core.file.QuickSource
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfAnnotationReader
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
    private val reader: PdfAnnotationReader,
    private val openCheck: PdfOpenCheck,
    private val original: OriginalFileWriter,
    private val store: QuickViewStore,
) {
    suspend fun run(
        token: String,
        source: QuickSource,
        file: File,
        items: List<PdfAnnotationItem>,
        backupName: String = token,
    ): OriginalWriteResult? {
        // Ids made unique per session, so a later session on the same original never collides with these /NM keys.
        val unique = items.map { it.copy(id = "$token-${it.id}") }
        return when (val result = writer.write(file, unique) { openCheck.opens(it) }) {
            is PdfWriteResult.NothingToWrite -> null
            is PdfWriteResult.Written ->
                original.overwrite(Uri.parse(source.uri), source.checksum, file, result.checksum, backupName)
                    .also { if (it == OriginalWriteResult.WRITTEN) store.updateChecksum(token, result.checksum) }
            else -> OriginalWriteResult.FAILED
        }
    }

    /** Marks already inside [file] (written earlier by us or by another app); empty when none or unreadable. */
    suspend fun existingMarks(file: File): List<PdfAnnotationItem> = reader.read(file)

    /** The marks written into [target], a copy of [file] (which stays untouched). False = [target] is not usable. */
    suspend fun markedCopy(token: String, file: File, target: File, items: List<PdfAnnotationItem>): Boolean {
        if (runCatching { file.copyTo(target, overwrite = true) }.isFailure) return false
        val unique = items.map { it.copy(id = "$token-${it.id}") }
        val written = writer.write(target, unique) { openCheck.opens(it) } is PdfWriteResult.Written
        if (!written) target.delete()
        return written
    }
}

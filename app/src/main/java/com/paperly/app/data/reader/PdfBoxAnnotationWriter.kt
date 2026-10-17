package com.paperly.app.data.reader

import android.content.Context
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.PdfAnnotationColors
import com.paperly.app.domain.reader.PdfAnnotationGeometry
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfAnnotationWriter
import com.paperly.app.domain.reader.PdfPageBox
import com.paperly.app.domain.reader.PdfQuad
import com.paperly.app.domain.reader.PdfSkipReason
import com.paperly.app.domain.reader.PdfWriteResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PdfBox-Android (Apache-2.0, pure Java, no native code) implementation. Library-copy PDFs only (our own file).
 * Memory: PdfBox runs in temp-file-only mode; files above [MAX_BYTES] are skipped, and an OutOfMemoryError is caught
 * and reported as a skip (the highlight then stays in the app only). Nothing here touches the database.
 */
@Singleton
class PdfBoxAnnotationWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : PdfAnnotationWriter {

    private val backupDir: File by lazy { File(context.filesDir, BACKUP_DIR).apply { mkdirs() } }

    /** Pages + ids that were really added in the temp file. */
    private class Staged(val pages: Int, val ids: List<String>)

    private class Unsupported(val reason: PdfSkipReason) : Exception()

    override suspend fun write(
        file: File,
        items: List<PdfAnnotationItem>,
        verify: suspend (File) -> Boolean,
    ): PdfWriteResult = withContext(Dispatchers.IO) {
        when {
            !file.isFile -> PdfWriteResult.Skipped(PdfSkipReason.NOT_FOUND)
            file.length() > MAX_BYTES -> PdfWriteResult.Skipped(PdfSkipReason.TOO_LARGE)
            items.isEmpty() -> PdfWriteResult.NothingToWrite
            else -> guarded(file, items, verify)
        }
    }

    private suspend fun guarded(
        file: File,
        items: List<PdfAnnotationItem>,
        verify: suspend (File) -> Boolean,
    ): PdfWriteResult =
        File(file.parentFile, "${file.name}$TMP_SUFFIX").let { tmp ->
            try {
                PDFBoxResourceLoader.init(context)
                pruneBackups()
                attempt(file, tmp, items, verify)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Unsupported) {
                PdfWriteResult.Skipped(e.reason)
            } catch (ignored: InvalidPasswordException) {
                PdfWriteResult.Skipped(PdfSkipReason.ENCRYPTED)
            } catch (ignored: OutOfMemoryError) {
                PdfWriteResult.Skipped(PdfSkipReason.OUT_OF_MEMORY)
            } catch (e: Exception) {
                PdfWriteResult.Failed(e)
            } finally {
                tmp.delete() // no-op after a successful move; removes a half-written temp otherwise
            }
        }

    private suspend fun attempt(
        file: File,
        tmp: File,
        items: List<PdfAnnotationItem>,
        verify: suspend (File) -> Boolean,
    ): PdfWriteResult {
        val staged = annotateToTemp(file, tmp, items)
        if (staged.ids.isEmpty()) return PdfWriteResult.NothingToWrite
        if (!structureOk(tmp, staged) || !verify(tmp)) throw IOException("Verification of the new PDF failed")
        val checksum = sha256Hex(tmp)
        val size = tmp.length()
        file.copyTo(File(backupDir, "${file.name}$BACKUP_SUFFIX"), overwrite = true) // no backup = no replace
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return PdfWriteResult.Written(staged.ids.size, checksum, size)
    }

    private fun annotateToTemp(file: File, tmp: File, items: List<PdfAnnotationItem>): Staged =
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            if (doc.isEncrypted) throw Unsupported(PdfSkipReason.ENCRYPTED)
            val ids = items.filter { addOne(doc, it) }.map { it.id }
            if (ids.isNotEmpty()) doc.save(tmp)
            Staged(doc.numberOfPages, ids)
        }

    /** True if added. Skips (false): bad page, no rects, degenerate page box, or our `/NM` already on the page. */
    private fun addOne(doc: PDDocument, item: PdfAnnotationItem): Boolean {
        if (item.page !in 0 until doc.numberOfPages || item.rects.isEmpty()) return false
        val page = doc.getPage(item.page)
        val box = pageBox(page)
        val annots = annotsOf(page)
        if (box.width <= 0f || box.height <= 0f || hasName(annots, item.id)) return false
        val quads = item.rects.take(AnnotationRects.MAX_RECTS).map { PdfAnnotationGeometry.toQuad(it, box) }
        annots.add(build(item, quads))
        return true
    }

    private fun build(item: PdfAnnotationItem, quads: List<PdfQuad>): PDAnnotation {
        val bounds = PdfAnnotationGeometry.bounds(quads)
        val annotation: PDAnnotation = if (item.type == AnnotationType.NOTE) {
            PDAnnotationText().apply {
                setName(PDAnnotationText.NAME_NOTE)
                setRectangle(rectOf(bounds.minX, bounds.maxY - NOTE_ICON, bounds.minX + NOTE_ICON, bounds.maxY))
            }
        } else {
            PDAnnotationTextMarkup(subtypeOf(item.type)).apply {
                setQuadPoints(quads.flatMap { it.quadPoints().asList() }.toFloatArray())
                setRectangle(rectOf(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY))
            }
        }
        val color = item.color ?: AnnotationColor.defaultFor(item.type)
        annotation.setColor(PDColor(PdfAnnotationColors.rgb(color), PDDeviceRGB.INSTANCE))
        annotation.setAnnotationName(item.id)
        annotation.setPrinted(true)
        item.noteText?.takeIf { it.isNotBlank() }?.let { annotation.setContents(it) }
        try {
            annotation.constructAppearances() // helps viewers that need an appearance stream; the others redraw anyway
        } catch (ignored: Exception) {
            // the annotation stays valid without an appearance stream
        }
        return annotation
    }

    /** The temp file reopens, has the same page count, and carries every id we meant to add. */
    private fun structureOk(tmp: File, staged: Staged): Boolean =
        PDDocument.load(tmp, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val names = HashSet<String>()
            for (i in 0 until doc.numberOfPages) {
                val annots = doc.getPage(i).getCOSObject().getDictionaryObject(COSName.ANNOTS) as? COSArray ?: continue
                for (j in 0 until annots.size()) {
                    (annots.getObject(j) as? COSDictionary)?.getString(COSName.NM)?.let(names::add)
                }
            }
            doc.numberOfPages == staged.pages && names.containsAll(staged.ids)
        }

    private fun pruneBackups() {
        val cutoff = System.currentTimeMillis() - BACKUP_KEEP_MS
        backupDir.listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { it.delete() }
    }

    companion object {
        /** Above this the file is not touched (PdfBox memory). Raise only after a device test with a large PDF. */
        const val MAX_BYTES = 50L * 1024 * 1024
        const val BACKUP_DIR = "pdf_backups"
        private const val BACKUP_SUFFIX = ".bak"
        private const val BACKUP_KEEP_MS = 7L * 24 * 60 * 60 * 1000
        private const val TMP_SUFFIX = ".annot.tmp" // ends with ".tmp": OrphanFiles removes a stale one
        private const val NOTE_ICON = 18f
    }
}

private fun pageBox(page: PDPage): PdfPageBox {
    val crop = page.getCropBox()
    return PdfPageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, page.getRotation())
}

private fun annotsOf(page: PDPage): COSArray {
    val dict = page.getCOSObject()
    return dict.getDictionaryObject(COSName.ANNOTS) as? COSArray
        ?: COSArray().also { dict.setItem(COSName.ANNOTS, it) }
}

private fun hasName(annots: COSArray, id: String): Boolean =
    (0 until annots.size()).any { (annots.getObject(it) as? COSDictionary)?.getString(COSName.NM) == id }

private fun subtypeOf(type: AnnotationType): String = when (type) {
    AnnotationType.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
    AnnotationType.SQUIGGLY -> PDAnnotationTextMarkup.SUB_TYPE_SQUIGGLY
    AnnotationType.STRIKETHROUGH -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
    else -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
}

private fun rectOf(minX: Float, minY: Float, maxX: Float, maxY: Float) =
    PDRectangle(minX, minY, maxX - minX, maxY - minY)

private const val BUFFER_SIZE = 64 * 1024

private fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buf = ByteArray(BUFFER_SIZE)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

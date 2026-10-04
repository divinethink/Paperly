package com.paperly.app.domain.converter

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipFile
import javax.xml.parsers.ParserConfigurationException
import org.xml.sax.SAXException

/** [title] = fallback title/`dc:title`; [identifier] e.g. `urn:uuid:...`; [modifiedUtc] = `yyyy-MM-ddTHH:mm:ssZ`. */
data class ConvertOptions(val title: String, val identifier: String, val modifiedUtc: String)

data class ConversionReport(val chapters: Int, val images: Int, val imagesSkipped: Int, val flags: DocxFlags)

enum class ConversionError { NOT_A_DOCX, UNREADABLE, TOO_LARGE, EMPTY }

sealed interface ConversionOutcome {
    data class Success(val report: ConversionReport) : ConversionOutcome

    data class Failure(val error: ConversionError) : ConversionOutcome
}

private const val SAMPLE_CHARS = 5_000
private const val BENGALI_SHARE_INVERSE = 3 // Bengali letters >= 1/3 of all letters -> "bn"

/**
 * DOCX file -> EPUB 3 bytes on [out] (pure JVM, no dependencies). The source is only read. Nothing is written to
 * [out] unless the document has content; the caller owns [out] (write to a temp file, validate, then rename).
 */
object DocxToEpub {
    fun convert(docx: File, out: OutputStream, options: ConvertOptions): ConversionOutcome {
        val notDocx = ConversionOutcome.Failure(ConversionError.NOT_A_DOCX)
        val zip = runCatching { ZipFile(docx) }.getOrNull() ?: return notDocx
        return DocxPackage(zip).use { pkg ->
            if (pkg.hasBody) run(pkg, out, options) else notDocx
        }
    }

    private fun run(pkg: DocxPackage, out: OutputStream, options: ConvertOptions): ConversionOutcome =
        runCatching {
            pkg.verify()
            build(pkg, out, options)
        }.getOrElse { ConversionOutcome.Failure(errorOf(it)) }

    private fun build(pkg: DocxPackage, out: OutputStream, options: ConvertOptions): ConversionOutcome {
        val doc = DocxParser.parse(pkg)
        if (doc.blocks.isEmpty()) return ConversionOutcome.Failure(ConversionError.EMPTY)
        val meta = EpubMeta(options.title, languageOf(doc.blocks), options.identifier, options.modifiedUtc)
        val stats = EpubWriter.write(doc, meta, pkg, out)
        return ConversionOutcome.Success(ConversionReport(stats.chapters, stats.images, stats.imagesSkipped, doc.flags))
    }

    /** Anything unexpected (e.g. out of memory) is rethrown, never reported as a normal conversion failure. */
    private fun errorOf(t: Throwable): ConversionError = when (t) {
        is ConversionLimitException -> ConversionError.TOO_LARGE
        is IOException, is SAXException, is ParserConfigurationException -> ConversionError.UNREADABLE
        else -> throw t
    }

    private fun languageOf(blocks: List<Block>): String {
        var letters = 0
        var bengali = 0
        val sample = blocks.asSequence().flatMap { it.inlines.asSequence() }.filterIsInstance<Inline.Text>()
            .flatMap { it.text.asSequence() }.take(SAMPLE_CHARS)
        for (c in sample) {
            if (c.isLetter()) {
                letters++
                if (c in '\u0980'..'\u09FF') bengali++
            }
        }
        return if (letters > 0 && bengali * BENGALI_SHARE_INVERSE >= letters) "bn" else "en"
    }
}

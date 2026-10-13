package com.paperly.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.paperly.app.domain.ocr.PageOcr
import com.paperly.app.domain.reader.ReaderEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val RENDER_WIDTH = 2000
private val straightBarAfterBengali = Regex("(?<=[\\u0980-\\u09FF])(\\s?)\\|")

/** Tesseract often reads the Bengali danda (।) as a straight bar; only a bar right after Bengali text is fixed. */
private fun fixDanda(text: String) = text.replace(straightBarAfterBengali, "\$1\u0964")

/**
 * Tesseract (Bengali + English) behind [PageOcr]. One recogniser, one page at a time; text is cached by
 * [OcrPageCache], so clearing it only costs a re-read. The recogniser is created on first use.
 */
@Singleton
class TesseractPageOcr @Inject constructor(
    @ApplicationContext private val context: Context,
) : PageOcr {
    private val lock = Mutex()
    private val cache = OcrPageCache(context)
    private val ready = MutableStateFlow(OcrModel.isReady(context))
    override val modelReady: StateFlow<Boolean> = ready.asStateFlow()
    private var api: TessBaseAPI? = null

    @Volatile
    private var releaseWanted = false

    override suspend fun downloadModel(): Boolean {
        val ok = withContext(Dispatchers.IO) { OcrModel.download(context) }
        ready.value = OcrModel.isReady(context)
        return ok
    }

    override suspend fun pageText(documentId: String, engine: ReaderEngine, index: Int): String? =
        (cache.read(documentId, index) ?: recognise(documentId, engine, index))?.let(::fixDanda)

    override fun release() {
        if (lock.tryLock()) {
            try {
                drop()
            } finally {
                lock.unlock()
            }
        } else {
            releaseWanted = true
        }
    }

    private suspend fun recognise(documentId: String, engine: ReaderEngine, index: Int): String? {
        if (!ready.value) return null
        val text = lock.withLock {
            val result = runOcr(engine, index)
            if (releaseWanted) {
                drop()
                releaseWanted = false
            }
            result
        } ?: return null
        cache.write(documentId, index, text)
        return text
    }

    private suspend fun runOcr(engine: ReaderEngine, index: Int): String? = try {
        val bitmap = engine.renderPage(index, RENDER_WIDTH)?.copy(Bitmap.Config.ARGB_8888, false)
        if (bitmap == null) {
            null
        } else {
            withContext(Dispatchers.Default) { tess()?.let { read(it, bitmap) } }.also { bitmap.recycle() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null // native/engine failure: the page shows "not available", the Reader keeps working
    }

    private fun read(tess: TessBaseAPI, bitmap: Bitmap): String {
        tess.setImage(bitmap)
        return tess.getUTF8Text().orEmpty().trim()
    }

    private fun tess(): TessBaseAPI? {
        api?.let { return it }
        val created = TessBaseAPI()
        if (!created.init(OcrModel.dataDir(context).absolutePath, OcrModel.LANG_SPEC)) {
            created.recycle()
            return null
        }
        created.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        api = created
        return created
    }

    private fun drop() {
        api?.recycle()
        api = null
    }
}

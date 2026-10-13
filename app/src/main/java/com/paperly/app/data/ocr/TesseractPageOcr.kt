package com.paperly.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.paperly.app.domain.ocr.PageOcr
import com.paperly.app.domain.reader.ReaderEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
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
private const val CACHE_DIR = "ocr1" // bump when the recognition setup changes: old text is simply re-read

/**
 * Tesseract (Bengali + English) behind [PageOcr]. One recogniser, one page at a time; text is cached in the
 * OS-clearable cache dir, so clearing it only costs a re-read. The recogniser is created on first use.
 */
@Singleton
class TesseractPageOcr @Inject constructor(
    @ApplicationContext private val context: Context,
) : PageOcr {
    private val lock = Mutex()
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
        readCache(documentId, index) ?: recognise(documentId, engine, index)

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
        writeCache(documentId, index, text)
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

    private fun cacheFile(documentId: String, index: Int) = File(context.cacheDir, "$CACHE_DIR/$documentId/p$index.txt")

    private suspend fun readCache(documentId: String, index: Int): String? = withContext(Dispatchers.IO) {
        try {
            cacheFile(documentId, index).takeIf { it.isFile }?.readText()
        } catch (e: IOException) {
            null
        }
    }

    private suspend fun writeCache(documentId: String, index: Int, text: String) = withContext(Dispatchers.IO) {
        try {
            val target = cacheFile(documentId, index)
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, target.name + ".part")
            part.writeText(text)
            if (!part.renameTo(target)) part.delete()
            Unit
        } catch (e: IOException) {
            Unit // a missing cache entry only means the page is read again next time
        }
    }
}

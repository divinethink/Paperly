package com.paperly.app.spike

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import kotlin.concurrent.thread

private const val CORE_CLASS = "com.shockwave.pdfium.PdfiumCore"
private const val MAX_RENDER_WIDTH = 1600f
private const val RENDER_SCALE = 2f
private const val TEXT_PREVIEW_CHARS = 200
private const val BYTES_PER_MB = 1024.0 * 1024.0
private const val PAGE_16K = 16384L
private const val HEADER_BYTES = 8192
private const val ELF_MAGIC_0 = 0x7f
private const val ELF_CLASS_OFFSET = 4
private const val ELF_CLASS_64 = 2
private const val ELF_PHOFF = 32
private const val ELF_PHENTSIZE = 54
private const val ELF_PHNUM = 56
private const val ELF_HEADER_MIN = 64
private const val PHDR_ALIGN_OFFSET = 48
private const val PHDR_MIN_SIZE = 56
private const val PT_LOAD = 1
private const val PADDING_PX = 32

/**
 * Debug-only spike for the P2 PDF-library decision (Checklist §3 P2).
 * Probes io.github.oothp:pdfium-android by reflection so a missing/renamed API is reported instead of breaking the build.
 * Checks: (1) 16 KB ELF alignment of native libs in this APK, (2) open/render/text-extract of a picked PDF, with timing + native memory.
 */
class PdfSpikeActivity : ComponentActivity() {
    private lateinit var output: TextView
    private lateinit var preview: ImageView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runPdfTest(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply { setTextIsSelectable(true) }
        preview = ImageView(this).apply { adjustViewBounds = true }
        val pick = Button(this).apply {
            text = "PDF বাছাই করুন"
            setOnClickListener { picker.launch(arrayOf("application/pdf")) }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
            addView(pick)
            addView(output)
            addView(preview)
        }
        val scroll = ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        }
        setContentView(scroll)
        show("== 16 KB পরীক্ষা ==")
        show("Device: ${Build.MODEL}, page size: ${Os.sysconf(OsConstants._SC_PAGESIZE)}")
        checkAlignment()
    }

    private fun show(line: String) = runOnUiThread { output.append(line + "\n") }

    private fun checkAlignment() {
        thread {
            try {
                val abi = Build.SUPPORTED_ABIS.first()
                show("ABI: $abi")
                ZipFile(applicationInfo.sourceDir).use { zip ->
                    val libs = zip.entries().toList().filter { it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") }
                    if (libs.isEmpty()) show("⚠️ APK-তে $abi native lib পাওয়া যায়নি")
                    for (entry in libs) {
                        val header = ByteArray(HEADER_BYTES)
                        val read = zip.getInputStream(entry).use { readFully(it, header) }
                        show(describeLib(entry.name.substringAfterLast('/'), header.copyOf(read)))
                    }
                }
            } catch (e: Throwable) {
                show("❌ 16 KB পরীক্ষা ব্যর্থ: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun readFully(stream: java.io.InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = stream.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun describeLib(name: String, header: ByteArray): String {
        val isElf64 = header.size >= ELF_HEADER_MIN &&
            header[0].toInt() == ELF_MAGIC_0 &&
            header[ELF_CLASS_OFFSET].toInt() == ELF_CLASS_64
        if (!isElf64) return "⚠️ $name: ELF64 নয়, বাদ"
        val min = loadAlignments(header).minOrNull() ?: return "⚠️ $name: LOAD segment পাওয়া যায়নি"
        return (if (min >= PAGE_16K) "✅" else "❌") + " $name: min align = $min"
    }

    private fun loadAlignments(header: ByteArray): List<Long> {
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val phoff = buf.getLong(ELF_PHOFF).toInt()
        val phentsize = buf.getShort(ELF_PHENTSIZE).toInt()
        val phnum = buf.getShort(ELF_PHNUM).toInt()
        val result = mutableListOf<Long>()
        for (i in 0 until phnum) {
            val base = phoff + i * phentsize
            if (base < 0 || base + PHDR_MIN_SIZE > header.size) break
            if (buf.getInt(base) == PT_LOAD) result += buf.getLong(base + PHDR_ALIGN_OFFSET)
        }
        return result
    }

    private fun runPdfTest(uri: Uri) {
        show("\n== PDF পরীক্ষা (oothp pdfium-android) ==")
        thread {
            try {
                probe(uri)
            } catch (e: Throwable) {
                val cause = (e as? InvocationTargetException)?.cause ?: e
                show("❌ ব্যর্থ: ${cause.javaClass.simpleName}: ${cause.message}")
            }
        }
    }

    private fun call(cls: Class<*>, target: Any, name: String, vararg args: Any): Any? {
        val method = cls.methods.firstOrNull { it.name == name && it.parameterCount == args.size }
            ?: error("method নেই: $name/${args.size}")
        return method.invoke(target, *args)
    }

    private fun mb(bytes: Long) = "%.1f MB".format(bytes / BYTES_PER_MB)

    private fun probe(uri: Uri) {
        val coreClass = Class.forName(CORE_CLASS)
        val textMethods = coreClass.methods.map { it.name }.filter { it.contains("Text") }.distinct().sorted()
        show("Text-সংক্রান্ত method: $textMethods")
        val core = coreClass.getConstructor(Context::class.java).newInstance(this)
        val pfd = contentResolver.openFileDescriptor(uri, "r") ?: error("ফাইল খোলা যায়নি")
        pfd.use {
            val nativeBefore = Debug.getNativeHeapAllocatedSize()
            val openStart = SystemClock.elapsedRealtime()
            val doc = coreClass.getMethod("newDocument", ParcelFileDescriptor::class.java).invoke(core, pfd)!!
            val pages = call(coreClass, core, "getPageCount", doc) as Int
            show("✅ খোলা: ${SystemClock.elapsedRealtime() - openStart} ms, পৃষ্ঠা: $pages")

            call(coreClass, core, "openPage", doc, 0)
            val bitmap = renderFirstPage(coreClass, core, doc)
            show("Native heap: আগে ${mb(nativeBefore)}, render-এর পর ${mb(Debug.getNativeHeapAllocatedSize())}")
            runOnUiThread { preview.setImageBitmap(bitmap) }

            extractText(coreClass, core, doc)
            call(coreClass, core, "closeDocument", doc)
            show("বন্ধ করার পর native heap: ${mb(Debug.getNativeHeapAllocatedSize())}")
        }
    }

    private fun renderFirstPage(coreClass: Class<*>, core: Any, doc: Any): Bitmap {
        val w = call(coreClass, core, "getPageWidthPoint", doc, 0) as Int
        val h = call(coreClass, core, "getPageHeightPoint", doc, 0) as Int
        check(w > 0 && h > 0) { "পৃষ্ঠার মাপ অবৈধ: ${w}x$h" }
        val scale = minOf(RENDER_SCALE, MAX_RENDER_WIDTH / w)
        val bw = (w * scale).toInt()
        val bh = (h * scale).toInt()
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val renderStart = SystemClock.elapsedRealtime()
        call(coreClass, core, "renderPageBitmap", doc, bitmap, 0, 0, 0, bw, bh)
        show("✅ Render ${bw}x$bh: ${SystemClock.elapsedRealtime() - renderStart} ms")
        return bitmap
    }

    private fun extractText(coreClass: Class<*>, core: Any, doc: Any) {
        val hasText = coreClass.methods.any { it.name == "getPageText" && it.parameterCount == 2 }
        if (!hasText) {
            show("❌ getPageText(doc, page) এই library-তে নেই → text-extraction (Search/Reflow) সম্ভব না")
            return
        }
        val start = SystemClock.elapsedRealtime()
        val text = call(coreClass, core, "getPageText", doc, 0)?.toString().orEmpty()
        show("✅ getPageText: ${text.length} অক্ষর, ${SystemClock.elapsedRealtime() - start} ms")
        show("নমুনা: " + text.take(TEXT_PREVIEW_CHARS).replace('\n', ' '))
    }
}

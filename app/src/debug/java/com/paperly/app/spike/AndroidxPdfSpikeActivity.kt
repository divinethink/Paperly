package com.paperly.app.spike

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Size
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile
import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

private const val PKG = "androidx.pdf."
private const val MAX_RENDER_WIDTH = 1600f
private const val RENDER_SCALE = 2f
private const val TEXT_PAGES = 3
private const val SEARCH_PAGES = 50
private const val PREVIEW_CHARS = 120
private const val MIN_WORD = 3
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
 * Debug-only spike, round 2 (Checklist §3 P2): androidx.pdf:pdf-document-service (non-Compose part only).
 * Every androidx.pdf API is reached by reflection, so a renamed/missing API is reported on screen instead of breaking the build.
 * Suspend functions are called by hand with a stdlib Continuation (no kotlinx-coroutines compile dependency).
 * Checks: 16 KB ELF alignment, open/render timing, text extraction (getPageContent) and in-document search (searchDocument).
 * Note: rendering runs in a sandboxed service process, so the app-process native-heap numbers below are NOT the full memory cost.
 */
class AndroidxPdfSpikeActivity : ComponentActivity() {
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
        show("== Round-2: androidx.pdf (document-service) ==")
        show("Device: ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, page size: ${Os.sysconf(OsConstants._SC_PAGESIZE)}")
        checkAlignment()
    }

    private fun show(line: String) = runOnUiThread { output.append(line + "\n") }

    // ---------- 16 KB ELF alignment (same logic as round 1) ----------

    private fun checkAlignment() {
        thread {
            try {
                val abi = Build.SUPPORTED_ABIS.first()
                show("== 16 KB পরীক্ষা (ABI: $abi) ==")
                ZipFile(applicationInfo.sourceDir).use { zip ->
                    val libs = zip.entries().toList().filter { it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") }
                    for (entry in libs) {
                        val header = ByteArray(HEADER_BYTES)
                        val read = zip.getInputStream(entry).use { readFully(it, header) }
                        show(describeLib(entry.name.substringAfterLast('/'), header.copyOf(read)))
                    }
                }
                show("(শুধু APK-র ভেতরের .so; androidx.pdf মূলত system PdfRenderer ব্যবহার করে, তাই তালিকা ছোট হতে পারে)")
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

    // ---------- reflection helpers ----------

    private fun mb(bytes: Long) = "%.1f MB".format(bytes / BYTES_PER_MB)

    private fun names(cls: Class<*>) = cls.methods.map { it.name }.distinct().sorted()

    /** Calls a Kotlin `suspend` method by hand: appends our own Continuation and waits for the result. */
    private fun callSuspend(target: Any, method: Method, args: List<Any?>): Any? {
        val box = AtomicReference<Result<Any?>?>(null)
        val latch = CountDownLatch(1)
        val continuation = object : Continuation<Any?> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<Any?>) {
                box.set(result)
                latch.countDown()
            }
        }
        val direct = try {
            method.invoke(target, *(args + continuation).toTypedArray())
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        }
        if (direct !== COROUTINE_SUSPENDED) return direct
        latch.await()
        return checkNotNull(box.get()).getOrThrow()
    }

    private fun plainCall(target: Any, method: Method, args: List<Any?>): Any? = try {
        method.invoke(target, *args.toTypedArray())
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }

    /** First public suspend method [name] whose non-Continuation parameters are all handled by [pick]; returns method + args. */
    private fun findSuspend(cls: Class<*>, name: String, pick: (Class<*>) -> Pair<Boolean, Any?>): Pair<Method, List<Any?>>? {
        for (m in cls.methods.filter { it.name == name }) {
            val types = m.parameterTypes.toList()
            if (types.isEmpty() || !Continuation::class.java.isAssignableFrom(types.last())) continue
            val picked = types.dropLast(1).map(pick)
            if (picked.all { it.first }) return m to picked.map { it.second }
        }
        return null
    }

    private fun sig(cls: Class<*>, name: String) =
        cls.methods.filter { it.name == name }.joinToString(" | ") { m -> m.parameterTypes.joinToString(",") { it.simpleName } }

    private fun ioContext(): CoroutineContext = try {
        val cls = Class.forName("kotlinx.coroutines.Dispatchers")
        val getter = cls.getMethod("getIO")
        val receiver = if (Modifier.isStatic(getter.modifiers)) null else cls.getField("INSTANCE").get(null)
        getter.invoke(receiver) as CoroutineContext
    } catch (e: ReflectiveOperationException) {
        show("⚠️ Dispatchers.IO পাওয়া যায়নি (${e.javaClass.simpleName}), EmptyCoroutineContext ব্যবহার হচ্ছে")
        EmptyCoroutineContext
    }

    // ---------- the probe ----------

    private fun runPdfTest(uri: Uri) {
        show("\n== PDF পরীক্ষা (androidx.pdf) ==")
        thread {
            try {
                probe(uri)
            } catch (e: Throwable) {
                show("❌ ব্যর্থ: ${e.javaClass.name}: ${e.message}")
            }
        }
    }

    private fun probe(uri: Uri) {
        val loaderCls = Class.forName(PKG + "SandboxedPdfLoader")
        val loaderApi = Class.forName(PKG + "PdfLoader")
        val docApi = Class.forName(PKG + "PdfDocument")
        show("PdfDocument method: ${names(docApi)}")
        val ctor = loaderCls.constructors.firstOrNull { it.parameterCount == 2 && it.parameterTypes[0] == Context::class.java }
            ?: error("SandboxedPdfLoader(Context, CoroutineContext) constructor নেই")
        val loader = ctor.newInstance(this, ioContext())

        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        val openStart = SystemClock.elapsedRealtime()
        val opener = findSuspend(loaderApi, "openDocument") { t ->
            when (t) {
                Uri::class.java -> true to uri
                String::class.java -> true to null
                else -> false to null
            }
        } ?: error("openDocument(Uri, String) নেই: ${sig(loaderApi, "openDocument")}")
        val doc = callSuspend(loader, opener.first, opener.second) ?: error("openDocument null ফেরত দিয়েছে")
        val pages = plainCall(doc, docApi.getMethod("getPageCount"), emptyList()) as Int
        show("✅ খোলা: ${SystemClock.elapsedRealtime() - openStart} ms, পৃষ্ঠা: $pages")

        try {
            renderFirstPage(docApi, doc)
            show("App-process native heap: আগে ${mb(nativeBefore)}, পরে ${mb(Debug.getNativeHeapAllocatedSize())} (sandbox বাদে)")
            val firstText = extractText(docApi, doc, pages)
            search(docApi, doc, pages, firstText)
        } finally {
            plainCall(doc, docApi.getMethod("close"), emptyList())
            show("ডকুমেন্ট বন্ধ; native heap: ${mb(Debug.getNativeHeapAllocatedSize())}")
        }
    }

    private fun renderFirstPage(docApi: Class<*>, doc: Any) {
        val infoM = findSuspend(docApi, "getPageInfo") { t -> if (t == Int::class.java) true to 0 else false to null }
            ?: error("getPageInfo(int) নেই: ${sig(docApi, "getPageInfo")}")
        val info = callSuspend(doc, infoM.first, infoM.second) ?: error("PageInfo null")
        val w = (info.javaClass.getMethod("getWidth").invoke(info) as Number).toInt()
        val h = (info.javaClass.getMethod("getHeight").invoke(info) as Number).toInt()
        check(w > 0 && h > 0) { "পৃষ্ঠার মাপ অবৈধ: ${w}x$h" }
        val sourceM = docApi.getMethod("getPageBitmapSource", Int::class.javaPrimitiveType)
        val source = plainCall(doc, sourceM, listOf(0)) ?: error("BitmapSource null")
        val sourceApi = Class.forName(PKG + "PdfDocument\$BitmapSource")
        val scale = minOf(RENDER_SCALE, MAX_RENDER_WIDTH / w)
        val bw = (w * scale).toInt()
        val bh = (h * scale).toInt()
        val bitmapM = findSuspend(sourceApi, "getBitmap") { t ->
            when (t) {
                Size::class.java -> true to Size(bw, bh)
                Rect::class.java -> true to null
                else -> false to null
            }
        } ?: error("getBitmap(Size, Rect?) নেই: ${sig(sourceApi, "getBitmap")}")
        val start = SystemClock.elapsedRealtime()
        val bitmap = callSuspend(source, bitmapM.first, bitmapM.second) as? Bitmap ?: error("Bitmap পাওয়া যায়নি")
        show("✅ Render ${bitmap.width}x${bitmap.height}: ${SystemClock.elapsedRealtime() - start} ms (পৃষ্ঠার মাপ ${w}x$h pt)")
        runOnUiThread { preview.setImageBitmap(bitmap) }
        plainCall(source, sourceApi.getMethod("close"), emptyList())
    }

    private fun extractText(docApi: Class<*>, doc: Any, pages: Int): String {
        val contentM = findSuspend(docApi, "getPageContent") { t -> if (t == Int::class.java) true to 0 else false to null }
        if (contentM == null) {
            show("❌ getPageContent(int) নেই: ${sig(docApi, "getPageContent")} → text-extraction সম্ভব না")
            return ""
        }
        var firstText = ""
        for (page in 0 until minOf(pages, TEXT_PAGES)) {
            val start = SystemClock.elapsedRealtime()
            val content = callSuspend(doc, contentM.first, listOf(page))
            val ms = SystemClock.elapsedRealtime() - start
            if (content == null) {
                show("পৃষ্ঠা $page: content null, $ms ms")
                continue
            }
            if (page == 0) show("PdfPageContent method: ${names(content.javaClass)}")
            val text = pageText(content)
            if (page == 0) firstText = text
            val mark = if (text.isNotBlank()) "✅" else "⚠️"
            show("$mark পৃষ্ঠা $page: ${text.length} অক্ষর, $ms ms | ${text.take(PREVIEW_CHARS).replace('\n', ' ')}")
        }
        return firstText
    }

    private fun pageText(content: Any): String {
        val getter = content.javaClass.methods.firstOrNull { it.name == "getTextContents" && it.parameterCount == 0 } ?: return ""
        val items = getter.invoke(content) as? List<*> ?: return ""
        return items.filterNotNull().joinToString("\n") { item ->
            item.javaClass.methods.firstOrNull { it.name == "getText" && it.parameterCount == 0 }?.invoke(item)?.toString().orEmpty()
        }
    }

    private fun search(docApi: Class<*>, doc: Any, pages: Int, firstText: String) {
        val query = firstText.split(Regex("\\s+")).firstOrNull { it.length >= MIN_WORD } ?: run {
            show("⚠️ search বাদ: প্রথম পৃষ্ঠায় text নেই (scanned/image PDF হলে স্বাভাবিক)")
            return
        }
        val range = 0..(minOf(pages, SEARCH_PAGES) - 1)
        val searchM = findSuspend(docApi, "searchDocument") { t ->
            when (t) {
                String::class.java -> true to query
                IntRange::class.java -> true to range
                else -> false to null
            }
        }
        if (searchM == null) {
            show("❌ searchDocument(String, IntRange) নেই: ${sig(docApi, "searchDocument")}")
            return
        }
        val start = SystemClock.elapsedRealtime()
        val result = callSuspend(doc, searchM.first, searchM.second)
        val size = result?.javaClass?.methods?.firstOrNull { it.name == "size" && it.parameterCount == 0 }?.invoke(result)
        val ms = SystemClock.elapsedRealtime() - start
        show("✅ searchDocument(\"$query\", $range): $ms ms, মিল-সহ পৃষ্ঠা: ${size ?: result}")
    }
}

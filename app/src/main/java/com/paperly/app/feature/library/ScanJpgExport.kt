package com.paperly.app.feature.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.paperly.app.R
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.domain.scanner.ExportSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val JPEG_MIME = "image/jpeg"
private const val EMAIL_MIME = "message/rfc822"

/** An action on rendered pages: file name base + 0-based pages to use (null = every page) + image size. */
internal typealias PagesAction = (name: String, pages: Set<Int>?, size: ExportSize) -> Unit

/** Scan "JPG" choice: pages are rendered, then shared or saved at once (no banner, nothing added to the Library). */
internal class ScanJpgExport(
    val share: PagesAction,
    val save: PagesAction,
    val email: PagesAction,
)

/**
 * Share = the system share sheet, Email = e-mail apps only. Save = one page: "save as" file; several pages: a
 * folder pick.
 */
@Composable
internal fun rememberJpgExport(documentId: String, viewModel: ExportViewModel): ScanJpgExport {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf<List<File>>(emptyList()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JPEG_MIME)) { uri ->
        if (uri != null) scope.launch { toast(context, saveTo(context, ready.firstOrNull(), uri)) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch { toast(context, saveToFolder(context, ready, uri)) }
    }
    suspend fun prepare(name: String, pages: Set<Int>?, size: ExportSize): List<File>? {
        toast(context, R.string.export_jpg_preparing)
        val files = viewModel.renderJpgs(documentId, name, pages, size)
        if (files == null) toast(context, R.string.export_failed)
        return files
    }
    fun action(block: suspend (List<File>) -> Unit): PagesAction = { name, pages, size ->
        scope.launch { prepare(name, pages, size)?.let { block(it) } }
    }
    return ScanJpgExport(
        share = action { shareImages(context, it) },
        email = action { shareImages(context, it, EMAIL_MIME) },
        save = action { files ->
            ready = files
            if (files.size == 1) filePicker.launch(files[0].name) else folderPicker.launch(null)
        },
    )
}

internal fun shareImages(context: Context, files: List<File>, mime: String = JPEG_MIME) {
    val uris = ArrayList(files.map { FileProvider.getUriForFile(context, ExportFiles.authority(context), it) })
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    val clip = ClipData.newRawUri(files[0].name, uris[0])
    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    send.type = mime
    send.clipData = clip
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

/** Writes every page file into the folder the user picked; true only when all were written completely. */
@StringRes
private suspend fun saveToFolder(context: Context, files: List<File>, tree: Uri): Int = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val allWritten = files.isNotEmpty() && files.all { file ->
        runCatching {
            val target = DocumentsContract.createDocument(resolver, folder, JPEG_MIME, file.name)
            val written = target?.let { uri ->
                resolver.openOutputStream(uri, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            }
            written == file.length()
        }.getOrDefault(false)
    }
    if (allWritten) R.string.export_saved else R.string.export_failed
}

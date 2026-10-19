package com.paperly.app.feature.library

import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val JPEG_MIME = "image/jpeg"
private const val EMAIL_MIME = "message/rfc822"
private const val GALLERY_FOLDER = "Paperly"

/** An action on rendered pages: file name base + 0-based pages to use (null = every page). */
internal typealias PagesAction = (name: String, pages: Set<Int>?) -> Unit

/** Scan "JPG" choice: pages are rendered, then shared or saved at once (no banner, nothing added to the Library). */
internal class ScanJpgExport(
    val share: PagesAction,
    val save: PagesAction,
    val email: PagesAction,
    val gallery: PagesAction,
)

/**
 * Share = the system share sheet, Email = e-mail apps only, Gallery = Pictures/Paperly. Save = one page: "save as"
 * file; several pages: a folder pick.
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
    suspend fun prepare(name: String, pages: Set<Int>?): List<File>? {
        toast(context, R.string.export_jpg_preparing)
        val files = viewModel.renderJpgs(documentId, name, pages)
        if (files == null) toast(context, R.string.export_failed)
        return files
    }
    fun action(block: suspend (List<File>) -> Unit): PagesAction = { name, pages ->
        scope.launch { prepare(name, pages)?.let { block(it) } }
    }
    return ScanJpgExport(
        share = action { shareImages(context, it) },
        email = action { shareImages(context, it, EMAIL_MIME) },
        gallery = action { toast(context, saveToGallery(context, it)) },
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

/** Adds every page picture to the phone's Gallery (Pictures/Paperly); no permission needed on Android 10+. */
@StringRes
private suspend fun saveToGallery(context: Context, files: List<File>): Int = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val allSaved = files.isNotEmpty() && files.all { runCatching { insertImage(resolver, it) }.getOrDefault(false) }
    if (allSaved) R.string.export_saved_gallery else R.string.export_failed
}

private fun insertImage(resolver: ContentResolver, file: File): Boolean {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Images.Media.MIME_TYPE, JPEG_MIME)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_FOLDER")
        put(MediaStore.Images.Media.IS_PENDING, 1) // hidden from other apps until fully written
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    val written = resolver.openOutputStream(uri, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
    if (written != file.length()) {
        resolver.delete(uri, null, null)
        return false
    }
    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    return true
}

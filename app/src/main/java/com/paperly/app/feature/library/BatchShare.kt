package com.paperly.app.feature.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperly.app.R
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.domain.document.Document
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Most documents one share sheet takes (keeps the cache copy and the receiving app's work small). */
internal const val MAX_BATCH_SHARE = 20

private const val ANY_MIME = "*/*"

/** The usual document file names; a name that repeats gets " (2)", " (3)" ... before the extension. */
internal fun shareFileNames(docs: List<Document>): List<String> {
    val used = HashSet<String>()
    return docs.map { doc ->
        val name = ExportFiles.fileName(doc.title, doc.type)
        val base = ExportFiles.baseName(doc.title)
        val extension = name.removePrefix(base)
        var candidate = name
        var count = 1
        while (!used.add(candidate)) {
            count++
            candidate = "$base ($count)$extension"
        }
        candidate
    }
}

/**
 * Returns the share action for the Library's selection bar: (selected documents, called when done).
 * The scope lives as long as the bar, so the selection is cleared only after the share sheet has opened.
 */
@Composable
internal fun rememberBatchShare(viewModel: ExportViewModel = hiltViewModel()): (List<Document>, () -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { docs, onDone ->
        if (docs.size > MAX_BATCH_SHARE) {
            Toast.makeText(context, context.getString(R.string.batch_share_limit, MAX_BATCH_SHARE), Toast.LENGTH_LONG)
                .show()
        } else {
            toast(context, R.string.batch_share_preparing)
            scope.launch {
                shareDocuments(context, docs, viewModel::file)
                onDone()
            }
        }
    }
}

/** Copies the documents to the export cache (originals untouched) and opens one share sheet. */
private suspend fun shareDocuments(context: Context, docs: List<Document>, resolve: (String) -> File?) {
    val prepared = withContext(Dispatchers.IO) { prepareCopies(context, docs.zip(shareFileNames(docs)), resolve) }
    when {
        prepared.isEmpty() -> toast(context, R.string.export_failed)
        else -> {
            if (prepared.size < docs.size) toast(context, R.string.batch_share_partial)
            startShare(context, prepared)
        }
    }
}

/** Old exports are cleared once, then every copy is size-checked; one that fails is skipped, the rest go on. */
private fun prepareCopies(
    context: Context,
    items: List<Pair<Document, String>>,
    resolve: (String) -> File?,
): List<Pair<File, String>> {
    val dir = ExportFiles.exportsDir(context).apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return items.mapNotNull { (doc, name) ->
        val source = resolve(doc.id) ?: return@mapNotNull null
        val target = File(dir, name)
        runCatching {
            source.copyTo(target, overwrite = true)
            check(target.length() == source.length())
            target to ExportFiles.mimeType(doc.type)
        }.onFailure { target.delete() }.getOrNull()
    }
}

private fun startShare(context: Context, files: List<Pair<File, String>>) {
    val uris = ArrayList(files.map { FileProvider.getUriForFile(context, ExportFiles.authority(context), it.first) })
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    val clip = ClipData.newRawUri(files[0].first.name, uris[0])
    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    send.type = files.map { it.second }.distinct().singleOrNull() ?: ANY_MIME
    send.clipData = clip
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

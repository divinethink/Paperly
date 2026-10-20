package com.paperly.app.feature.library

import com.paperly.app.domain.document.Document
import com.paperly.app.domain.folder.Folder
import com.paperly.app.domain.folder.childrenOf
import com.paperly.app.domain.folder.subtreeIds

/** One folder row on the Scanned tab: name and how many documents of the tab's type are inside. */
class ScanFolderItem(val id: String, val name: String, val count: Int)

/**
 * [folders] are already the ones visible in this tab. Rows = the folders directly inside [parentId] (null = top
 * level); each count includes the documents of its subfolders and only counts documents of [docType].
 */
internal fun scanFolderItems(
    folders: List<Folder>,
    docs: List<Document>,
    docType: String,
    parentId: String? = null,
): List<ScanFolderItem> {
    val counts = docs.filter { it.type == docType }.mapNotNull { it.folderId }.groupingBy { it }.eachCount()
    return folders.childrenOf(parentId).map { f ->
        ScanFolderItem(f.id, f.name, folders.subtreeIds(f.id).sumOf { counts[it] ?: 0 })
    }
}

/** Notebloc-style root: folders are listed on top, so the list below shows only documents that are in no folder. */
internal fun List<Document>.withoutFolder(): List<Document> = filter { it.folderId == null }

private const val MAX_STACK_LAYERS = 2

/** Paper layers drawn behind a scan's cover: none for one page (or unknown), up to two for a longer document. */
internal fun scanStackLayers(pages: Int?): Int = ((pages ?: 1) - 1).coerceIn(0, MAX_STACK_LAYERS)

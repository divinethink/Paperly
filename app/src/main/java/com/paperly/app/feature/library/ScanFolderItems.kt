package com.paperly.app.feature.library

import com.paperly.app.domain.document.Document
import com.paperly.app.domain.folder.Folder

/** One folder row on the Scanned tab: name and how many documents of the tab's type are inside. */
internal class ScanFolderItem(val id: String, val name: String, val count: Int)

/** [folders] are already the ones visible in this tab; counts only include documents of [docType]. */
internal fun scanFolderItems(folders: List<Folder>, docs: List<Document>, docType: String): List<ScanFolderItem> {
    val counts = docs.filter { it.type == docType }.mapNotNull { it.folderId }.groupingBy { it }.eachCount()
    return folders.map { ScanFolderItem(it.id, it.name, counts[it.id] ?: 0) }
}

/** Notebloc-style root: folders are listed on top, so the list below shows only documents that are in no folder. */
internal fun List<Document>.withoutFolder(): List<Document> = filter { it.folderId == null }

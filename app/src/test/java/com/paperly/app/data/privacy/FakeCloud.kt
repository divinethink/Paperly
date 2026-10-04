package com.paperly.app.data.privacy

import com.paperly.app.domain.sync.CloudFile
import com.paperly.app.domain.sync.CloudInventory
import com.paperly.app.domain.sync.CloudListing
import com.paperly.app.domain.sync.DocumentMeta
import com.paperly.app.domain.sync.DownloadTarget
import com.paperly.app.domain.sync.FileSyncResult
import com.paperly.app.domain.sync.ReadingMeta
import com.paperly.app.domain.sync.RemoteDocument
import com.paperly.app.domain.sync.RemoteFileStore
import com.paperly.app.domain.sync.RemoteMetadataStore
import com.paperly.app.domain.sync.RemoteReadingStore
import com.paperly.app.domain.sync.RemoteResult
import java.io.File

/** One stateful stand-in for Drive + Firestore documents + Firestore reading positions. */
internal class FakeCloud : CloudInventory, RemoteMetadataStore, RemoteReadingStore, RemoteFileStore {
    val fileIds = mutableSetOf<String>()
    val docIds = mutableSetOf<String>()
    val readingIds = mutableSetOf<String>()
    var listing: ((Set<String>) -> CloudListing)? = null
    var fileDelete: RemoteResult = RemoteResult.OK
    var docDelete: RemoteResult = RemoteResult.OK
    var failList = false
    var ignoreReadingDelete = false

    fun seed(vararg ids: String) {
        fileIds += ids
        docIds += ids
        readingIds += ids
    }

    val isEmpty: Boolean get() = fileIds.isEmpty() && docIds.isEmpty() && readingIds.isEmpty()

    override suspend fun listOwned(): CloudListing =
        listing?.invoke(fileIds) ?: CloudListing.Complete(fileIds.map { CloudFile("f-$it", it, 1, 0) })

    override suspend fun delete(documentId: String): RemoteResult {
        if (fileDelete == RemoteResult.OK) fileIds -= documentId
        return fileDelete
    }

    override suspend fun deleteDocument(uid: String, documentId: String): RemoteResult {
        if (docDelete == RemoteResult.OK) docIds -= documentId
        return docDelete
    }

    override suspend fun deleteReading(uid: String, documentId: String): RemoteResult {
        if (!ignoreReadingDelete) readingIds -= documentId
        return RemoteResult.OK
    }

    override suspend fun listDocumentIds(uid: String): Set<String>? = if (failList) null else docIds.toSet()

    override suspend fun listReadingIds(uid: String): Set<String>? = readingIds.toSet()

    override suspend fun putDocument(uid: String, meta: DocumentMeta, baseUpdatedAt: Long?) = RemoteResult.OK

    override suspend fun fetchDocument(uid: String, documentId: String): RemoteDocument = RemoteDocument.Absent

    override suspend fun fetchDocumentsSince(uid: String, updatedAfter: Long): List<DocumentMeta>? = emptyList()

    override suspend fun putReading(uid: String, meta: ReadingMeta) = RemoteResult.OK

    override suspend fun fetchReadingSince(uid: String, updatedAfter: Long): List<ReadingMeta>? = emptyList()

    override suspend fun upload(documentId: String, file: File, sha256: String): FileSyncResult =
        throw UnsupportedOperationException()

    override suspend fun download(documentId: String, sha256: String, target: DownloadTarget): FileSyncResult =
        throw UnsupportedOperationException()

    override suspend fun forgetUploads() = Unit
}

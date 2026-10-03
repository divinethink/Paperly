package com.paperly.app.data.sync

import com.paperly.app.domain.sync.ReadingMeta
import com.paperly.app.domain.sync.ReadingPullDecision
import com.paperly.app.domain.sync.RemoteReadingStore
import com.paperly.app.domain.sync.RemoteResult

/** Stands in for Firestore: documentId -> cloud updatedAt, checked with the real newer-wins rule. */
internal class FakeReadingRemote(var result: RemoteResult = RemoteResult.OK) : RemoteReadingStore {
    val puts = mutableListOf<ReadingMeta>()
    val deletes = mutableListOf<String>()
    val cloud = mutableMapOf<String, Long>()

    /** What the cloud "holds" for pull tests; [failFetch] = offline. */
    val stored = mutableMapOf<String, ReadingMeta>()
    var failFetch = false

    override suspend fun putReading(uid: String, meta: ReadingMeta): RemoteResult {
        if (result != RemoteResult.OK) return result
        if (ReadingPullDecision.shouldPush(cloud[meta.documentId], meta.updatedAt)) {
            cloud[meta.documentId] = meta.updatedAt
            stored[meta.documentId] = meta
            puts += meta
        }
        return RemoteResult.OK
    }

    override suspend fun deleteReading(uid: String, documentId: String): RemoteResult {
        if (result == RemoteResult.OK) {
            deletes += documentId
            cloud.remove(documentId)
            stored.remove(documentId)
        }
        return result
    }

    override suspend fun fetchReadingSince(uid: String, updatedAfter: Long): List<ReadingMeta>? =
        if (failFetch) null else stored.values.filter { it.updatedAt > updatedAfter }
}

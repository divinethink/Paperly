package com.paperly.app.domain.sync

/**
 * Which cloud files belong to no document any more. Pure rules, no I/O. Deliberately cautious: a file is only an
 * orphan if its document id is unknown locally AND in the cloud metadata AND every copy is older than the grace
 * period (another device may have uploaded it moments ago and its metadata may not have arrived yet).
 */
object OrphanPolicy {
    const val GRACE_MS = 24L * 60 * 60 * 1000

    fun orphans(cloud: List<CloudFile>, local: Set<String>, remote: Set<String>, now: Long): List<CloudFile> =
        cloud.groupBy { it.documentId }
            .filter { (id, copies) ->
                id !in local && id !in remote && copies.all { it.createdAt > 0 && now - it.createdAt > GRACE_MS }
            }
            .values
            .flatten()
}

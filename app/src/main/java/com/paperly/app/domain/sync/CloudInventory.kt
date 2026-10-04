package com.paperly.app.domain.sync

/** One file of this app in the cloud store. [createdAt] = UTC epoch millis; 0 = unknown (never treated as old). */
data class CloudFile(val fileId: String, val documentId: String, val sizeBytes: Long, val createdAt: Long)

/** Complete = every file was listed. Anything else means "do not conclude anything from this". */
sealed interface CloudListing {
    data class Complete(val files: List<CloudFile>) : CloudListing

    data object NeedsConsent : CloudListing

    data object Failed : CloudListing
}

/** Lists the files this app keeps in the cloud store (only those that carry a document id). */
interface CloudInventory {
    suspend fun listOwned(): CloudListing
}

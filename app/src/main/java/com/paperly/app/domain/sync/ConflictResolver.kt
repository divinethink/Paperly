package com.paperly.app.domain.sync

/** The user's decision for a document whose cloud copy and local copy both changed. Both return false = try again. */
interface ConflictResolver {
    /** This device's values replace the cloud's. */
    suspend fun keepThisDevice(documentId: String): Boolean

    /** The cloud's values replace this device's (a document that is gone from the cloud goes to Trash, not away). */
    suspend fun keepCloud(documentId: String): Boolean
}

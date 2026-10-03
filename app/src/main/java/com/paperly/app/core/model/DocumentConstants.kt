package com.paperly.app.core.model

/** String + runtime validation (not enum) so new types/states are additive. Architecture §২.১, §২.৪. */
object DocumentType {
    const val PDF = "pdf"
    const val EPUB = "epub"
    const val SCANNED_PDF = "scanned-pdf"
    private val all = setOf(PDF, EPUB, SCANNED_PDF)
    fun isValid(value: String): Boolean = value in all
}

object StorageState {
    const val LOCAL_ONLY = "LOCAL_ONLY"
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val SYNCED = "SYNCED"
    const val RETRYING = "RETRYING"
    const val CONFLICT = "CONFLICT"

    /** Pulled from the cloud: the row exists, the file is still to be downloaded (P8-E2). */
    const val CLOUD_ONLY = "CLOUD_ONLY"
    private val all = setOf(LOCAL_ONLY, QUEUED, RUNNING, SYNCED, RETRYING, CONFLICT, CLOUD_ONLY)
    fun isValid(value: String): Boolean = value in all
}

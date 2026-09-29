package com.paperly.app.core.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class StoredFile(val path: String, val sizeBytes: Long, val checksum: String)

/**
 * File-abstraction layer. Originals are immutable: store() never overwrites an existing file.
 * Encryption for "Sensitive" documents (P1) plugs in behind this interface.
 */
interface DocumentFileStore {
    suspend fun store(source: Uri, documentId: String): StoredFile
    fun resolve(documentId: String): File?
    suspend fun delete(documentId: String): Boolean
}

/** App-private storage: copy to temp -> verify -> atomic rename. Partial files never appear as documents. */
@Singleton
class AppPrivateDocumentFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentFileStore {

    private val dir: File by lazy { File(context.filesDir, "documents").apply { mkdirs() } }

    override suspend fun store(source: Uri, documentId: String): StoredFile =
        withContext(Dispatchers.IO) {
            val target = File(dir, documentId)
            if (target.exists()) throw IOException("Document file already exists: $documentId")
            val tmp = File(dir, "$documentId.tmp")
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            try {
                val input = context.contentResolver.openInputStream(source)
                    ?: throw IOException("Cannot open source")
                input.use { ins ->
                    tmp.outputStream().buffered().use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            size += n
                        }
                    }
                }
                if (tmp.length() != size) throw IOException("Size verification failed")
                if (!tmp.renameTo(target)) throw IOException("Could not finalize file")
            } catch (e: Throwable) {
                tmp.delete()
                throw e
            }
            StoredFile(target.absolutePath, size, digest.digest().joinToString("") { "%02x".format(it) })
        }

    override fun resolve(documentId: String): File? =
        File(dir, documentId).takeIf { it.isFile }

    override suspend fun delete(documentId: String): Boolean =
        withContext(Dispatchers.IO) { File(dir, documentId).delete() }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}

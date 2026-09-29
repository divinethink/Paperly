package com.paperly.app.data.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSource(val doc: BackupDocument, val file: File)

data class BackupHeader(val schemaVersion: Int, val appVersion: String, val createdAt: Long)

/**
 * ZIP layout: `documents/<documentId>` (raw file bytes) ... then `manifest.json` LAST.
 * Manifest-last means a truncated/interrupted archive has no manifest and is rejected outright.
 * Entry names are only ever looked up by id from the validated manifest, never used as paths.
 */
object BackupArchive {
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val DOC_PREFIX = "documents/"
    private const val MAX_MANIFEST_BYTES = 16 * 1024 * 1024
    private const val BUFFER_SIZE = 64 * 1024

    fun write(
        out: OutputStream,
        sources: List<BackupSource>,
        folders: List<BackupFolder>,
        header: BackupHeader,
    ) {
        val zip = ZipOutputStream(BufferedOutputStream(out))
        sources.forEach { s ->
            zip.putNextEntry(ZipEntry(DOC_PREFIX + s.doc.documentId))
            s.file.inputStream().use { it.copyTo(zip, BUFFER_SIZE) }
            zip.closeEntry()
        }
        val manifest = BackupManifest(
            BACKUP_FORMAT_VERSION, header.schemaVersion, header.appVersion, header.createdAt, folders,
            sources.map { it.doc },
        )
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(BackupManifestCodec.encode(manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
        zip.flush()
    }

    /** Re-reads a written archive: manifest valid and every document entry's SHA-256 equals its manifest checksum. */
    fun verify(input: InputStream): Boolean = try {
        val hashes = HashMap<String, String>()
        var manifestJson: String? = null
        ZipInputStream(BufferedInputStream(input)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                when {
                    entry.name == MANIFEST_ENTRY -> manifestJson = readBounded(zin)
                    entry.name.startsWith(DOC_PREFIX) -> hashes[entry.name.removePrefix(DOC_PREFIX)] = sha256(zin)
                }
                entry = zin.nextEntry
            }
        }
        val manifest = manifestJson?.let { BackupManifestCodec.decode(it) }
        manifest != null && manifest.invalidEntries == 0 &&
            manifest.documents.all { hashes[it.documentId] == it.checksum }
    } catch (e: IOException) {
        false // corrupted/truncated archive (ZipException is an IOException)
    }

    fun readManifest(zip: ZipFile): BackupManifest? =
        zip.getEntry(MANIFEST_ENTRY)?.let { e -> zip.getInputStream(e).use { readBounded(it) } }
            ?.let { BackupManifestCodec.decode(it) }

    fun openDocument(zip: ZipFile, documentId: String): InputStream? =
        zip.getEntry(DOC_PREFIX + documentId)?.let { zip.getInputStream(it) }

    private fun readBounded(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(BUFFER_SIZE)
        while (out.size() <= MAX_MANIFEST_BYTES) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return if (out.size() > MAX_MANIFEST_BYTES) null else out.toString(Charsets.UTF_8.name())
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(BUFFER_SIZE)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

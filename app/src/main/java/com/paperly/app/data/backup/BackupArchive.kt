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

/** [damaged] = entries whose bytes do not match the manifest checksum; [missing] = listed documents with no entry. */
data class ArchiveCheck(val manifest: BackupManifest, val damaged: Int, val missing: Int) {
    val isIntact: Boolean get() = manifest.invalidEntries == 0 && damaged == 0 && missing == 0
}

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
        extras: BackupExtras = BackupExtras.EMPTY,
    ) {
        val zip = ZipOutputStream(BufferedOutputStream(out))
        sources.forEach { s ->
            zip.putNextEntry(ZipEntry(DOC_PREFIX + s.doc.documentId))
            s.file.inputStream().use { it.copyTo(zip, BUFFER_SIZE) }
            zip.closeEntry()
        }
        val manifest = BackupManifest(
            formatVersion = BACKUP_FORMAT_VERSION,
            schemaVersion = header.schemaVersion,
            appVersion = header.appVersion,
            createdAt = header.createdAt,
            folders = folders,
            documents = sources.map { it.doc },
            extras = extras,
        )
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(BackupManifestCodec.encode(manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
        zip.flush()
    }

    /** Re-reads a written archive: manifest valid and every document entry's SHA-256 equals its manifest checksum. */
    fun verify(input: InputStream): Boolean = check(input)?.isIntact == true

    /**
     * Streams the whole archive once, writing nothing: decodes the manifest and hashes every document entry.
     * null = no readable manifest / corrupted or truncated archive (ZipException is an IOException).
     */
    fun check(input: InputStream): ArchiveCheck? = try {
        val hashes = HashMap<String, String>()
        val manifestJson = ZipInputStream(BufferedInputStream(input)).use { scan(it, hashes) }
        manifestJson?.let { BackupManifestCodec.decode(it) }?.let { m ->
            val damaged = m.documents.count { hashes[it.documentId]?.let { h -> h != it.checksum } == true }
            val missing = m.documents.count { it.documentId !in hashes }
            ArchiveCheck(m, damaged, missing)
        }
    } catch (e: IOException) {
        null
    }

    /** Fills [hashes] (id -> SHA-256) for document entries and returns the manifest text, if present. */
    private fun scan(zin: ZipInputStream, hashes: MutableMap<String, String>): String? {
        var manifestJson: String? = null
        var entry = zin.nextEntry
        while (entry != null) {
            when {
                entry.name == MANIFEST_ENTRY -> manifestJson = readBounded(zin)
                entry.name.startsWith(DOC_PREFIX) -> hashes[entry.name.removePrefix(DOC_PREFIX)] = sha256(zin)
            }
            entry = zin.nextEntry
        }
        return manifestJson
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

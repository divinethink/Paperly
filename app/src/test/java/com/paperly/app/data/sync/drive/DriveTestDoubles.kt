package com.paperly.app.data.sync.drive

import android.content.Intent
import com.paperly.app.domain.auth.DriveAuth
import com.paperly.app.domain.auth.DriveToken
import com.paperly.app.domain.sync.DownloadTarget
import java.io.InputStream

internal class FakeDriveAuth(var token: DriveToken = DriveToken.Ready("tok")) : DriveAuth {
    var invalidated = 0

    override suspend fun token() = token

    override suspend fun invalidate(token: String) {
        invalidated++
    }

    override suspend fun tokenFromConsent(result: Intent?) = token
}

internal class MemorySessions : UploadSessionStore {
    val map = HashMap<String, UploadSession>()

    override suspend fun get(documentId: String) = map[documentId]

    override suspend fun save(documentId: String, session: UploadSession) {
        map[documentId] = session
    }

    override suspend fun clear(documentId: String) {
        map.remove(documentId)
    }

    override suspend fun clearAll() = map.clear()
}

internal class MemoryTarget : DownloadTarget {
    var stored: ByteArray? = null
    var discarded = 0

    override suspend fun store(input: InputStream): String {
        val bytes = input.readBytes()
        stored = bytes
        return sha256Hex(bytes)
    }

    override suspend fun discard() {
        discarded++
        stored = null
    }
}

package com.paperly.app.data.backup

import android.content.Intent
import com.paperly.app.data.sync.drive.ChunkResult
import com.paperly.app.data.sync.drive.DriveEndpoints
import com.paperly.app.data.sync.drive.DriveException
import com.paperly.app.data.sync.drive.DriveFailure
import com.paperly.app.data.sync.drive.DriveFile
import com.paperly.app.data.sync.drive.DriveFileApi
import com.paperly.app.data.sync.drive.digestOf
import com.paperly.app.data.sync.drive.io
import com.paperly.app.data.sync.drive.matches
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.DriveToken
import com.paperly.app.domain.backup.DriveBackupStore
import com.paperly.app.domain.backup.DriveResult
import com.paperly.app.domain.backup.StoredBackup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

private const val MAX_STALLS = 3

/**
 * Google Drive folder "Paperly Backup" as backup storage. The upload is a brand-new file that is checked against
 * the local ZIP (size and hash) before older copies of the same name are removed, so a failed or half-finished
 * upload can never destroy the previous backup.
 */
@Singleton
class DriveBackupStoreImpl @Inject constructor(
    private val auth: AuthRepository,
    private val driveAuth: GoogleDriveFileAuth,
) : DriveBackupStore {
    private val chunks = DriveFileApi(DriveEndpoints.PRODUCTION)
    private val api = DriveBackupApi(DriveEndpoints.PRODUCTION)

    override suspend fun access(): DriveResult<Unit> = withDrive { }

    override suspend fun upload(file: File, name: String): DriveResult<Unit> = withDrive { token ->
        val folder = io { api.findFolder(token) ?: api.createFolder(token) }
        val url = io { api.createSession(token, folder, name, file.length()) }
        val sent = sendAll(url, file)
        val remote = io { chunks.getFile(token, sent.id) }
        if (!io { remote.isCopyOf(file) }) {
            runCatching { io { chunks.deleteFile(token, sent.id) } }
            throw DriveException(DriveFailure.RETRY, "uploaded copy does not match")
        }
        // The new copy is verified: now (and only now) the older copies of the same name go.
        runCatching {
            io {
                api.listBackups(token, folder).filter { it.name == name && it.id != sent.id }
                    .forEach { chunks.deleteFile(token, it.id) }
            }
        }
        Unit
    }

    override suspend fun list(): DriveResult<List<StoredBackup>> = withDrive { token ->
        io { api.findFolder(token)?.let { api.listBackups(token, it) }.orEmpty() }
    }

    override suspend fun download(id: String, target: File): DriveResult<Unit> = withDrive { token ->
        io {
            chunks.openMedia(token, id).use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        Unit
    }

    override suspend fun consentGranted(data: Intent?): Boolean = driveAuth.tokenFromConsent(data) is DriveToken.Ready

    /** Sends the file chunk by chunk until Drive reports it complete. Cancellation is honoured between chunks. */
    private suspend fun sendAll(url: String, file: File): DriveFile {
        val size = file.length()
        var offset = 0L
        var stalls = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            when (val result = io { chunks.putChunk(url, file, offset, size) }) {
                is ChunkResult.Complete -> return result.file
                is ChunkResult.Continue -> {
                    stalls = if (result.next > offset) 0 else stalls + 1
                    if (stalls >= MAX_STALLS || result.next >= size) throw DriveException(DriveFailure.RETRY, "stuck")
                    offset = result.next
                }
            }
        }
    }

    private suspend fun <T> withDrive(block: suspend (String) -> T): DriveResult<T> {
        if (auth.state.first() !is AuthState.SignedIn) return DriveResult.NotSignedIn
        return when (val token = driveAuth.token()) {
            is DriveToken.Ready -> attempt(token.value, block)
            is DriveToken.NeedsConsent -> DriveResult.NeedsConsent(token.request)
            DriveToken.Unavailable -> DriveResult.Failed
        }
    }

    private suspend fun <T> attempt(token: String, block: suspend (String) -> T): DriveResult<T> = try {
        DriveResult.Ok(block(token))
    } catch (e: CancellationException) {
        throw e
    } catch (e: DriveException) {
        if (e.failure == DriveFailure.AUTH_EXPIRED) driveAuth.invalidate(token)
        DriveResult.Failed
    } catch (e: Exception) {
        DriveResult.Failed
    }
}

/** Same size and the same SHA-256 (or MD5 when Drive has no SHA-256) as the local file. */
private fun DriveFile.isCopyOf(file: File): Boolean =
    matches(file.length(), digestOf(file, "SHA-256")) { digestOf(file, "MD5") }

@Module
@InstallIn(SingletonComponent::class)
abstract class DriveBackupModule {
    @Binds
    abstract fun bindDriveBackupStore(impl: DriveBackupStoreImpl): DriveBackupStore
}

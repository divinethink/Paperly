package com.paperly.app.core.file

import android.content.Context
import com.paperly.app.core.di.ApplicationScope
import com.paperly.app.domain.privacy.LocalWipe
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Cheap background housekeeping at app start. Never throws; never touches documents or the database. */
@Singleton
class StartupMaintenance @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localWipe: LocalWipe,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun run() {
        scope.launch(Dispatchers.IO) {
            // A "Delete all my data" that was cut short (e.g. the app was killed) is finished first.
            runCatching { localWipe.resumeIfInterrupted() }
            runCatching {
                // Old share/export copies (and image exports) in the cache; the OS may also clear the cache itself.
                val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(EXPORT_CACHE_DAYS)
                ExportFiles.pruneOlderThan(ExportFiles.exportsDir(context), cutoff)
            }
        }
    }

    private companion object {
        const val EXPORT_CACHE_DAYS = 7L
    }
}

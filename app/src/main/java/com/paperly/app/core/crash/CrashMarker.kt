package com.paperly.app.core.crash

import java.io.File
import java.io.IOException

data class CrashInfo(val timeMillis: Long, val exceptionClass: String)

/**
 * Best-effort "the app crashed last time" marker.
 * Stores only the time and the exception class name — never document content or messages.
 */
class CrashMarker(private val file: File) {

    fun record(timeMillis: Long, throwable: Throwable) {
        try {
            file.writeText("$timeMillis\n${throwable.javaClass.name}")
        } catch (ignored: IOException) {
            // Best effort: a failed marker write must never mask the original crash.
        }
    }

    /** Returns the recorded crash (if any) and removes the marker, so the notice shows only once. */
    fun consume(): CrashInfo? {
        if (!file.exists()) return null
        val info = try {
            val lines = file.readLines()
            val time = lines.getOrNull(0)?.toLongOrNull()
            val name = lines.getOrNull(1)
            if (time != null && !name.isNullOrBlank()) CrashInfo(time, name) else null
        } catch (ignored: IOException) {
            null
        }
        file.delete()
        return info
    }

    companion object {
        fun forApp(dir: File) = CrashMarker(File(dir, "last_crash.marker"))
    }
}

object CrashRecovery {
    /** Records the crash, then always hands over to the previous handler so Crashlytics reporting stays intact. */
    fun install(marker: CrashMarker) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            marker.record(System.currentTimeMillis(), throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}

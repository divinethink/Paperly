package com.paperly.app

import android.app.Application
import com.paperly.app.core.crash.CrashMarker
import com.paperly.app.core.crash.CrashRecovery
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaperlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Chains to the previously installed handler (Crashlytics, when wired), so reporting is unaffected.
        CrashRecovery.install(CrashMarker.forApp(noBackupFilesDir))
    }
}

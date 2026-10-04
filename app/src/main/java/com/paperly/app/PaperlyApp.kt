package com.paperly.app

import android.app.Application
import com.paperly.app.core.crash.CrashMarker
import com.paperly.app.core.crash.CrashRecovery
import com.paperly.app.core.file.StartupMaintenance
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PaperlyApp : Application() {
    @Inject
    lateinit var maintenance: StartupMaintenance

    override fun onCreate() {
        super.onCreate()
        // Chains to the previously installed handler (Crashlytics, when wired), so reporting is unaffected.
        CrashRecovery.install(CrashMarker.forApp(noBackupFilesDir))
        maintenance.run()
    }
}

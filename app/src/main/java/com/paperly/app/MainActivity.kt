package com.paperly.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.core.crash.CrashMarker
import com.paperly.app.core.crash.CrashNoticeDialog
import com.paperly.app.core.intent.ContinueReadingRequests
import com.paperly.app.core.intent.IncomingImportRequests
import com.paperly.app.core.intent.extractImportUri
import com.paperly.app.core.intent.isContinueReading
import com.paperly.app.core.ui.theme.PaperlyTheme
import com.paperly.app.feature.reader.EpubFragmentHost
import com.paperly.app.feature.settings.AppearanceViewModel
import com.paperly.app.navigation.PaperlyRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var incomingImports: IncomingImportRequests

    @Inject lateinit var continueReading: ContinueReadingRequests

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        supportFragmentManager.fragmentFactory = EpubFragmentHost // before super: safe fragment restore (P3)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch: after rotation/process restore the same intent must not import again.
        if (savedInstanceState == null) postIntent(intent)
        setContent {
            val appearance: AppearanceViewModel = hiltViewModel()
            val dynamicColor by appearance.dynamicColor.collectAsStateWithLifecycle()
            PaperlyTheme(dynamicColor = dynamicColor) {
                val pendingImport by incomingImports.pending.collectAsStateWithLifecycle()
                PaperlyRoot(hasIncomingImport = pendingImport != null)
                // consume() runs once per fresh process; rememberSaveable keeps the notice across rotation.
                var showCrashNotice by rememberSaveable {
                    mutableStateOf(CrashMarker.forApp(noBackupFilesDir).consume() != null)
                }
                if (showCrashNotice) CrashNoticeDialog(onDismiss = { showCrashNotice = false })
                // Marks first usable frame: feeds system startup metrics (logcat "Fully drawn", Play vitals).
                LaunchedEffect(Unit) { reportFullyDrawn() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        postIntent(intent)
    }

    private fun postIntent(intent: Intent?) {
        extractImportUri(intent)?.let(incomingImports::post)
        if (isContinueReading(intent)) continueReading.post()
    }
}

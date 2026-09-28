package com.paperly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.paperly.app.core.crash.CrashMarker
import com.paperly.app.core.crash.CrashNoticeDialog
import com.paperly.app.core.ui.theme.PaperlyTheme
import com.paperly.app.navigation.PaperlyRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaperlyTheme {
                PaperlyRoot()
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
}

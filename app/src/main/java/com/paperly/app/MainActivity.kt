package com.paperly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
                // Marks first usable frame: feeds system startup metrics (logcat "Fully drawn", Play vitals).
                LaunchedEffect(Unit) { reportFullyDrawn() }
            }
        }
    }
}

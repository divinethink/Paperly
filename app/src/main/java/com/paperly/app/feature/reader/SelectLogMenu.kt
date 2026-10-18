package com.paperly.app.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** TEMPORARY: copies the text-select diagnostics to the clipboard (see [SelectDebugLog]). */
@Composable
fun SelectLogMenuItem(onDone: () -> Unit) {
    val context = LocalContext.current
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return // debug builds only
    DropdownMenuItem(
        text = { Text("Copy selection log (debug)") },
        onClick = {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("selection-log", SelectDebugLog.takeAll()))
            Toast.makeText(context, "Selection log copied", Toast.LENGTH_SHORT).show()
            onDone()
        },
    )
}

package com.paperly.app.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.paperly.app.R
import kotlinx.coroutines.launch

/**
 * S3: returns the "Copy scanned text" action. Created outside the dropdown so its scope outlives the menu closing.
 * Copies the recognised text of the whole scan; tells the owner when nothing has been read yet.
 */
@Composable
fun rememberCopyScanText(viewModel: ScanPageViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val text = viewModel.scanText()
            if (text == null) {
                Toast.makeText(context, R.string.scan_text_none, Toast.LENGTH_LONG).show()
            } else {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("scan", text))
                Toast.makeText(context, R.string.scan_text_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Menu items that exist only for scanned documents: page title/note and (S3) copy of the recognised text. */
@Composable
fun ScanMenuItems(onPageNote: () -> Unit, onCopyText: () -> Unit, onDone: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.scan_page_edit)) },
        onClick = {
            onPageNote()
            onDone()
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.scan_text_copy)) },
        onClick = {
            onCopyText()
            onDone()
        },
    )
}

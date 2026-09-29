package com.paperly.app.core.crash

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.paperly.app.R

@Composable
fun CrashNoticeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_notice_title)) },
        text = { Text(stringResource(R.string.crash_notice_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.crash_notice_ok)) } },
    )
}

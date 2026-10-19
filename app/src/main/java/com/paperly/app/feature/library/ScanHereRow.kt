package com.paperly.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R

/** Shown while a folder is open on the Scanned tab: the new scan is saved into that folder. */
@Composable
internal fun ScanHereRow(onScan: () -> Unit, onGallery: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onScan, modifier = Modifier.weight(1f).height(44.dp)) {
            Text(stringResource(R.string.scan_here))
        }
        OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f).height(44.dp)) {
            Text(stringResource(R.string.scan_here_gallery))
        }
    }
}

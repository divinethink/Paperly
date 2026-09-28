package com.paperly.app.feature.scanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.paperly.app.R

/** Shell only. ML Kit scanner entry arrives in P4. */
@Composable
fun ScanScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.scan_placeholder))
    }
}

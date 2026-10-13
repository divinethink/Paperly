package com.paperly.app.feature.ocr

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Temporary Settings block for the Bengali OCR trial (P10 spike). English-only on purpose; removed afterwards. */
@Composable
fun OcrSpikeSection(viewModel: OcrSpikeViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.runOcr(uri)
    }
    Text("OCR test (temporary)", style = MaterialTheme.typography.titleMedium)
    Text("Bengali OCR trial on the first 3 pages of a PDF.", style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::downloadModel, enabled = !ui.busy && !ui.modelReady) { Text("Get model") }
        Button(
            onClick = { picker.launch(arrayOf("application/pdf")) },
            enabled = ui.modelReady && !ui.busy,
        ) { Text("Pick PDF & run") }
    }
    if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    if (ui.message.isNotEmpty()) SelectionContainer { Text(ui.message, Modifier.padding(top = 8.dp)) }
}

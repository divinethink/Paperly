package com.paperly.app.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.core.ui.theme.TrashGreen
import com.paperly.app.domain.storage.StorageUsage

private val BarHeight = 10.dp
private val DotSize = 10.dp

/** Storage summary: total used, a two-colour bar (documents / Trash), a legend; the clean-up button comes below. */
@Composable
fun StorageCardContent(usage: StorageUsage?) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CardHeader(R.string.settings_storage_header, Modifier.weight(1f))
        if (usage != null) {
            val used = Formatter.formatShortFileSize(context, usage.totalBytes)
            Text(
                stringResource(R.string.settings_storage_used, used),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (usage != null) {
        StorageBar(usage, Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(
                MaterialTheme.colorScheme.primary,
                pluralStringResource(R.plurals.settings_storage_documents, usage.activeCount, usage.activeCount),
            )
            val trashSize = Formatter.formatShortFileSize(context, usage.trashBytes)
            LegendItem(TrashGreen, stringResource(R.string.settings_storage_trash_size, trashSize))
        }
    }
    OrphanFilesSection()
}

@Composable
private fun StorageBar(usage: StorageUsage, modifier: Modifier = Modifier) {
    val total = usage.totalBytes.coerceAtLeast(1L).toFloat()
    val docs = (usage.activeBytes / total).coerceIn(0f, 1f)
    val trash = (usage.trashBytes / total).coerceIn(0f, 1f - docs)
    val track = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth().height(BarHeight).clip(CircleShape).background(track)) {
        if (docs > 0f) Box(Modifier.weight(docs).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        if (trash > 0f) Box(Modifier.weight(trash).fillMaxHeight().background(TrashGreen))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(DotSize).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

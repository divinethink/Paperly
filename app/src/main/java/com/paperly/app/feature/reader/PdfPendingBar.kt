package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

/** Annotate-mode bars: the type/color strip, plus the Done/Cancel bar while parts are pending. */
@Composable
internal fun PdfAnnotateBars(
    controller: AnnotationController,
    annotate: Boolean,
    onEditor: (AnnotationEditor) -> Unit,
) {
    if (!annotate) return
    val style by controller.style.collectAsStateWithLifecycle()
    val pending by controller.pending.collectAsStateWithLifecycle()
    PdfAnnotateStrip(style, controller::setStyle)
    pending?.let { parts ->
        PendingBar(
            count = parts.rects.size,
            onDone = { controller.commitPending()?.let(onEditor) },
            onCancel = controller::cancelPending,
        )
    }
}

@Composable
private fun PendingBar(count: Int, onDone: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.reader_pending_parts, count), Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        TextButton(onClick = onDone) { Text(stringResource(R.string.reader_pending_done)) }
    }
}

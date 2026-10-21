package com.paperly.app.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R

private const val ORIGINAL_WIDTH_PX = 1200

/** One tap shows the real page image under the OCR text, so a misread page can be checked (nothing is saved). */
@Composable
internal fun OriginalPageToggle(ocr: ReflowOcrController, index: Int, color: Color) {
    var show by rememberSaveable(index) { mutableStateOf(false) }
    TextButton(onClick = { show = !show }) {
        Text(stringResource(if (show) R.string.reader_ocr_hide_original else R.string.reader_ocr_show_original))
    }
    if (show) {
        val image by produceState<Result<ImageBitmap?>?>(null, index) {
            value = runCatching { ocr.pageImage(index, ORIGINAL_WIDTH_PX)?.asImageBitmap() }
        }
        val loaded = image
        val bitmap = loaded?.getOrNull()
        when {
            loaded == null -> Unit
            bitmap == null -> Text(
                stringResource(R.string.reader_ocr_original_failed),
                color = color,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            else -> Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.reader_ocr_original_desc, index + 1),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }
    }
}

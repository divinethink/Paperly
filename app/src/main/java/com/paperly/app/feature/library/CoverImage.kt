package com.paperly.app.feature.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.paperly.app.core.ui.theme.CoverPalette
import com.paperly.app.core.ui.theme.ReadingFontFamily
import com.paperly.app.domain.cover.CoverLetter
import com.paperly.app.domain.document.Document

/** Decorative: the title is read by the card text, so the cover carries no semantics of its own. */
@Composable
internal fun CoverImage(doc: Document, covers: CoverViewModel, modifier: Modifier = Modifier) {
    var bmp by remember(doc.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(doc.id, doc.type) { bmp = covers.load(doc.id, doc.type) }
    val shape = MaterialTheme.shapes.small
    val image = bmp
    if (image != null) {
        Image(
            image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(
            modifier.clip(shape).background(CoverPalette[CoverLetter.colorIndex(doc.title, CoverPalette.size)]),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                CoverLetter.letter(doc.title),
                color = Color.White,
                fontFamily = ReadingFontFamily,
                style = MaterialTheme.typography.displayMedium,
            )
        }
    }
}

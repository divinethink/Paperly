package com.paperly.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

private const val RIBBON_NOTCH = 0.75f

/** Small bookmark ribbon: a bookmarked page stays recognisable while the bars (and their star) are hidden. */
private val RibbonShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width / 2, size.height * RIBBON_NOTCH)
    lineTo(0f, size.height)
    close()
}

/**
 * EPUB full-page hints (shown only while the bars are hidden): the bookmark ribbon, and a one-time tip
 * saying where to tap for the menu (counts as seen once the bars have been opened).
 */
@Composable
internal fun BoxScope.EpubFullPageHints(
    barsVisible: Boolean,
    bookmarked: Boolean,
    comfortViewModel: ReaderComfortViewModel = hiltViewModel(),
) {
    val comfort by comfortViewModel.comfort.collectAsStateWithLifecycle()
    val markSeen = { comfortViewModel.update(comfortViewModel.comfort.value.copy(menuTipSeen = true)) }
    LaunchedEffect(barsVisible) { if (barsVisible && !comfort.menuTipSeen) markSeen() }
    if (barsVisible) return
    if (bookmarked) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 20.dp)
                .size(width = 14.dp, height = 28.dp)
                .background(MaterialTheme.colorScheme.primary, RibbonShape),
        )
    }
    if (!comfort.menuTipSeen) {
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 96.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.epub_menu_tip), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = markSeen, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.epub_menu_tip_ok))
                }
            }
        }
    }
}

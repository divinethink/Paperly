package com.paperly.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.paperly.app.R

private val SmallBadge = 22.dp

/** Small "DOC" tag: this EPUB was made from a Word file by the converter. */
@Composable
internal fun DocBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.doc_from_word)
    Text(
        stringResource(R.string.doc_badge_word),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .semantics { contentDescription = description },
    )
}

/** Round check mark: the document was read to the end. */
@Composable
internal fun FinishedBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.doc_finished)
    Box(
        modifier.size(SmallBadge).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp),
        )
    }
}

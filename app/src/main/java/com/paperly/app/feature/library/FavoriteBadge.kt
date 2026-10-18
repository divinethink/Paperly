package com.paperly.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.paperly.app.R

/** Round badge on the cover of a favourite document (toggled from the card menu). */
@Composable
internal fun FavoriteBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.doc_is_favorite)
    Box(
        modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = BADGE_ALPHA))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(18.dp))
    }
}

private const val BADGE_ALPHA = 0.4f
private val StarGold = Color(0xFFF5C24A)

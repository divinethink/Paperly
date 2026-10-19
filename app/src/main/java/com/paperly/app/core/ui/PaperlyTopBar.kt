package com.paperly.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.app.R

private const val DISABLED_ALPHA = 0.5f

/**
 * Notebloc-style teal title bar. The background also paints the status-bar area, so the route must not add the
 * status-bar inset itself (see `rootInsets` in PaperlyNavHost). Text and icons are `onPrimary`.
 */
@Composable
fun PaperlyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
        Row(
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back),
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack == null) 12.dp else 0.dp)
                    .semantics { heading() },
            )
            actions()
        }
    }
}

/** A text button that stays readable on the teal bar (the default primary colour would vanish). */
@Composable
fun topBarTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = DISABLED_ALPHA),
)

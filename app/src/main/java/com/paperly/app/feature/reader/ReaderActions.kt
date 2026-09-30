package com.paperly.app.feature.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.ReaderTheme

/** Reader top-bar actions: Bookmark button, one-tap Annotate toggle, ⋮ menu (Fit mode, Theme). */
@Composable
fun ReaderActions(viewModel: ReaderViewModel, page: Int, fitHeight: Boolean, onToggleFit: () -> Unit) {
    val bookmarks by viewModel.bookmarkedPages.collectAsStateWithLifecycle()
    TextButton(onClick = { viewModel.toggleBookmark(page) }) {
        val label = if (page in bookmarks) R.string.reader_bookmark_remove else R.string.reader_bookmark_add
        Text(stringResource(label))
    }
    val annotate by viewModel.annotations.annotateMode.collectAsStateWithLifecycle()
    IconToggleButton(
        checked = annotate,
        onCheckedChange = { viewModel.annotations.toggleMode() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.reader_annotate_mode))
    }
    OverflowMenu(viewModel, fitHeight, onToggleFit)
}

@Composable
private fun OverflowMenu(viewModel: ReaderViewModel, fitHeight: Boolean, onToggleFit: () -> Unit) {
    val current by viewModel.theme.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.reader_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(if (fitHeight) R.string.reader_fit_width else R.string.reader_fit_height)) },
            onClick = {
                onToggleFit()
                open = false
            },
        )
        ReaderTheme.entries.forEach { option ->
            val name = stringResource(R.string.reader_theme_item, stringResource(themeName(option)))
            DropdownMenuItem(
                text = { Text(if (option == current) "✓ $name" else name) },
                onClick = {
                    viewModel.setTheme(option)
                    open = false
                },
            )
        }
    }
}

private fun themeName(theme: ReaderTheme): Int = when (theme) {
    ReaderTheme.AUTO -> R.string.reader_theme_auto
    ReaderTheme.LIGHT -> R.string.reader_theme_light
    ReaderTheme.SEPIA -> R.string.reader_theme_sepia
    ReaderTheme.WARM -> R.string.reader_theme_warm
    ReaderTheme.DARK -> R.string.reader_theme_dark
    ReaderTheme.AMOLED -> R.string.reader_theme_amoled
}

package com.paperly.app.feature.settings

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.privacy.DeleteScope
import com.paperly.app.domain.privacy.ScopedDeleteOutcome

@StringRes
internal fun DeleteScope.labelRes(): Int = when (this) {
    DeleteScope.TRASH -> R.string.menu_trash
    DeleteScope.SCAN -> R.string.library_name_scanned
    DeleteScope.PDF -> R.string.library_name_pdf
    DeleteScope.EPUB -> R.string.library_name_epub
    DeleteScope.ALL -> R.string.backup_scope_all
}

@StringRes
internal fun DeleteScope.whatRes(): Int = when (this) {
    DeleteScope.TRASH -> R.string.delete_scope_what_trash
    DeleteScope.SCAN -> R.string.delete_scope_what_scan
    DeleteScope.PDF -> R.string.delete_scope_what_pdf
    DeleteScope.EPUB -> R.string.delete_scope_what_epub
    DeleteScope.ALL -> R.string.delete_all_what
}

/** Trash / Scanned / PDF / EPUB / All chips with a one-line "N documents · size" summary of the chosen one. */
@Composable
internal fun DeleteScopeChips(state: DeleteUiState, onChoose: (DeleteScope) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeleteScope.entries.forEach { scope ->
            FilterChip(
                selected = state.scope == scope,
                onClick = { onChoose(scope) },
                label = { Text(stringResource(scope.labelRes()), maxLines = 1) },
                enabled = !state.running,
            )
        }
    }
    val stat = state.stats[state.scope] ?: ScopeStat(0, 0L)
    Text(
        pluralStringResource(
            R.plurals.backup_scope_summary,
            stat.count,
            stat.count,
            Formatter.formatShortFileSize(LocalContext.current, stat.bytes),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
internal fun scopedOutcomeText(outcome: ScopedDeleteOutcome): String = when (outcome) {
    is ScopedDeleteOutcome.Done -> pluralStringResource(R.plurals.delete_scope_done, outcome.deleted, outcome.deleted)
    is ScopedDeleteOutcome.Partial ->
        stringResource(R.string.delete_scope_partial, outcome.deleted, outcome.failed)
    ScopedDeleteOutcome.Failed -> stringResource(R.string.delete_scope_failed)
}

@StringRes
internal fun DeleteScope.confirmRes(): Int =
    if (this == DeleteScope.ALL) R.string.delete_all_confirm else R.string.delete_scope_confirm

@StringRes
internal fun DeleteScope.finalRes(): Int =
    if (this == DeleteScope.ALL) R.string.delete_all_final_body else R.string.delete_scope_final

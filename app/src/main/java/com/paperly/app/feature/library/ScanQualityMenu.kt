package com.paperly.app.feature.library

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.paperly.app.R
import com.paperly.app.domain.scanner.ScanOutputSettings
import com.paperly.app.domain.scanner.ScanOutputSize
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ScanQualityViewModel @Inject constructor(
    private val settings: ScanOutputSettings,
) : ViewModel() {
    val size: StateFlow<ScanOutputSize> =
        settings.size.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanOutputSize.MEDIUM)

    fun choose(size: ScanOutputSize) {
        viewModelScope.launch { settings.setSize(size) }
    }
}

private fun ScanOutputSize.labelRes(): Int = when (this) {
    ScanOutputSize.SMALL -> R.string.scan_size_small
    ScanOutputSize.MEDIUM -> R.string.scan_size_medium
    ScanOutputSize.LARGE -> R.string.scan_size_large
}

/** Scanned tab menu section: the scan quality is chosen once here and used for every save (no per-save question). */
@Composable
internal fun ColumnScope.ScanQualityItems(onDismiss: () -> Unit, viewModel: ScanQualityViewModel = hiltViewModel()) {
    val current by viewModel.size.collectAsStateWithLifecycle()
    Text(
        stringResource(R.string.scan_quality_label).uppercase(),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ScanOutputSize.entries.forEach { size ->
        DropdownMenuItem(
            text = { Text(stringResource(size.labelRes())) },
            leadingIcon = { RadioButton(selected = current == size, onClick = null) },
            onClick = {
                viewModel.choose(size)
                onDismiss()
            },
        )
    }
}

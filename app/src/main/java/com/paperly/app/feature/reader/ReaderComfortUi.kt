package com.paperly.app.feature.reader

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.MIN_BRIGHTNESS
import com.paperly.app.domain.reader.ReaderComfort

private const val DEFAULT_BRIGHTNESS = 0.6f
private const val NO_BRIGHTNESS_OVERRIDE = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
private const val BLUE_LIGHT_MAX_ALPHA = 0.4f
private val BlueLightTint = Color(0xFFFF8C00)

/** Applies brightness / keep-awake / hidden system bars to the activity window; all restored on leaving the reader. */
@Composable
internal fun ReaderComfortEffect(comfort: ReaderComfort) {
    val window = (LocalContext.current as FragmentActivity).window
    val view = LocalView.current
    DisposableEffect(window) {
        onDispose { window.attributes = window.attributes.apply { screenBrightness = NO_BRIGHTNESS_OVERRIDE } }
    }
    SideEffect {
        window.attributes = window.attributes.apply { screenBrightness = comfort.brightness ?: NO_BRIGHTNESS_OVERRIDE }
    }
    DisposableEffect(window, comfort.keepAwake) {
        if (comfort.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(window, view, comfort.hideBars) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (comfort.hideBars) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Warm tint over the whole reader; a plain Box, so touches pass through to the page. */
@Composable
internal fun BlueLightOverlay(intensity: Float) {
    if (intensity > 0f) {
        Box(Modifier.fillMaxSize().background(BlueLightTint.copy(alpha = intensity * BLUE_LIGHT_MAX_ALPHA)))
    }
}

@Composable
internal fun ColumnScope.ComfortRows(comfort: ReaderComfort, onChange: (ReaderComfort) -> Unit) {
    val brightness = comfort.brightness
    SwitchRow(R.string.reader_brightness_auto, brightness == null) {
        onChange(comfort.copy(brightness = if (it) null else DEFAULT_BRIGHTNESS))
    }
    if (brightness != null) {
        SliderRow(R.string.reader_brightness, brightness, MIN_BRIGHTNESS) { onChange(comfort.copy(brightness = it)) }
    }
    SliderRow(R.string.reader_blue_light, comfort.blueLight, 0f) { onChange(comfort.copy(blueLight = it)) }
    SwitchRow(R.string.reader_keep_awake, comfort.keepAwake) { onChange(comfort.copy(keepAwake = it)) }
    SwitchRow(R.string.reader_hide_bars, comfort.hideBars) { onChange(comfort.copy(hideBars = it)) }
}

@Composable
private fun SliderRow(label: Int, value: Float, min: Float, onValue: (Float) -> Unit) {
    val name = stringResource(label)
    Text(name)
    Slider(
        value = value,
        onValueChange = onValue,
        valueRange = min..1f,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = name },
    )
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Rows wired to the shared ViewModel (same instance as the one driving the reader route). */
@Composable
internal fun ColumnScope.ComfortRowsHost(viewModel: ReaderComfortViewModel = hiltViewModel()) {
    val comfort by viewModel.comfort.collectAsStateWithLifecycle()
    ComfortRows(comfort, viewModel::update)
}

/** PDF reader entry: no dim scrim, so brightness and tint changes are visible on the page while adjusting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComfortSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, scrimColor = Color.Transparent) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComfortRowsHost()
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.epub_done))
            }
        }
    }
}

@Composable
internal fun ComfortMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(R.string.reader_comfort)) }, onClick = onClick)
}

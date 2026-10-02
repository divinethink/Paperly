package com.paperly.app.feature.reader

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.OrientationEventListener
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.paperly.app.R

// Degrees reported by the sensor; the gaps between the ranges are a dead zone so the button does not flicker.
private const val PORTRAIT_UP_MAX = 30
private const val PORTRAIT_UP_MIN = 330
private const val LANDSCAPE_A_MIN = 60
private const val LANDSCAPE_A_MAX = 120
private const val PORTRAIT_DOWN_MIN = 150
private const val PORTRAIT_DOWN_MAX = 210
private const val LANDSCAPE_B_MIN = 240
private const val LANDSCAPE_B_MAX = 300

private fun isLandscapeDegrees(degrees: Int) =
    degrees in LANDSCAPE_A_MIN..LANDSCAPE_A_MAX || degrees in LANDSCAPE_B_MIN..LANDSCAPE_B_MAX

private fun isPortraitDegrees(degrees: Int) = degrees <= PORTRAIT_UP_MAX || degrees >= PORTRAIT_UP_MIN ||
    degrees in PORTRAIT_DOWN_MIN..PORTRAIT_DOWN_MAX

/**
 * When the device is held in an orientation different from the screen's (e.g. system rotation lock is on),
 * offers a button that locks the reader to the held orientation. The lock survives rotation recreation and
 * is released when the reader is left. Independent from the system rotation setting.
 */
@Composable
internal fun OrientationToggle(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as FragmentActivity
    val landscapeNow = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // true = held landscape, false = held portrait, null = unknown yet.
    var heldLandscape by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var locked by rememberSaveable { mutableStateOf<Int?>(null) }
    // Re-applied after recreation (the new Activity starts unlocked).
    SideEffect { activity.requestedOrientation = locked ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    DisposableEffect(activity) {
        val listener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                if (isLandscapeDegrees(orientation)) {
                    heldLandscape = true
                } else if (isPortraitDegrees(orientation)) {
                    heldLandscape = false
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose {
            listener.disable()
            if (!activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    val held = heldLandscape
    if (held != null && held != landscapeNow) {
        val target = if (held) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        FilledTonalButton(
            onClick = { locked = target },
            modifier = modifier.navigationBarsPadding().padding(16.dp),
        ) {
            Text(stringResource(if (held) R.string.reader_rotate_landscape else R.string.reader_rotate_portrait))
        }
    }
}

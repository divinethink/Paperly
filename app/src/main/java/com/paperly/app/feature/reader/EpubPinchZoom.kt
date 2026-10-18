package com.paperly.app.feature.reader

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.EpubTypography
import kotlin.math.roundToInt

private const val SNAP = 20f // text size moves in 0.05 steps while pinching
private const val PERCENT = 100
private val BadgeElevation = 6.dp

/** Text size for a pinch: the size when the fingers went down times the pinch factor, snapped and clamped. */
internal fun pinchedScale(base: Float, factor: Float): Float =
    ((base * factor * SNAP).roundToInt() / SNAP).coerceIn(EpubTypography.MIN_SCALE, EpubTypography.MAX_SCALE)

/**
 * Wraps the EPUB navigator. A two-finger pinch is reported as a cumulative factor (and a final call with
 * `finished = true`); the fingers' touches are not passed to the book. One-finger touches pass through untouched.
 */
internal class PinchLayout(context: Context) : FrameLayout(context) {
    var onPinch: (Float, Boolean) -> Unit = { _, _ -> }
    private var factor = 1f
    private var pinching = false
    private var swallow = false
    private val detector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                factor = 1f
                pinching = true
                return true
            }

            override fun onScale(d: ScaleGestureDetector): Boolean {
                factor *= d.scaleFactor
                onPinch(factor, false)
                return true
            }

            override fun onScaleEnd(d: ScaleGestureDetector) {
                pinching = false
                onPinch(factor, true)
            }
        },
    ).apply { isQuickScaleEnabled = false }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val was = pinching
        detector.onTouchEvent(ev)
        if (pinching && !was) cancelChildren(ev)
        // one finger may stay down after the pinch: the rest of that gesture is not a scroll or a tap
        if (was && !pinching) swallow = true
        val consume = pinching || swallow
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) swallow = false
        return consume || super.dispatchTouchEvent(ev)
    }

    private fun cancelChildren(ev: MotionEvent) {
        val cancel = MotionEvent.obtain(ev)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }
}

/** Live text size while pinching ([live], applied to the book at once) and the save when the fingers lift. */
@Stable
internal class EpubPinchState(
    private val current: () -> EpubTypography,
    private val save: (EpubTypography) -> Unit,
) {
    var live by mutableStateOf<Float?>(null)
    var active by mutableStateOf(false)
        private set
    private var base = 1f

    fun onPinch(factor: Float, finished: Boolean) {
        if (!active) base = current().fontScale
        val scale = pinchedScale(base, factor)
        live = scale
        active = !finished
        if (finished) save(current().copy(fontScale = scale))
    }
}

@Composable
internal fun rememberEpubPinch(settings: EpubSettingsViewModel): EpubPinchState {
    val state = remember(settings) { EpubPinchState({ settings.typography.value }, settings::updateTypography) }
    val typography by settings.typography.collectAsStateWithLifecycle()
    // the saved value has arrived: from now on the stored size is the truth again
    LaunchedEffect(typography.fontScale) { state.live = null }
    return state
}

/** Small "120%" label in the middle of the page while the fingers are down. */
@Composable
internal fun PinchBadge(pinch: EpubPinchState, modifier: Modifier = Modifier) {
    val scale = pinch.live ?: return
    if (!pinch.active) return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = BadgeElevation,
        shadowElevation = BadgeElevation,
    ) {
        Text(
            stringResource(R.string.epub_text_size_percent, (scale * PERCENT).roundToInt()),
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

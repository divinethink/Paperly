package com.paperly.app.feature.reader

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Rest time before select / quick box starts; longer than the system long-press, so reading touches pass. */
internal const val HOLD_PRESS_MS = 700L

/**
 * Waits for [down] to be held still for [HOLD_PRESS_MS]. Returns [down] when it was, and null when the finger
 * moved, lifted, was consumed, or a second finger arrived (page scroll / pinch stay untouched).
 */
internal suspend fun AwaitPointerEventScope.awaitHoldPress(down: PointerInputChange): PointerInputChange? {
    val held = try {
        withTimeout(HOLD_PRESS_MS) { waitUntilBroken(down) }
        false
    } catch (expected: TimeoutCancellationException) {
        true
    }
    return down.takeIf { held }
}

private suspend fun AwaitPointerEventScope.waitUntilBroken(down: PointerInputChange) {
    val slop = viewConfiguration.touchSlop
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        val gone = change == null || change.isConsumed || !change.pressed
        val moved = change != null && (change.position - down.position).getDistance() > slop
        if (gone || moved || event.changes.size > 1) return
    }
}

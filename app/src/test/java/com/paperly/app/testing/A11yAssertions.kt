package com.paperly.app.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTouchHeightIsAtLeast
import androidx.compose.ui.test.assertTouchWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.dp

private val MinTouchTarget = 48.dp

private val HasLabel = SemanticsMatcher("has text or contentDescription") {
    it.config.contains(SemanticsProperties.Text) || it.config.contains(SemanticsProperties.ContentDescription)
}

/** Every clickable node must have a label for TalkBack and a touch target of at least 48dp. */
fun ComposeContentTestRule.assertClickablesAccessible() {
    val clickables = onAllNodes(hasClickAction())
    val count = clickables.fetchSemanticsNodes().size
    check(count > 0) { "No clickable nodes found — the screen under test is empty" }
    repeat(count) { i ->
        clickables[i]
            .assert(HasLabel)
            .assertTouchWidthIsAtLeast(MinTouchTarget)
            .assertTouchHeightIsAtLeast(MinTouchTarget)
    }
}

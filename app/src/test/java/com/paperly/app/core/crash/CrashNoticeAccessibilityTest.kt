package com.paperly.app.core.crash

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.paperly.app.R
import com.paperly.app.core.ui.theme.PaperlyTheme
import com.paperly.app.testing.assertClickablesAccessible
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashNoticeAccessibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dialogButtonsAreLabelledAndLargeEnough() {
        compose.setContent { PaperlyTheme { CrashNoticeDialog(onDismiss = {}) } }
        compose.assertClickablesAccessible()
    }

    @Test
    fun okButtonDismissesDialog() {
        var dismissed = false
        compose.setContent { PaperlyTheme { CrashNoticeDialog(onDismiss = { dismissed = true }) } }
        compose.onNodeWithText(compose.activity.getString(R.string.crash_notice_ok)).performClick()
        assertTrue(dismissed)
    }
}

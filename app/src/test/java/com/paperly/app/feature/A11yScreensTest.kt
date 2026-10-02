package com.paperly.app.feature

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.paperly.app.core.ui.theme.PaperlyTheme
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.feature.common.EmptyState
import com.paperly.app.feature.common.ErrorRetry
import com.paperly.app.feature.library.FilterRow
import com.paperly.app.feature.library.LibraryFilter
import com.paperly.app.feature.reader.ColorPicker
import com.paperly.app.feature.reader.EpubBarActions
import com.paperly.app.feature.reader.EpubBarState
import com.paperly.app.feature.reader.EpubTopBar
import com.paperly.app.testing.assertClickablesAccessible
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Full-surface a11y checks (label + 48dp touch target) for composables that need no Hilt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class A11yScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readerTopBarButtonsAreAccessible() {
        compose.setContent {
            PaperlyTheme {
                EpubTopBar(
                    bar = EpubBarState(canSearch = true, bookmarked = false),
                    actions = EpubBarActions({}, {}, {}, {}, {}, {}),
                )
            }
        }
        compose.assertClickablesAccessible()
    }

    @Test
    fun annotationColorSwatchesAreAccessible() {
        compose.setContent { PaperlyTheme { ColorPicker(AnnotationColor.AMBER) {} } }
        compose.assertClickablesAccessible()
    }

    @Test
    fun libraryFilterChipsAreAccessible() {
        compose.setContent { PaperlyTheme { FilterRow(LibraryFilter.All) {} } }
        compose.assertClickablesAccessible()
    }

    @Test
    fun errorAndEmptyStateActionsAreAccessible() {
        compose.setContent {
            PaperlyTheme {
                ErrorRetry("Failed") {}
                EmptyState("Nothing here", "Import") {}
            }
        }
        compose.assertClickablesAccessible()
    }
}

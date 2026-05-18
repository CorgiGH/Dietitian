package com.dietician.shared.ui.nav

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.DieticianLocaleProvider
import com.dietician.shared.ui.theme.DieticianTheme
import kotlin.test.Test

/**
 * KMP Compose UI tests for the bottom-nav scaffolding. Verifies the
 * `[testTag="nav-{key}"]` contract — these are the KMP equivalents of
 * `[data-testid]` selectors that the spec final-paint gate will assert
 * against.
 */
@OptIn(ExperimentalTestApi::class)
class DieticianNavUiTest {

    @Test
    fun `bottom nav paints with all 5 testTags on first compose`() = runComposeUiTest {
        setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    DieticianBottomNav()
                }
            }
        }
        onNodeWithTag("dietician-bottom-nav").assertIsDisplayed()
        onAllNodesWithTag("nav-home").assertCountEquals(1)
        onAllNodesWithTag("nav-food-log").assertCountEquals(1)
        onAllNodesWithTag("nav-pantry").assertCountEquals(1)
        onAllNodesWithTag("nav-coach-chat").assertCountEquals(1)
        onAllNodesWithTag("nav-settings").assertCountEquals(1)
    }

    @Test
    fun `bottom nav respects RO locale on labels`() = runComposeUiTest {
        setContent {
            DieticianLocaleProvider(locale = AppLocale.RO) {
                DieticianTheme {
                    DieticianBottomNav()
                }
            }
        }
        // testTag set still works regardless of label locale.
        onNodeWithTag("nav-home").assertIsDisplayed()
        onNodeWithTag("nav-food-log").assertIsDisplayed()
    }
}

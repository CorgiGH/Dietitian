package com.dietician.shared.ui.nav

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.DieticianLocaleProvider
import com.dietician.shared.ui.theme.DieticianTheme
import org.junit.Rule
import kotlin.test.Test

/**
 * **RC8 (Council 1779120600):** Dietician's data model is per-device-per-friend.
 * A single device runs ONE signed-in subject at a time; there is no shared-server
 * "switch user" UI element. This is enforced by absence — no subject-switcher
 * component should exist in the bottom nav or anywhere else in the shell.
 *
 * The test asserts the bottom nav contains exactly the five spec'd entries
 * (Home / FoodLog / Pantry / Coach / Settings) and that NO node with a tag
 * containing "subject-switcher" is anywhere in the tree.
 */
class NoSubjectSwitcherTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `bottom nav exposes only the 5 top-level destinations`() {
        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    DieticianBottomNav()
                }
            }
        }
        composeRule.onNodeWithTag("dietician-bottom-nav").assertIsDisplayed()
        // Exactly 5 tabs.
        composeRule.onNodeWithTag("nav-home").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-food-log").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-pantry").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-coach-chat").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-settings").assertIsDisplayed()
        // Zero subject-switcher elements.
        composeRule.onAllNodesWithTag("subject-switcher").assertCountEquals(0)
        composeRule.onAllNodesWithTag("nav-subject-switcher").assertCountEquals(0)
    }
}

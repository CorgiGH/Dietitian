package com.dietician.shared.ui.integration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.DieticianLocaleProvider
import com.dietician.shared.ui.nav.DieticianBottomNav
import com.dietician.shared.ui.screens.SplashScreen
import com.dietician.shared.ui.screens.TailscaleDisconnectedScreen
import com.dietician.shared.ui.theme.DieticianTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-4-5 Task 33 — RC16 Tailscale pre-auth blocker flow.
 *
 * Models the platform-shell decision tree:
 *   reachable = null  → SplashScreen
 *   reachable = false → TailscaleDisconnectedScreen (blocker)
 *   reachable = true  → DieticianApp shell (bottom nav)
 *
 * Asserts the user can drive the retry path from blocker → app after
 * Tailscale comes back up.
 */
class TailscaleBlockerFlowTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `null reachable shows splash, false shows blocker, true shows app shell`() {
        var reachable by mutableStateOf<Boolean?>(null)
        var retryCalls = 0

        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    when (reachable) {
                        null -> SplashScreen()
                        false -> TailscaleDisconnectedScreen(
                            onRetry = {
                                retryCalls += 1
                                reachable = true
                            },
                        )
                        true -> DieticianBottomNav()
                    }
                }
            }
        }

        // Splash phase.
        composeRule.onNodeWithTag("dietician-splash").assertIsDisplayed()

        // Probe returns false → blocker.
        reachable = false
        composeRule.onNodeWithTag("tailscale-disconnected-blocker").assertIsDisplayed()
        composeRule.onNodeWithTag("tailscale-disconnected-retry").assertIsDisplayed()

        // User taps retry → probe returns true → bottom nav paints.
        composeRule.onNodeWithTag("tailscale-disconnected-retry").performClick()
        assertEquals(1, retryCalls)

        composeRule.onNodeWithTag("dietician-bottom-nav").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertIsDisplayed()
    }

    @Test
    fun `retry from blocker keeps blocker visible if VPS still unreachable (false negative guarded)`() {
        // Risk-analyst surfaced concern: Tailscale itself up but VPS unreachable
        // → retry must NOT silently flip the user to "OK". This test pins that
        // a still-false reachability result keeps the blocker mounted.
        var reachable by mutableStateOf<Boolean?>(false)
        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    when (reachable) {
                        null -> SplashScreen()
                        false -> TailscaleDisconnectedScreen(onRetry = { /* probe stays false */ })
                        true -> DieticianBottomNav()
                    }
                }
            }
        }
        composeRule.onNodeWithTag("tailscale-disconnected-blocker").assertIsDisplayed()
        composeRule.onNodeWithTag("tailscale-disconnected-retry").performClick()
        // Still on blocker, no nav paint.
        composeRule.onNodeWithTag("tailscale-disconnected-blocker").assertIsDisplayed()
    }
}

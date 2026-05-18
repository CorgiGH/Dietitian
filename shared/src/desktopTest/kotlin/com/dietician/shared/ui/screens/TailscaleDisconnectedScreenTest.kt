package com.dietician.shared.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.DieticianLocaleProvider
import com.dietician.shared.ui.theme.DieticianTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RC16 — Tailscale-disconnected blocker UI.
 *
 * Asserts the testTags + Retry CTA wire-through. The full pre-auth gate
 * (SplashScreen → blocker → DieticianApp) is exercised at the platform-shell
 * level (MainActivity / Main.kt); this test pins the leaf Composable.
 */
class TailscaleDisconnectedScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `renders blocker with title and retry button`() {
        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    TailscaleDisconnectedScreen(onRetry = {})
                }
            }
        }
        composeRule.onNodeWithTag("tailscale-disconnected-blocker").assertIsDisplayed()
        composeRule.onNodeWithTag("tailscale-disconnected-retry").assertIsDisplayed()
    }

    @Test
    fun `retry button invokes callback`() {
        val clicks = mutableStateOf(0)
        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    TailscaleDisconnectedScreen(onRetry = { clicks.value += 1 })
                }
            }
        }
        composeRule.onNodeWithTag("tailscale-disconnected-retry").performClick()
        assertEquals(1, clicks.value)
    }

    @Test
    fun `splash screen renders its testTag`() {
        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    SplashScreen()
                }
            }
        }
        composeRule.onNodeWithTag("dietician-splash").assertIsDisplayed()
    }
}

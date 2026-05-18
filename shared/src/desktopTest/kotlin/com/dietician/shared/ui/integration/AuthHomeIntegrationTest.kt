package com.dietician.shared.ui.integration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dietician.shared.ui.auth.MagicLinkVerifyScreen
import com.dietician.shared.ui.auth.OnboardingActions
import com.dietician.shared.ui.auth.OnboardingScreen
import com.dietician.shared.ui.auth.VerifyUiState
import com.dietician.shared.ui.components.TodayNutrientsState
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.DieticianLocaleProvider
import com.dietician.shared.ui.screens.HomeLoader
import com.dietician.shared.ui.screens.HomeScreen
import com.dietician.shared.ui.screens.HomeViewModel
import com.dietician.shared.ui.screens.MeProfile
import com.dietician.shared.ui.theme.DieticianTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-4-5 Task 33 — Onboarding → MagicLinkVerify → Home integration.
 *
 * Mirrors the platform-shell flow (Tasks 5 + 25):
 *   1. OnboardingScreen renders email input
 *   2. User types email + taps "Send magic link" → stage flips to CheckEmail
 *   3. Caller swaps in MagicLinkVerifyScreen in Loading state (token in flight)
 *   4. Verify returns Success → host swaps to Home
 *   5. Home paints with the verified subject id
 *
 * No real Ktor calls — onboarding actions are recorded into local state and the
 * caller drives the next screen.
 */
class AuthHomeIntegrationTest {

    @get:Rule val composeRule = createComposeRule()

    private class RecordingActions : OnboardingActions {
        var requestedEmail: String? = null
        var resendCalls: Int = 0
        var verifiedCallback: (() -> Unit)? = null

        override fun onSendMagicLink(email: String) {
            requestedEmail = email
        }

        override fun onResend(email: String) {
            resendCalls += 1
        }

        override fun onVerified() {
            verifiedCallback?.invoke()
        }

        override fun bindVerifiedSignal(callback: () -> Unit) {
            verifiedCallback = callback
        }
    }

    private class StubLoader : HomeLoader {
        override suspend fun loadMe(): MeProfile = MeProfile("00000000-victor", "Victor")
        override suspend fun loadTodayNutrients(subjectId: String): TodayNutrientsState =
            TodayNutrientsState(kcal = 0.0, kcalTarget = 2750.0)
    }

    @Test
    fun `Onboarding form transitions to CheckEmail then MagicLinkVerify Success then Home`() {
        val actions = RecordingActions()
        var route by mutableStateOf("onboarding")
        var verifyState by mutableStateOf<VerifyUiState>(VerifyUiState.Loading)
        val homeVm = HomeViewModel(StubLoader())
        runBlocking { homeVm.load() }

        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    when (route) {
                        "onboarding" -> OnboardingScreen(actions = actions)
                        "verify" -> MagicLinkVerifyScreen(
                            state = verifyState,
                            onBackToOnboarding = { route = "onboarding" },
                        )
                        "home" -> HomeScreen(
                            viewModel = homeVm,
                            onLogMeal = {},
                            onOpenSettings = {},
                        )
                    }
                }
            }
        }

        // Email entry stage.
        composeRule.onNodeWithTag("onboarding-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-email-input").performTextInput("victor@example.com")
        composeRule.onNodeWithTag("onboarding-send-magic-link").performClick()
        assertEquals("victor@example.com", actions.requestedEmail)

        // CheckEmail stage.
        composeRule.onNodeWithTag("onboarding-check-email").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-same-device-copy").assertIsDisplayed()

        // Host receives the deep-link → swaps to verify Loading.
        route = "verify"
        composeRule.onNodeWithTag("magic-link-verify-loading").assertIsDisplayed()

        // Verify completes → Success.
        verifyState = VerifyUiState.Success
        composeRule.onNodeWithTag("magic-link-verify-success").assertIsDisplayed()

        // Host swaps to Home.
        route = "home"
        composeRule.onNodeWithTag("home-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("home-subject-card").assertIsDisplayed()
    }

    @Test
    fun `MagicLinkVerify Error state renders back-to-onboarding link`() {
        var verifyState by mutableStateOf<VerifyUiState>(VerifyUiState.Error("invalid token"))
        var clickedBack = false

        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    MagicLinkVerifyScreen(
                        state = verifyState,
                        onBackToOnboarding = { clickedBack = true },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("magic-link-verify-error").assertIsDisplayed()
        composeRule.onNodeWithTag("magic-link-back-to-onboarding").performClick()
        assertEquals(true, clickedBack)
    }

    @Test
    fun `Cross-device verify signal (RC20) advances onboarding to success without explicit nav`() {
        val actions = RecordingActions()
        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    OnboardingScreen(actions = actions)
                }
            }
        }

        composeRule.onNodeWithTag("onboarding-email-input").performTextInput("victor@example.com")
        composeRule.onNodeWithTag("onboarding-send-magic-link").performClick()
        composeRule.onNodeWithTag("onboarding-check-email").assertIsDisplayed()

        // RC20 — WS Verified event fires → bound callback flips local success flag.
        composeRule.runOnIdle {
            actions.verifiedCallback?.invoke()
        }
        composeRule.onNodeWithTag("onboarding-success").assertIsDisplayed()
    }
}

package com.dietician.shared.ui.nav

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.dietician.shared.ui.components.AILiteracyVersionGate
import com.dietician.shared.ui.di.uiModule
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.network.networkModule
import com.dietician.shared.ui.settings.InMemorySettingsStore
import com.dietician.shared.ui.settings.SettingsStore
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * First-launch gate: OnboardingScreen → DieticianApp → AILiteracyBanner ack flow.
 *
 * Drives the full flow with fresh SettingsStore (onboarded=false, ai-literacy
 * un-acked) and asserts each transition paints the right testTags.
 */
@OptIn(ExperimentalTestApi::class)
class OnboardingGateTest {

    @BeforeTest
    fun startKoinForTest() {
        if (GlobalContext.getOrNull() == null) {
            val testOverride = module {
                single<SettingsStore>(createdAtStart = true) { InMemorySettingsStore() }
            }
            startKoin { modules(networkModule, uiModule, testOverride) }
        }
        // Fresh state — leave onboarded=false + ai-literacy=null so the gate fires.
    }

    @AfterTest
    fun stopKoinForTest() {
        stopKoin()
    }

    @Test
    fun `first launch shows OnboardingScreen, not DieticianApp`() = runComposeUiTest {
        setContent { DieticianApp(locale = AppLocale.EN) }
        onNodeWithTag("onboarding-screen").assertIsDisplayed()
        onNodeWithTag("dietician-bottom-nav").assertDoesNotExist()
    }

    @Test
    fun `simulate-verify transitions to DieticianApp with AI literacy banner`() = runComposeUiTest {
        setContent { DieticianApp(locale = AppLocale.EN) }
        // Type email + send.
        onNodeWithTag("onboarding-email-input").performTextInput("victor@example.com")
        onNodeWithTag("onboarding-send-magic-link").performClick()
        // CheckEmail stage now visible.
        onNodeWithTag("onboarding-check-email").assertIsDisplayed()
        // Tap simulate-verify → SettingsStore.markOnboarded() fires.
        onNodeWithTag("onboarding-simulate-verify").performClick()
        // DieticianApp paints.
        onNodeWithTag("dietician-bottom-nav").assertIsDisplayed()
        // AI literacy banner overlays — banner is an AlertDialog so the root tag
        // sits on a popup-window; assert via the inside ok-button instead.
        onNodeWithTag("ai-literacy-ok-button").assertIsDisplayed()
    }

    @Test
    fun `AI literacy ack dismisses banner + leaves DieticianApp on Home`() = runComposeUiTest {
        // Pre-onboard + leave ai-literacy unacked so the banner shows.
        val store = GlobalContext.get().get<SettingsStore>()
        store.markOnboarded()

        setContent { DieticianApp(locale = AppLocale.EN) }
        onNodeWithTag("ai-literacy-ok-button").assertIsDisplayed()
        onNodeWithTag("ai-literacy-ok-button").performClick()
        onNodeWithTag("ai-literacy-ok-button").assertDoesNotExist()

        onNodeWithTag("home-subject-card").assertIsDisplayed()
        // Store should now hold the current version.
        assert(store.state.value.aiLiteracyAckedVersion == AILiteracyVersionGate.CURRENT_VERSION)
    }
}

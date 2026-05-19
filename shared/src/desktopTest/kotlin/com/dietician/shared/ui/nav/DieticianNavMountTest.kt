package com.dietician.shared.ui.nav

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dietician.shared.ui.components.AILiteracyVersionGate
import com.dietician.shared.ui.data.InMemoryPantryStore
import com.dietician.shared.ui.data.PantryReader
import com.dietician.shared.ui.data.PantryWriter
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
 * Live nav-graph mount tests — catches the 2026-05-11 Slice 1 ghost-component
 * pattern that the original Plan-4-5 council 1779128340 missed.
 *
 * Strategy: mount the FULL [DieticianApp] (not just `DieticianBottomNav` in
 * isolation) under a real Koin context loaded with [uiModule] + [networkModule]
 * (the latter provides `AuditRepository` + `RecipeIngestClient` etc that some
 * screen VMs consume in iteration 2). Asserts each real screen testTag paints
 * after clicking its bottom-nav tab.
 *
 * If any real-screen testTag assertion fails the test harness surfaces "no
 * node with this tag" — that's the exact error a future ghost-component
 * regression would produce.
 */
@OptIn(ExperimentalTestApi::class)
class DieticianNavMountTest {

    @BeforeTest
    fun startKoinForTest() {
        if (GlobalContext.getOrNull() == null) {
            // Override SettingsStore to InMemorySettingsStore so tests don't read
            // a leftover settings.json from a prior session on this machine.
            val testOverride = module {
                single<SettingsStore>(createdAtStart = true) { InMemorySettingsStore() }
                // Override SqlDelight-backed pantry with in-memory store — tests
                // don't bring up the platform DataModule that binds DieticianDatabase.
                single(createdAtStart = true) { InMemoryPantryStore() }
                single<PantryReader>(createdAtStart = true) { get<InMemoryPantryStore>() }
                single<PantryWriter>(createdAtStart = true) { get<InMemoryPantryStore>() }
                // iter-11: tests skip the platform module, so provide a noop
                // CoachLlmGateway so CoachLlmGatewayLlmStream resolves.
                single<com.dietician.shared.llm.CoachLlmGateway> {
                    object : com.dietician.shared.llm.CoachLlmGateway {
                        override fun streamCoachTurn(
                            prompt: String,
                            locale: com.dietician.shared.llm.CoachLocale,
                        ): kotlinx.coroutines.flow.Flow<com.dietician.shared.llm.LlmChunk> =
                            kotlinx.coroutines.flow.emptyFlow()
                    }
                }
            }
            startKoin { modules(networkModule, uiModule, testOverride) }
        }
        // Skip onboarding + AI literacy ack so tests land on DieticianApp routes.
        // The dedicated OnboardingGateTest exercises the gating logic.
        val store = GlobalContext.get().get<SettingsStore>()
        store.markOnboarded()
        store.setAiLiteracyAckedVersion(AILiteracyVersionGate.CURRENT_VERSION)
    }

    @AfterTest
    fun stopKoinForTest() {
        stopKoin()
    }

    @Test
    fun `Home tab mounts real HomeScreen on first compose`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        onNodeWithTag("home-screen").assertIsDisplayed()
        onNodeWithTag("home-subject-card").assertIsDisplayed()
        onNodeWithTag("home-expenditure-preview").assertIsDisplayed()
        onNodeWithTag("home-log-meal-cta").assertIsDisplayed()
    }

    @Test
    fun `Food log tab mounts real FoodLogScreen`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        onNodeWithTag("nav-food-log").performClick()
        onNodeWithTag("foodlog-screen").assertIsDisplayed()
    }

    @Test
    fun `Pantry tab mounts real PantryScreen`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        onNodeWithTag("nav-pantry").performClick()
        onNodeWithTag("pantry-screen").assertIsDisplayed()
    }

    @Test
    fun `Coach tab mounts real CoachChatScreen`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        onNodeWithTag("nav-coach-chat").performClick()
        onNodeWithTag("coach-screen").assertIsDisplayed()
    }

    @Test
    fun `Settings tab mounts real SettingsScreen with locale + theme toggles`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        onNodeWithTag("nav-settings").performClick()
        onNodeWithTag("settings-screen").assertIsDisplayed()
        onNodeWithTag("settings-locale-en").assertIsDisplayed()
        onNodeWithTag("settings-locale-ro").assertIsDisplayed()
        onNodeWithTag("settings-dark-theme").assertIsDisplayed()
        onNodeWithTag("settings-accessible-typography").assertIsDisplayed()
        onNodeWithTag("settings-coach-disabled").assertIsDisplayed()
        onNodeWithTag("settings-view-audit-log").assertIsDisplayed()
        onNodeWithTag("settings-about").assertIsDisplayed()
    }

    @Test
    fun `Settings to AuditLog deep-link works`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        onNodeWithTag("nav-settings").performClick()
        onNodeWithTag("settings-view-audit-log").performClick()
        onNodeWithTag("audit-log-screen").assertIsDisplayed()
    }

    @Test
    fun `clicking nav-home restores HomeScreen after nav away`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        onNodeWithTag("nav-pantry").performClick()
        onNodeWithTag("pantry-screen").assertIsDisplayed()
        onNodeWithTag("nav-home").performClick()
        onNodeWithTag("home-subject-card").assertIsDisplayed()
    }
}

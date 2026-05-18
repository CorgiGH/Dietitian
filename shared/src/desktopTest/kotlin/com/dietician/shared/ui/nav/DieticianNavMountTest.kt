package com.dietician.shared.ui.nav

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dietician.shared.ui.di.uiModule
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.network.networkModule
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
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
            startKoin { modules(networkModule, uiModule) }
        }
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
    fun `Settings tab still shows placeholder (no SettingsScreen yet)`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        onNodeWithTag("nav-settings").performClick()
        onNodeWithTag("placeholder-settings").assertIsDisplayed()
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

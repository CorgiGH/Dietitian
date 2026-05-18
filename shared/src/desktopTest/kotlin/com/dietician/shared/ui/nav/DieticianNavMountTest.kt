package com.dietician.shared.ui.nav

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dietician.shared.ui.di.uiModule
import com.dietician.shared.ui.i18n.AppLocale
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
 * isolation) under a real Koin context loaded with [uiModule], then assert
 * that:
 *
 *   1. Real screen testTags paint on first compose (NOT placeholder-* tags).
 *   2. Clicking a not-yet-mounted bottom-nav tab swaps to the placeholder
 *      content for that route.
 *   3. Clicking back to Home restores the real screen surface.
 *
 * If any `home-subject-card` / real-screen testTag assertion fails the test
 * harness surfaces "no node with this tag" — that's the exact error a
 * future ghost-component regression would produce.
 */
@OptIn(ExperimentalTestApi::class)
class DieticianNavMountTest {

    @BeforeTest
    fun startKoinForTest() {
        if (GlobalContext.getOrNull() == null) {
            startKoin { modules(uiModule) }
        }
    }

    @AfterTest
    fun stopKoinForTest() {
        stopKoin()
    }

    @Test
    fun `Home tab mounts real HomeScreen, not a placeholder`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        // Real HomeScreen testTags — sourced from SubjectCard / TodayNutrientsCard /
        // PlannedCutToggle / AdaptiveExpenditurePreview / FoodLog CTA composables.
        onNodeWithTag("home-screen").assertIsDisplayed()
        onNodeWithTag("home-subject-card").assertIsDisplayed()
        onNodeWithTag("home-expenditure-preview").assertIsDisplayed()
        onNodeWithTag("home-log-meal-cta").assertIsDisplayed()
    }

    @Test
    fun `clicking nav-food-log swaps content to placeholder-food-log`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        onNodeWithTag("home-subject-card").assertIsDisplayed()
        onNodeWithTag("nav-food-log").performClick()
        // Real FoodLog mount is a follow-up iteration — placeholder testTag
        // proves the nav switch fired AND the screen is renderable.
        onNodeWithTag("placeholder-food-log").assertIsDisplayed()
    }

    @Test
    fun `clicking nav-home restores HomeScreen after nav away`() = runComposeUiTest {
        setContent {
            DieticianApp(locale = AppLocale.EN)
        }
        onNodeWithTag("nav-pantry").performClick()
        onNodeWithTag("placeholder-pantry").assertIsDisplayed()
        onNodeWithTag("nav-home").performClick()
        onNodeWithTag("home-subject-card").assertIsDisplayed()
    }
}

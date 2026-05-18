package com.dietician.shared.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey

/**
 * Sealed Voyager [Screen] hierarchy for Dietician's top-level destinations.
 *
 * Each leaf has a stable [key] for testTag mapping ("nav-{key}" in
 * DieticianBottomNav). Detail screens (MealDetail) embed their id into the
 * key so back-stack restoration round-trips correctly.
 *
 * Bodies are placeholders — later batches replace the Text(...) with the real
 * screen Composables. Keeping them as compilable stubs here means the nav
 * skeleton can be live-exercised end-to-end without later tasks blocking.
 */
sealed class DieticianScreen : Screen {

    abstract override val key: ScreenKey

    data object Home : DieticianScreen() {
        override val key: ScreenKey = "home"

        @Composable
        override fun Content() {
            PlaceholderScreen("Home")
        }
    }

    data object FoodLog : DieticianScreen() {
        override val key: ScreenKey = "food-log"

        @Composable
        override fun Content() {
            PlaceholderScreen("Food log")
        }
    }

    data object Pantry : DieticianScreen() {
        override val key: ScreenKey = "pantry"

        @Composable
        override fun Content() {
            PlaceholderScreen("Pantry")
        }
    }

    data object Cookbook : DieticianScreen() {
        override val key: ScreenKey = "cookbook"

        @Composable
        override fun Content() {
            PlaceholderScreen("Cookbook")
        }
    }

    data object CoachChat : DieticianScreen() {
        override val key: ScreenKey = "coach-chat"

        @Composable
        override fun Content() {
            PlaceholderScreen("Coach")
        }
    }

    data object Settings : DieticianScreen() {
        override val key: ScreenKey = "settings"

        @Composable
        override fun Content() {
            PlaceholderScreen("Settings")
        }
    }

    data object AuditLog : DieticianScreen() {
        override val key: ScreenKey = "audit-log"

        @Composable
        override fun Content() {
            PlaceholderScreen("Audit log")
        }
    }

    data class MealDetail(val mealId: String) : DieticianScreen() {
        override val key: ScreenKey = "meal-detail/$mealId"

        @Composable
        override fun Content() {
            PlaceholderScreen("Meal detail: $mealId")
        }
    }
}

/** Top-level bottom-nav destinations in spec order (RC: 5 tabs). */
val BottomNavScreens: List<DieticianScreen> = listOf(
    DieticianScreen.Home,
    DieticianScreen.FoodLog,
    DieticianScreen.Pantry,
    DieticianScreen.CoachChat,
    DieticianScreen.Settings,
)

@Composable
private fun PlaceholderScreen(label: String) {
    Text(text = label)
}

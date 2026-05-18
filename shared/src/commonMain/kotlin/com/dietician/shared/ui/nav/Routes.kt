package com.dietician.shared.ui.nav

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.dietician.shared.ui.screens.HomeScreen
import com.dietician.shared.ui.screens.HomeViewModel
import org.koin.compose.koinInject

/**
 * Sealed Voyager [Screen] hierarchy for Dietician's top-level destinations.
 *
 * Each leaf has a stable [key] for testTag mapping ("nav-{key}" in
 * DieticianBottomNav). Detail screens (MealDetail) embed their id into the
 * key so back-stack restoration round-trips correctly.
 *
 * **Mount state (nav-mount-fix iteration 1):**
 *   - [Home] mounts the real [HomeScreen] via Koin-resolved [HomeViewModel].
 *   - All other tabs still render [PlaceholderScreen] until their own mount
 *     iteration ships. PlaceholderScreen carries a `placeholder-{key}` testTag
 *     so smoke walks can distinguish "tab not yet mounted" from "tab mounted
 *     and broken".
 */
sealed class DieticianScreen : Screen {

    abstract override val key: ScreenKey

    data object Home : DieticianScreen() {
        override val key: ScreenKey = "home"

        @Composable
        override fun Content() {
            val viewModel = koinInject<HomeViewModel>()
            val navigator = LocalNavigator.currentOrThrow
            LaunchedEffect(viewModel) { viewModel.load() }
            HomeScreen(
                viewModel = viewModel,
                onLogMeal = { navigator.push(FoodLog) },
                onOpenSettings = { navigator.push(Settings) },
            )
        }
    }

    data object FoodLog : DieticianScreen() {
        override val key: ScreenKey = "food-log"

        @Composable
        override fun Content() {
            PlaceholderScreen("Food log", key)
        }
    }

    data object Pantry : DieticianScreen() {
        override val key: ScreenKey = "pantry"

        @Composable
        override fun Content() {
            PlaceholderScreen("Pantry", key)
        }
    }

    data object Cookbook : DieticianScreen() {
        override val key: ScreenKey = "cookbook"

        @Composable
        override fun Content() {
            PlaceholderScreen("Cookbook", key)
        }
    }

    data object CoachChat : DieticianScreen() {
        override val key: ScreenKey = "coach-chat"

        @Composable
        override fun Content() {
            PlaceholderScreen("Coach", key)
        }
    }

    data object Settings : DieticianScreen() {
        override val key: ScreenKey = "settings"

        @Composable
        override fun Content() {
            PlaceholderScreen("Settings", key)
        }
    }

    data object AuditLog : DieticianScreen() {
        override val key: ScreenKey = "audit-log"

        @Composable
        override fun Content() {
            PlaceholderScreen("Audit log", key)
        }
    }

    data class MealDetail(val mealId: String) : DieticianScreen() {
        override val key: ScreenKey = "meal-detail/$mealId"

        @Composable
        override fun Content() {
            PlaceholderScreen("Meal detail: $mealId", key)
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
private fun PlaceholderScreen(label: String, key: String) {
    Text(text = label, modifier = Modifier.testTag("placeholder-$key"))
}

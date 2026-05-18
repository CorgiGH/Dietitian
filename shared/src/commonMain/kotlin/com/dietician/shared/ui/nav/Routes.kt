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
import com.dietician.shared.ui.screens.AuditLogScreen
import com.dietician.shared.ui.screens.AuditLogViewModel
import com.dietician.shared.ui.screens.CoachChatScreen
import com.dietician.shared.ui.screens.CoachChatViewModel
import com.dietician.shared.ui.screens.FoodLogScreen
import com.dietician.shared.ui.screens.FoodLogViewModel
import com.dietician.shared.ui.screens.HomeScreen
import com.dietician.shared.ui.screens.HomeViewModel
import com.dietician.shared.ui.screens.PantryScreen
import com.dietician.shared.ui.screens.PantryViewModel
import com.dietician.shared.ui.screens.SettingsScreen
import com.dietician.shared.ui.screens.SettingsViewModel
import org.koin.compose.koinInject

/**
 * Sealed Voyager [Screen] hierarchy for Dietician's top-level destinations.
 *
 * Each leaf has a stable [key] for testTag mapping ("nav-{key}" in
 * DieticianBottomNav). Detail screens (MealDetail) embed their id into the
 * key so back-stack restoration round-trips correctly.
 *
 * **Mount state (nav-mount iteration 2):**
 *   - [Home] / [FoodLog] / [Pantry] / [CoachChat] / [AuditLog] mount their real
 *     screen composables via Koin-resolved ViewModels.
 *   - [Cookbook] + [Settings] still render [PlaceholderScreen]. Cookbook isn't a
 *     bottom-nav tab in spec; Settings has no `SettingsScreen.kt` yet.
 *     PlaceholderScreen carries a `placeholder-{key}` testTag so smoke walks
 *     distinguish "not yet mounted" from "mounted and broken".
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
            val viewModel = koinInject<FoodLogViewModel>()
            LaunchedEffect(viewModel) { viewModel.load() }
            FoodLogScreen(viewModel = viewModel)
        }
    }

    data object Pantry : DieticianScreen() {
        override val key: ScreenKey = "pantry"

        @Composable
        override fun Content() {
            val viewModel = koinInject<PantryViewModel>()
            PantryScreen(viewModel = viewModel)
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
            val viewModel = koinInject<CoachChatViewModel>()
            val navigator = LocalNavigator.currentOrThrow
            CoachChatScreen(
                viewModel = viewModel,
                onOpenAuditRow = { navigator.push(AuditLog) },
                onOpenSettings = { navigator.push(Settings) },
            )
        }
    }

    data object Settings : DieticianScreen() {
        override val key: ScreenKey = "settings"

        @Composable
        override fun Content() {
            val viewModel = koinInject<SettingsViewModel>()
            val navigator = LocalNavigator.currentOrThrow
            SettingsScreen(
                viewModel = viewModel,
                onOpenAuditLog = { navigator.push(AuditLog) },
            )
        }
    }

    data object AuditLog : DieticianScreen() {
        override val key: ScreenKey = "audit-log"

        @Composable
        override fun Content() {
            val viewModel = koinInject<AuditLogViewModel>()
            AuditLogScreen(viewModel = viewModel)
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

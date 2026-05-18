package com.dietician.shared.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.DieticianLocaleProvider
import com.dietician.shared.ui.i18n.Strings
import com.dietician.shared.ui.i18n.strings
import com.dietician.shared.ui.theme.DieticianTheme

/**
 * Top-level Dietician app entry point. Wraps theme + locale + Voyager
 * navigator + bottom nav scaffolding. Composables-only — platform launch
 * code in androidApp / desktopApp constructs this.
 */
@Composable
fun DieticianApp(
    locale: AppLocale = AppLocale.EN,
    darkTheme: Boolean = false,
    useAccessibleTypography: Boolean = false,
) {
    DieticianLocaleProvider(locale = locale) {
        DieticianTheme(darkTheme = darkTheme, useAccessibleTypography = useAccessibleTypography) {
            Navigator(screen = DieticianScreen.Home) {
                Scaffold(
                    bottomBar = { DieticianBottomNav() },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        CurrentScreen()
                    }
                }
            }
        }
    }
}

/**
 * Icon set for the bottom nav. We use `material-icons-core` only (avoids the
 * `material-icons-extended` 11MB dep). Mapping:
 *   Home  -> Home  | Food log -> List  | Pantry -> ShoppingCart
 *   Coach -> Person | Settings -> Settings
 * Real product icons get filled in by the design system task (later batch).
 */
private data class NavMeta(val icon: ImageVector, val label: String)

private fun bottomNavMeta(screen: DieticianScreen, s: Strings): NavMeta =
    when (screen) {
        is DieticianScreen.Home -> NavMeta(Icons.Filled.Home, s.nav_home)
        is DieticianScreen.FoodLog -> NavMeta(Icons.AutoMirrored.Filled.List, s.nav_food_log)
        is DieticianScreen.Pantry -> NavMeta(Icons.Filled.ShoppingCart, s.nav_pantry)
        is DieticianScreen.CoachChat -> NavMeta(Icons.Filled.Person, s.nav_coach)
        is DieticianScreen.Settings -> NavMeta(Icons.Filled.Settings, s.nav_settings)
        // Non-bottom-nav screens shouldn't be passed here; fall back to MailOutline.
        else -> NavMeta(Icons.Filled.MailOutline, screen.key)
    }

/**
 * Bottom navigation bar with 5 top-level tabs (per spec). Each item carries
 * a [testTag] of `"nav-{screen.key}"` so KMP UI tests can target it via
 * `onNodeWithTag("nav-home")` etc.
 *
 * **Active routing:** reads [LocalNavigator] from the surrounding [Navigator]
 * scope. `selected` reflects the navigator's `lastItem`; clicking a non-
 * selected tab calls `navigator.replaceAll(screen)` to keep the back stack
 * shallow (matches the spec's "5 tabs are roots, no nested back stack at
 * top level" guidance).
 */
@Composable
fun DieticianBottomNav() {
    val s: Strings = strings()
    val navigator = LocalNavigator.currentOrThrow
    val currentKey = navigator.lastItem.key
    NavigationBar(modifier = Modifier.testTag("dietician-bottom-nav")) {
        BottomNavScreens.forEach { screen ->
            val meta = bottomNavMeta(screen, s)
            val isSelected = currentKey == screen.key
            NavigationBarItem(
                selected = isSelected,
                onClick = { if (!isSelected) navigator.replaceAll(screen) },
                icon = { Icon(imageVector = meta.icon, contentDescription = meta.label) },
                label = { Text(meta.label) },
                modifier = Modifier.testTag("nav-${screen.key}"),
            )
        }
    }
}

package com.dietician.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dietician.desktop.di.desktopPlatformModule
import com.dietician.shared.Dietician
import com.dietician.shared.ui.di.uiModule
import com.dietician.shared.ui.nav.DieticianApp
import com.dietician.shared.ui.network.BaseUrlProvider
import com.dietician.shared.ui.network.networkModule
import com.dietician.shared.ui.platform.TailnetReachability
import com.dietician.shared.ui.screens.SplashScreen
import com.dietician.shared.ui.screens.TailscaleDisconnectedScreen
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Desktop entry point.
 *
 * Boot order:
 *   1. Start Koin with [networkModule] + [desktopPlatformModule] (guarded so a
 *      second `main()` invocation from a dev tool doesn't double-start).
 *   2. Open the single window.
 *   3. Run the [TailnetReachability] probe (RC16). null → splash; false →
 *      blocker; true → [DieticianApp].
 *
 * The Retry button on the blocker re-bumps `probeNonce` to re-run the probe.
 */
fun main() {
    bootKoin()
    application {
        val baseUrlProvider = remember { GlobalContext.get().get<BaseUrlProvider>() }
        Window(onCloseRequest = ::exitApplication, title = "Dietician ${Dietician.VERSION}") {
            var reachable by remember { mutableStateOf<Boolean?>(null) }
            var probeNonce by remember { mutableStateOf(0) }

            LaunchedEffect(probeNonce) {
                reachable = null
                reachable =
                    if (System.getenv("DIETICIAN_DEV_SKIP_REACHABILITY")?.lowercase() == "true") {
                        // Dev affordance — skip the RC16 probe when the VPS backend
                        // is not deployed yet so smoke walks aren't gated. Logged
                        // loud so a misconfigured prod build doesn't silently bypass.
                        @Suppress("ForbiddenComment")
                        println("[DIETICIAN] DEV_SKIP_REACHABILITY=true — bypassing Tailscale probe.")
                        true
                    } else {
                        TailnetReachability.check(baseUrlProvider.baseUrl)
                    }
            }

            when (reachable) {
                null -> SplashScreen()
                false -> TailscaleDisconnectedScreen(onRetry = { probeNonce += 1 })
                true -> DieticianApp()
            }
        }
    }
}

private fun bootKoin() {
    val existing = runCatching { GlobalContext.getOrNull() }.getOrNull()
    if (existing != null) return
    startKoin {
        modules(networkModule, uiModule, desktopPlatformModule)
    }
}

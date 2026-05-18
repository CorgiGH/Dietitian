package com.dietician.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.dietician.shared.ui.nav.DieticianApp
import com.dietician.shared.ui.network.BaseUrlProvider
import com.dietician.shared.ui.platform.AndroidPlatformHandle
import com.dietician.shared.ui.platform.TailnetReachability
import com.dietician.shared.ui.screens.SplashScreen
import com.dietician.shared.ui.screens.TailscaleDisconnectedScreen
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Single-activity host for Dietician. Renders one of three states:
 *
 *   1. **null reachability** → [SplashScreen] (in-flight probe).
 *   2. **`false`** → [TailscaleDisconnectedScreen] (RC16 — VPS unreachable
 *      behind Tailscale; user must open Tailscale + retry).
 *   3. **`true`** → [DieticianApp] (the full app — auth flow + bottom nav).
 *
 * The reachability probe ([TailnetReachability.check]) runs on first composition
 * with a 3s timeout. The Retry button on the blocker screen re-triggers the
 * probe via state bump.
 *
 * The platform handle ([AndroidPlatformHandle]) is installed here (rather than
 * in [DieticianAndroidApplication]) because we need a [androidx.lifecycle.LifecycleOwner]
 * for CameraX `bindToLifecycle` — only an Activity is. We pass `this` Activity as
 * the camera Context so CameraX bind succeeds.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Resolve Koin-bound CameraX + MediaStore facades from the running graph
        // (started by DieticianAndroidApplication). If Koin isn't running (e.g.
        // tests instantiate MainActivity directly without the Application boot),
        // fall back to constructing the facades inline.
        val koin = runCatching { GlobalContext.get() }.getOrNull()
        val cameraX = koin?.get<CameraXCapture>() ?: CameraXCapture(this)
        val saver = koin?.get<MediaStoreSaver>() ?: MediaStoreSaver(this)
        AndroidPlatformHandle.install(
            context = this,
            captureImage = {
                // Synchronous bridge — CameraX is async but the actual signature
                // is synchronous. In practice the receipt + food-log capture
                // screens drive this via a coroutine + dialog; tests inject a
                // fake via AndroidPlatformHandle.installForTest.
                kotlinx.coroutines.runBlocking { cameraX.capture() }
            },
            saveFile = { name, mime, bytes -> saver.save(name, mime, bytes) },
        )

        setContent {
            val scope = rememberCoroutineScope()
            val baseUrlProvider = remember { koin?.get<BaseUrlProvider>() ?: BaseUrlProvider() }
            var reachable by remember { mutableStateOf<Boolean?>(null) }
            var probeNonce by remember { mutableStateOf(0) }

            LaunchedEffect(probeNonce) {
                reachable = null
                reachable = TailnetReachability.check(baseUrlProvider.baseUrl)
            }

            when (reachable) {
                null -> SplashScreen()
                false -> TailscaleDisconnectedScreen(onRetry = {
                    scope.launch { probeNonce += 1 }
                })
                true -> DieticianApp()
            }
        }
    }
}

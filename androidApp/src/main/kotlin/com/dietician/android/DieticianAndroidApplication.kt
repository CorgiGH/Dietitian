package com.dietician.android

import android.app.Application
import com.dietician.android.di.androidPlatformModule
import com.dietician.shared.ui.network.networkModule
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Custom [Application] class that boots Koin with the cross-platform shared
 * modules ([networkModule] — HTTP repos + Ktor client) plus the Android
 * platform-specific bindings ([androidPlatformModule] — CameraX + MediaStore +
 * Clipboard + TailnetReachability handle).
 *
 * Wired via `android:name=".DieticianAndroidApplication"` in `AndroidManifest.xml`.
 *
 * **Koin start guard:** Robolectric tests instantiate this class via reflection
 * (lazy `Application` init). If Koin was already started by a prior test the
 * second [startKoin] throws `KoinAppAlreadyStartedException`. Guarding with
 * [GlobalContext.getOrNull] returns null when uninitialized and a started
 * KoinApplication otherwise, so we can safely skip the boot.
 *
 * We deliberately do NOT depend on `koin-android` (extra dep + only need the
 * Application-context handoff which we can do via a single `single { app }`
 * binding instead).
 */
class DieticianAndroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        bootKoin()
    }

    private fun bootKoin() {
        val existing = runCatching { GlobalContext.getOrNull() }.getOrNull()
        if (existing != null) return
        startKoin {
            modules(
                networkModule,
                androidPlatformModule(this@DieticianAndroidApplication),
            )
        }
    }
}

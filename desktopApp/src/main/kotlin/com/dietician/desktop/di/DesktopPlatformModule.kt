package com.dietician.desktop.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop Koin module.
 *
 * Currently a thin shell — the desktop captureImage / saveExportedFile actuals
 * use AWT `FileDialog` directly (no Context to inject) so there's nothing
 * platform-specific to bind beyond what [networkModule] already provides. The
 * module exists for symmetry with [com.dietician.android.di.AndroidPlatformModule]
 * and as a hook-point for future Desktop-only services (e.g. ClaudeMax CLI
 * subprocess pool, Playwright handle, whisper.cpp binding).
 */
val desktopPlatformModule: Module = module {
    // intentionally empty — symmetry placeholder
}

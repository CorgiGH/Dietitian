package com.dietician.shared.ui.network

/**
 * Desktop impl reads from the JVM process env or a `-D` system property
 * override + falls back to the Tailscale MagicDNS default. The system
 * property path lets `./gradlew :desktopApp:run -Pdietician.host=...` work
 * for dev iteration without touching the shell env.
 */
actual class BaseUrlProvider actual constructor() {
    actual val baseUrl: String =
        System.getProperty("dietician.host")?.takeIf { it.isNotBlank() }
            ?: System.getenv(DIETICIAN_HOST_OVERRIDE)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_VPS_HOST
}

package com.dietician.shared.ui.network

/**
 * Android impl reads from the process env (set by the launcher / dev shell) or
 * falls back to the Tailscale MagicDNS default.
 *
 * **Note on `BuildConfig`:** in a future patch we could inject `BASE_URL` via
 * Gradle's `buildConfigField` to bake the host into release builds. For Batch E
 * the env-var path is sufficient — Victor's dev environment sets
 * `DIETICIAN_HOST_OVERRIDE` and prod just uses the default.
 */
actual class BaseUrlProvider actual constructor() {
    actual val baseUrl: String =
        System.getenv(DIETICIAN_HOST_OVERRIDE)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_VPS_HOST
}

package com.dietician.shared.ui.network

/**
 * Source of the VPS base URL used by every Plan-3 endpoint client.
 *
 * **Why expect/actual:** Android reads from `BuildConfig.BASE_URL` injected at
 * build time + an env override; Desktop reads from a process env var (no
 * BuildConfig). Both fall back to the Tailscale MagicDNS hostname.
 *
 * The fallback is intentionally a `.ts.net` MagicDNS name so the device MUST
 * have Tailscale connected to resolve it — this is the RC16 invariant. If the
 * user sets [DIETICIAN_HOST_OVERRIDE] (e.g. on a LAN dev box) the override
 * bypasses Tailscale, but that's an explicit dev affordance.
 *
 * Usage:
 * ```kotlin
 * val client = HttpClient(engine) {
 *     defaultRequest { url(get<BaseUrlProvider>().baseUrl) }
 * }
 * ```
 */
expect class BaseUrlProvider() {
    val baseUrl: String
}

/** Env-var name read by [BaseUrlProvider] on both Android + Desktop. */
const val DIETICIAN_HOST_OVERRIDE: String = "DIETICIAN_HOST_OVERRIDE"

/** Fallback hostname when the override is unset. Tailscale MagicDNS form. */
const val DEFAULT_VPS_HOST: String = "https://victor-vps.tail-scale.ts.net:8081"

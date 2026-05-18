package com.dietician.shared.ui.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android engine = OkHttp. Reasons:
 *   - HTTP/2 + ALPN supported (Tailscale TLS handshake needs it).
 *   - Cookie jar survives process lifecycle correctly.
 *   - Plan-2 Batch B precedent — anywhere else in the codebase we use OkHttp
 *     on Android, CIO on JVM-only desktop.
 */
internal actual fun createPlatformHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(OkHttp) {
    configure()
}

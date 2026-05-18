package com.dietician.shared.ui.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO

/**
 * Desktop engine = CIO. Reasons:
 *   - KMP-pure, no extra JVM-only OkHttp dep.
 *   - Tailscale connection from a desktop VPN is a regular TCP+TLS stream;
 *     CIO handles it fine.
 *   - Avoids the OkHttp client lifecycle quirks (shutdown executor etc.) on
 *     Compose Desktop windows.
 */
internal actual fun createPlatformHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(CIO) {
    configure()
}

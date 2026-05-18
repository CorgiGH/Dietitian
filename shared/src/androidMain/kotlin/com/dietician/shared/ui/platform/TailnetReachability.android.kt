package com.dietician.shared.ui.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android impl of the RC16 Tailscale-reachability probe.
 *
 * Uses Ktor + OkHttp engine (matches the production [HttpClientFactory] choice on
 * Android — same TLS / HTTP/2 stack). Builds a one-shot client per probe so the
 * blocker doesn't share state with the authenticated client (we MUST NOT send
 * X-Subject-Id during the probe — the device may still be resolving Tailscale).
 *
 * Treats 200..299 + 401 as reachable. 401 = server reachable but auth missing,
 * which means Tailscale + TLS handshake worked. Anything else (DNS fail, connect
 * timeout, SSL handshake error) → false.
 */
actual object TailnetReachability {
    actual suspend fun check(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val client = HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 3_000
                connectTimeoutMillis = 3_000
                socketTimeoutMillis = 3_000
            }
            expectSuccess = false
        }
        try {
            val response = client.get { url("$baseUrl/health") }
            val code = response.status.value
            code in 200..299 || code == 401
        } catch (t: Throwable) {
            false
        } finally {
            client.close()
        }
    }
}

package com.dietician.shared.ui.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop impl of the RC16 Tailscale-reachability probe.
 *
 * Uses Ktor + CIO engine (matches the desktop [HttpClientFactory] choice — CIO is
 * KMP-pure and avoids the OkHttp JVM bloat we don't need on JVM-only desktop).
 *
 * Same semantics as the Android actual: 200..299 + 401 → reachable; everything
 * else → false. Probe completes in ≤3s.
 */
actual object TailnetReachability {
    actual suspend fun check(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val client = HttpClient(CIO) {
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

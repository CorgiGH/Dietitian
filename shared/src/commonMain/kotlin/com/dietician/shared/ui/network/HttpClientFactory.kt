package com.dietician.shared.ui.network

import com.dietician.shared.ui.auth.SessionInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cross-platform [HttpClient] factory used by every HTTP*Repository in
 * [com.dietician.shared.ui.data].
 *
 * Installed plugins:
 *   - [ContentNegotiation] (JSON) — kotlinx.serialization codec.
 *   - [WebSockets] — required by CrossDeviceVerifyListener (Plan-3 magic-link push).
 *   - [SessionInterceptor] — RC15 X-Subject-Id header (reads SessionStore on every
 *     request; no captured state).
 *   - [HttpTimeout] — 20s request / 5s connect default. Receipt-upload uses a
 *     longer per-call override.
 *
 * Engine selection is platform-specific via [createPlatformHttpClient]:
 *   - **Android** → `OkHttp` (proper HTTP/2 + TLS for Tailscale MagicDNS).
 *   - **Desktop** → `CIO` (KMP-pure, no extra JVM dep).
 *
 * **No `Logging` plugin** in production. Ktor's logger dumps request + response
 * bodies which would leak BYOK API keys in `logcat` / desktop console. If
 * Plan-4-5 ever ships a debug build flavour we'll gate `install(Logging)` on
 * that.
 */
object HttpClientFactory {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(baseUrlProvider: BaseUrlProvider): HttpClient =
        createPlatformHttpClient { applyShared() }
            .also {
                // baseUrlProvider is held by each repo (see HttpAuditRepository etc.);
                // we don't `defaultRequest { url(...) }` here because the repos
                // build full URLs and Ktor's defaultRequest url() merges in a
                // way that surprised tests during Batch B.
                @Suppress("UNUSED_PARAMETER")
                val ref = baseUrlProvider
            }

    /**
     * Shared HttpClient config block — installs all plugins. Engine-specific
     * configuration (proxy, follow-redirects, etc.) is layered by the platform
     * actual after this runs.
     */
    fun HttpClientConfig<*>.applyShared() {
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
        install(SessionInterceptor)
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 20_000
        }
        expectSuccess = false
    }
}

/**
 * Platform actual builds the engine-specific HttpClient. The lambda is applied
 * to the [HttpClientConfig] before client construction so [HttpClientFactory]'s
 * shared plugins land regardless of engine choice.
 */
internal expect fun createPlatformHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient

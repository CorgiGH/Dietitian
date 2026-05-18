package com.dietician.shared.ui.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * RC20 (Council 1779120600) — cross-device magic-link verify race.
 *
 * Scenario: user fills the email form on phone, opens magic link on laptop instead.
 * Without a signal back to the phone, the phone stays stuck on "Check your email".
 *
 * This listener opens a WebSocket to Plan-3 `/auth/verify-events` after the user
 * submits the email form. When ANY device (laptop, phone, etc.) verifies a link
 * for this email-derived bind key, the WS publishes a `verified` event that the
 * phone uses to auto-advance to Home.
 *
 * **Tolerance:** Plan-3 may not have shipped `/auth/verify-events` yet. The listener
 * gracefully tolerates `501 Not Implemented` (and any non-101 handshake) by sleeping
 * the [retryBackoffMillis] interval and retrying until [stop] is called. UI stays on
 * "Check your email" — same UX as without WS — but flips when the route lands.
 *
 * Plan-3.5 fix-up will ship the route; UI surface is unchanged.
 */
class CrossDeviceVerifyListener(
    private val http: HttpClient,
    private val baseUrl: String,
    private val retryBackoffMillis: Long = RETRY_BACKOFF_MS,
) {
    private val _events = MutableSharedFlow<VerifyEvent>(replay = 0, extraBufferCapacity = 4)
    val events: SharedFlow<VerifyEvent> = _events.asSharedFlow()

    private var job: Job? = null

    /**
     * Start subscribing. [scope] should be tied to the screen's lifecycle so the
     * connection is torn down when the user backs out.
     */
    fun start(scope: CoroutineScope, bindKey: String) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val gotConnection = runCatching { connectOnce(bindKey) }.isSuccess
                if (!gotConnection) {
                    // 501 / 404 / handshake failure — sleep + retry.
                    delay(retryBackoffMillis)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun connectOnce(bindKey: String) {
        // wss:// is mapped automatically by Ktor when baseUrl is https://.
        val wsUrl = baseUrl
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
            .trimEnd('/') + "/auth/verify-events?bindKey=$bindKey"
        http.webSocket(wsUrl) {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (text.contains("verified", ignoreCase = true)) {
                        _events.tryEmit(VerifyEvent.Verified)
                    }
                }
            }
        }
    }

    companion object {
        const val RETRY_BACKOFF_MS: Long = 5_000L
    }
}

/** Cross-device WebSocket events. */
sealed class VerifyEvent {
    /** A magic link with this [bindKey] just verified on some device. UI advances to Home. */
    data object Verified : VerifyEvent()
}

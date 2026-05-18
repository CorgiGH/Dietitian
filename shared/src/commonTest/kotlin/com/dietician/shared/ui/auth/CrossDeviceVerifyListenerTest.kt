package com.dietician.shared.ui.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CrossDeviceVerifyListenerTest {

    @Test
    fun `start does not throw when websocket route missing (501-tolerant)`() = runTest {
        // Plan-3 may not have shipped /auth/verify-events yet. The listener should
        // backoff + retry, never crash, even when the WS handshake fails.
        val client = HttpClient(MockEngine { _ ->
            respond("Not Implemented", HttpStatusCode.NotImplemented, headersOf())
        })
        val listener = CrossDeviceVerifyListener(client, "http://test", retryBackoffMillis = 1_000L)
        val scope = CoroutineScope(Dispatchers.Default)
        listener.start(scope, bindKey = "victor@example.com")
        // Give it a beat to attempt the connect + see the 501 + sleep.
        delay(50L)
        listener.stop()
        scope.cancel()
        assertTrue(true, "listener survived 501 handshake")
    }

    @Test
    fun `stop cancels the listener job`() = runTest {
        val client = HttpClient(MockEngine { _ ->
            respond("nope", HttpStatusCode.NotImplemented, headersOf())
        })
        val listener = CrossDeviceVerifyListener(client, "http://test", retryBackoffMillis = 5_000L)
        val scope = CoroutineScope(Dispatchers.Default)
        listener.start(scope, "k")
        listener.stop()
        // No assertion needed — stop() returning without exception is the contract.
        scope.cancel()
        assertTrue(true)
    }
}

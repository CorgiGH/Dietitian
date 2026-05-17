package com.dietician.shared.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WebSocketListener(
    private val wsUrl: String,
    private val httpFactory: () -> HttpClient,
    private val onTrigger: () -> Unit,
) {
    private val http: HttpClient by lazy { httpFactory().config { install(WebSockets) } }
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job =
            scope.launch {
                while (isActive(scope)) {
                    runCatching {
                        http.webSocket(wsUrl) {
                            for (frame in incoming) {
                                if (frame is Frame.Text && frame.readText().contains("\"new_events\"")) {
                                    onTrigger()
                                }
                            }
                        }
                    }
                    kotlinx.coroutines.delay(2_000) // reconnect backoff (will be replaced by RetryPolicy in Plan-3)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun isActive(scope: CoroutineScope): Boolean = scope.coroutineContext[Job]?.isActive == true
}

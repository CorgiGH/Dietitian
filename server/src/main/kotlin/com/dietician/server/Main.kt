package com.dietician.server

import com.dietician.shared.Dietician
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.websocket.WebSockets

/**
 * Dietician VPS backend.
 *
 * Binds Tailscale IP only (no public exposure).
 * Listen address read from DIETICIAN_BIND env (default "127.0.0.1"), port from DIETICIAN_PORT (default 8081).
 *
 * Endpoints per spec §6:
 *   POST /sync/push        — drain client outbox
 *   POST /sync/pull        — return new events since cursor
 *   POST /receipts/upload  — multipart image upload
 *   GET  /health           — health check for app-layer ping (Council 4 BREAK #3)
 *   GET  /diag/{device_id} — full diagnostic surface (Council 4 mandate)
 *   POST /jobs/queue       — enqueue heavy job (desktop pickup)
 *   POST /jobs/{id}/result — desktop posts back
 *   WS   /ws/sync          — push notifications + sync events
 */
fun main() {
    val bindHost = System.getenv("DIETICIAN_BIND") ?: "127.0.0.1"
    val bindPort = System.getenv("DIETICIAN_PORT")?.toIntOrNull() ?: 8081

    embeddedServer(CIO, host = bindHost, port = bindPort, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages)
    install(WebSockets)

    routing {
        get("/health") {
            call.respond(mapOf(
                "service" to "dietician-backend",
                "version" to Dietician.VERSION,
                "spec_date" to Dietician.SPEC_DATE,
                "status" to "ok"
            ))
        }
    }
}

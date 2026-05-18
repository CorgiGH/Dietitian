package com.dietician.server

import com.dietician.server.di.dieticianModule
import com.dietician.server.observability.installObservability
import com.dietician.server.routes.installAuthRoutes
import com.dietician.server.routes.installEmbedRoutes
import com.dietician.server.routes.installMeRoutes
import com.dietician.server.routes.installReceiptsRoutes
import com.dietician.server.routes.installSyncRoutes
import com.dietician.shared.Dietician
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/**
 * Wires the Ktor Application: Koin DI, JSON, observability, status pages,
 * call logging, websockets, plus the auth + health route trees.
 *
 * Endpoints per spec §6 (subset shipped in Batch A):
 *   GET  /health                       — service liveness
 *   POST /auth/magic-link/request      — email token (anti-enum 202 always)
 *   POST /auth/magic-link/verify       — consume token + issue cookie
 *   POST /auth/sign-out                — invalidate current session
 *   POST /auth/sign-out-all-sessions   — RC8 credential rotation
 *
 * Sync / receipts / jobs / WS land in subsequent batches; their stubs are
 * intentionally not pre-registered to avoid 200-on-empty-handler smoke
 * false-positives.
 */
fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(dieticianModule)
    }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }

    installObservability()

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "invalid_argument", "message" to (cause.message ?: "")),
            )
        }
        exception<NoSuchElementException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "not_found", "message" to (cause.message ?: "")),
            )
        }
        exception<SecurityException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("error" to "forbidden", "message" to (cause.message ?: "")),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled in handler", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "internal", "message" to "see server logs"),
            )
        }
    }

    install(CallLogging)
    install(WebSockets)

    routing {
        get("/health") {
            call.respond(
                mapOf(
                    "service" to "dietician-backend",
                    "version" to Dietician.VERSION,
                    "spec_date" to Dietician.SPEC_DATE,
                    "status" to "ok",
                ),
            )
        }
    }

    installAuthRoutes()
    installSyncRoutes()
    installReceiptsRoutes()
    installEmbedRoutes()
    installMeRoutes()
}

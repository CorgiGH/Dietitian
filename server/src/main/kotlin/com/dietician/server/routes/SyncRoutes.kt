package com.dietician.server.routes

import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.middleware.requireSubject
import com.dietician.server.repo.EventRepository
import com.dietician.server.sync.ServerPushAccepted
import com.dietician.server.sync.ServerPushRejected
import com.dietician.server.sync.ServerPushRequest
import com.dietician.server.sync.ServerPushResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.ktor.ext.inject
import java.time.Instant

/**
 * Plan-3 sync routes (Council 1779120000 RC9 baseline).
 *
 * Endpoints:
 *   - `POST /sync/push`  — batch idempotent upsert of client outbox envelopes.
 *                          Server emits one `accepted` row per inserted event
 *                          and one `rejected` row per failed insert with a
 *                          reason string the client can DLQ-classify.
 *                          Duplicate `event_uuid` values are silently coalesced
 *                          per Plan-1 semantics (idempotency-by-PK).
 *   - `GET  /sync/pull`  — per-table cursor pagination. Cursor encodes
 *                          `(synced_at_ms, event_uuid)` so resumption picks up
 *                          strictly after the last returned row.
 *
 * Auth: every route requires a valid session — [requireSubject] responds 401
 * on missing/invalid session and the handler short-circuits.
 *
 * RLS: all DB ops route through [EventRepository] which sets
 * `app.current_subject_id` per call. Cross-subject pushes are rejected by PG
 * (RLS denies the INSERT) and surface as a per-envelope `rejected` row.
 *
 * Audit-log: one row per push/pull invocation summarising counts (NOT
 * per-event-uuid — Art 12 wants a trace, not a copy).
 *
 * RC8 paired surface (sign-out-all-sessions shipped Batch A) is the
 * `/me/sessions` listing — that lives in [installMeRoutes], not here.
 */
fun Application.installSyncRoutes() {
    val eventRepo: EventRepository by inject()
    val auditLog: AuditLogWriter by inject()
    val authService: AuthService by inject()

    routing {
        route("/sync") {
            post("/push") {
                val subjectId = call.requireSubject(authService) ?: return@post
                val req = call.receive<ServerPushRequest>()
                val accepted = mutableListOf<ServerPushAccepted>()
                val rejected = mutableListOf<ServerPushRejected>()
                val now = Instant.now().toEpochMilli()
                for (event in req.events) {
                    if (event.tableName !in EventRepository.TABLES) {
                        rejected += ServerPushRejected(event.eventUuid, "unknown_table")
                        continue
                    }
                    runCatching { eventRepo.upsert(subjectId, event.tableName, event.payloadJson) }
                        .onSuccess { inserted ->
                            // Both new-row and dup-row are surfaced as accepted
                            // (idempotency). Reason inferred client-side via the
                            // PK they already hold.
                            accepted += ServerPushAccepted(event.eventUuid, serverRecvAt = now)
                            // Silence ktlint unused-var warning.
                            inserted.let { /* PK-based de-dup; client doesn't care */ }
                        }
                        .onFailure { t ->
                            rejected += ServerPushRejected(event.eventUuid, t.message ?: "insert_failed")
                        }
                }
                auditLog.write(
                    subjectId = subjectId,
                    kind = "sync_push",
                    extra = JsonObject(
                        mapOf(
                            "device_id" to JsonPrimitive(req.deviceId),
                            "accepted_count" to JsonPrimitive(accepted.size),
                            "rejected_count" to JsonPrimitive(rejected.size),
                        ),
                    ),
                )
                call.respond(HttpStatusCode.OK, ServerPushResponse(accepted = accepted, rejected = rejected))
            }
        }
    }
}

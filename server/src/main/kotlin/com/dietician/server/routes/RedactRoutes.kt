package com.dietician.server.routes

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.middleware.requireSubject
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.ktor.ext.inject
import org.postgresql.util.PGobject
import java.util.UUID

/**
 * Plan-3 Task 30: GDPR Art 17 redaction route.
 *
 * `DELETE /me/subject/{id}`:
 *   - Caller MUST be authenticated.
 *   - `{id}` MUST match the caller's own subject id (you cannot redact
 *     another subject — admin/operator redaction lives outside this route).
 *   - Invokes V015 `subject_redact(uuid)` plpgsql function which:
 *       * counts per-table rows for audit trail
 *       * emits a `tombstone_events` row
 *       * cascade-deletes pantry/meal/weight/receipt events
 *       * soft-deletes the `subjects` row (so tombstone reference holds)
 *   - Returns 200 + the counts JSONB the function emitted.
 *   - Writes an `AuditLogActions.SUBJECT_REDACT` row with `subject_id = NULL`
 *     (system context — the subject is now soft-deleted, can't pass RLS).
 *
 * The function returns JSONB; we parse it server-side into a [JsonObject]
 * so the wire shape matches Plan-1 expectations.
 */
fun Application.installRedactRoutes() {
    val authService: AuthService by inject()
    val auditLog: AuditLogWriter by inject()
    val db: DatabaseFactory by inject()

    routing {
        delete("/me/subject/{id}") {
            val callerSubject = call.requireSubject(authService) ?: return@delete
            val targetIdRaw = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "missing_subject_id"),
            )
            val targetId = runCatching { UUID.fromString(targetIdRaw) }.getOrNull()
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "invalid_subject_id"),
                )
            if (callerSubject != targetId) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "cannot_redact_other_subject"))
                return@delete
            }

            // Call V015 subject_redact() in system context — the function is
            // SECURITY DEFINER and operates across per-subject event tables.
            // Running outside the subject's RLS context here is intentional:
            // after the cascade deletes the subject's rows the same context
            // would return zero on the audit-log write that follows.
            val countsJson: JsonObject = db.withSystemContext { conn ->
                conn.prepareStatement("SELECT subject_redact(?)").use { ps ->
                    ps.setObject(1, targetId)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) JsonObject(emptyMap())
                        else {
                            val pg = rs.getObject(1)
                            val raw = (pg as? PGobject)?.value ?: pg.toString()
                            runCatching {
                                Json.parseToJsonElement(raw) as? JsonObject ?: JsonObject(emptyMap())
                            }.getOrElse { JsonObject(emptyMap()) }
                        }
                    }
                }
            }

            // subject_id = NULL on the audit row because the subject is now
            // soft-deleted — RLS would block the write under their own
            // context. The V018 policy explicitly allows NULL subject rows.
            auditLog.write(
                subjectId = null,
                kind = AuditLogActions.SUBJECT_REDACT,
                extra = JsonObject(
                    mapOf(
                        "target_subject_id" to JsonPrimitive(targetId.toString()),
                        "counts" to countsJson as JsonElement,
                    ),
                ),
            )

            call.respond(
                HttpStatusCode.OK,
                JsonObject(
                    mapOf(
                        "status" to JsonPrimitive("redacted"),
                        "counts" to countsJson,
                    ),
                ),
            )
        }
    }
}

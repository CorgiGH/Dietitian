// DiagResponse is a wire DTO; the file's purpose is installHealthRoutes()
@file:Suppress("MatchingDeclarationName")

package com.dietician.server.routes

import com.dietician.server.auth.AuthService
import com.dietician.server.auth.SessionStore
import com.dietician.server.cron.CronBootstrap
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.middleware.requireSubject
import com.dietician.server.net.TailnetDiscovery
import com.dietician.server.repo.HealthRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * Plan-3 Task 37/38 — deep `/health` + Victor-only `/diag`.
 *
 * `/health` is unauthenticated (intentionally) so Tailnet monitoring +
 * desktop client poll without a session. The response shape comes from
 * [HealthRepository.aggregate] and surfaces every signal listed in council
 * 1779120000 RC13.
 *
 * `/diag` is authenticated and additionally gated to the Victor subject id
 * (env `DIETICIAN_VICTOR_SUBJECT_ID`). Surfaces in-JVM cron schedule, active
 * session count, magic-link pending count, and tailnet IP. Useful when
 * something in `/health` looks wrong and Victor wants the next layer of
 * detail without having to SSH into the VPS.
 *
 * Why Victor-only: `cron_next_fires` exposes the schedule timetable, which
 * combined with knowledge of the audit-prune SQL effectively names the
 * window where stale rows are most likely to be examined. Not a P0 leak
 * for an n=1 system, but defaults are tight.
 */
@Serializable
@Suppress("ConstructorParameterNaming") // snake_case wire contract per RC13
data class DiagResponse(
    val schema_version: String,
    val audit_log_count_last_24h: Int,
    val active_sessions: Int,
    val magic_link_pending_count: Int,
    val cron_next_fires_epoch_seconds: Map<String, Long>,
    val tailnet_ip: String?,
)

/** Default Victor UUID if `DIETICIAN_VICTOR_SUBJECT_ID` env not set. */
private val DEFAULT_VICTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")

fun Application.installHealthRoutes() {
    val healthRepo: HealthRepository by inject()
    val db: DatabaseFactory by inject()
    val auth: AuthService by inject()
    val sessions: SessionStore by inject()
    val cron: CronBootstrap by inject()

    routing {
        get("/health/deep") {
            // Why /health/deep, not /health: the existing `/health` in
            // Application.kt returns a tiny liveness shape consumed by the
            // Ktor smoke test in Task 17. We don't break that contract; the
            // RC13 aggregate lives at /health/deep so monitoring can opt in.
            call.respond(healthRepo.aggregate())
        }

        get("/diag") {
            val subjectId = call.requireSubject(auth) ?: return@get
            val victorId = System.getenv("DIETICIAN_VICTOR_SUBJECT_ID")
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: DEFAULT_VICTOR_ID
            if (subjectId != victorId) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "victor_only"))
                return@get
            }

            val audit24h = db.withSystemContext { conn ->
                conn.createStatement().executeQuery(
                    "SELECT count(*) FROM audit_log WHERE occurred_at >= NOW() - INTERVAL '24 hours'",
                ).use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
            val schemaVersion = db.withSystemContext { conn ->
                conn.createStatement().executeQuery(
                    "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                ).use { rs -> if (rs.next()) rs.getString(1) else null }
            } ?: "unknown"
            // Magic-link "pending" = MagicLinkService keeps in-memory; the
            // count is not surfaced via a public API today. Plan-3.5 moves
            // magic links into Postgres; until then surface -1 so the field
            // is present without lying.
            val magicLinkPending = -1
            // Tailnet discovery returns "0.0.0.0" or similar in dev. Surface
            // raw so Victor can see what the binder saw.
            val tailnetIp = runCatching { TailnetDiscovery.discover() }.getOrNull()

            call.respond(
                DiagResponse(
                    schema_version = schemaVersion,
                    audit_log_count_last_24h = audit24h,
                    active_sessions = sessions.activeCount(),
                    magic_link_pending_count = magicLinkPending,
                    cron_next_fires_epoch_seconds = cron.nextFires(),
                    tailnet_ip = tailnetIp,
                ),
            )
        }
    }
}

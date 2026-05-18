package com.dietician.server.routes

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.auth.SessionStore
import com.dietician.server.middleware.requireSubject
import com.dietician.server.repo.BudgetRepository
import com.dietician.server.repo.ConsentRepository
import com.dietician.server.repo.CredentialRepository
import com.dietician.server.repo.SubjectRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.ktor.ext.inject

/**
 * Plan-3 Task 29: /me/... self-management surface.
 *
 * Routes (council 1779120000 RC1 + RC8 + RC12):
 *   - GET    /me                         — subject profile + has_byok + trial_remaining
 *   - POST   /me/byok                    — store BYOK key (pgcrypto encrypted)
 *   - DELETE /me/byok/{provider}         — revoke a credential
 *   - POST   /me/consent                 — grant/withdraw a consent scope
 *   - GET    /me/sessions                — RC8 second half: list active sessions
 *
 * All routes require an authenticated session. Cross-subject mutation is
 * impossible because every repo call routes through RLS-scoped helpers.
 *
 * Audit emission on every state change (GDPR Art 30 + AI Act Art 12):
 *   - CONSENT_GRANT / CONSENT_WITHDRAW
 *   - SUBJECT_CREDENTIAL_REVOKED (on DELETE /me/byok)
 *   - 'credential_grant' (BYOK upsert; new constant locally — Plan-2 may
 *     promote it into AuditLogActions when the moderator surface ships).
 */
@Serializable
data class ByokRequest(val provider: String, val key: String)

@Serializable
data class ConsentRequest(val scope: String, val granted: Boolean)

@Serializable
data class SessionRow(
    val sessionId: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
)

fun Application.installMeRoutes() {
    val authService: AuthService by inject()
    val subjects: SubjectRepository by inject()
    val credentials: CredentialRepository by inject()
    val consents: ConsentRepository by inject()
    val budgets: BudgetRepository by inject()
    val sessions: SessionStore by inject()
    val auditLog: AuditLogWriter by inject()

    routing {
        route("/me") {
            get {
                val subjectId = call.requireSubject(authService) ?: return@get
                val subject = subjects.findById(subjectId)
                if (subject == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "subject_not_found"))
                    return@get
                }
                val creds = credentials.listForSubject(subjectId).filter { it.isActive }
                val trialRemaining = budgets.remainingThisPeriod(subjectId, provider = "voyage")
                call.respond(
                    HttpStatusCode.OK,
                    JsonObject(
                        mapOf(
                            "subject_id" to JsonPrimitive(subject.subjectId.toString()),
                            "display_name" to JsonPrimitive(subject.displayName),
                            "email_for_magic_link" to (
                                subject.emailForMagicLink?.let { JsonPrimitive(it) } ?: JsonNull
                                ),
                            "has_byok" to JsonPrimitive(creds.isNotEmpty()),
                            "byok_providers" to JsonObject(
                                creds.associate { c -> c.provider to JsonPrimitive(c.createdAt.toString()) },
                            ),
                            "trial_queries_remaining_voyage" to (
                                trialRemaining?.let { JsonPrimitive(it) } ?: JsonNull
                                ),
                        ),
                    ),
                )
            }

            post("/byok") {
                val subjectId = call.requireSubject(authService) ?: return@post
                val req = call.receive<ByokRequest>()
                if (req.provider !in ALLOWED_PROVIDERS) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "invalid_provider", "allowed" to ALLOWED_PROVIDERS.joinToString(",")),
                    )
                    return@post
                }
                if (req.key.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "empty_key"))
                    return@post
                }
                credentials.upsert(subjectId, req.provider, req.key)
                auditLog.write(
                    subjectId = subjectId,
                    kind = "credential_grant",
                    extra = JsonObject(mapOf("provider" to JsonPrimitive(req.provider))),
                )
                call.respond(HttpStatusCode.OK, mapOf("status" to "stored"))
            }

            delete("/byok/{provider}") {
                val subjectId = call.requireSubject(authService) ?: return@delete
                val provider = call.parameters["provider"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "missing_provider"),
                )
                val rows = credentials.revoke(subjectId, provider)
                auditLog.write(
                    subjectId = subjectId,
                    kind = AuditLogActions.SUBJECT_CREDENTIAL_REVOKED,
                    extra = JsonObject(
                        mapOf(
                            "provider" to JsonPrimitive(provider),
                            "rows_revoked" to JsonPrimitive(rows),
                        ),
                    ),
                )
                if (rows == 0) {
                    call.respond(HttpStatusCode.NotFound, mapOf("status" to "no_active_credential"))
                } else {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "revoked"))
                }
            }

            post("/consent") {
                val subjectId = call.requireSubject(authService) ?: return@post
                val req = call.receive<ConsentRequest>()
                if (req.scope !in ALLOWED_CONSENT_SCOPES) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "invalid_scope", "allowed" to ALLOWED_CONSENT_SCOPES.joinToString(",")),
                    )
                    return@post
                }
                if (req.granted) {
                    consents.grant(subjectId, req.scope, versionHash = CONSENT_VERSION_HASH)
                    auditLog.write(
                        subjectId = subjectId,
                        kind = AuditLogActions.CONSENT_GRANT,
                        extra = JsonObject(
                            mapOf(
                                "scope" to JsonPrimitive(req.scope),
                                "version_hash" to JsonPrimitive(CONSENT_VERSION_HASH),
                            ),
                        ),
                    )
                    call.respond(HttpStatusCode.OK, mapOf("status" to "granted"))
                } else {
                    val rows = consents.withdraw(subjectId, req.scope)
                    auditLog.write(
                        subjectId = subjectId,
                        kind = AuditLogActions.CONSENT_WITHDRAW,
                        extra = JsonObject(
                            mapOf(
                                "scope" to JsonPrimitive(req.scope),
                                "rows_withdrawn" to JsonPrimitive(rows),
                            ),
                        ),
                    )
                    call.respond(HttpStatusCode.OK, mapOf("status" to "withdrawn"))
                }
            }

            get("/sessions") {
                val subjectId = call.requireSubject(authService) ?: return@get
                val rows = sessions.listForSubject(subjectId).map { s ->
                    SessionRow(
                        sessionId = s.sessionId,
                        createdAtMs = s.createdAt.toEpochMilli(),
                        expiresAtMs = s.expiresAt.toEpochMilli(),
                    )
                }
                call.respond(HttpStatusCode.OK, rows)
            }
        }
    }
}

/** V019 CHECK constraint domain — mirror to fail-fast at the route layer. */
private val ALLOWED_PROVIDERS = setOf("openrouter", "anthropic", "gemini", "groq")

/** V016 CHECK constraint domain — mirror to fail-fast at the route layer. */
private val ALLOWED_CONSENT_SCOPES = setOf(
    "process_meal_data",
    "process_weight_data",
    "process_voice_memos",
    "export_to_anelis",
    "share_with_friends",
)

/**
 * Versioned hash for the consent text the subject is granting/withdrawing.
 * Placeholder until Plan-3.5 introduces the per-locale consent-text registry;
 * for first-ship this single hash is acceptable per the threat model.
 */
private const val CONSENT_VERSION_HASH = "v1-2026-05-18"

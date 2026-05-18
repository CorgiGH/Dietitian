package com.dietician.server.routes

import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.middleware.RateLimiter
import com.dietician.server.middleware.requireSubject
import com.dietician.server.repo.BudgetRepository
import com.dietician.server.repo.SubjectRepository
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.ktor.ext.inject
import java.time.Duration
import java.util.UUID

/**
 * Plan-3 Task 28: POST /embed.
 *
 * Per Council 1779120000 RC12 + RC9: first-ship subset is a 501-stub UNTIL
 * Plan-2 EmbeddingService lands. The route DOES exercise the per-call
 * scaffolding (rate-limit + budget ceiling + audit-log) so the stub is a
 * production-shaped placeholder, not a no-op handler.
 *
 * Flow:
 *   1. Authenticate via session cookie.
 *   2. RL: 30 req/min/subject (non-Victor) or 300/min (Victor exempt-tier).
 *      Victor recognition: `DIETICIAN_VICTOR_SUBJECT_ID` env var matches the
 *      authenticated subject id. Env unset → no exempt tier.
 *   3. Budget: V019 `consume_or_fail(voyage, tokens, 1¢)` returns false →
 *      402 Payment Required.
 *   4. Audit row `embed_request` with text length.
 *   5. Respond 501 NotImplemented + stub body — Plan-2 EmbeddingService is
 *      the missing piece.
 *
 * The token estimate is a `text.length / 4` rough heuristic — adequate for
 * the 501-stub period since no real Voyage call fires. Plan-2 replaces it
 * with the actual tokenizer round-trip.
 */
@Serializable
data class EmbedRequest(val text: String, val corpus: String)

fun Application.installEmbedRoutes() {
    val authService: AuthService by inject()
    val rateLimiter: RateLimiter by inject()
    val budgetRepo: BudgetRepository by inject()
    val auditLog: AuditLogWriter by inject()
    val subjects: SubjectRepository by inject()

    val victorSubjectIdEnv = System.getenv("DIETICIAN_VICTOR_SUBJECT_ID")
        ?: System.getProperty("DIETICIAN_VICTOR_SUBJECT_ID")

    routing {
        post("/embed") {
            val subjectId = call.requireSubject(authService) ?: return@post

            val isVictor = victorSubjectIdEnv != null &&
                runCatching { UUID.fromString(victorSubjectIdEnv) == subjectId }.getOrDefault(false)
            val perMinuteLimit = if (isVictor) VICTOR_LIMIT_PER_MIN else DEFAULT_LIMIT_PER_MIN

            if (!rateLimiter.permit(
                    scope = SCOPE,
                    key = subjectId.toString(),
                    limit = perMinuteLimit,
                    window = Duration.ofMinutes(1),
                )
            ) {
                call.response.headers.append(HttpHeaders.RetryAfter, "60")
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    mapOf("error" to "rate_limit_per_minute"),
                )
                return@post
            }

            val req = call.receive<EmbedRequest>()
            val tokensNeeded = estimateTokens(req.text)
            val budgetOk = budgetRepo.consumeOrFail(
                subjectId = subjectId,
                provider = PROVIDER,
                tokensNeeded = tokensNeeded,
                costCentsEstimated = 1,
            )
            if (!budgetOk) {
                call.respond(
                    HttpStatusCode.PaymentRequired,
                    mapOf("error" to "budget_exhausted"),
                )
                return@post
            }

            // Keep the subjects ref read so DI verifies on first request — also
            // tells us where Plan-2 will plug the EmbeddingService.
            subjects.findById(subjectId)

            auditLog.write(
                subjectId = subjectId,
                kind = "embed_request",
                extra = JsonObject(
                    mapOf(
                        "text_len" to JsonPrimitive(req.text.length),
                        "corpus" to JsonPrimitive(req.corpus),
                        "tokens_estimate" to JsonPrimitive(tokensNeeded),
                    ),
                ),
            )

            call.respond(
                HttpStatusCode.NotImplemented,
                mapOf(
                    "status" to "stub",
                    "message" to "Plan-2 EmbeddingService not yet wired",
                ),
            )
        }
    }
}

/** Naive token estimate — replaced by Plan-2 tokenizer when the service ships. */
internal fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)

private const val SCOPE = "embed"
private const val PROVIDER = "voyage"
private const val DEFAULT_LIMIT_PER_MIN = 30
private const val VICTOR_LIMIT_PER_MIN = 300

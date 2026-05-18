package com.dietician.server.routes

import com.dietician.server.auth.AuthService
import com.dietician.server.middleware.RateLimiter
import com.dietician.server.middleware.SESSION_COOKIE
import com.dietician.server.middleware.extractSessionId
import com.dietician.server.middleware.requireSubject
import com.dietician.server.observability.Counters
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Wire shapes for the magic-link auth routes.
 *
 * Notes:
 *   - `email` is normalized lower-cased server-side; clients may submit any
 *     case.
 *   - Anti-enumeration: `/auth/magic-link/request` returns the SAME response
 *     shape for known and unknown emails.
 */
@Serializable
data class MagicLinkRequest(val email: String)

@Serializable
data class MagicLinkRequestResponse(val status: String = "sent_if_exists")

@Serializable
data class MagicLinkVerifyRequest(val token: String)

@Serializable
data class MagicLinkVerifyResponse(
    val sessionId: String,
    val subjectId: String,
    val expiresAtMs: Long,
)

@Serializable
data class SignOutAllResponse(val sessionsKilled: Int)

/**
 * Registers the `/auth/...` route tree. Called from
 * [com.dietician.server.module] inside `routing { ... }`.
 *
 * Routes (Council 1779120000 RC1 + RC8):
 *   - `POST /auth/magic-link/request`     — 202 always (anti-enumeration);
 *                                           per-email rate-limited
 *                                           (5 req/hour).
 *   - `POST /auth/magic-link/verify`      — 200 with session, sets cookie;
 *                                           401 on invalid/expired token.
 *   - `POST /auth/sign-out`               — invalidate the caller's session.
 *   - `POST /auth/sign-out-all-sessions`  — RC8 credential rotation; kills
 *                                           every session for the subject.
 *
 * Cookie shape: `HttpOnly; Secure; SameSite=Strict; Path=/`. Cookie name
 * lives in [SESSION_COOKIE].
 */
fun Application.installAuthRoutes() {
    val auth: AuthService by inject()
    val rl: RateLimiter by inject()

    routing {
        route("/auth") {
            post("/magic-link/request") {
                val req = call.receive<MagicLinkRequest>()
                val normalized = req.email.trim().lowercase()
                // Per-email anti-spam: 5 req/hour.
                if (!rl.permit(
                        scope = "magic-link-request",
                        key = normalized,
                        limit = RateLimiter.MAGIC_LINK_LIMIT,
                        window = RateLimiter.MAGIC_LINK_WINDOW,
                    )
                ) {
                    // Same 202 shape — do not leak "rate limited" because a 429
                    // for an unknown email would reveal it was tried multiple
                    // times. Silent drop preserves anti-enumeration.
                    call.respond(HttpStatusCode.Accepted, MagicLinkRequestResponse())
                    return@post
                }
                auth.requestMagicLink(normalized)
                call.respond(HttpStatusCode.Accepted, MagicLinkRequestResponse())
            }

            post("/magic-link/verify") {
                val req = call.receive<MagicLinkVerifyRequest>()
                val session = auth.verifyMagicLink(req.token)
                if (session == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "invalid_or_expired_token"),
                    )
                    return@post
                }
                Counters.authSignInTotal.increment()
                call.response.cookies.append(
                    Cookie(
                        name = SESSION_COOKIE,
                        value = session.sessionId,
                        path = "/",
                        httpOnly = true,
                        secure = true,
                        extensions = mapOf("SameSite" to "Strict"),
                    ),
                )
                call.respond(
                    HttpStatusCode.OK,
                    MagicLinkVerifyResponse(
                        sessionId = session.sessionId,
                        subjectId = session.subjectId.toString(),
                        expiresAtMs = session.expiresAt.toEpochMilli(),
                    ),
                )
            }

            post("/sign-out") {
                val sessionId = call.extractSessionId()
                if (sessionId == null) {
                    call.respond(HttpStatusCode.NoContent)
                    return@post
                }
                auth.signOut(sessionId)
                Counters.authSignOutTotal.increment()
                call.response.cookies.append(
                    Cookie(name = SESSION_COOKIE, value = "", path = "/", maxAge = 0),
                )
                call.respond(HttpStatusCode.NoContent)
            }

            post("/sign-out-all-sessions") {
                val subjectId = call.requireSubject(auth) ?: return@post
                val killed = auth.signOutAll(subjectId)
                Counters.authSignOutAllTotal.increment()
                call.response.cookies.append(
                    Cookie(name = SESSION_COOKIE, value = "", path = "/", maxAge = 0),
                )
                call.respond(HttpStatusCode.OK, SignOutAllResponse(sessionsKilled = killed))
            }
        }
    }
}

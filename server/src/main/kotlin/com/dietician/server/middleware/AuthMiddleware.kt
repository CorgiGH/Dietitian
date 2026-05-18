package com.dietician.server.middleware

import com.dietician.server.auth.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import java.util.UUID

/** Cookie name for the magic-link session id. */
const val SESSION_COOKIE = "dietician_session"

/**
 * Per-call attribute holding the authenticated subject id. Routes call
 * [requireSubject] (which throws 401) or [optionalSubject] (which returns
 * null for unauth) instead of poking attributes directly.
 */
val SubjectIdKey: AttributeKey<UUID> = AttributeKey("dietician.subject_id")
val SessionIdKey: AttributeKey<String> = AttributeKey("dietician.session_id")

/**
 * Extracts the session id from cookie (preferred) or `Authorization: Bearer`
 * header (CLI / mobile fallback). Returns null if no candidate string found.
 *
 * Cookie wins so the browser flow doesn't need an explicit Authorization
 * header.
 */
fun ApplicationCall.extractSessionId(): String? {
    val cookie = request.cookies[SESSION_COOKIE]?.takeIf { it.isNotBlank() }
    if (cookie != null) return cookie
    val bearer = request.header("Authorization")?.removePrefix("Bearer ")?.trim()
    return bearer?.takeIf { it.isNotBlank() }
}

/**
 * Resolves the session id to a subject id via [AuthService.currentSubject].
 * Stores both on call attributes for downstream handlers. Returns the subject
 * id, or null if the session is missing / invalid / expired.
 *
 * Routes that need an authenticated caller should use [requireSubject] which
 * delegates here + responds 401 on null.
 */
fun ApplicationCall.resolveSubject(authService: AuthService): UUID? {
    val sessionId = extractSessionId() ?: return null
    val subjectId = authService.currentSubject(sessionId) ?: return null
    attributes.put(SessionIdKey, sessionId)
    attributes.put(SubjectIdKey, subjectId)
    return subjectId
}

/**
 * Resolves the session and responds 401 if unauthenticated. Returns the
 * authenticated subject id on success; on failure the response is already
 * sent and the caller must `return@get`/etc.
 */
suspend fun ApplicationCall.requireSubject(authService: AuthService): UUID? {
    val s = resolveSubject(authService)
    if (s == null) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthenticated"))
    }
    return s
}

package com.dietician.server.auth

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.repo.SubjectRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Magic-link-only auth orchestrator (Council 1779120000 RC1; passkey
 * paths deferred to Plan-3.5).
 *
 * Surface:
 *   - [requestMagicLink] — issues + emails a time-limited token.
 *   - [verifyMagicLink]  — consumes token + creates a session.
 *   - [signOut]          — invalidates a single session.
 *   - [signOutAll]       — invalidates every session for a subject
 *     (council RC8 credential-rotation flow).
 *   - [currentSubject]   — middleware extracts subject from cookie/Bearer.
 *
 * Audit-log emission per AI Act Art 12:
 *   - SIGN_IN on successful verifyMagicLink.
 *   - SIGN_OUT_ALL_SESSIONS on signOutAll.
 *
 * Anti-enumeration: [requestMagicLink] returns the same shape regardless
 * of whether [email] maps to a known subject (the email is sent only if
 * the address matches an active subject row). Caller cannot probe for
 * registered emails.
 */
class AuthService(
    private val subjects: SubjectRepository,
    private val magicLinks: MagicLinkService,
    private val sessions: SessionStore,
    private val email: EmailSender,
    private val audit: AuditLogWriter,
    private val fromAddress: String = System.getenv("DIETICIAN_MAIL_FROM") ?: "onboarding@resend.dev",
    private val magicLinkBaseUrl: String = System.getenv("DIETICIAN_MAGIC_LINK_BASE")
        ?: "https://dietician.local/auth/magic-link",
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    data class MagicLinkOutcome(val emailSent: Boolean, val knownSubject: Boolean)

    /**
     * Generates + emails a magic-link token for [emailAddress] IF that
     * email matches an active subject. Returns a uniform outcome shape so
     * the route can respond identically in both cases (no enumeration).
     */
    suspend fun requestMagicLink(emailAddress: String): MagicLinkOutcome {
        val normalized = emailAddress.trim().lowercase()
        val subject = subjects.findByEmail(normalized)
        if (subject == null) {
            // Do NOT issue + send for unknown emails. Outcome shape stays
            // uniform; caller returns 202 either way.
            log.debug("magic-link requested for unknown email — silently ignored")
            return MagicLinkOutcome(emailSent = false, knownSubject = false)
        }
        val issued = magicLinks.issue(normalized)
        val sent = try {
            email.send(
                to = normalized,
                from = fromAddress,
                subject = "Your Dietician sign-in link",
                htmlBody = htmlBody(issued.plaintextToken, subject.displayName),
            )
        } catch (e: Throwable) {
            log.warn("magic-link send failed: {}", e.message)
            false
        }
        return MagicLinkOutcome(emailSent = sent, knownSubject = true)
    }

    /**
     * Consumes [token] and creates a session. Emits SIGN_IN audit row.
     * Returns null if the token is invalid / expired / already used.
     */
    fun verifyMagicLink(token: String): SessionStore.Session? {
        val email = magicLinks.verifyAndConsume(token) ?: return null
        val subject = subjects.findByEmail(email) ?: return null
        val session = sessions.create(subject.subjectId)
        audit.write(
            subjectId = subject.subjectId,
            kind = AuditLogActions.SIGN_IN,
            extra = JsonObject(mapOf("method" to JsonPrimitive("magic_link"))),
        )
        return session
    }

    fun signOut(sessionId: String): Boolean = sessions.invalidate(sessionId)

    /**
     * Invalidates every session for [subjectId]. Emits
     * SIGN_OUT_ALL_SESSIONS audit row even when zero sessions are killed
     * (so the audit trail records the operator's intent).
     */
    fun signOutAll(subjectId: UUID): Int {
        val n = sessions.invalidateAllFor(subjectId)
        audit.write(
            subjectId = subjectId,
            kind = AuditLogActions.SIGN_OUT_ALL_SESSIONS,
            extra = JsonObject(mapOf("sessions_killed" to JsonPrimitive(n))),
        )
        return n
    }

    /** Middleware helper — resolve session cookie/bearer to a subject id. */
    fun currentSubject(sessionId: String?): UUID? {
        if (sessionId == null) return null
        return sessions.get(sessionId)?.subjectId
    }

    private fun htmlBody(token: String, displayName: String): String {
        // Token is high-entropy random + URL-safe-base64 — no further
        // encoding needed for the query parameter.
        val link = "$magicLinkBaseUrl?token=$token"
        // Plaintext fallback intentionally omitted from this HTML body;
        // Resend can be configured to auto-derive text from HTML.
        return """
            <p>Hi $displayName,</p>
            <p>Click the link below to sign in to Dietician. The link expires in 15 minutes
            and can only be used once.</p>
            <p><a href="$link">Sign in to Dietician</a></p>
            <p>If you didn't request this, you can ignore this email.</p>
        """.trimIndent()
    }
}

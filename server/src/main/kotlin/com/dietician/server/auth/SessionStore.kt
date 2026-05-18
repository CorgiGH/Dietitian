package com.dietician.server.auth

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory session store for magic-link-only auth (Council 1779120000 RC1).
 *
 * V1 ships single-node — a `dietician-backend.service` restart logs every
 * subject out. Documented in `docs/runbooks/restart.md`. Plan-3.5 moves
 * sessions to a persistent `sessions` table; this file becomes a thin
 * memory cache + DB writer at that point.
 *
 * Session id format: 32 random bytes URL-safe-base64-encoded (no padding).
 * Backed by `SecureRandom`. Tokens are NEVER logged.
 *
 * Threading: `ConcurrentHashMap` for read/write; expiry sweep is
 * lazy-on-read (no background scheduler — keeps the surface minimal for
 * V1; Plan-3.5 can add a cron sweep when sessions move to PG).
 */
class SessionStore(
    private val ttl: Duration = Duration.ofDays(30),
    private val clock: Clock = Clock.systemUTC(),
) {
    data class Session(
        val sessionId: String,
        val subjectId: UUID,
        val createdAt: Instant,
        val expiresAt: Instant,
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val random = SecureRandom()

    fun create(subjectId: UUID): Session {
        val now = clock.instant()
        val raw = ByteArray(32).also { random.nextBytes(it) }
        val id = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val s = Session(
            sessionId = id,
            subjectId = subjectId,
            createdAt = now,
            expiresAt = now.plus(ttl),
        )
        sessions[id] = s
        return s
    }

    /**
     * Returns the session iff it exists AND is unexpired. Lazily evicts
     * expired entries.
     */
    fun get(sessionId: String): Session? {
        val s = sessions[sessionId] ?: return null
        if (clock.instant().isAfter(s.expiresAt)) {
            sessions.remove(sessionId, s)
            return null
        }
        return s
    }

    fun invalidate(sessionId: String): Boolean = sessions.remove(sessionId) != null

    /**
     * Invalidates EVERY session for [subjectId]. Used by `/auth/sign-out-all-sessions`
     * (council 1779120000 RC8 credential-rotation flow). Returns the count
     * of sessions removed.
     */
    fun invalidateAllFor(subjectId: UUID): Int {
        var removed = 0
        sessions.entries.removeIf {
            if (it.value.subjectId == subjectId) {
                removed++
                true
            } else {
                false
            }
        }
        return removed
    }

    /** Test/debug — total active count (after lazy eviction). */
    fun activeCount(): Int {
        val now = clock.instant()
        sessions.entries.removeIf { now.isAfter(it.value.expiresAt) }
        return sessions.size
    }

    /**
     * Returns all active (non-expired) sessions for [subjectId]. Used by
     * `GET /me/sessions` (RC8 paired surface with `/auth/sign-out-all-sessions`)
     * to render the per-subject session table. Order is undefined.
     *
     * Session ids ARE included in the returned shape. The endpoint that
     * serializes them gates them behind authentication, so the disclosure is
     * to the session-owner only. (Don't widen the surface to admin-style
     * cross-subject listing without revisiting that decision.)
     */
    fun listForSubject(subjectId: UUID): List<Session> {
        val now = clock.instant()
        sessions.entries.removeIf { now.isAfter(it.value.expiresAt) }
        return sessions.values.filter { it.subjectId == subjectId }
    }
}

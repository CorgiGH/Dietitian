package com.dietician.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory magic-link token store + verification (Council 1779120000 RC1
 * — magic-link only auth).
 *
 * Token lifecycle:
 *   1. `issue(email)` — generates a 32-byte URL-safe random token,
 *      hashes it via SHA-256, stores `(emailHash → IssuedToken)` keyed by
 *      the SHA-256 of the token (so the server NEVER stores the plaintext
 *      token side-by-side with the email).
 *   2. `verifyAndConsume(token)` — hashes the token, looks up the entry,
 *      checks expiry + single-use, removes on success.
 *
 * TTL is short (default 15 minutes). Single-use: verify removes the entry
 * atomically.
 *
 * V1 first-ship: in-memory. Sessions evaporate on backend restart — the
 * user requests a new link, no UX issue. Plan-3.5 moves to a
 * `magic_link_pending` table (PG canonical) for multi-node + restart
 * survivability.
 */
class MagicLinkService(
    private val ttl: Duration = Duration.ofMinutes(15),
    private val clock: Clock = Clock.systemUTC(),
) {
    data class IssuedToken(
        /** Plaintext token — returned to caller for the email body ONLY. */
        val plaintextToken: String,
        /** SHA-256 of plaintext — used as the storage key. Plaintext is NOT stored. */
        val tokenHashHex: String,
        val email: String,
        val expiresAt: Instant,
    )

    /** Stored row — note absence of plaintext token. */
    private data class PendingRow(
        val email: String,
        val expiresAt: Instant,
    )

    private val pending = ConcurrentHashMap<String, PendingRow>()
    private val random = SecureRandom()

    /**
     * Generates a fresh magic-link token for [email]. Returns the plaintext
     * token (for embedding in the email URL) plus storage metadata. The
     * service stores only the SHA-256 of the token.
     */
    fun issue(email: String): IssuedToken {
        val raw = ByteArray(32).also { random.nextBytes(it) }
        val plain = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val hash = sha256Hex(plain)
        val now = clock.instant()
        val expires = now.plus(ttl)
        pending[hash] = PendingRow(email = email.trim().lowercase(), expiresAt = expires)
        return IssuedToken(
            plaintextToken = plain,
            tokenHashHex = hash,
            email = email.trim().lowercase(),
            expiresAt = expires,
        )
    }

    /**
     * Atomically verifies [plaintextToken]. Returns the associated email if
     * the token is valid + unexpired + unused (it is consumed on success).
     * Returns null otherwise. Constant-time comparison is not strictly
     * required since we look up by hash, but expiry / not-found are
     * indistinguishable to the caller by design (no enumeration leak).
     */
    fun verifyAndConsume(plaintextToken: String): String? {
        val hash = sha256Hex(plaintextToken)
        val row = pending.remove(hash) ?: return null
        if (clock.instant().isAfter(row.expiresAt)) return null
        return row.email
    }

    /** Test/debug — pending entry count after lazy expiry sweep. */
    fun pendingCount(): Int {
        val now = clock.instant()
        pending.entries.removeIf { now.isAfter(it.value.expiresAt) }
        return pending.size
    }

    companion object {
        private fun sha256Hex(s: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

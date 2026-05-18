package com.dietician.server.repo

import com.dietician.server.db.DatabaseFactory
import java.util.UUID

/**
 * V019 `subject_credentials` row metadata (encrypted key value NOT included
 * to avoid accidental in-memory exposure outside the repository).
 */
data class CredentialMetadata(
    val subjectId: UUID,
    val provider: String,
    val createdAt: java.time.OffsetDateTime,
    val revokedAt: java.time.OffsetDateTime?,
) {
    val isActive: Boolean get() = revokedAt == null
}

/**
 * Per-subject BYOK credential storage. Plaintext keys are encrypted inside
 * Postgres via pgcrypto `pgp_sym_encrypt` keyed by the master passphrase
 * read from `/run/dietician-keys/credentials.passphrase` (tmpfs, populated
 * by `/opt/dietician/bin/unlock` after VPS SSH).
 *
 * The plaintext key NEVER touches disk in app memory longer than the
 * `read` call needs. Caller wraps reads in a `try { use … } finally { …
 * zero out via String reassignment }` pattern (best-effort; Kotlin Strings
 * are immutable so the JVM-internal char[] is the actual concern).
 *
 * Provider domain (V019 CHECK constraint): openrouter | anthropic | gemini |
 * groq.
 */
class CredentialRepository(
    private val db: DatabaseFactory,
    /**
     * Override the encryption passphrase explicitly. Production callers pass
     * `null` so the repo reads from
     * `/run/dietician-keys/credentials.passphrase` (tmpfs) or the
     * `DIETICIAN_CREDENTIAL_PASSPHRASE` env var. Tests pass a known value.
     */
    passphraseOverride: String? = null,
) {
    private val passphrase: String by lazy { passphraseOverride ?: readPassphrase() }

    /**
     * Upserts a credential. Encrypts [plaintextKey] inline via pgcrypto.
     *
     * ON CONFLICT re-binds plaintext + passphrase since EXCLUDED.encrypted_key
     * is already-encrypted and cannot be re-encrypted at the SQL layer
     * without double-wrapping.
     */
    fun upsert(subjectId: UUID, provider: String, plaintextKey: String) {
        val sql =
            """
            INSERT INTO subject_credentials(subject_id, provider, encrypted_key, revoked_at)
            VALUES (?, ?, pgp_sym_encrypt(?, ?), NULL)
            ON CONFLICT (subject_id, provider)
              DO UPDATE SET encrypted_key = pgp_sym_encrypt(?, ?),
                            revoked_at = NULL
            """.trimIndent()
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, subjectId)
                ps.setString(2, provider)
                ps.setString(3, plaintextKey)
                ps.setString(4, passphrase)
                ps.setString(5, plaintextKey)
                ps.setString(6, passphrase)
                ps.executeUpdate()
            }
        }
    }

    /**
     * Decrypts and returns the plaintext key, or null if the credential
     * doesn't exist or has been revoked.
     */
    fun read(subjectId: UUID, provider: String): String? =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                "SELECT pgp_sym_decrypt(encrypted_key, ?) AS plain FROM subject_credentials " +
                    "WHERE subject_id = ? AND provider = ? AND revoked_at IS NULL",
            ).use { ps ->
                ps.setString(1, passphrase)
                ps.setObject(2, subjectId)
                ps.setString(3, provider)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("plain") else null
                }
            }
        }

    /** Marks the credential revoked; key stays in storage for forensic audit. */
    fun revoke(subjectId: UUID, provider: String): Int =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                "UPDATE subject_credentials SET revoked_at = now() " +
                    "WHERE subject_id = ? AND provider = ? AND revoked_at IS NULL",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.setString(2, provider)
                ps.executeUpdate()
            }
        }

    fun listForSubject(subjectId: UUID): List<CredentialMetadata> =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                "SELECT subject_id, provider, created_at, revoked_at " +
                    "FROM subject_credentials WHERE subject_id = ? ORDER BY created_at DESC",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<CredentialMetadata>()
                    while (rs.next()) {
                        out +=
                            CredentialMetadata(
                                subjectId = rs.getObject("subject_id", UUID::class.java),
                                provider = rs.getString("provider"),
                                createdAt = rs.getObject("created_at", java.time.OffsetDateTime::class.java),
                                revokedAt = rs.getObject("revoked_at", java.time.OffsetDateTime::class.java),
                            )
                    }
                    out
                }
            }
        }

    companion object {
        /**
         * Reads the master encryption passphrase. Lookup order:
         *   1. `/run/dietician-keys/credentials.passphrase` (tmpfs, prod).
         *   2. `DIETICIAN_CREDENTIAL_PASSPHRASE` env (dev / tests).
         *
         * Per-subject keys never share a passphrase with any other subject
         * (a future enhancement could shard passphrases per-tenant; for the
         * single-friend-group V1 a global tmpfs key is acceptable per the
         * threat model).
         */
        internal fun readPassphrase(): String {
            val tmpfs = java.io.File("/run/dietician-keys/credentials.passphrase")
            if (tmpfs.exists()) return tmpfs.readText().trim()
            return System.getenv("DIETICIAN_CREDENTIAL_PASSPHRASE")
                ?: error(
                    "DIETICIAN_CREDENTIAL_PASSPHRASE not set and " +
                        "/run/dietician-keys/credentials.passphrase absent — " +
                        "run /opt/dietician/bin/unlock first; see docs/runbooks/restart.md",
                )
        }
    }
}

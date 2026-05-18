package com.dietician.server.repo

import com.dietician.server.db.DatabaseFactory
import java.time.OffsetDateTime
import java.util.UUID

/**
 * V016 `consents` row. `scope` is checked at the SQL layer against the
 * closed set documented in the migration (process_meal_data,
 * process_weight_data, process_voice_memos, export_to_anelis,
 * share_with_friends).
 */
data class Consent(
    val consentId: UUID,
    val subjectId: UUID,
    val scope: String,
    val grantedAt: OffsetDateTime,
    val withdrawnAt: OffsetDateTime?,
    val versionHash: String,
) {
    val isActive: Boolean get() = withdrawnAt == null
}

/**
 * Append-only consent log per V016. `grant` writes a new row; `withdraw`
 * stamps `withdrawn_at` on the existing active row for that subject + scope.
 *
 * All operations go through [DatabaseFactory.withSubject] so RLS enforces
 * per-subject isolation. Cross-subject mutation is rejected by PG.
 */
class ConsentRepository(private val db: DatabaseFactory) {
    fun grant(subjectId: UUID, scope: String, versionHash: String): UUID =
        db.withSubject(subjectId) { conn ->
            val id = UUID.randomUUID()
            conn.prepareStatement(
                "INSERT INTO consents(consent_id, subject_id, scope, version_hash) " +
                    "VALUES (?, ?, ?, ?)",
            ).use { ps ->
                ps.setObject(1, id)
                ps.setObject(2, subjectId)
                ps.setString(3, scope)
                ps.setString(4, versionHash)
                ps.executeUpdate()
            }
            id
        }

    /**
     * Stamps `withdrawn_at = now()` on every active consent for [subjectId] +
     * [scope]. Returns the number of rows affected (0 if no active consent
     * existed).
     */
    fun withdraw(subjectId: UUID, scope: String): Int =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                "UPDATE consents SET withdrawn_at = now() " +
                    "WHERE subject_id = ? AND scope = ? AND withdrawn_at IS NULL",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.setString(2, scope)
                ps.executeUpdate()
            }
        }

    fun listForSubject(subjectId: UUID): List<Consent> =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                "SELECT consent_id, subject_id, scope, granted_at, withdrawn_at, version_hash " +
                    "FROM consents WHERE subject_id = ? ORDER BY granted_at DESC",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<Consent>()
                    while (rs.next()) {
                        out +=
                            Consent(
                                consentId = rs.getObject("consent_id", UUID::class.java),
                                subjectId = rs.getObject("subject_id", UUID::class.java),
                                scope = rs.getString("scope"),
                                grantedAt = rs.getObject("granted_at", OffsetDateTime::class.java),
                                withdrawnAt = rs.getObject("withdrawn_at", OffsetDateTime::class.java),
                                versionHash = rs.getString("version_hash"),
                            )
                    }
                    out
                }
            }
        }

    /**
     * Returns true if the subject currently has an active (non-withdrawn)
     * consent for [scope].
     */
    fun hasActive(subjectId: UUID, scope: String): Boolean =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                "SELECT 1 FROM consents WHERE subject_id = ? AND scope = ? " +
                    "AND withdrawn_at IS NULL LIMIT 1",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.setString(2, scope)
                ps.executeQuery().use { rs -> rs.next() }
            }
        }
}

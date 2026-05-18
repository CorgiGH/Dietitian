package com.dietician.server.repo

import com.dietician.server.db.DatabaseFactory
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Repository over V013 `subjects` + `devices`.
 *
 * Subject lookup-by-email runs under [DatabaseFactory.withSystemContext]
 * because magic-link auth needs to find a subject BEFORE a session and
 * therefore BEFORE an RLS context can be set. The `subjects` table is NOT
 * RLS-protected (no policy in V013) so this is safe.
 *
 * Per-subject reads use [DatabaseFactory.withSubject] so RLS enforces
 * isolation transparently.
 */
class SubjectRepository(private val db: DatabaseFactory) {
    fun findByEmail(email: String): Subject? =
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "SELECT subject_id, display_name, email_for_magic_link, created_at, deleted_at " +
                    "FROM subjects WHERE email_for_magic_link = ? AND deleted_at IS NULL " +
                    "LIMIT 1",
            ).use { ps ->
                ps.setString(1, email)
                ps.executeQuery().use { rs -> if (rs.next()) readSubject(rs) else null }
            }
        }

    fun findById(subjectId: UUID): Subject? =
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "SELECT subject_id, display_name, email_for_magic_link, created_at, deleted_at " +
                    "FROM subjects WHERE subject_id = ? LIMIT 1",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.executeQuery().use { rs -> if (rs.next()) readSubject(rs) else null }
            }
        }

    /**
     * Creates a new subject. Magic-link-only auth: caller provides email +
     * display name; subject id is generated server-side.
     */
    fun create(displayName: String, emailForMagicLink: String?): UUID =
        db.withSystemContext { conn ->
            val id = UUID.randomUUID()
            conn.prepareStatement(
                "INSERT INTO subjects(subject_id, display_name, email_for_magic_link) " +
                    "VALUES (?, ?, ?)",
            ).use { ps ->
                ps.setObject(1, id)
                ps.setString(2, displayName)
                if (emailForMagicLink != null) ps.setString(3, emailForMagicLink) else ps.setNull(3, java.sql.Types.VARCHAR)
                ps.executeUpdate()
            }
            id
        }

    /**
     * Soft-delete (sets `deleted_at`). GDPR Art 17 "right to erasure" hard
     * delete is handled by the V015 `redact_subject` PG function on a
     * separate path.
     */
    fun softDelete(subjectId: UUID) {
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "UPDATE subjects SET deleted_at = now() WHERE subject_id = ?",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.executeUpdate()
            }
        }
    }

    /**
     * Lists devices for a subject. RLS-aware via `withSubject` even though
     * `devices` has no policy in V013 — the wrapper is harmless and keeps
     * the call shape uniform with future RLS-protected access patterns.
     */
    fun listDevices(subjectId: UUID): List<Device> =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                "SELECT device_id, subject_id, label, created_at, last_seen_at " +
                    "FROM devices WHERE subject_id = ? ORDER BY created_at ASC",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<Device>()
                    while (rs.next()) {
                        out +=
                            Device(
                                deviceId = rs.getObject("device_id", UUID::class.java),
                                subjectId = rs.getObject("subject_id", UUID::class.java),
                                label = rs.getString("label"),
                                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                                lastSeenAt = rs.getObject("last_seen_at", OffsetDateTime::class.java),
                            )
                    }
                    out
                }
            }
        }

    fun registerDevice(subjectId: UUID, label: String): UUID =
        db.withSystemContext { conn ->
            val id = UUID.randomUUID()
            conn.prepareStatement(
                "INSERT INTO devices(device_id, subject_id, label) VALUES (?, ?, ?)",
            ).use { ps ->
                ps.setObject(1, id)
                ps.setObject(2, subjectId)
                ps.setString(3, label)
                ps.executeUpdate()
            }
            id
        }

    private fun readSubject(rs: ResultSet): Subject =
        Subject(
            subjectId = rs.getObject("subject_id", UUID::class.java),
            displayName = rs.getString("display_name"),
            emailForMagicLink = rs.getString("email_for_magic_link"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            deletedAt = rs.getObject("deleted_at", OffsetDateTime::class.java),
        )
}

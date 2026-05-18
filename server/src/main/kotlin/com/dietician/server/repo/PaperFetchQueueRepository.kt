package com.dietician.server.repo

import com.dietician.server.db.DatabaseFactory
import java.time.OffsetDateTime
import java.util.UUID

/**
 * V020 `paper_fetch_queue` row.
 *
 * Status domain (CHECK constraint in migration):
 *   queued | fetched | retry_next_run | permanent_fail
 */
data class PaperFetchEntry(
    val doi: String,
    val priority: Int,
    val requestedBySubjectId: UUID,
    val requestedAt: OffsetDateTime,
    val status: String,
    val lastAttemptAt: OffsetDateTime?,
    val lastError: String?,
    val attempts: Int,
)

/**
 * Repository for the Anelis batch-pull queue (A19).
 *
 * System-context only — V1 ships single-friend-group (only Victor uses
 * Anelis); the table is NOT RLS-protected (no policy in V020) so all rows
 * are readable across subjects. The `requested_by_subject_id` column
 * preserves provenance for audit but does NOT gate access.
 *
 * The Sunday 03:00 cron drains `status='queued'` rows; on permanent
 * fetch-fail it stamps `permanent_fail`; on transient fail it stamps
 * `retry_next_run` and bumps `attempts`.
 */
class PaperFetchQueueRepository(private val db: DatabaseFactory) {
    /**
     * Inserts a new fetch request. On `doi` conflict, keeps the LOWER
     * priority value (lower = higher priority per the migration's
     * `ORDER BY priority DESC` queue order). NOTE: V020 index orders
     * `priority DESC` so callers pass priority where higher = sooner.
     * Conflict resolution here keeps the MAX so a re-request with higher
     * urgency wins.
     */
    fun enqueue(doi: String, priority: Int, requestedBy: UUID) {
        db.withSystemContext { conn ->
            conn.prepareStatement(
                """
                INSERT INTO paper_fetch_queue(doi, priority, requested_by_subject_id, status)
                VALUES (?, ?, ?, 'queued')
                ON CONFLICT (doi) DO UPDATE
                  SET priority = GREATEST(paper_fetch_queue.priority, EXCLUDED.priority),
                      status = CASE WHEN paper_fetch_queue.status IN ('fetched','permanent_fail')
                                    THEN paper_fetch_queue.status
                                    ELSE 'queued'
                                    END
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, doi)
                ps.setInt(2, priority)
                ps.setObject(3, requestedBy)
                ps.executeUpdate()
            }
        }
    }

    /**
     * Pops up to [limit] entries whose status is `queued`, ordered by
     * (priority DESC, requested_at ASC). Does NOT mutate row state; the
     * cron worker calls `markFetched` / `markRetry` / `markPermanentFail`
     * once the network attempt resolves.
     */
    fun dequeue(limit: Int = 100): List<PaperFetchEntry> =
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "SELECT doi, priority, requested_by_subject_id, requested_at, status, " +
                    "last_attempt_at, last_error, attempts " +
                    "FROM paper_fetch_queue WHERE status = 'queued' " +
                    "ORDER BY priority DESC, requested_at ASC LIMIT ?",
            ).use { ps ->
                ps.setInt(1, limit)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<PaperFetchEntry>()
                    while (rs.next()) out += readEntry(rs)
                    out
                }
            }
        }

    fun markFetched(doi: String): Int =
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "UPDATE paper_fetch_queue SET status = 'fetched', last_attempt_at = now(), " +
                    "attempts = attempts + 1, last_error = NULL WHERE doi = ?",
            ).use { ps ->
                ps.setString(1, doi)
                ps.executeUpdate()
            }
        }

    fun markRetryNextRun(doi: String, reason: String): Int =
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "UPDATE paper_fetch_queue SET status = 'retry_next_run', last_attempt_at = now(), " +
                    "attempts = attempts + 1, last_error = ? WHERE doi = ?",
            ).use { ps ->
                ps.setString(1, reason)
                ps.setString(2, doi)
                ps.executeUpdate()
            }
        }

    fun markPermanentFail(doi: String, reason: String): Int =
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "UPDATE paper_fetch_queue SET status = 'permanent_fail', last_attempt_at = now(), " +
                    "attempts = attempts + 1, last_error = ? WHERE doi = ?",
            ).use { ps ->
                ps.setString(1, reason)
                ps.setString(2, doi)
                ps.executeUpdate()
            }
        }

    /**
     * Re-queues ALL `retry_next_run` rows back to `queued`. Cron worker
     * calls this at the START of its run so transient failures from the
     * previous run get retried this cycle.
     */
    fun requeueRetryRows(): Int =
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "UPDATE paper_fetch_queue SET status = 'queued' WHERE status = 'retry_next_run'",
            ).use { ps -> ps.executeUpdate() }
        }

    fun findByDoi(doi: String): PaperFetchEntry? =
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "SELECT doi, priority, requested_by_subject_id, requested_at, status, " +
                    "last_attempt_at, last_error, attempts " +
                    "FROM paper_fetch_queue WHERE doi = ?",
            ).use { ps ->
                ps.setString(1, doi)
                ps.executeQuery().use { rs -> if (rs.next()) readEntry(rs) else null }
            }
        }

    private fun readEntry(rs: java.sql.ResultSet): PaperFetchEntry =
        PaperFetchEntry(
            doi = rs.getString("doi"),
            priority = rs.getInt("priority"),
            requestedBySubjectId = rs.getObject("requested_by_subject_id", UUID::class.java),
            requestedAt = rs.getObject("requested_at", OffsetDateTime::class.java),
            status = rs.getString("status"),
            lastAttemptAt = rs.getObject("last_attempt_at", OffsetDateTime::class.java),
            lastError = rs.getString("last_error"),
            attempts = rs.getInt("attempts"),
        )
}

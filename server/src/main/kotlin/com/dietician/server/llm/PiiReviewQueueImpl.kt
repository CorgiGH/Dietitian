package com.dietician.server.llm

import com.dietician.server.db.DatabaseFactory
import com.dietician.shared.llm.PiiReviewQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Plan-2 Task 31 — Server-side [PiiReviewQueue] writing to V020 `pii_review_queue`.
 *
 * Subject context is set on the connection so RLS policy `pii_review_queue_subject`
 * matches the insert.
 */
class PiiReviewQueueImpl(
    private val db: DatabaseFactory,
) : PiiReviewQueue {
    override suspend fun enqueue(subjectId: String, rawRef: String, context: String) {
        val uuid = UUID.fromString(subjectId)
        withContext(Dispatchers.IO) {
            db.withSubject(uuid) { conn ->
                conn.prepareStatement(
                    "INSERT INTO pii_review_queue(subject_id, raw_ref, context) VALUES (?, ?, ?)",
                ).use { ps ->
                    ps.setObject(1, uuid)
                    ps.setString(2, rawRef)
                    ps.setString(3, context)
                    ps.executeUpdate()
                }
            }
        }
    }
}

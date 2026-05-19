package com.dietician.server.cron

import com.dietician.server.coach.CoachRepository
import com.dietician.server.coach.CoachReserveRequest
import com.dietician.server.coach.CoachService
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.PiiRedactor
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachOrphanCleanupCronTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var db: DatabaseFactory
    private lateinit var service: CoachService
    private lateinit var repo: CoachRepository
    private lateinit var cron: CoachOrphanCleanupCron
    private val subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeAll
    fun setup() {
        pg = PostgreSQLContainer("pgvector/pgvector:pg16").apply { start() }
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        db = DatabaseFactory(pg.jdbcUrl, pg.username, pg.password)
        db.withSystemContext { c ->
            c.prepareStatement(
                "INSERT INTO subjects (subject_id, display_name) VALUES (?, 'Victor') ON CONFLICT DO NOTHING",
            ).apply { setObject(1, subjectId) }.execute()
        }
        repo = CoachRepository(db)
        service = CoachService(repo, BudgetRepository(db), PiiRedactor())
        cron = CoachOrphanCleanupCron(db)
    }

    @AfterAll
    fun teardown() {
        db.close()
        pg.stop()
    }

    @Test
    fun `runOnce flips rows older than 60s to orphaned and refunds budget`() {
        val key = UUID.randomUUID()
        service.reserve(
            subjectId,
            CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60),
        )
        db.withSubject(subjectId) { c ->
            c.prepareStatement(
                "UPDATE audit_log SET occurred_at = now() - interval '120 seconds', " +
                    "reserved_until = now() - interval '60 seconds' WHERE idempotency_key = ?",
            ).apply { setObject(1, key) }.executeUpdate()
        }
        val compensated = cron.runOnce()
        assertEquals(1, compensated)
        assertEquals("orphaned", repo.findByIdempotencyKey(subjectId, key)!!.status)
    }

    @Test
    fun `runOnce ignores rows younger than 60s`() {
        val key = UUID.randomUUID()
        service.reserve(subjectId, CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60))
        val compensated = cron.runOnce()
        assertEquals(0, compensated)
        assertEquals("pending", repo.findByIdempotencyKey(subjectId, key)!!.status)
    }
}

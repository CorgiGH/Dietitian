package com.dietician.server.coach

import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachRepositoryTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var db: DatabaseFactory
    private lateinit var repo: CoachRepository
    private val subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeAll
    fun setup() {
        pg = PostgreSQLContainer("pgvector/pgvector:pg16").apply { start() }
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        db = DatabaseFactory(pg.jdbcUrl, pg.username, pg.password)
        repo = CoachRepository(db)
        db.withSystemContext { c ->
            c.prepareStatement(
                "INSERT INTO subjects (subject_id, display_name) VALUES (?, 'Victor') ON CONFLICT DO NOTHING",
            ).apply {
                setObject(1, subjectId)
                execute()
            }
        }
    }

    @AfterAll
    fun teardown() {
        db.close()
        pg.stop()
    }

    @Test
    fun `insertPendingAudit writes a pending row with idempotency_key + reserved_until`() {
        val key = UUID.randomUUID()
        val (auditId, reservedUntilMs) =
            repo.insertPendingAudit(
                subjectId = subjectId,
                idempotencyKey = key,
                promptHash = "abc",
                provider = "claudemax",
                estimatedCostCents = 5,
                reservationTtlSeconds = 60,
            )
        assertNotNull(auditId)
        assertTrue(reservedUntilMs > System.currentTimeMillis())
        val row = repo.findByIdempotencyKey(subjectId, key)
        assertNotNull(row)
        assertEquals("pending", row.status)
        assertEquals(5, row.costCents)
    }

    @Test
    fun `updateAuditOnCommit flips status + records usage + clears reserved_until`() {
        val key = UUID.randomUUID()
        val (auditId, _) =
            repo.insertPendingAudit(subjectId, key, "h", "claudemax", 5, 60)
        repo.updateAuditOnCommit(
            subjectId = subjectId,
            idempotencyKey = key,
            status = "success",
            promptTokens = 10,
            completionTokens = 20,
            costCents = 4,
            provider = "claudemax",
            latencyMs = 2200,
            responseHash = "def",
        )
        val row = repo.findByIdempotencyKey(subjectId, key)
        assertNotNull(row)
        assertEquals(auditId, row.auditId)
        assertEquals("success", row.status)
        assertEquals(10, row.promptTokens)
        assertEquals(20, row.completionTokens)
        assertEquals(4, row.costCents)
        assertNull(row.reservedUntilMs)
    }

    @Test
    fun `findByIdempotencyKey returns null for unknown key`() {
        assertNull(repo.findByIdempotencyKey(subjectId, UUID.randomUUID()))
    }
}

package com.dietician.server.coach

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachServiceReserveTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var db: DatabaseFactory
    private lateinit var service: CoachService
    private lateinit var repo: CoachRepository
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
    }

    @AfterAll
    fun teardown() {
        db.close()
        pg.stop()
    }

    @Test
    fun `reserve writes pending audit + reserves budget + returns reservation envelope`() {
        val key = UUID.randomUUID()
        val resp =
            service.reserve(
                subjectId = subjectId,
                request =
                    CoachReserveRequest(
                        idempotencyKey = key.toString(),
                        prompt = "How many grams of chicken for 50g protein?",
                        locale = "en",
                        provider = "openrouter",
                        estimatedCostCents = 5,
                        reservationTtlSeconds = 60,
                    ),
            )
        assertTrue(resp is CoachServiceReserveResult.Reserved, "got: $resp")
        val r = resp.envelope
        assertNotNull(UUID.fromString(r.reservationId))
        assertNotNull(UUID.fromString(r.auditId))
        assertTrue(r.reservedUntilEpochMs > System.currentTimeMillis())
    }

    @Test
    fun `reserve is idempotent — second call with same key returns the same audit row`() {
        val key = UUID.randomUUID()
        val req =
            CoachReserveRequest(
                idempotencyKey = key.toString(),
                prompt = "test",
                locale = "en",
                provider = "openrouter",
                estimatedCostCents = 5,
                reservationTtlSeconds = 60,
            )
        val first = service.reserve(subjectId, req) as CoachServiceReserveResult.Reserved
        val second = service.reserve(subjectId, req) as CoachServiceReserveResult.Reserved
        assertEquals(first.envelope.auditId, second.envelope.auditId)
    }

    @Test
    fun `reserve returns Rejected when budget cap is exceeded`() {
        db.withSubject(subjectId) { c ->
            c.prepareStatement(
                """
                INSERT INTO llm_budget
                  (subject_id, provider, period_starts_at, period_ends_at, cost_cents_used, cost_cents_cap)
                VALUES (?, 'capped',
                        date_trunc('month', now())::DATE,
                        (date_trunc('month', now()) + interval '1 month - 1 day')::DATE,
                        500, 500)
                ON CONFLICT (subject_id, provider, period_starts_at)
                DO UPDATE SET cost_cents_used = 500, cost_cents_cap = 500
                """.trimIndent(),
            ).apply { setObject(1, subjectId) }.execute()
        }
        val resp =
            service.reserve(
                subjectId = subjectId,
                request =
                    CoachReserveRequest(
                        idempotencyKey = UUID.randomUUID().toString(),
                        prompt = "x",
                        locale = "en",
                        provider = "capped",
                        estimatedCostCents = 5,
                        reservationTtlSeconds = 60,
                    ),
            )
        assertTrue(resp is CoachServiceReserveResult.Rejected, "got: $resp")
        assertEquals("over_budget", resp.reason)
    }
}

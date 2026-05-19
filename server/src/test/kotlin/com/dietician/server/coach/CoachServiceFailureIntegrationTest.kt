package com.dietician.server.coach

import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.LlmChunk
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmStream
import com.dietician.shared.llm.PiiRedactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * iter-11 T26 + T27 — integration tests covering the two binding failure
 * modes of the 2-phase commit Coach pipeline:
 *
 *   T26: mid-stream provider failure must flip the audit row to status=failed
 *        (commit fires in finally block) and propagate the exception so the
 *        client surfaces an error rather than a silent half-response.
 *
 *   T27: over-budget reserve must reject BEFORE the audit_log row is written;
 *        consume_or_fail returns false → no pending row → no orphan-saga
 *        debt to clean up later.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachServiceFailureIntegrationTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var db: DatabaseFactory
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
    }

    @AfterAll
    fun teardown() {
        db.close()
        pg.stop()
    }

    @Test
    fun `T26 — mid-stream provider failure flips audit row to status=failed and propagates`() =
        runTest {
            val failingStream =
                object : LlmStream {
                    override fun streamRoute(request: LlmRequest): Flow<LlmChunk> =
                        flow {
                            emit(LlmChunk("Eat ", isDone = false))
                            @Suppress("TooGenericExceptionThrown")
                            throw RuntimeException("provider crashed")
                        }
                }
            val service =
                CoachService(
                    repo = CoachRepository(db),
                    budgets = BudgetRepository(db),
                    redactor = PiiRedactor(),
                    llmStream = failingStream,
                )
            val key = UUID.randomUUID().toString()
            val req = CoachStreamRequest(idempotencyKey = key, prompt = "p", locale = "en")
            assertFailsWith<RuntimeException> {
                service.streamServerRouted(subjectId, req).toList()
            }
            val row = CoachRepository(db).findByIdempotencyKey(subjectId, UUID.fromString(key))!!
            assertEquals("failed", row.status)
        }

    @Test
    fun `T27 — over-budget reserve returns Rejected and writes NO audit row`() =
        runTest {
            // Seed an llm_budget row with cap = used (over).
            db.withSubject(subjectId) { c ->
                c.prepareStatement(
                    """
                    INSERT INTO llm_budget
                        (subject_id, provider, period_starts_at, period_ends_at, cost_cents_used, cost_cents_cap)
                    VALUES (?, 'overcapped',
                            date_trunc('month', now())::DATE,
                            (date_trunc('month', now()) + interval '1 month - 1 day')::DATE,
                            500, 500)
                    ON CONFLICT (subject_id, provider, period_starts_at)
                    DO UPDATE SET cost_cents_used = 500, cost_cents_cap = 500
                    """.trimIndent(),
                ).apply { setObject(1, subjectId) }.execute()
            }
            val noopStream =
                object : LlmStream {
                    override fun streamRoute(request: LlmRequest): Flow<LlmChunk> = kotlinx.coroutines.flow.emptyFlow()
                }
            val service =
                CoachService(
                    repo = CoachRepository(db),
                    budgets = BudgetRepository(db),
                    redactor = PiiRedactor(),
                    llmStream = noopStream,
                )
            val key = UUID.randomUUID()
            val resp =
                service.reserve(
                    subjectId,
                    CoachReserveRequest(key.toString(), "x", "en", "overcapped", 5, 60),
                )
            assertTrue(resp is CoachServiceReserveResult.Rejected, "got: $resp")
            assertEquals("over_budget", resp.reason)
            // No audit row should exist for this key.
            assertNull(CoachRepository(db).findByIdempotencyKey(subjectId, key))
        }
}

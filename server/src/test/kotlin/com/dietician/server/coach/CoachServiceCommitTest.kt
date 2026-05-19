package com.dietician.server.coach

import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.LlmChunk
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmStream
import com.dietician.shared.llm.PiiRedactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachServiceCommitTest {
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
        val noopStream =
            object : LlmStream {
                override fun streamRoute(request: LlmRequest): Flow<LlmChunk> = emptyFlow()
            }
        service = CoachService(repo, BudgetRepository(db), PiiRedactor(), noopStream)
    }

    @AfterAll
    fun teardown() {
        db.close()
        pg.stop()
    }

    @Test
    fun `commit success flips status + records usage`() {
        val key = UUID.randomUUID()
        service.reserve(
            subjectId,
            CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60),
        )
        val resp =
            service.commit(
                subjectId,
                CoachCommitRequest(key.toString(), "success", 10, 20, 4, "claudemax", 2200, "hash"),
            )
        assertEquals("success", resp.status)
        val row = repo.findByIdempotencyKey(subjectId, key)!!
        assertEquals("success", row.status)
        assertEquals(10, row.promptTokens)
    }

    @Test
    fun `commit is idempotent — second call returns same auditId, no double-write`() {
        val key = UUID.randomUUID()
        service.reserve(subjectId, CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60))
        val req = CoachCommitRequest(key.toString(), "success", 10, 20, 4, "claudemax", 2200, "hash")
        val a = service.commit(subjectId, req)
        val b = service.commit(subjectId, req)
        assertEquals(a.auditId, b.auditId)
        assertEquals("success", b.status)
    }

    @Test
    fun `commit on failed status surfaces the failure but still writes usage`() {
        val key = UUID.randomUUID()
        service.reserve(subjectId, CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60))
        val resp =
            service.commit(
                subjectId,
                CoachCommitRequest(key.toString(), "failed", 5, 0, 1, "claudemax", 800, "errhash"),
            )
        assertEquals("failed", resp.status)
        assertEquals("failed", repo.findByIdempotencyKey(subjectId, key)!!.status)
    }
}

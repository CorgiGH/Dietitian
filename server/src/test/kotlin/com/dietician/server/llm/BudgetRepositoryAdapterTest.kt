package com.dietician.server.llm

import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.ProviderId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Plan-2 Task 28 — BudgetRepositoryAdapter integration tests against real Postgres.
 *
 * Verifies the shared `BudgetLedger` contract maps correctly to V019 `consume_or_fail` +
 * the finalize/release UPDATE statements.
 */
@Testcontainers
class BudgetRepositoryAdapterTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "budget_adapter_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS budget_adapter_test")
                    st.execute("CREATE ROLE budget_adapter_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO budget_adapter_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO budget_adapter_test")
                    st.execute("GRANT EXECUTE ON FUNCTION consume_or_fail(uuid, text, int, int) TO budget_adapter_test")
                }
            }
            bootstrapped = true
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            dbRef?.close()
        }
    }

    private fun freshDb(): DatabaseFactory {
        bootstrap()
        val d = DatabaseFactory(pg.jdbcUrl, "budget_adapter_test", APP_PW)
        dbRef = d
        return d
    }

    private fun seedSubject(db: DatabaseFactory): UUID {
        val id = UUID.randomUUID()
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "INSERT INTO subjects(subject_id, display_name) VALUES (?, ?) ON CONFLICT DO NOTHING",
            ).use { ps ->
                ps.setObject(1, id)
                ps.setString(2, "BudAdapter-${id.toString().take(8)}")
                ps.executeUpdate()
            }
        }
        return id
    }

    @Test
    fun `reserve succeeds and returns Reservation with provided estimates`() = runBlocking {
        val db = freshDb()
        val subj = seedSubject(db)
        val adapter = BudgetRepositoryAdapter(BudgetRepository(db))
        val res = adapter.reserve(
            subjectId = subj.toString(),
            provider = ProviderId("openrouter"),
            estimateTokens = 100,
            estimateCostCents = 3,
        )
        assertEquals(subj.toString(), res.subjectId)
        assertEquals(ProviderId("openrouter"), res.provider)
        assertEquals(100, res.reservedTokens)
        assertEquals(3, res.reservedCostCents)
        assertNotNull(res.id)
    }

    @Test
    fun `reserve throws BudgetExhausted when cap is breached`() = runBlocking {
        val db = freshDb()
        val subj = seedSubject(db)
        val adapter = BudgetRepositoryAdapter(BudgetRepository(db))
        // Seed cap = 5¢.
        db.withSubject(subj) { conn ->
            conn.prepareStatement(
                "INSERT INTO llm_budget(subject_id, provider, period_starts_at, period_ends_at, cost_cents_cap) " +
                    "VALUES (?, 'openrouter', date_trunc('month', now())::DATE, (date_trunc('month', now()) + INTERVAL '1 month - 1 day')::DATE, 5) " +
                    "ON CONFLICT DO NOTHING",
            ).use { ps ->
                ps.setObject(1, subj)
                ps.executeUpdate()
            }
        }
        // Fit under cap (5 ≤ 5).
        adapter.reserve(subj.toString(), ProviderId("openrouter"), 1, 5)
        // Second reserve breaches.
        assertThrows<LlmError.BudgetExhausted> {
            runBlocking { adapter.reserve(subj.toString(), ProviderId("openrouter"), 1, 1) }
        }
    }

    @Test
    fun `release reverses reservation cost_cents_used`() = runBlocking {
        val db = freshDb()
        val subj = seedSubject(db)
        val adapter = BudgetRepositoryAdapter(BudgetRepository(db))
        val res = adapter.reserve(subj.toString(), ProviderId("openrouter"), 50, 7)
        adapter.release(res)

        val used = db.withSubject(subj) { conn ->
            conn.prepareStatement(
                "SELECT cost_cents_used FROM llm_budget WHERE subject_id = ? AND provider = 'openrouter' " +
                    "AND period_starts_at = date_trunc('month', now())::DATE",
            ).use { ps ->
                ps.setObject(1, subj)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
        assertEquals(0, used, "release should subtract 7¢ back to 0")
    }

    @Test
    fun `finalize adjusts by actualCost minus reservedCost delta`() = runBlocking {
        val db = freshDb()
        val subj = seedSubject(db)
        val adapter = BudgetRepositoryAdapter(BudgetRepository(db))
        val res = adapter.reserve(subj.toString(), ProviderId("openrouter"), 50, 10)
        // Actual cost came back at 12¢ — under-reserved by 2¢.
        adapter.finalize(res, actualTokens = 60, actualCostCents = 12)

        val (used, finalized) = db.withSubject(subj) { conn ->
            conn.prepareStatement(
                "SELECT cost_cents_used, finalized_tokens FROM llm_budget " +
                    "WHERE subject_id = ? AND provider = 'openrouter' " +
                    "AND period_starts_at = date_trunc('month', now())::DATE",
            ).use { ps ->
                ps.setObject(1, subj)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1) to rs.getInt(2)
                }
            }
        }
        // Reserved 10 → finalize adds delta (12 - 10) = 2. Total = 12¢.
        assertEquals(12, used)
        assertEquals(60, finalized)
    }
}

package com.dietician.server.repo

import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers
class BudgetRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "budget_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS budget_app_test")
                    st.execute("CREATE ROLE budget_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO budget_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO budget_app_test")
                    st.execute("GRANT EXECUTE ON FUNCTION consume_or_fail(uuid, text, int, int) TO budget_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "budget_app_test", APP_PW)
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
                ps.setString(2, "Budget-${id.toString().take(8)}")
                ps.executeUpdate()
            }
        }
        return id
    }

    @Test
    fun `consumeOrFail succeeds when cap is NULL`() {
        val db = freshDb()
        val subj = seedSubject(db)
        val repo = BudgetRepository(db)
        // First call creates the period row with cap NULL.
        assertTrue(repo.consumeOrFail(subj, "voyage", tokensNeeded = 100, costCentsEstimated = 1))
        assertTrue(repo.consumeOrFail(subj, "voyage", tokensNeeded = 100, costCentsEstimated = 1))
    }

    @Test
    fun `consumeOrFail returns false when cap is exceeded`() {
        val db = freshDb()
        val subj = seedSubject(db)
        val repo = BudgetRepository(db)
        // Seed a 10-cent cap directly so we can trip it.
        db.withSubject(subj) { conn ->
            conn.prepareStatement(
                "INSERT INTO llm_budget(subject_id, provider, period_starts_at, period_ends_at, cost_cents_cap) " +
                    "VALUES (?, 'voyage', date_trunc('month', now())::DATE, (date_trunc('month', now()) + INTERVAL '1 month - 1 day')::DATE, 10) " +
                    "ON CONFLICT DO NOTHING",
            ).use { ps ->
                ps.setObject(1, subj)
                ps.executeUpdate()
            }
        }
        // 5 + 5 = 10 — last one still fits.
        assertTrue(repo.consumeOrFail(subj, "voyage", 1, 5))
        assertTrue(repo.consumeOrFail(subj, "voyage", 1, 5))
        // 11 trips cap.
        assertFalse(repo.consumeOrFail(subj, "voyage", 1, 5))
    }
}

package com.dietician.server.audit

import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for [AuditLogWriter] against a live Postgres container.
 * Asserts subject-scoped + system-scoped writes both reach `audit_log`, and
 * that JSONB serialization for `extra` round-trips.
 */
@Testcontainers
class AuditLogWriterTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "audit_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS audit_app_test")
                    st.execute("CREATE ROLE audit_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO audit_app_test")
                    st.execute("GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA public TO audit_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "audit_app_test", APP_PW)
        dbRef = d
        return d
    }

    @Test
    fun `write with subject id persists row with JSONB extra and numeric fields`() {
        val db = freshDb()
        val victor = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val writer = AuditLogWriter(db)

        writer.write(
            subjectId = victor,
            kind = AuditLogActions.LLM_CALL,
            model = "claude-3-5-sonnet",
            promptHash = "deadbeef",
            responseHash = "cafebabe",
            inputTokens = 1200,
            outputTokens = 400,
            costCents = 4,
            requestId = "req-001",
            extra = JsonObject(mapOf("provider" to JsonPrimitive("claudemax-cli"))),
        )

        // Read back as superuser so RLS doesn't filter.
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT kind, model, input_tokens, cost_cents, request_id, extra::TEXT AS extra_text " +
                    "FROM audit_log WHERE subject_id = '$victor' ORDER BY occurred_at DESC LIMIT 1",
            ).use { rs ->
                assertTrue(rs.next(), "expected one row for victor")
                assertEquals(AuditLogActions.LLM_CALL, rs.getString("kind"))
                assertEquals("claude-3-5-sonnet", rs.getString("model"))
                assertEquals(1200, rs.getInt("input_tokens"))
                assertEquals(4, rs.getInt("cost_cents"))
                assertEquals("req-001", rs.getString("request_id"))
                val extra = rs.getString("extra_text")
                assertNotNull(extra)
                assertTrue(
                    extra.contains("claudemax-cli"),
                    "extra JSONB should contain provider value, got: $extra",
                )
            }
        }
    }

    @Test
    fun `write with null subject id persists system event row`() {
        val db = freshDb()
        val writer = AuditLogWriter(db)

        writer.write(
            subjectId = null,
            kind = AuditLogActions.BACKUP_COMPLETED,
            requestId = "backup-2026-05-18",
        )

        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log " +
                    "WHERE subject_id IS NULL AND kind = '${AuditLogActions.BACKUP_COMPLETED}'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1, "expected ≥1 system backup row")
            }
        }
    }
}

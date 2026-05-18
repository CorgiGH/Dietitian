package com.dietician.server.llm

import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.shared.llm.AuditEntry
import kotlinx.coroutines.runBlocking
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
 * Plan-2 Task 28 — AuditLogSinkAdapter integration tests against real Postgres `audit_log`.
 */
@Testcontainers
class AuditLogSinkAdapterTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "audit_sink_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS audit_sink_test")
                    st.execute("CREATE ROLE audit_sink_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO audit_sink_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO audit_sink_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "audit_sink_test", APP_PW)
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
                ps.setString(2, "AuditSink-${id.toString().take(8)}")
                ps.executeUpdate()
            }
        }
        return id
    }

    @Test
    fun `write maps AuditEntry to audit_log row with jsonb extra`() = runBlocking {
        val db = freshDb()
        val subj = seedSubject(db)
        val adapter = AuditLogSinkAdapter(AuditLogWriter(db))
        adapter.write(
            AuditEntry(
                subjectId = subj.toString(),
                kind = "llm_call",
                model = "openrouter/anthropic/claude-sonnet-4.5",
                inputTokens = 10,
                outputTokens = 5,
                costCents = 3,
                extra = mapOf("device_class" to "VICTOR_DESKTOP", "task" to "TEXT"),
            ),
        )

        val row = db.withSubject(subj) { conn ->
            conn.prepareStatement(
                "SELECT kind, model, input_tokens, output_tokens, cost_cents, extra::text FROM audit_log " +
                    "WHERE subject_id = ? AND kind = 'llm_call' LIMIT 1",
            ).use { ps ->
                ps.setObject(1, subj)
                ps.executeQuery().use { rs ->
                    rs.next()
                    listOf(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6))
                }
            }
        }
        assertEquals("llm_call", row[0])
        assertEquals("openrouter/anthropic/claude-sonnet-4.5", row[1])
        assertEquals(10, row[2])
        assertEquals(5, row[3])
        assertEquals(3, row[4])
        val extra = row[5] as String
        assertTrue(extra.contains("device_class") && extra.contains("VICTOR_DESKTOP"))
    }

    @Test
    fun `write with null subjectId routes through system context`() = runBlocking {
        val db = freshDb()
        val adapter = AuditLogSinkAdapter(AuditLogWriter(db))
        adapter.write(
            AuditEntry(
                subjectId = null,
                kind = "backup_completed",
                extra = mapOf("duration_ms" to "1234"),
            ),
        )
        val found = db.withSystemContext { conn ->
            conn.prepareStatement("SELECT id FROM audit_log WHERE kind = 'backup_completed' LIMIT 1").use { ps ->
                ps.executeQuery().use { rs -> rs.next() }
            }
        }
        assertNotNull(found)
        assertTrue(found)
    }
}

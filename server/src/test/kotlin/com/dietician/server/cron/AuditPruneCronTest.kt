package com.dietician.server.cron

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for [AuditPruneCron] — Plan-3 Task 33.
 *
 * Inserts a mix of fresh + 13-month-old `audit_log` rows, runs the cron,
 * asserts only the old rows were deleted, and that an
 * [AuditLogActions.AUDIT_PRUNE_COMPLETED] row was emitted with the right
 * count.
 */
@Testcontainers
class AuditPruneCronTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "prune_cron_app_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS prune_cron_app")
                    st.execute("CREATE ROLE prune_cron_app LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO prune_cron_app")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO prune_cron_app")
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
        val d = DatabaseFactory(pg.jdbcUrl, "prune_cron_app", APP_PW)
        dbRef = d
        return d
    }

    @Test
    fun `run deletes rows older than 12 months and emits prune-completed audit row`() {
        val db = freshDb()
        val subjectId = UUID.fromString("00000000-0000-0000-0000-00000000a001")
        // Seed subject so audit_log FK is satisfied for the kept rows.
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "INSERT INTO subjects(subject_id, display_name) VALUES (?, 'prune-cron') ON CONFLICT DO NOTHING",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.executeUpdate()
            }
        }
        // Insert one fresh row and three stale rows (13 months old) under
        // the subject's RLS context so audit_log's USING/WITH-CHECK policy
        // permits the writes.
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                "INSERT INTO audit_log(subject_id, occurred_at, kind) VALUES (?, NOW(), 'sign_in')",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.executeUpdate()
            }
            repeat(3) {
                conn.prepareStatement(
                    "INSERT INTO audit_log(subject_id, occurred_at, kind) " +
                        "VALUES (?, NOW() - INTERVAL '13 months', 'sign_in')",
                ).use { ps ->
                    ps.setObject(1, subjectId)
                    ps.executeUpdate()
                }
            }
        }

        val deleted = AuditPruneCron(db, AuditLogWriter(db)).run()
        assertEquals(3, deleted, "should delete exactly the 3 stale rows")

        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            // Fresh + the new prune-completed row should remain.
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE occurred_at >= NOW() - INTERVAL '12 months'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 2, "fresh row + prune-completed row must remain")
            }
            // The prune-completed row carries deleted_rows = 3 in extra.
            c.createStatement().executeQuery(
                "SELECT extra::TEXT FROM audit_log WHERE kind = '${AuditLogActions.AUDIT_PRUNE_COMPLETED}' " +
                    "ORDER BY occurred_at DESC LIMIT 1",
            ).use { rs ->
                assertTrue(rs.next(), "prune-completed row must exist")
                val extra = rs.getString(1)
                assertTrue(
                    extra.replace(" ", "").contains("\"deleted_rows\":3"),
                    "extra should record deleted_rows=3, got: $extra",
                )
            }
        }
    }
}

package com.dietician.server.llm

import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Plan-2 Task 31 RC5 — Server-side PiiReviewQueue writes to V020 `pii_review_queue`.
 */
@Testcontainers
class PiiReviewQueueImplTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "pii_queue_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS pii_queue_test")
                    st.execute("CREATE ROLE pii_queue_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO pii_queue_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO pii_queue_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "pii_queue_test", APP_PW)
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
                ps.setString(2, "PiiQ-${id.toString().take(8)}")
                ps.executeUpdate()
            }
        }
        return id
    }

    @Test
    fun `enqueue inserts a row into pii_review_queue`() = runBlocking {
        val db = freshDb()
        val subj = seedSubject(db)
        val queue = PiiReviewQueueImpl(db)
        queue.enqueue(subj.toString(), "raw-abc-123", "voice_memo")

        val (rawRef, context) = db.withSubject(subj) { conn ->
            conn.prepareStatement(
                "SELECT raw_ref, context FROM pii_review_queue WHERE subject_id = ? ORDER BY queued_at DESC LIMIT 1",
            ).use { ps ->
                ps.setObject(1, subj)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getString(1) to rs.getString(2)
                }
            }
        }
        assertEquals("raw-abc-123", rawRef)
        assertEquals("voice_memo", context)
    }
}

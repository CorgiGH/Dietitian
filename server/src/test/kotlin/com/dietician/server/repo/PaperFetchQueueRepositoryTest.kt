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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class PaperFetchQueueRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "paper_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS paper_app_test")
                    st.execute("CREATE ROLE paper_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO paper_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO paper_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "paper_app_test", APP_PW)
        dbRef = d
        return d
    }

    private fun seedSubject(db: DatabaseFactory, subjectId: UUID) {
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "INSERT INTO subjects(subject_id, display_name) VALUES (?, ?) ON CONFLICT DO NOTHING",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.setString(2, "Subject-${subjectId.toString().take(8)}")
                ps.executeUpdate()
            }
        }
    }

    @Test
    fun `enqueue + findByDoi round-trip`() {
        val db = freshDb()
        val repo = PaperFetchQueueRepository(db)
        val victor = UUID.randomUUID()
        seedSubject(db, victor)

        val doi = "10.1234/example.${UUID.randomUUID()}"
        repo.enqueue(doi, priority = 70, requestedBy = victor)
        val row = repo.findByDoi(doi)
        assertNotNull(row)
        assertEquals(doi, row.doi)
        assertEquals(70, row.priority)
        assertEquals("queued", row.status)
        assertEquals(0, row.attempts)
        assertNull(row.lastError)
    }

    @Test
    fun `enqueue conflict keeps higher priority`() {
        val db = freshDb()
        val repo = PaperFetchQueueRepository(db)
        val victor = UUID.randomUUID()
        seedSubject(db, victor)

        val doi = "10.5678/conflict.${UUID.randomUUID()}"
        repo.enqueue(doi, priority = 40, requestedBy = victor)
        repo.enqueue(doi, priority = 90, requestedBy = victor)
        val row = repo.findByDoi(doi)
        assertNotNull(row)
        assertEquals(90, row.priority, "higher-priority enqueue should win")
    }

    @Test
    fun `dequeue orders by priority desc then requested_at asc`() {
        val db = freshDb()
        val repo = PaperFetchQueueRepository(db)
        val victor = UUID.randomUUID()
        seedSubject(db, victor)
        val mid = "10.1/mid.${UUID.randomUUID()}"
        val high = "10.1/high.${UUID.randomUUID()}"
        val low = "10.1/low.${UUID.randomUUID()}"

        repo.enqueue(mid, 50, victor)
        Thread.sleep(20)
        repo.enqueue(high, 90, victor)
        Thread.sleep(20)
        repo.enqueue(low, 10, victor)

        val rows = repo.dequeue(10)
        // High first by priority; mid then by FIFO; low last.
        val ours = rows.filter { it.doi in setOf(mid, high, low) }
        assertEquals(listOf(high, mid, low), ours.map { it.doi })
    }

    @Test
    fun `markFetched + markRetryNextRun + markPermanentFail update state`() {
        val db = freshDb()
        val repo = PaperFetchQueueRepository(db)
        val victor = UUID.randomUUID()
        seedSubject(db, victor)
        val a = "10.9/a.${UUID.randomUUID()}"
        val b = "10.9/b.${UUID.randomUUID()}"
        val c = "10.9/c.${UUID.randomUUID()}"
        repo.enqueue(a, 50, victor)
        repo.enqueue(b, 50, victor)
        repo.enqueue(c, 50, victor)

        assertEquals(1, repo.markFetched(a))
        assertEquals(1, repo.markRetryNextRun(b, "timeout"))
        assertEquals(1, repo.markPermanentFail(c, "404 from anelis"))

        assertEquals("fetched", repo.findByDoi(a)?.status)
        assertEquals("retry_next_run", repo.findByDoi(b)?.status)
        assertEquals("permanent_fail", repo.findByDoi(c)?.status)
        assertEquals("timeout", repo.findByDoi(b)?.lastError)
        assertEquals("404 from anelis", repo.findByDoi(c)?.lastError)
        assertTrue((repo.findByDoi(b)?.attempts ?: 0) == 1)
    }

    @Test
    fun `requeueRetryRows transitions retry_next_run back to queued`() {
        val db = freshDb()
        val repo = PaperFetchQueueRepository(db)
        val victor = UUID.randomUUID()
        seedSubject(db, victor)
        val doi = "10.2/retry.${UUID.randomUUID()}"
        repo.enqueue(doi, 50, victor)
        repo.markRetryNextRun(doi, "transient net err")
        assertEquals("retry_next_run", repo.findByDoi(doi)?.status)
        val moved = repo.requeueRetryRows()
        assertTrue(moved >= 1)
        assertEquals("queued", repo.findByDoi(doi)?.status)
    }
}

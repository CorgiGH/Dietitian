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
import kotlin.test.assertTrue

@Testcontainers
class EventRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "event_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS event_app_test")
                    st.execute("CREATE ROLE event_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO event_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO event_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "event_app_test", APP_PW)
        dbRef = d
        return d
    }

    @Test
    fun `upsert is idempotent by event_uuid`() {
        val db = freshDb()
        val repo = EventRepository(db)
        val subject = UUID.randomUUID()
        seedSubject(db, subject)
        val deviceId = "dev-${UUID.randomUUID()}"
        val sku = UUID.randomUUID()
        val evt = UUID.randomUUID()
        val payload = """
            {"event_uuid":"$evt","device_id":"$deviceId","originated_at":"2026-05-18T10:00:00Z",
             "synced_at":"2026-05-18T10:00:01Z",
             "sku_uuid":"$sku","delta_qty":1.0,"unit":"g","subject_id":"$subject"}
        """.trimIndent().replace("\n", " ")
        assertTrue(repo.upsert(subject, "pantry_events", payload), "first insert should add a row")
        assertTrue(!repo.upsert(subject, "pantry_events", payload), "duplicate event_uuid should be a no-op")
    }

    @Test
    fun `listSince returns only this subject's rows`() {
        val db = freshDb()
        val repo = EventRepository(db)
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        seedSubject(db, alice)
        seedSubject(db, bob)
        repo.upsert(alice, "pantry_events", payloadFor(alice))
        repo.upsert(bob, "pantry_events", payloadFor(bob))

        val aliceRows = repo.listSince(alice, "pantry_events", 0L, null, 500)
        val bobRows = repo.listSince(bob, "pantry_events", 0L, null, 500)
        assertEquals(1, aliceRows.size, "Alice sees her own row")
        assertEquals(1, bobRows.size, "Bob sees his own row")
        assertTrue(aliceRows.first().payloadJson.contains(alice.toString()))
        assertTrue(bobRows.first().payloadJson.contains(bob.toString()))
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

    private fun payloadFor(subjectId: UUID): String {
        val evt = UUID.randomUUID()
        val sku = UUID.randomUUID()
        return """
            {"event_uuid":"$evt","device_id":"dev-test","originated_at":"2026-05-18T10:00:00Z",
             "synced_at":"2026-05-18T10:00:01Z",
             "sku_uuid":"$sku","delta_qty":1.0,"unit":"g","subject_id":"$subjectId"}
        """.trimIndent().replace("\n", " ")
    }
}

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

    /**
     * Council 1779188964 tracer-bullet finding. Server must inject
     * `subject_id` from the session-derived caller, NOT trust the
     * client-supplied value. If the client lies (or omits) subject_id, the
     * server's value wins.
     */
    @Test
    fun `upsert overrides client-supplied subject_id with session subject`() {
        val db = freshDb()
        val repo = EventRepository(db)
        val sessionSubject = UUID.randomUUID()
        val attackerClaim = UUID.randomUUID()
        seedSubject(db, sessionSubject)
        seedSubject(db, attackerClaim)
        val evt = UUID.randomUUID()
        val sku = UUID.randomUUID()
        // Payload claims subject_id = attackerClaim (an attacker attempting
        // to write into another subject's row).
        val payload =
            """
            {"event_uuid":"$evt","device_id":"dev-attack","originated_at":"2026-05-18T10:00:00Z",
             "sku_uuid":"$sku","delta_qty":1.0,"unit":"g","subject_id":"$attackerClaim"}
            """.trimIndent().replace("\n", " ")
        // Server's withSubject + jsonb_set forces subject_id to sessionSubject.
        assertTrue(repo.upsert(sessionSubject, "pantry_events", payload))
        val sessionRows = repo.listSince(sessionSubject, "pantry_events", 0L, null, 500)
        val attackerRows = repo.listSince(attackerClaim, "pantry_events", 0L, null, 500)
        assertEquals(1, sessionRows.size, "row landed under the session subject")
        assertEquals(0, attackerRows.size, "client-claimed subject MUST NOT receive the row")
        assertTrue(
            sessionRows.first().payloadJson.contains(sessionSubject.toString()),
            "stored subject_id must equal sessionSubject ($sessionSubject)",
        )
        assertTrue(
            !sessionRows.first().payloadJson.contains(attackerClaim.toString()),
            "stored row must NOT carry the client's claimed subject_id ($attackerClaim)",
        )
    }

    /**
     * Council 1779188964 tracer-bullet finding. Server must set `synced_at`
     * to its own clock (time-of-arrival), not trust the client's. If the
     * client lies about synced_at (e.g. with a 2020 timestamp), the server
     * still records the actual receipt time.
     */
    @Test
    fun `upsert sets server-side synced_at, overriding client value`() {
        val db = freshDb()
        val repo = EventRepository(db)
        val subject = UUID.randomUUID()
        seedSubject(db, subject)
        val evt = UUID.randomUUID()
        // Client lies about synced_at — 6 years in the past.
        val payload =
            """
            {"event_uuid":"$evt","device_id":"dev","originated_at":"2026-05-18T10:00:00Z",
             "synced_at":"2020-01-01T00:00:00Z",
             "sku_uuid":"${UUID.randomUUID()}","delta_qty":1.0,"unit":"g"}
            """.trimIndent().replace("\n", " ")
        val beforeMs = System.currentTimeMillis()
        assertTrue(repo.upsert(subject, "pantry_events", payload))
        val afterMs = System.currentTimeMillis()
        val rows = repo.listSince(subject, "pantry_events", 0L, null, 500)
        assertEquals(1, rows.size)
        val serverSyncedMs = rows.first().syncedAtMs
        // 1-second slack each side for clock variance.
        assertTrue(
            serverSyncedMs >= beforeMs - 1000,
            "synced_at must be server-side (>= request issuance): got $serverSyncedMs vs $beforeMs",
        )
        assertTrue(
            serverSyncedMs <= afterMs + 1000,
            "synced_at must be server-side (<= response time): got $serverSyncedMs vs $afterMs",
        )
    }

    /**
     * Council 1779188964 tracer-bullet finding. Real clients (Plan-4-5 UI
     * outbox) don't know `subject_id` (session-derived) or `synced_at`
     * (server time). Without server injection, the row's NOT NULL
     * constraints fire and the push is rejected. With server injection, a
     * minimal payload omitting both fields lands cleanly.
     */
    @Test
    fun `upsert accepts payload that omits subject_id and synced_at`() {
        val db = freshDb()
        val repo = EventRepository(db)
        val subject = UUID.randomUUID()
        seedSubject(db, subject)
        val evt = UUID.randomUUID()
        val sku = UUID.randomUUID()
        // Client payload omits server-authoritative fields entirely.
        val payload =
            """
            {"event_uuid":"$evt","device_id":"plan-4-5-client","originated_at":"2026-05-18T10:00:00Z",
             "sku_uuid":"$sku","delta_qty":1.0,"unit":"g","reason":"manual_log"}
            """.trimIndent().replace("\n", " ")
        assertTrue(repo.upsert(subject, "pantry_events", payload), "insert should succeed")
        val rows = repo.listSince(subject, "pantry_events", 0L, null, 500)
        assertEquals(1, rows.size, "row must be readable under the session subject")
        // synced_at must be populated despite being absent from the payload.
        assertTrue(rows.first().syncedAtMs > 0, "synced_at must be populated by the server")
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

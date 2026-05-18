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
class SubjectRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "subject_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS subject_app_test")
                    st.execute("CREATE ROLE subject_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO subject_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO subject_app_test")
                    st.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO subject_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "subject_app_test", APP_PW)
        dbRef = d
        return d
    }

    @Test
    fun `create then findByEmail returns the created subject`() {
        val repo = SubjectRepository(freshDb())
        val email = "alice-${UUID.randomUUID()}@example.com"
        val id = repo.create(displayName = "Alice", emailForMagicLink = email)
        val found = repo.findByEmail(email)
        assertNotNull(found)
        assertEquals(id, found.subjectId)
        assertEquals("Alice", found.displayName)
        assertEquals(email, found.emailForMagicLink)
        assertFalse(found.isDeleted, "fresh subject should not be deleted")
    }

    @Test
    fun `findByEmail returns null on unknown email`() {
        val repo = SubjectRepository(freshDb())
        assertNull(repo.findByEmail("no-such-${UUID.randomUUID()}@example.com"))
    }

    @Test
    fun `softDelete makes findByEmail return null but findById still resolves`() {
        val repo = SubjectRepository(freshDb())
        val email = "todelete-${UUID.randomUUID()}@example.com"
        val id = repo.create(displayName = "Bob", emailForMagicLink = email)
        repo.softDelete(id)
        assertNull(repo.findByEmail(email), "soft-deleted subject must not show up in email lookup")
        val byId = repo.findById(id)
        assertNotNull(byId)
        assertTrue(byId.isDeleted, "findById still returns the row but flagged deleted")
    }

    @Test
    fun `registerDevice + listDevices round-trip`() {
        val repo = SubjectRepository(freshDb())
        val sid = repo.create(displayName = "Carol", emailForMagicLink = "carol-${UUID.randomUUID()}@example.com")
        val d1 = repo.registerDevice(sid, "carol-android")
        val d2 = repo.registerDevice(sid, "carol-desktop")
        val devices = repo.listDevices(sid)
        assertEquals(2, devices.size)
        val ids = devices.map { it.deviceId }.toSet()
        assertTrue(d1 in ids && d2 in ids, "both registered devices should appear")
    }
}

private fun assertFalse(actual: Boolean, message: String) {
    assertTrue(!actual, message)
}

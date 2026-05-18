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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
class ConsentRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "consent_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS consent_app_test")
                    st.execute("CREATE ROLE consent_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO consent_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO consent_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "consent_app_test", APP_PW)
        dbRef = d
        return d
    }

    @Test
    fun `grant + listForSubject + withdraw round-trip`() {
        val db = freshDb()
        val repo = ConsentRepository(db)
        val subject = UUID.randomUUID()
        seedSubject(db, subject)

        val cId = repo.grant(subject, "process_meal_data", "v1-sha-aaaaaa")
        assertNotNull(cId)
        assertTrue(repo.hasActive(subject, "process_meal_data"), "active consent must report active")

        val list = repo.listForSubject(subject)
        assertEquals(1, list.size)
        assertEquals("process_meal_data", list.first().scope)
        assertTrue(list.first().isActive)

        val withdrew = repo.withdraw(subject, "process_meal_data")
        assertEquals(1, withdrew, "withdraw should affect 1 row")
        assertFalse(repo.hasActive(subject, "process_meal_data"), "after withdraw, no active row")

        val listAfter = repo.listForSubject(subject)
        assertEquals(1, listAfter.size)
        assertFalse(listAfter.first().isActive)
    }

    @Test
    fun `cross-subject RLS isolation - Alice cannot withdraw Bob's consent`() {
        val db = freshDb()
        val repo = ConsentRepository(db)
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        seedSubject(db, alice)
        seedSubject(db, bob)

        repo.grant(bob, "process_weight_data", "v1")
        // Alice attempts to withdraw bob's consent.
        val affected = repo.withdraw(alice, "process_weight_data")
        assertEquals(0, affected, "Alice should not be able to affect Bob's rows")
        assertTrue(repo.hasActive(bob, "process_weight_data"), "Bob's consent still active")
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
}

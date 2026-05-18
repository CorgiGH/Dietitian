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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class CredentialRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "credential_app_test_pw"
        private const val CRED_PASS = "test-master-passphrase-32-chars!"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS credential_app_test")
                    st.execute("CREATE ROLE credential_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO credential_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO credential_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "credential_app_test", APP_PW)
        dbRef = d
        return d
    }

    private fun freshRepo(db: DatabaseFactory): CredentialRepository =
        CredentialRepository(db, passphraseOverride = CRED_PASS)

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
    fun `upsert then read round-trip via pgcrypto`() {
        val db = freshDb()
        val repo = freshRepo(db)
        val subject = UUID.randomUUID()
        seedSubject(db, subject)

        repo.upsert(subject, "openrouter", "sk-or-v1-test-key-001")
        val plain = repo.read(subject, "openrouter")
        assertEquals("sk-or-v1-test-key-001", plain)
    }

    @Test
    fun `upsert second time overwrites encrypted_key`() {
        val db = freshDb()
        val repo = freshRepo(db)
        val subject = UUID.randomUUID()
        seedSubject(db, subject)

        repo.upsert(subject, "anthropic", "key-A")
        repo.upsert(subject, "anthropic", "key-B")
        assertEquals("key-B", repo.read(subject, "anthropic"))
    }

    @Test
    fun `revoke makes read return null but metadata still listed`() {
        val db = freshDb()
        val repo = freshRepo(db)
        val subject = UUID.randomUUID()
        seedSubject(db, subject)

        repo.upsert(subject, "gemini", "key-G")
        assertNotNull(repo.read(subject, "gemini"))
        assertEquals(1, repo.revoke(subject, "gemini"))
        assertNull(repo.read(subject, "gemini"), "revoked credential must not decrypt")

        val list = repo.listForSubject(subject)
        assertEquals(1, list.size)
        assertFalse(list.first().isActive)
    }

    @Test
    fun `cross-subject RLS isolation - Alice cannot read Bob's key`() {
        val db = freshDb()
        val repo = freshRepo(db)
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        seedSubject(db, alice)
        seedSubject(db, bob)

        repo.upsert(bob, "groq", "bob-secret")
        assertNull(repo.read(alice, "groq"), "Alice must not be able to read Bob's credential")
        assertTrue(repo.read(bob, "groq") == "bob-secret", "Bob still sees his own")
    }
}

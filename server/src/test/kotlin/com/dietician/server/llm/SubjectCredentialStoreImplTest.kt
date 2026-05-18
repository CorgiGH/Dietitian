package com.dietician.server.llm

import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.repo.CredentialRepository
import com.dietician.shared.llm.ProviderId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan-2 Task 29 — Server-side SubjectCredentialStore (wraps Plan-3 CredentialRepository
 * with pgcrypto round-trip).
 */
@Testcontainers
class SubjectCredentialStoreImplTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "cred_store_test_pw"
        private const val PASSPHRASE = "test-passphrase-not-prod"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto")
                    st.execute("DROP ROLE IF EXISTS cred_store_test")
                    st.execute("CREATE ROLE cred_store_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO cred_store_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cred_store_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "cred_store_test", APP_PW)
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
                ps.setString(2, "CredStore-${id.toString().take(8)}")
                ps.executeUpdate()
            }
        }
        return id
    }

    @Test
    fun `getKey returns decrypted key after upsert`() = runBlocking {
        val db = freshDb()
        val subj = seedSubject(db)
        val credRepo = CredentialRepository(db, passphraseOverride = PASSPHRASE)
        credRepo.upsert(subj, "openrouter", "sk-secret-test-123")

        val store = SubjectCredentialStoreImpl(credRepo)
        val key = store.getKey(subj.toString(), ProviderId("openrouter"))
        assertEquals("sk-secret-test-123", key)
    }

    @Test
    fun `getKey returns null for unknown subject + provider`() = runBlocking {
        val db = freshDb()
        val credRepo = CredentialRepository(db, passphraseOverride = PASSPHRASE)
        val store = SubjectCredentialStoreImpl(credRepo)
        val key = store.getKey(UUID.randomUUID().toString(), ProviderId("anthropic"))
        assertNull(key)
    }

    @Test
    fun `getKey returns null for non-UUID subject id`() = runBlocking {
        val db = freshDb()
        val credRepo = CredentialRepository(db, passphraseOverride = PASSPHRASE)
        val store = SubjectCredentialStoreImpl(credRepo)
        assertNull(store.getKey("not-a-uuid", ProviderId("openrouter")))
    }

    @Test
    fun `listProviders returns active credential set`() = runBlocking {
        val db = freshDb()
        val subj = seedSubject(db)
        val credRepo = CredentialRepository(db, passphraseOverride = PASSPHRASE)
        credRepo.upsert(subj, "openrouter", "a")
        credRepo.upsert(subj, "anthropic", "b")
        credRepo.revoke(subj, "anthropic")

        val store = SubjectCredentialStoreImpl(credRepo)
        val active = store.listProviders(subj.toString())
        assertEquals(setOf(ProviderId("openrouter")), active)
        assertTrue(ProviderId("anthropic") !in active)
    }
}

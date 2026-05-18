package com.dietician.server.db

import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Council #1 fix: was originally Docker-blocked and shipped with a hard-coded migration count.
 * Now actually runs against pgvector/pgvector:pg16 Testcontainer and asserts:
 *   1. migration count matches the live `db/migration` directory (no hard-coded drift),
 *   2. core event-ledger tables exist post-migration (smoke against schema regressions),
 *   3. `vector` extension is installed (V010 pgvector indexes depend on it).
 */
@Testcontainers
class MigrationOrderingTest {
    companion object {
        @Container
        @JvmStatic
        val pg = PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")
    }

    @Test
    fun `all V00X migrations apply cleanly and are idempotent`() {
        val migrationDir = File("src/main/resources/db/migration")
        check(migrationDir.isDirectory) { "migration dir not found at ${migrationDir.absolutePath}" }
        val expectedCount =
            migrationDir.listFiles { f -> f.name.matches(Regex("V\\d+__.*\\.sql")) }!!.size
        check(expectedCount > 0) { "no V*.sql migrations found in ${migrationDir.absolutePath}" }

        val applied1 = runMigrations(pg.jdbcUrl, pg.username, pg.password)
        val applied2 = runMigrations(pg.jdbcUrl, pg.username, pg.password)
        assertEquals(expectedCount, applied1, "first run applies $expectedCount migrations")
        assertEquals(0, applied2, "second run is a no-op")
    }

    @Test
    fun `core event-ledger tables exist after migrations`() {
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        val expected = setOf("pantry_events", "meal_events", "weight_events", "receipt_events")
        val found = mutableSetOf<String>()
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { conn ->
            conn.createStatement().executeQuery(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                """.trimIndent(),
            ).use { rs ->
                while (rs.next()) found += rs.getString(1)
            }
        }
        val missing = expected - found
        assertTrue(
            missing.isEmpty(),
            "core event-ledger tables missing after migrations: $missing (found=$found)",
        )
    }

    @Test
    fun `pgvector extension is installed after migrations`() {
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { conn ->
            conn.createStatement().executeQuery(
                "SELECT extname FROM pg_extension WHERE extname = 'vector'",
            ).use { rs ->
                assertTrue(rs.next(), "pgvector extension not installed after migrations")
                assertEquals("vector", rs.getString(1))
                assertTrue(!rs.next(), "more than one 'vector' extension row found")
            }
        }
    }
}

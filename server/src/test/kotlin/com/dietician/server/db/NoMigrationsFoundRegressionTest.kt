package com.dietician.server.db

import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertTrue

/**
 * Council 1779188964 tracer-bullet first deploy regression.
 *
 * 2026-05-19 first VPS deploy: server booted OK, Hikari connected, but
 * `Flyway.configure()` (default thread-context classloader) emitted
 * `"No migrations found. Are your locations set up correctly?"` and ran
 * ZERO migrations despite all 21 SQL files being inside the fat-JAR at
 * `db/migration/V*.sql`. Operator had to extract + apply manually via
 * `psql` to unblock the tracer-bullet round-trip.
 *
 * Fix: [FlywayClassloaderAnchor] in `Flyway.kt` forces Flyway to use a
 * classloader that can actually see the embedded resources.
 *
 * This test asserts `runMigrations` reports >0 executed migrations against
 * a fresh database. If the classpath fix ever regresses, this test fails
 * BEFORE the JAR can ship to the VPS.
 */
@Testcontainers
class NoMigrationsFoundRegressionTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_migrate_test")
    }

    @Test
    fun `runMigrations finds and applies the V0xx migration files on a fresh database`() {
        val executed = runMigrations(pg.jdbcUrl, pg.username, pg.password)
        assertTrue(
            executed > 0,
            "runMigrations must execute >0 migrations on a fresh DB. Got $executed. " +
                "If this is 0, Flyway can't find db/migration/V*.sql via its current classloader " +
                "— check FlywayClassloaderAnchor in Flyway.kt (council 1779188964 regression).",
        )
        // We currently ship 21 migrations (V001..V021). Assert at least
        // the expected count so a future "Flyway runs but skips some" bug
        // is also caught — and the assertion's lower bound makes it
        // tolerant of new migrations being added.
        assertTrue(
            executed >= 21,
            "Expected >=21 migrations executed on fresh DB; got $executed. " +
                "Check resources/db/migration/ for missing files.",
        )
    }
}

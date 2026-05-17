package com.dietician.server.db

import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@Testcontainers
class MigrationOrderingTest {
    companion object {
        @Container
        @JvmStatic
        val pg = PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")
    }

    @Test
    fun `all V00X migrations apply cleanly and are idempotent`() {
        val applied1 = runMigrations(pg.jdbcUrl, pg.username, pg.password)
        val applied2 = runMigrations(pg.jdbcUrl, pg.username, pg.password)
        assertEquals(10, applied1, "first run applies 10 migrations")
        assertEquals(0, applied2, "second run is a no-op")
    }
}

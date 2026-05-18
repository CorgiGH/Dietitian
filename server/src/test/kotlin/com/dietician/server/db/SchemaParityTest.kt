package com.dietician.server.db

import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.fail

/**
 * Council BREAK #4: schema-parity CI gate.
 *
 * Boots a `pgvector/pgvector:pg16` Testcontainer, runs Flyway migrations against it, then
 * instantiates `DieticianDatabase.Schema` against an in-memory SQLite driver. Compares the two
 * schemas modulo the allow-list at `server/src/test/resources/schema-parity/allow-list.json`.
 *
 * If a CORE event-ledger column drifts (e.g. `pantry_events.synced_at` becomes BIGINT on
 * Postgres while staying INTEGER on SQLite, or a new column lands on one side only), this test
 * fails BEFORE the divergence reaches production. The plan-1/shared-data sync layer treats
 * (timestamp,uuid) cursors as source-of-truth; a type-alias drift on those columns is exactly
 * the corruption class this gate prevents.
 *
 * Adding to the allow-list is intentionally awkward — only legitimate client-only or
 * server-only tables (queues, snapshot caches, ledger projections) belong there. Any core
 * event-ledger divergence should be fixed in the offending `.sql` / `.sq` instead.
 */
@Testcontainers
class SchemaParityTest {
    companion object {
        @Container
        @JvmStatic
        val pg = PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_parity")
    }

    @Test
    fun `Postgres schema parity vs SQLDelight schema after migrations`() {
        runMigrations(pg.jdbcUrl, pg.username, pg.password)

        val pgSchema = dumpPgSchema(pg.jdbcUrl, pg.username, pg.password)
        val sqldelightSchema = dumpSqldelightSchema()
        val allowList = loadAllowList()

        val violations = compareSchemas(pgSchema, sqldelightSchema, allowList)
        if (violations.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("Schema parity violations (${violations.size}):")
                    violations.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine(
                        "If a violation is a legitimate client-vs-server split, add it to " +
                            "server/src/test/resources/schema-parity/allow-list.json with a justification. " +
                            "Otherwise fix the offending Flyway .sql or SQLDelight .sq file.",
                    )
                },
            )
        }
    }
}

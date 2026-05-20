package com.dietician.shared.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.sql.DieticianDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSchemaMigratorTest {

    private fun newDriver(): JdbcSqliteDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private fun tempDir(): File = Files.createTempDirectory("dm-test").toFile()

    private fun userVersion(driver: SqlDriver): Long {
        var v = 0L
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { c ->
                if (c.next().value) v = c.getLong(0) ?: 0L
                QueryResult.Unit
            },
            parameters = 0,
        )
        return v
    }

    @Test
    fun freshDatabaseGetsFullSchemaAtCurrentVersion() {
        newDriver().use { driver ->
            val db = DieticianDatabase(driver)
            DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())
            assertEquals(SchemaInvariant.EXPECTED_TABLES, SchemaInvariant.liveTables(driver))
            assertEquals(DieticianDatabase.Schema.version, userVersion(driver))
        }
    }

    @Test
    fun freshDatabaseRunDeletesNoLongerNeededMarkerIfSomehowPresent() {
        newDriver().use { driver ->
            val db = DieticianDatabase(driver)
            val dir = tempDir()
            File(dir, ".schema_applied").writeText("v1")
            DesktopSchemaMigrator.ensureSchema(db, driver, dir)
            assertFalse(File(dir, ".schema_applied").exists())
        }
    }

    @Test
    fun reRunOnAFreshlyMigratedDatabaseIsIdempotent() {
        newDriver().use { driver ->
            val db = DieticianDatabase(driver)
            val dir = tempDir()
            DesktopSchemaMigrator.ensureSchema(db, driver, dir)
            DesktopSchemaMigrator.ensureSchema(db, driver, dir) // second run must not throw
            assertEquals(SchemaInvariant.EXPECTED_TABLES, SchemaInvariant.liveTables(driver))
        }
    }

    /** Builds a database in the exact v1 shape: 28 tables, no audit_pending_outbox, user_version 0. */
    private fun createV1Database(driver: SqlDriver) {
        DieticianDatabase.Schema.create(driver) // v2 schema
        driver.execute(null, "DROP INDEX idx_audit_pending_outbox_started", 0)
        driver.execute(null, "DROP TABLE audit_pending_outbox", 0)
        driver.execute(null, "PRAGMA user_version = 0", 0)
    }

    private fun tableExists(driver: SqlDriver, name: String): Boolean =
        SchemaInvariant.liveTables(driver).contains(name)

    @Test
    fun legacyV1DatabaseWithMarkerIsMigratedToV2() {
        newDriver().use { driver ->
            val db = DieticianDatabase(driver)
            val dir = tempDir()
            createV1Database(driver)
            File(dir, ".schema_applied").writeText("v1")
            assertFalse(tableExists(driver, "audit_pending_outbox"))

            DesktopSchemaMigrator.ensureSchema(db, driver, dir)

            assertEquals(SchemaInvariant.EXPECTED_TABLES, SchemaInvariant.liveTables(driver))
            assertEquals(DieticianDatabase.Schema.version, userVersion(driver))
            assertFalse(File(dir, ".schema_applied").exists())
        }
    }

    @Test
    fun legacyV1DatabaseWithNoMarkerIsStillMigrated() {
        // Council case: the old code wrote the marker non-atomically after Schema.create,
        // so a crash leaves a populated v1 DB with no marker. Decision must come from
        // sqlite_master, so this still migrates.
        newDriver().use { driver ->
            val db = DieticianDatabase(driver)
            val dir = tempDir()
            createV1Database(driver)

            DesktopSchemaMigrator.ensureSchema(db, driver, dir)

            assertTrue(tableExists(driver, "audit_pending_outbox"))
            assertEquals(DieticianDatabase.Schema.version, userVersion(driver))
            assertFalse(File(dir, ".schema_applied").exists())
        }
    }

    @Test
    fun migrationPreservesUnsyncedLedgerRows() {
        newDriver().use { driver ->
            val db = DieticianDatabase(driver)
            createV1Database(driver)
            driver.execute(
                null,
                "INSERT INTO pantry_events(event_uuid, device_id, originated_at, synced_at, " +
                    "sku_uuid, delta_qty, unit, reason, evidence_ref) " +
                    "VALUES ('evt-1', 'dev-1', 100, NULL, 'sku-1', 2.0, 'pcs', NULL, NULL)",
                0,
            )

            DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())

            var surviving = 0L
            driver.executeQuery(
                identifier = null,
                sql = "SELECT count(*) FROM pantry_events WHERE event_uuid = 'evt-1'",
                mapper = { c ->
                    if (c.next().value) surviving = c.getLong(0) ?: 0L
                    QueryResult.Unit
                },
                parameters = 0,
            )
            assertEquals(1L, surviving)
        }
    }

    @Test
    fun auditPendingOutboxIsQueryableAfterMigration() {
        newDriver().use { driver ->
            val db = DieticianDatabase(driver)
            createV1Database(driver)

            DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())

            db.`0009_audit_pending_outboxQueries`.insertOutboxRow(
                idempotency_key = "key-1",
                reservation_id = "res-1",
                prompt_hash = "hash-1",
                started_at_ms = 1L,
                last_attempt_at_ms = 1L,
                attempts = 0L,
                provider = "claudemax",
            )
            assertEquals(1, db.`0009_audit_pending_outboxQueries`.findUncommitted().executeAsList().size)
        }
    }

    @Test
    fun fullV2SchemaAtUserVersionZeroIsReconciledNotRecreated() {
        // A DB created post-0009 by old code, or a fresh create that crashed before the
        // version bump: all 29 tables present, user_version still 0. Must NOT re-run
        // Schema.create (would crash "table already exists") — just bump the version.
        newDriver().use { driver ->
            val db = DieticianDatabase(driver)
            DieticianDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA user_version = 0", 0)

            DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())

            assertEquals(SchemaInvariant.EXPECTED_TABLES, SchemaInvariant.liveTables(driver))
            assertEquals(DieticianDatabase.Schema.version, userVersion(driver))
        }
    }

    @Test
    fun partialSchemaFailsLoud() {
        // A crash mid-create in the OLD (non-transactional) code path could leave some
        // tables and not others. The migrator must refuse to guess.
        newDriver().use { driver ->
            val db = DieticianDatabase(driver)
            DieticianDatabase.Schema.create(driver)
            driver.execute(null, "DROP TABLE meal_events", 0) // 28 tables, but not the v1 set

            val ex = assertFailsWith<IllegalStateException> {
                DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())
            }
            assertTrue(ex.message!!.contains("Unrecognized desktop client schema"))
        }
    }
}

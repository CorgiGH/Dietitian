package com.dietician.shared.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.sql.DieticianDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
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
            mapper = { c -> if (c.next().value) v = c.getLong(0) ?: 0L; QueryResult.Unit },
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
            assertTrue(true)
        }
    }
}

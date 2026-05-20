package com.dietician.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.sql.DieticianDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchemaInvariantTest {

    @Test
    fun schemaVersionIsTwoAfterAddingFirstMigration() {
        assertEquals(2L, DieticianDatabase.Schema.version)
    }

    @Test
    fun expectedTablesHasTwentyNineEntries() {
        assertEquals(29, SchemaInvariant.EXPECTED_TABLES.size)
    }

    @Test
    fun freshCreateProducesExactlyExpectedTables() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DieticianDatabase.Schema.create(driver)
        assertEquals(SchemaInvariant.EXPECTED_TABLES, SchemaInvariant.liveTables(driver))
    }

    @Test
    fun assertExpectedTablesPassesOnFullSchema() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DieticianDatabase.Schema.create(driver)
        SchemaInvariant.assertExpectedTables(driver) // does not throw
    }

    @Test
    fun assertExpectedTablesThrowsWhenATableIsMissing() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DieticianDatabase.Schema.create(driver)
        driver.execute(null, "DROP TABLE audit_pending_outbox", 0)
        val ex = assertFailsWith<IllegalStateException> {
            SchemaInvariant.assertExpectedTables(driver)
        }
        assertTrue(ex.message!!.contains("audit_pending_outbox"))
    }
}

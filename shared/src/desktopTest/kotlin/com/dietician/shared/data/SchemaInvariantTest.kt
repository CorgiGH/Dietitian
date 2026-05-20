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

    // Guards EXPECTED_TABLES itself: catches a simultaneous add to both EXPECTED_TABLES
    // and the schema, which the set-equality test below would not flag.
    @Test
    fun expectedTablesHasTwentyNineEntries() {
        assertEquals(29, SchemaInvariant.EXPECTED_TABLES.size)
    }

    @Test
    fun freshCreateProducesExactlyExpectedTables() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DieticianDatabase.Schema.create(driver)
            assertEquals(SchemaInvariant.EXPECTED_TABLES, SchemaInvariant.liveTables(driver))
        }
    }

    @Test
    fun assertExpectedTablesPassesOnFullSchema() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DieticianDatabase.Schema.create(driver)
            SchemaInvariant.assertExpectedTables(driver) // does not throw
        }
    }

    @Test
    fun assertExpectedTablesThrowsWhenATableIsMissing() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DieticianDatabase.Schema.create(driver)
            driver.execute(null, "DROP TABLE audit_pending_outbox", 0)
            val ex = assertFailsWith<IllegalStateException> {
                SchemaInvariant.assertExpectedTables(driver)
            }
            assertTrue(ex.message!!.contains("audit_pending_outbox"))
        }
    }
}

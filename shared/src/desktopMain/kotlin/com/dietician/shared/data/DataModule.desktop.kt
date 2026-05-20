package com.dietician.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.local.WalPragmas
import com.dietician.shared.data.sql.DieticianDatabase
import java.io.File
import java.util.Properties

object DataModuleDesktop {
    fun build(): DieticianDatabase {
        val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Dietician").apply { mkdirs() }
        val driver =
            JdbcSqliteDriver(
                "jdbc:sqlite:${File(dir, "dietician.db")}",
                Properties().apply { put("foreign_keys", "ON") },
            )
        val database = DieticianDatabase(driver)
        // Versioned create/migrate driven by sqlite_master, not a marker file. Council 1779306247.
        DesktopSchemaMigrator.ensureSchema(database, driver, dir)
        WalPragmas.applyAll(driver)
        return database
    }
}

package com.dietician.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.local.WalPragmas
import com.dietician.shared.data.sql.DieticianDatabase
import java.io.File
import java.util.Properties

object DataModuleDesktop {
    fun build(): DieticianDatabase {
        val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Dietician").apply { mkdirs() }
        val driver = JdbcSqliteDriver(
            "jdbc:sqlite:${File(dir, "dietician.db")}",
            Properties().apply { put("foreign_keys", "ON") },
        )
        // SQLDelight's JDBC driver doesn't auto-apply Schema; do it explicitly the first time.
        if (!File(dir, ".schema_applied").exists()) {
            DieticianDatabase.Schema.create(driver)
            File(dir, ".schema_applied").writeText("v1")
        }
        WalPragmas.applyAll(driver)
        return DieticianDatabase(driver)
    }
}

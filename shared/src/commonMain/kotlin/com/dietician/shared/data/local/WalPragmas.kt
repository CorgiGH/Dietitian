package com.dietician.shared.data.local

import app.cash.sqldelight.db.SqlDriver

object WalPragmas {
    val INIT = listOf(
        "PRAGMA journal_mode=WAL",
        "PRAGMA synchronous=NORMAL",
        "PRAGMA busy_timeout=5000",
        "PRAGMA cache_size=-64000",
        "PRAGMA foreign_keys=ON",
        "PRAGMA wal_autocheckpoint=1000",
    )

    fun applyAll(driver: SqlDriver) {
        INIT.forEach { driver.execute(null, it, 0) }
    }

    fun forceTruncatingCheckpoint(driver: SqlDriver) {
        driver.execute(null, "PRAGMA wal_checkpoint(TRUNCATE)", 0)
    }
}

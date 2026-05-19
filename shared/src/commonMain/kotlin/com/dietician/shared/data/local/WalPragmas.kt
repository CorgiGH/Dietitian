package com.dietician.shared.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

object WalPragmas {
    /**
     * Full PRAGMA list, ordered. `journal_mode=WAL` must come first.
     *
     * Used by the Android `AndroidSqliteDriver.Callback.onOpen` path which routes through
     * `SupportSQLiteDatabase.execSQL` — that path tolerates row-returning PRAGMAs.
     */
    val INIT =
        listOf(
            "PRAGMA journal_mode=WAL",
            "PRAGMA synchronous=NORMAL",
            "PRAGMA busy_timeout=5000",
            "PRAGMA cache_size=-64000",
            "PRAGMA foreign_keys=ON",
            "PRAGMA wal_autocheckpoint=1000",
        )

    /**
     * Driver-portable apply. Every PRAGMA is routed through `executeQuery` because the
     * Android SQLite driver's `execute` path uses `SQLiteStatement.executeUpdateDelete`,
     * which throws on PRAGMA statements that yield a row ("Queries can be performed using
     * SQLiteDatabase query or rawQuery methods only."). PRAGMAs frequently echo the new
     * value, so we play it safe and drain via executeQuery for all of them.
     */
    fun applyAll(driver: SqlDriver) {
        INIT.forEach { sql -> runPragma(driver, sql) }
    }

    fun forceTruncatingCheckpoint(driver: SqlDriver) {
        // `wal_checkpoint(TRUNCATE)` returns (busy, log, checkpointed) — must drain.
        runPragma(driver, "PRAGMA wal_checkpoint(TRUNCATE)")
    }

    private fun runPragma(
        driver: SqlDriver,
        sql: String,
    ) {
        // Driver asymmetry: the Android SQLite driver's `execute` path throws on
        // row-yielding PRAGMAs ("Queries can be performed using ... query ...
        // only"), so executeQuery is needed there. The desktop xerial JDBC
        // driver does the opposite — `executeQuery` on a SET-style PRAGMA that
        // yields NO ResultSet (e.g. `synchronous=NORMAL`, `foreign_keys=ON`)
        // throws `SQLException: Query does not return results`. Try the
        // query path first (covers Android + row-yielding pragmas), fall back
        // to `execute` for the no-result desktop case.
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        try {
            driver.executeQuery(
                identifier = null,
                sql = sql,
                mapper = { cursor ->
                    // Drain any rows the PRAGMA emits so the statement releases cleanly.
                    while (cursor.next().value) { /* discard */ }
                    QueryResult.Unit
                },
                parameters = 0,
            )
        } catch (e: Exception) {
            driver.execute(identifier = null, sql = sql, parameters = 0)
        }
    }
}

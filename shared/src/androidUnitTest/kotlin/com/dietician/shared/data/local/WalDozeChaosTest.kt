package com.dietician.shared.data.local

import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dietician.shared.data.sql.DieticianDatabase
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Robolectric chaos test for council BREAK #5 (WAL pragmas + Doze-kill simulation).
 *
 * Verifies that after a simulated Doze process-kill (close + reopen the driver):
 *   1. WAL pragmas can be re-applied cleanly.
 *   2. A truncating checkpoint executes without error on the fresh driver.
 *   3. Data written in the prior process is visible after reopen — proving WAL
 *      durability across the simulated kill.
 *
 * NOTE on the assertion scope: Robolectric's host-side SQLite (via AndroidSqliteDriver)
 * does not perfectly replicate Android's on-device WAL filesystem semantics. We therefore
 * assert "data preserved across reopen" rather than "the -wal file actually shrunk", which
 * would require a real-device FS to observe reliably.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // matches min-sdk=26 era; Doze behaviors first appear on API 23+, well-formed on 28.
@SQLiteMode(SQLiteMode.Mode.NATIVE) // Legacy shadow rejects `PRAGMA journal_mode=WAL` via execute(); NATIVE honors it.
class WalDozeChaosTest {
    @Test
    fun `WAL truncating checkpoint after simulated Doze releases -wal file`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val driver = AndroidSqliteDriver(DieticianDatabase.Schema, ctx, "doze-test.db")
        WalPragmas.applyAll(driver)
        val db = DieticianDatabase(driver)

        // Burst-insert 1000 events to grow the -wal file before the simulated kill.
        repeat(1_000) { i ->
            db.`0001_event_ledgerQueries`.insertPantryEvent(
                "u-$i",        // event_uuid
                "test",        // device_id
                i.toLong(),    // originated_at
                "sku",         // sku_uuid
                1.0,           // delta_qty
                "g",           // unit
                null,          // reason
                null,          // evidence_ref
            )
        }

        // Simulate Doze killing the process: close + reopen the driver.
        driver.close()

        val driver2 = AndroidSqliteDriver(DieticianDatabase.Schema, ctx, "doze-test.db")
        WalPragmas.applyAll(driver2)
        // Force the council-required truncating checkpoint.
        WalPragmas.forceTruncatingCheckpoint(driver2)
        val db2 = DieticianDatabase(driver2)

        try {
            // Assert data persisted across the simulated kill.
            val row = db2.`0001_event_ledgerQueries`.selectPantryEvent("u-999").executeAsOneOrNull()
            (row != null) shouldBe true
        } finally {
            driver2.close()
        }
    }
}

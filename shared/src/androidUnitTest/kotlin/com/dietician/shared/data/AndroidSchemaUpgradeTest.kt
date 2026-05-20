package com.dietician.shared.data

import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dietician.shared.data.sql.DieticianDatabase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import kotlin.test.assertTrue

/**
 * Verifies that reopening an Android SQLite DB stamped at user_version 1 with
 * [DieticianDatabase.Schema] (version 2) causes [AndroidSqliteDriver] to fire
 * `onUpgrade(1, 2)` → `Schema.migrate` → the `1.sqm` DDL, creating
 * `audit_pending_outbox`.
 *
 * This is a regression/characterisation test — `1.sqm` and the Android driver
 * are already in place, so the test MUST pass on the first write. A failure
 * means an [AndroidSqliteDriver] wiring problem.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AndroidSchemaUpgradeTest {

    /**
     * A stand-in version-1 schema.
     *
     * `create` writes one real table so that the open-helper has something to
     * operate on and reliably stamps `user_version = 1`.  An empty `create`
     * risks the helper skipping the version stamp on some Robolectric builds.
     */
    private val v1Schema =
        object : SqlSchema<QueryResult.Value<Unit>> {
            override val version: Long = 1L

            override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
                // A minimal sentinel table so SQLiteOpenHelper records version 1.
                driver.execute(
                    identifier = null,
                    sql = "CREATE TABLE IF NOT EXISTS _v1_sentinel (id INTEGER PRIMARY KEY)",
                    parameters = 0,
                )
                return QueryResult.Unit
            }

            override fun migrate(
                driver: SqlDriver,
                oldVersion: Long,
                newVersion: Long,
                vararg callbacks: AfterVersion,
            ): QueryResult.Value<Unit> = QueryResult.Unit
        }

    /**
     * Returns true if [name] exists as a table in sqlite_master.
     *
     * Uses the real SQLDelight 2.0.2 `executeQuery` signature:
     * `executeQuery(identifier, sql, mapper, parameters) { /* binders */ }`
     */
    private fun tableExists(driver: SqlDriver, name: String): Boolean {
        var found = false
        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            mapper = { cursor ->
                found = cursor.next().value
                QueryResult.Unit
            },
            parameters = 1,
        ) {
            bindString(0, name)
        }
        return found
    }

    @Before
    fun deleteStaleDatabaseIfPresent() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .deleteDatabase("upgrade-test.db")
    }

    @Test
    fun upgradingFromV1RunsTheMigrationAndAddsAuditPendingOutbox() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

        // First open at version 1 — creates the sentinel table and stamps user_version=1.
        AndroidSqliteDriver(schema = v1Schema, context = ctx, name = "upgrade-test.db").close()

        // Reopen with the real schema (version 2).
        // AndroidSqliteDriver calls onUpgrade(1 → 2) → Schema.migrate → 1.sqm DDL.
        val driver = AndroidSqliteDriver(
            schema = DieticianDatabase.Schema,
            context = ctx,
            name = "upgrade-test.db",
        )

        // This query is the first DB access — it forces AndroidSqliteDriver to open
        // the file, which runs onUpgrade(1, 2) -> Schema.migrate -> 1.sqm.
        assertTrue(
            tableExists(driver, "audit_pending_outbox"),
            "audit_pending_outbox must exist after upgrading from v1 to v2 via 1.sqm",
        )

        driver.close()
    }
}

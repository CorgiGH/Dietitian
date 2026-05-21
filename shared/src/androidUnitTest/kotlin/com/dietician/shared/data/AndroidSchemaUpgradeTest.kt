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

/**
 * Verifies that reopening an Android SQLite DB stamped at user_version 1 with
 * [DieticianDatabase.Schema] (version 2) causes [AndroidSqliteDriver] to fire
 * `onUpgrade(1, 2)` → `Schema.migrate` → the `1.sqm` DDL, and that the
 * resulting schema exactly matches the full 29-table v2 schema.
 *
 * The v1 seed is faithful: it creates the real v2 schema then drops the one
 * table (`audit_pending_outbox`) and its index that v1 never had, leaving
 * exactly the 28-table production v1 state. This mirrors [DesktopSchemaMigratorTest.createV1Database].
 *
 * A failure here means either [AndroidSqliteDriver] wiring is broken OR
 * `1.sqm` does not converge a real 28-table v1 database to the full v2 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AndroidSchemaUpgradeTest {

    /**
     * A faithful representation of the production v1 schema (28 tables).
     *
     * `create` materialises the real v1 state by running the full v2 schema
     * creation and then dropping the one table and its index that v1 never had
     * (`audit_pending_outbox` / `idx_audit_pending_outbox_started`). This
     * mirrors [DesktopSchemaMigratorTest.createV1Database] and ensures the
     * Android upgrade test exercises the migration against a real v1 database,
     * not a synthetic single-table fake.
     *
     * [AndroidSqliteDriver] stamps `user_version = 1` (the schema's [version])
     * after `create` returns, so the reopen at version 2 fires `onUpgrade(1, 2)`.
     */
    private val v1Schema =
        object : SqlSchema<QueryResult.Value<Unit>> {
            override val version: Long = 1L

            override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
                DieticianDatabase.Schema.create(driver)
                driver.execute(null, "DROP INDEX idx_audit_pending_outbox_started", 0)
                driver.execute(null, "DROP TABLE audit_pending_outbox", 0)
                return QueryResult.Unit
            }

            override fun migrate(
                driver: SqlDriver,
                oldVersion: Long,
                newVersion: Long,
                vararg callbacks: AfterVersion,
            ): QueryResult.Value<Unit> = QueryResult.Unit
        }

    @Before
    fun deleteStaleDatabaseIfPresent() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .deleteDatabase("upgrade-test.db")
    }

    @Test
    fun upgradingFromV1RunsTheMigrationAndConvergesToFullV2Schema() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

        // First open at version 1 — materialises the real 28-table v1 schema and stamps user_version=1.
        AndroidSqliteDriver(schema = v1Schema, context = ctx, name = "upgrade-test.db").close()

        // Reopen with the real schema (version 2).
        // AndroidSqliteDriver calls onUpgrade(1 → 2) → Schema.migrate → 1.sqm DDL.
        val driver = AndroidSqliteDriver(
            schema = DieticianDatabase.Schema,
            context = ctx,
            name = "upgrade-test.db",
        )

        // assertExpectedTables throws IllegalStateException if any of the 29 expected v2 tables
        // is missing OR any unexpected table is present. A clean return proves:
        //   (a) audit_pending_outbox was added by 1.sqm, AND
        //   (b) nothing else drifted — the upgrade fully converged the 28-table v1 to the
        //       exact 29-table v2 set defined in SchemaInvariant.EXPECTED_TABLES.
        SchemaInvariant.assertExpectedTables(driver)

        driver.close()
    }
}

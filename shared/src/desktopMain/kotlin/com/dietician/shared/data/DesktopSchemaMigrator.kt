package com.dietician.shared.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dietician.shared.data.sql.DieticianDatabase
import java.io.File

/**
 * Desktop client schema bring-up. Replaces the old `.schema_applied` marker file,
 * which permanently skipped `Schema.create` and never applied later `.sq` tables.
 *
 * The create-vs-migrate decision is driven by the DATABASE ITSELF (`sqlite_master`),
 * never by a sidecar file: the old marker was written non-atomically AFTER
 * `Schema.create`, so a crash could leave a fully-populated DB with no marker.
 * Each create/migrate runs inside one transaction with its `user_version` bump, so a
 * crash leaves the DB at exactly the old or the new schema. Council 1779306247.
 */
object DesktopSchemaMigrator {

    private const val LEGACY_MARKER = ".schema_applied"

    /** v1 = the schema before 0009 added audit_pending_outbox. */
    private val V1_TABLES: Set<String> = SchemaInvariant.EXPECTED_TABLES - "audit_pending_outbox"

    fun ensureSchema(database: DieticianDatabase, driver: SqlDriver, dbDir: File) {
        val present = SchemaInvariant.liveTables(driver)
        val target = DieticianDatabase.Schema.version

        when {
            present.isEmpty() -> {
                // Genuine fresh DB.
                database.transaction {
                    DieticianDatabase.Schema.create(driver)
                    setUserVersion(driver, target)
                }
            }

            present == SchemaInvariant.EXPECTED_TABLES -> {
                // Structurally at v2: a DB created post-0009, an already-migrated DB,
                // or a fresh create that crashed before the version bump. Idempotently
                // reconcile user_version; never re-create.
                if (readUserVersion(driver) != target) {
                    database.transaction { setUserVersion(driver, target) }
                }
            }

            present == V1_TABLES -> {
                // Legacy pre-0009 DB. Migrate v1 -> current. Marker, if any, is irrelevant.
                database.transaction {
                    DieticianDatabase.Schema.migrate(driver, oldVersion = 1L, newVersion = target)
                    setUserVersion(driver, target)
                }
            }

            else -> {
                // Partial / unrecognized: do NOT guess. Re-running Schema.create here
                // would crash on "table already exists"; a blind migrate could double-apply.
                error(
                    "Unrecognized desktop client schema — refusing to guess. " +
                        "Present (${present.size}): ${present.sorted()}. " +
                        "Expected v1 (${V1_TABLES.size}) or v2 (${SchemaInvariant.EXPECTED_TABLES.size}).",
                )
            }
        }

        File(dbDir, LEGACY_MARKER).delete() // best-effort cleanup of the retired marker
        SchemaInvariant.assertExpectedTables(driver) // runtime invariant — fail loud on drift
    }

    private fun readUserVersion(driver: SqlDriver): Long {
        var version = 0L
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor ->
                if (cursor.next().value) version = cursor.getLong(0) ?: 0L
                QueryResult.Unit
            },
            parameters = 0,
        )
        return version
    }

    private fun setUserVersion(driver: SqlDriver, version: Long) {
        // PRAGMA user_version takes no bind parameters — the value must be inlined.
        // It is transactional in SQLite, so it commits atomically with the surrounding
        // Schema.create / Schema.migrate.
        driver.execute(identifier = null, sql = "PRAGMA user_version = $version", parameters = 0)
    }
}

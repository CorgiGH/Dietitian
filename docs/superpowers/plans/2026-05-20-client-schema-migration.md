# Client Schema Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the desktop `.schema_applied` marker — which permanently skips `Schema.create` and never applies later schema tables — with SQLDelight 2.x versioned migrations, so an existing client DB upgrades correctly when a `.sq` table is added.

**Architecture:** Adopt SQLDelight `.sqm` migration discipline. The `.sq` files stay the current-schema definition; a numbered `N.sqm` file defines each upgrade and increments `DieticianDatabase.Schema.version`. A retroactive `1.sqm` carries the v1→v2 delta (`audit_pending_outbox`). Desktop uses a hand-rolled `DesktopSchemaMigrator` that decides create-vs-migrate by probing `sqlite_master` (the database itself — never the unreliable marker file), runs each create/migrate inside one transaction, and asserts a runtime table-set invariant. Android needs no migration logic — `AndroidSqliteDriver(schema=…)` already upgrades off `user_version` once `Schema.version` moves — only the runtime invariant is added.

**Tech Stack:** Kotlin Multiplatform, SQLDelight 2.0.2 (`JdbcSqliteDriver` desktop, `AndroidSqliteDriver` android), SQLite, JUnit/kotlin.test + Robolectric.

**Council:** Pre-impl council `1779306247` — verdict FLAWED, fixes baked in below: (a) decide create-vs-migrate from `sqlite_master`, not the marker; (b) each migration + version bump in one transaction; (c) runtime startup table-set invariant; (d) tests seed the broken real-world states + the Android driver path. Transcript: `.claude/council-cache/council-1779306247.md`.

**Out of scope (followup):** Build-time `verifyMigrations` activation needs committed `<version>.db` schema snapshots; reconstructing a clean v1 snapshot is git archaeology. `verifyMigrations` stays `true` but dormant (no `.db` files — exactly as today, build green). `1.sqm` is verified here by the runtime migration tests, which check real data integrity and are the stronger gate. Generating `.db` snapshots so future `.sqm` files get build-verified is a separate followup.

---

## File Structure

| File | Responsibility |
|---|---|
| `shared/src/commonMain/sqldelight/com/dietician/shared/data/sql/1.sqm` | **Create.** The v1→v2 migration: `CREATE TABLE audit_pending_outbox` + index. |
| `shared/src/commonMain/kotlin/com/dietician/shared/data/SchemaInvariant.kt` | **Create.** Canonical `EXPECTED_TABLES` set; `liveTables()`; `assertExpectedTables()` runtime guard. Shared by both platforms. |
| `shared/src/desktopMain/kotlin/com/dietician/shared/data/DesktopSchemaMigrator.kt` | **Create.** Desktop schema bring-up: `sqlite_master` probe → create / migrate / reconcile / fail-loud, each inside one transaction. |
| `shared/src/desktopMain/kotlin/com/dietician/shared/data/DataModule.desktop.kt` | **Modify.** Replace the `.schema_applied` marker block with a `DesktopSchemaMigrator` call. |
| `shared/src/androidMain/kotlin/com/dietician/shared/data/DataModule.android.kt` | **Modify.** Add the `SchemaInvariant` runtime check after driver construction. |
| `shared/src/desktopTest/kotlin/com/dietician/shared/data/SchemaInvariantTest.kt` | **Create.** Tests for the invariant. |
| `shared/src/desktopTest/kotlin/com/dietician/shared/data/DesktopSchemaMigratorTest.kt` | **Create.** The council test matrix: fresh, legacy-v1 (marker present + absent), already-v2 reconcile, partial fail-loud, re-run idempotency, ledger-data survival. |
| `shared/src/androidUnitTest/kotlin/com/dietician/shared/data/AndroidSchemaUpgradeTest.kt` | **Create.** Robolectric: `AndroidSqliteDriver` runs `1.sqm` on `onUpgrade(1→2)`. |

---

## Task 1: Add the retroactive `1.sqm` migration

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/dietician/shared/data/sql/1.sqm`

SQLDelight 2.x: an `N.sqm` file migrates schema version N→N+1 and increments `DieticianDatabase.Schema.version`. With zero `.sqm` files today `Schema.version` is `1`; adding `1.sqm` makes it `2`. The `.sqm` holds raw DDL only (no labelled queries, no `BEGIN/END` — SQLDelight wraps migrations in a transaction itself).

- [ ] **Step 1: Create `1.sqm`**

The DDL is copied verbatim from `0009_audit_pending_outbox.sq:9-19` (the table + index — NOT the labelled queries).

```sql
-- Migration v1 -> v2. Retroactively adds the audit_pending_outbox table
-- (iter-11 client outbox for the 2-phase-commit Coach pipeline). A client DB
-- created before 0009_audit_pending_outbox.sq existed never received this
-- table, because the old desktop `.schema_applied` marker skipped Schema.create
-- and Schema.version never moved off 1. Council 1779306247.

CREATE TABLE audit_pending_outbox (
    idempotency_key      TEXT NOT NULL PRIMARY KEY,
    reservation_id       TEXT,
    prompt_hash          TEXT NOT NULL,
    started_at_ms        INTEGER NOT NULL,
    last_attempt_at_ms   INTEGER NOT NULL,
    attempts             INTEGER NOT NULL DEFAULT 0,
    provider             TEXT NOT NULL
);

CREATE INDEX idx_audit_pending_outbox_started ON audit_pending_outbox (started_at_ms);
```

- [ ] **Step 2: Rebuild `:shared` so SQLDelight regenerates**

Run: `./gradlew :shared:generateCommonMainDieticianDatabaseInterface`
Expected: BUILD SUCCESSFUL. SQLDelight picks up `1.sqm` and the generated `DieticianDatabase.Schema.version` becomes `2`. (Task 2's first test asserts this — no manual grep needed.)

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/sqldelight/com/dietician/shared/data/sql/1.sqm
git commit -m "feat(shared:data): add 1.sqm v1->v2 migration for audit_pending_outbox"
```

---

## Task 2: `SchemaInvariant` — runtime table-set guard

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/shared/data/SchemaInvariant.kt`
- Test: `shared/src/desktopTest/kotlin/com/dietician/shared/data/SchemaInvariantTest.kt`

Council fix (c): a runtime guard so a future migration that runs but leaves a table missing crashes loudly instead of silently. `EXPECTED_TABLES` is the canonical v2 table set (29 tables, enumerated from the nine `.sq` files).

- [ ] **Step 1: Write the failing test**

`shared/src/desktopTest/kotlin/com/dietician/shared/data/SchemaInvariantTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.data.SchemaInvariantTest"`
Expected: FAIL — `SchemaInvariant` is unresolved.

- [ ] **Step 3: Write `SchemaInvariant`**

`shared/src/commonMain/kotlin/com/dietician/shared/data/SchemaInvariant.kt`:

```kotlin
package com.dietician.shared.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Runtime guard: after schema create/migrate the live SQLite table set MUST equal
 * the schema the code expects. A migration that runs but leaves a table missing is
 * otherwise silent; this turns the next such defect into a loud startup crash.
 * Council 1779306247 fix (c).
 *
 * When a `.sq` table is added you MUST add it here AND add the matching `N.sqm`
 * migration. `freshCreateProducesExactlyExpectedTables` fails loudly if this set
 * drifts from what `Schema.create` actually produces.
 */
object SchemaInvariant {
    /** Canonical v2 table set — every table across 0001..0009 `.sq` files. */
    val EXPECTED_TABLES: Set<String> =
        setOf(
            // 0001_event_ledger
            "pantry_events", "meal_events", "weight_events", "receipt_events",
            // 0002_outbox
            "outbox", "outbox_dead",
            // 0003_pantry_snapshot
            "pantry_snapshot", "pantry_snapshot_checkpoint",
            // 0004_metadata_lww
            "pantry_metadata",
            // 0005_cache_meta
            "sync_cursor_per_table", "sync_log",
            // 0006_caches_readonly
            "sku_canonical_cache", "price_posterior_cache", "food_composition_cache",
            "nutrient_dri_cache", "food_safety_temps_cache", "substitution_rules_cache",
            "cooking_methods_cache", "glycemic_index_cache", "recipes_cache",
            "stores_cache", "user_location_state_cache", "boredom_rolling_cache",
            "meal_plans_cache", "shopping_lists_cache", "loss_leader_alerts_cache",
            // 0007_local_location
            "user_location_current",
            // 0008_hlc_state
            "hlc_state",
            // 0009_audit_pending_outbox
            "audit_pending_outbox",
        )

    /** All non-internal user tables currently present in the SQLite file. */
    fun liveTables(driver: SqlDriver): Set<String> {
        val names = mutableSetOf<String>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
            mapper = { cursor ->
                while (cursor.next().value) {
                    cursor.getString(0)?.let { names.add(it) }
                }
                QueryResult.Unit
            },
            parameters = 0,
        )
        return names
    }

    /** Throws IllegalStateException if the live table set is not exactly EXPECTED_TABLES. */
    fun assertExpectedTables(driver: SqlDriver) {
        val live = liveTables(driver)
        check(live == EXPECTED_TABLES) {
            "Client SQLite schema invariant violated. " +
                "Missing: ${(EXPECTED_TABLES - live).sorted()}. " +
                "Unexpected: ${(live - EXPECTED_TABLES).sorted()}."
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.data.SchemaInvariantTest"`
Expected: PASS (5 tests). If `schemaVersionIsTwoAfterAddingFirstMigration` fails, Task 1's `1.sqm` was not picked up — re-run `./gradlew :shared:generateCommonMainDieticianDatabaseInterface` and recheck the filename is exactly `1.sqm`.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dietician/shared/data/SchemaInvariant.kt shared/src/desktopTest/kotlin/com/dietician/shared/data/SchemaInvariantTest.kt
git commit -m "feat(shared:data): add SchemaInvariant runtime table-set guard"
```

---

## Task 3: `DesktopSchemaMigrator` — fresh-create path

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/dietician/shared/data/DesktopSchemaMigrator.kt`
- Test: `shared/src/desktopTest/kotlin/com/dietician/shared/data/DesktopSchemaMigratorTest.kt`

Council fix (a)+(b): decide from `sqlite_master`; run create inside one transaction so a crash leaves an empty DB, never a partial one.

- [ ] **Step 1: Write the failing test**

`shared/src/desktopTest/kotlin/com/dietician/shared/data/DesktopSchemaMigratorTest.kt`:

```kotlin
package com.dietician.shared.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.sql.DieticianDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSchemaMigratorTest {

    private fun newDriver(): JdbcSqliteDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private fun tempDir(): File = Files.createTempDirectory("dm-test").toFile()

    private fun userVersion(driver: SqlDriver): Long {
        var v = 0L
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { c -> if (c.next().value) v = c.getLong(0) ?: 0L; QueryResult.Unit },
            parameters = 0,
        )
        return v
    }

    @Test
    fun freshDatabaseGetsFullSchemaAtCurrentVersion() {
        val driver = newDriver()
        val db = DieticianDatabase(driver)

        DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())

        assertEquals(SchemaInvariant.EXPECTED_TABLES, SchemaInvariant.liveTables(driver))
        assertEquals(DieticianDatabase.Schema.version, userVersion(driver))
    }

    @Test
    fun freshDatabaseRunDeletesNoLongerNeededMarkerIfSomehowPresent() {
        val driver = newDriver()
        val db = DieticianDatabase(driver)
        val dir = tempDir()
        File(dir, ".schema_applied").writeText("v1")

        DesktopSchemaMigrator.ensureSchema(db, driver, dir)

        assertFalse(File(dir, ".schema_applied").exists())
    }

    @Test
    fun reRunOnAFreshlyMigratedDatabaseIsIdempotent() {
        val driver = newDriver()
        val db = DieticianDatabase(driver)
        val dir = tempDir()

        DesktopSchemaMigrator.ensureSchema(db, driver, dir)
        DesktopSchemaMigrator.ensureSchema(db, driver, dir) // second run must not throw

        assertEquals(SchemaInvariant.EXPECTED_TABLES, SchemaInvariant.liveTables(driver))
        assertTrue(true)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.data.DesktopSchemaMigratorTest"`
Expected: FAIL — `DesktopSchemaMigrator` is unresolved.

- [ ] **Step 3: Write `DesktopSchemaMigrator`**

`shared/src/desktopMain/kotlin/com/dietician/shared/data/DesktopSchemaMigrator.kt`:

```kotlin
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
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.data.DesktopSchemaMigratorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/dietician/shared/data/DesktopSchemaMigrator.kt shared/src/desktopTest/kotlin/com/dietician/shared/data/DesktopSchemaMigratorTest.kt
git commit -m "feat(shared:data): add DesktopSchemaMigrator fresh-create path"
```

---

## Task 4: `DesktopSchemaMigrator` — legacy v1→v2 migrate path

**Files:**
- Modify: `shared/src/desktopTest/kotlin/com/dietician/shared/data/DesktopSchemaMigratorTest.kt`

The migrate branch already exists in `DesktopSchemaMigrator` (Task 3). This task proves it against a real legacy-v1 DB — including the council's marker-absent case and ledger-data survival. A "v1 DB" is built by creating the v2 schema then dropping the 0009 table — the exact shape of a pre-0009 client DB.

- [ ] **Step 1: Add the v1-DB helper + migrate tests**

Append to `DesktopSchemaMigratorTest.kt` (inside the class):

```kotlin
    /** Builds a database in the exact v1 shape: 28 tables, no audit_pending_outbox, user_version 0. */
    private fun createV1Database(driver: SqlDriver) {
        DieticianDatabase.Schema.create(driver) // v2 schema
        driver.execute(null, "DROP INDEX idx_audit_pending_outbox_started", 0)
        driver.execute(null, "DROP TABLE audit_pending_outbox", 0)
        driver.execute(null, "PRAGMA user_version = 0", 0)
    }

    private fun tableExists(driver: SqlDriver, name: String): Boolean =
        SchemaInvariant.liveTables(driver).contains(name)

    @Test
    fun legacyV1DatabaseWithMarkerIsMigratedToV2() {
        val driver = newDriver()
        val db = DieticianDatabase(driver)
        val dir = tempDir()
        createV1Database(driver)
        File(dir, ".schema_applied").writeText("v1")
        assertFalse(tableExists(driver, "audit_pending_outbox"))

        DesktopSchemaMigrator.ensureSchema(db, driver, dir)

        assertEquals(SchemaInvariant.EXPECTED_TABLES, SchemaInvariant.liveTables(driver))
        assertEquals(DieticianDatabase.Schema.version, userVersion(driver))
        assertFalse(File(dir, ".schema_applied").exists())
    }

    @Test
    fun legacyV1DatabaseWithNoMarkerIsStillMigrated() {
        // Council case: the old code wrote the marker non-atomically after Schema.create,
        // so a crash leaves a populated v1 DB with no marker. Decision must come from
        // sqlite_master, so this still migrates.
        val driver = newDriver()
        val db = DieticianDatabase(driver)
        createV1Database(driver)

        DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())

        assertTrue(tableExists(driver, "audit_pending_outbox"))
        assertEquals(DieticianDatabase.Schema.version, userVersion(driver))
    }

    @Test
    fun migrationPreservesUnsyncedLedgerRows() {
        val driver = newDriver()
        val db = DieticianDatabase(driver)
        createV1Database(driver)
        driver.execute(
            null,
            "INSERT INTO pantry_events(event_uuid, device_id, originated_at, synced_at, " +
                "sku_uuid, delta_qty, unit, reason, evidence_ref) " +
                "VALUES ('evt-1', 'dev-1', 100, NULL, 'sku-1', 2.0, 'pcs', NULL, NULL)",
            0,
        )

        DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())

        var surviving = 0L
        driver.executeQuery(
            identifier = null,
            sql = "SELECT count(*) FROM pantry_events WHERE event_uuid = 'evt-1'",
            mapper = { c -> if (c.next().value) surviving = c.getLong(0) ?: 0L; QueryResult.Unit },
            parameters = 0,
        )
        assertEquals(1L, surviving)
    }

    @Test
    fun audit_pending_outboxIsQueryableAfterMigration() {
        val driver = newDriver()
        val db = DieticianDatabase(driver)
        createV1Database(driver)

        DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())

        db.auditPendingOutboxQueries.insertOutboxRow(
            idempotency_key = "key-1", reservation_id = "res-1", prompt_hash = "hash-1",
            started_at_ms = 1L, last_attempt_at_ms = 1L, attempts = 0L, provider = "claudemax",
        )
        assertEquals(1, db.auditPendingOutboxQueries.findUncommitted().executeAsList().size)
    }
```

> Note for the implementer: confirm the generated query accessor name on `DieticianDatabase` (`auditPendingOutboxQueries`) and the `insertOutboxRow` parameter names by reading the generated `DieticianDatabaseImpl` or `0009_audit_pending_outbox.sq:21-24`; SQLDelight derives them from the labelled query. Adjust the call if the generated names differ.

- [ ] **Step 2: Run the new tests**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.data.DesktopSchemaMigratorTest"`
Expected: PASS (7 tests total — the migrate branch written in Task 3 Step 3 already handles these).

- [ ] **Step 3: Commit**

```bash
git add shared/src/desktopTest/kotlin/com/dietician/shared/data/DesktopSchemaMigratorTest.kt
git commit -m "test(shared:data): cover DesktopSchemaMigrator legacy v1->v2 migration"
```

---

## Task 5: `DesktopSchemaMigrator` — reconcile + fail-loud paths

**Files:**
- Modify: `shared/src/desktopTest/kotlin/com/dietician/shared/data/DesktopSchemaMigratorTest.kt`

Covers the already-v2-but-`user_version`-0 reconcile branch (a legacy DB created after 0009, or a create that crashed before its version bump) and the partial/unrecognized fail-loud branch.

- [ ] **Step 1: Add the reconcile + fail-loud tests**

Append to `DesktopSchemaMigratorTest.kt` (inside the class):

```kotlin
    @Test
    fun fullV2SchemaAtUserVersionZeroIsReconciledNotRecreated() {
        // A DB created post-0009 by old code, or a fresh create that crashed before the
        // version bump: all 29 tables present, user_version still 0. Must NOT re-run
        // Schema.create (would crash "table already exists") — just bump the version.
        val driver = newDriver()
        val db = DieticianDatabase(driver)
        DieticianDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA user_version = 0", 0)

        DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())

        assertEquals(SchemaInvariant.EXPECTED_TABLES, SchemaInvariant.liveTables(driver))
        assertEquals(DieticianDatabase.Schema.version, userVersion(driver))
    }

    @Test
    fun partialSchemaFailsLoud() {
        // A crash mid-create in the OLD (non-transactional) code path could leave some
        // tables and not others. The migrator must refuse to guess.
        val driver = newDriver()
        val db = DieticianDatabase(driver)
        DieticianDatabase.Schema.create(driver)
        driver.execute(null, "DROP TABLE meal_events", 0) // 28 tables, but not the v1 set

        val ex = assertFailsWith<IllegalStateException> {
            DesktopSchemaMigrator.ensureSchema(db, driver, tempDir())
        }
        assertTrue(ex.message!!.contains("Unrecognized desktop client schema"))
    }
```

- [ ] **Step 2: Run the new tests**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.data.DesktopSchemaMigratorTest"`
Expected: PASS (9 tests total).

- [ ] **Step 3: Commit**

```bash
git add shared/src/desktopTest/kotlin/com/dietician/shared/data/DesktopSchemaMigratorTest.kt
git commit -m "test(shared:data): cover DesktopSchemaMigrator reconcile + fail-loud paths"
```

---

## Task 6: Wire `DesktopSchemaMigrator` into `DataModule.desktop.kt`

**Files:**
- Modify: `shared/src/desktopMain/kotlin/com/dietician/shared/data/DataModule.desktop.kt`

Replace the `.schema_applied` marker block with the migrator call. The marker is gone for good.

- [ ] **Step 1: Replace the marker block**

`DataModule.desktop.kt` — full new contents:

```kotlin
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
```

Changes vs. the old file: construct `DieticianDatabase` before schema bring-up (it only wraps the driver — no I/O); the `if (!File(dir, ".schema_applied").exists()) { … }` block is deleted; `DesktopSchemaMigrator.ensureSchema(...)` replaces it.

- [ ] **Step 2: Build the desktop app to confirm it compiles**

Run: `./gradlew :desktopApp:assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full shared desktop test suite**

Run: `./gradlew :shared:desktopTest`
Expected: PASS — including the existing `AuditPendingOutboxQueriesTest`, `OutboxStoreTest`, etc., plus the new `SchemaInvariantTest` and `DesktopSchemaMigratorTest`.

- [ ] **Step 4: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/dietician/shared/data/DataModule.desktop.kt
git commit -m "fix(shared:data): replace .schema_applied marker with DesktopSchemaMigrator"
```

---

## Task 7: Android — runtime invariant + `1.sqm` upgrade test

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/dietician/shared/data/DataModule.android.kt`
- Create: `shared/src/androidUnitTest/kotlin/com/dietician/shared/data/AndroidSchemaUpgradeTest.kt`

Android needs no migrator — `AndroidSqliteDriver(schema = DieticianDatabase.Schema, …)` runs `Schema.migrate` via `onUpgrade` once `Schema.version` is 2 (Task 1). Add only the runtime invariant, and prove `1.sqm` runs on the Android driver (council fix (d)).

- [ ] **Step 1: Write the failing Android upgrade test**

`shared/src/androidUnitTest/kotlin/com/dietician/shared/data/AndroidSchemaUpgradeTest.kt`:

```kotlin
package com.dietician.shared.data

import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dietician.shared.data.sql.DieticianDatabase
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidSchemaUpgradeTest {

    /** A stand-in version-1 schema that creates nothing — only fixes the file at user_version 1. */
    private val v1Schema =
        object : SqlSchema<QueryResult.Value<Unit>> {
            override val version: Long = 1L
            override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Unit
            override fun migrate(
                driver: SqlDriver,
                oldVersion: Long,
                newVersion: Long,
                vararg callbacks: app.cash.sqldelight.db.AfterVersion,
            ): QueryResult.Value<Unit> = QueryResult.Unit
        }

    private fun tableExists(driver: SqlDriver, name: String): Boolean {
        var found = false
        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            mapper = { c -> found = c.next().value; QueryResult.Unit },
            parameters = 1,
            binders = { bindString(0, name) },
        )
        return found
    }

    @Test
    fun upgradingFromV1RunsTheMigrationAndAddsAuditPendingOutbox() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Open at version 1, then close — the SQLite file is now stamped user_version 1.
        AndroidSqliteDriver(schema = v1Schema, context = ctx, name = "upgrade-test.db").close()

        // Reopen with the real schema (version 2) — AndroidSqliteDriver fires onUpgrade(1, 2),
        // which runs Schema.migrate -> 1.sqm.
        val driver = AndroidSqliteDriver(
            schema = DieticianDatabase.Schema,
            context = ctx,
            name = "upgrade-test.db",
        )
        // Force the DB open so onUpgrade runs.
        tableExists(driver, "audit_pending_outbox")

        assertTrue(tableExists(driver, "audit_pending_outbox"))
        driver.close()
    }
}
```

> Note for the implementer: confirm `binders` is the correct trailing-lambda parameter name of `SqlDriver.executeQuery` in SQLDelight 2.0.2 (read `WalPragmas.kt:53-62` for the no-parameter form already used in this repo). If the v1 stand-in DB does not stamp `user_version = 1`, give `v1Schema.create` a single real `CREATE TABLE` so AndroidSqliteDriver's open-helper records version 1.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dietician.shared.data.AndroidSchemaUpgradeTest"`
Expected: FAIL (before Task 1 it would be a no-op upgrade; with `1.sqm` present it should pass — if it fails here, the failure pinpoints an `AndroidSqliteDriver` wiring problem, which is exactly what this test exists to catch).

- [ ] **Step 3: Add the runtime invariant to `DataModule.android.kt`**

`DataModule.android.kt` — full new contents:

```kotlin
package com.dietician.shared.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dietician.shared.data.local.WalPragmas
import com.dietician.shared.data.sql.DieticianDatabase
import com.dietician.shared.data.sync.WalCheckpointHook

object DataModuleAndroid {
    fun build(ctx: Context): DieticianDatabase {
        bindAndroidContext(ctx)
        val driver =
            AndroidSqliteDriver(
                schema = DieticianDatabase.Schema,
                context = ctx,
                name = "dietician.db",
                callback =
                object : AndroidSqliteDriver.Callback(DieticianDatabase.Schema) {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        WalPragmas.INIT.forEach { db.execSQL(it) }
                    }
                },
            )
        // Force the DB open (runs Schema.create / onUpgrade), then assert the schema
        // converged. Council 1779306247 runtime invariant.
        SchemaInvariant.assertExpectedTables(driver)
        // Council BREAK #5 mandate: checkpoint on app-background.
        WalCheckpointHook().registerOnBackground {
            WalPragmas.forceTruncatingCheckpoint(driver)
        }
        return DieticianDatabase(driver)
    }
}
```

Change vs. the old file: one line — `SchemaInvariant.assertExpectedTables(driver)` after driver construction. The first `executeQuery` it issues forces `AndroidSqliteDriver` to open the DB, which runs create/upgrade.

- [ ] **Step 4: Run the Android unit tests**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dietician.shared.data.AndroidSchemaUpgradeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/androidMain/kotlin/com/dietician/shared/data/DataModule.android.kt shared/src/androidUnitTest/kotlin/com/dietician/shared/data/AndroidSchemaUpgradeTest.kt
git commit -m "feat(shared:data): assert schema invariant on Android + test 1.sqm upgrade"
```

---

## Task 8: Full preflight + post-impl council

**Files:** none (verification only)

- [ ] **Step 1: Run the full preflight**

Run: `./gradlew :shared:test :androidApp:assembleDebug :desktopApp:assemble :server:assemble ktlintCheck detekt`
Expected: BUILD SUCCESSFUL, all suites green. Fix anything red before proceeding — do not claim done on a red build.

- [ ] **Step 2: Live desktop smoke**

Run: `./gradlew :desktopApp:run`
With the current `%APPDATA%/Dietician/dietician.db` in place (already a full v2 DB), confirm the app boots without error and the schema-migrator log path is silent (no migration needed — reconcile-only). Then rename `dietician.db` aside, relaunch → fresh-create path → app boots. This exercises both the reconcile and create branches on the real desktop surface, per the visible-on-first-paint gate.

- [ ] **Step 3: Post-impl council**

Convene the 5-agent council on the completed implementation (project rule: council after impl). Feed it the diff of all eight tasks. Save the transcript to `.claude/council-cache/`.

- [ ] **Step 4: Update the backlog**

In `docs/backlog.md`, move the P0 item "Client SQLDelight migration runner" to the **Done** section with the PR number once the PR merges (the `/wrap` command does this).

---

## Self-Review

**1. Spec/council coverage.** Council `1779306247` required: (a) `sqlite_master`-driven decision — Task 3 `DesktopSchemaMigrator` (`when` keyed on `liveTables`); (b) one-transaction create/migrate + version bump — Task 3 (`database.transaction { … }` wrapping each branch); (c) runtime table-set invariant — Task 2 `SchemaInvariant` + wired in Tasks 6 & 7; (d) tests for broken states (legacy v1, marker-absent, partial, reconcile, re-run idempotency) — Tasks 4 & 5, + Android `1.sqm` path — Task 7. `.sqm` discipline + retroactive `1.sqm` + `Schema.version` bump — Task 1. All covered.

**2. Placeholder scan.** No "TBD"/"handle edge cases"/"similar to Task N". Two implementer notes flag SQLDelight-2.0.2 generated-API names to confirm against the repo (query-accessor name, `executeQuery` `binders` param) — these are verification instructions with the fallback spelled out, not placeholders.

**3. Type consistency.** `SchemaInvariant.EXPECTED_TABLES: Set<String>`, `liveTables(SqlDriver): Set<String>`, `assertExpectedTables(SqlDriver)` — used consistently in Tasks 2/3/6/7. `DesktopSchemaMigrator.ensureSchema(DieticianDatabase, SqlDriver, File)` — same signature in the test (Tasks 3/4/5) and the call site (Task 6). `DesktopSchemaMigrator.V1_TABLES` derived as `EXPECTED_TABLES - "audit_pending_outbox"` — single source of the table list (DRY).

**4–6. Build+mount / component-reuse / data-testid.** N/A — no frontend components, no `data-testid` selectors in scope.

---

## Execution Handoff

Plan complete. Two execution options:

1. **Subagent-Driven (recommended)** — a fresh subagent per task, two-stage review between tasks.
2. **Inline Execution** — execute tasks in this session with checkpoints.

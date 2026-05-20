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

    /**
     * All non-internal user tables currently present in the SQLite file — a
     * point-in-time snapshot. The [driver] must be open.
     */
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

    /**
     * Asserts the live SQLite table set is exactly [EXPECTED_TABLES].
     *
     * @param driver an open driver for the database to check.
     * @throws IllegalStateException if any expected table is missing or any unexpected table is present.
     */
    fun assertExpectedTables(driver: SqlDriver) {
        val live = liveTables(driver)
        check(live == EXPECTED_TABLES) {
            "Client SQLite schema invariant violated. " +
                "Missing: ${(EXPECTED_TABLES - live).sorted()}. " +
                "Unexpected: ${(live - EXPECTED_TABLES).sorted()}."
        }
    }
}

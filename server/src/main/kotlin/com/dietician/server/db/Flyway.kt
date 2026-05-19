package com.dietician.server.db

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

/**
 * Anchor object whose classloader backs the migration scan.
 *
 * Followup #16 (2026-05-19) root-caused why Flyway 10.20 emitted "No migrations
 * found" on the Ktor fat-JAR even with all 21 `db/migration/V*.sql` entries
 * present at the expected path: the Shadow plugin's default merge strategy
 * overwrites `META-INF/services/org.flywaydb.core.extensibility.Plugin` instead
 * of concatenating. `flyway-database-postgresql` ships a 3-line file;
 * `flyway-core` ships a 22-line file that includes `CoreResourceTypeProvider`
 * (registers the V/R/U prefix types used by `ResourceNameParser`). When the
 * 3-line file wins the merge, every `V001__*.sql` is rejected as
 * "Unrecognised migration name format" before any SQL runs.
 *
 * Fix: `mergeServiceFiles()` on the `shadowJar` task in `server/build.gradle.kts`
 * concatenates both files (25 entries) so `CoreResourceTypeProvider` is loaded.
 * With that in place, `classpath:db/migration` resolves correctly inside the
 * fat-JAR — no temp-extract workaround needed.
 */
private object FlywayClassloaderAnchor

private val log = LoggerFactory.getLogger("com.dietician.server.db.Flyway")

fun runMigrations(
    jdbcUrl: String,
    user: String,
    password: String,
): Int {
    log.info("Flyway: scanning classpath:db/migration")
    val flyway =
        Flyway.configure(FlywayClassloaderAnchor::class.java.classLoader)
            .dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration")
            // VPS schema for the initial deploy (2026-05-19) was manually
            // `psql -f`-applied while the META-INF/services merge bug blocked
            // Flyway from finding any migration. Once that's fixed, Flyway
            // would otherwise try to re-apply V001..V021 against a populated
            // schema and fail on "relation X already exists".
            //
            // baselineOnMigrate=true + baselineVersion=021 tells Flyway:
            //   - if `flyway_schema_history` is absent AND the schema has objects,
            //     write a single BASELINE row at 021 and treat V001..V021 as
            //     already-applied.
            //   - if the history table exists, the flag is a no-op.
            //
            // Fresh databases (CI Testcontainers) start with an empty schema,
            // so the flag is also a no-op there and all 21 migrations run
            // from scratch.
            .baselineOnMigrate(true)
            .baselineVersion("021")
            .baselineDescription("Pre-merge manual psql apply")
            // Fail fast if a future migration filename drifts away from the
            // V<version>__<description>.sql convention. The default INFO-level
            // warning hid the SPI merge bug for two days.
            .validateMigrationNaming(true)
            .load()
    return flyway.migrate().migrationsExecuted
}

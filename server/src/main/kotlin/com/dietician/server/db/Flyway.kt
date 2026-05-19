package com.dietician.server.db

import org.flywaydb.core.Flyway

/**
 * Anchor object whose classloader Flyway uses to scan the embedded
 * `db/migration/V*.sql` resources. Must live in the same JAR module as the
 * migration SQL files so the classloader Flyway gets is the one that can
 * actually see those resources.
 *
 * Council 1779188964 tracer-bullet first deploy on 2026-05-19 surfaced a
 * classpath-scanning failure: `Flyway.configure()` without an explicit
 * classloader uses the thread-context classloader, which is unreliable
 * inside Ktor fat-JARs assembled by the ktor-gradle-plugin — Flyway
 * silently emitted "No migrations found. Are your locations set up
 * correctly?" despite all 21 SQL files being present at `db/migration/`
 * inside the fat-JAR (verified via `unzip -l`). The fix is to pass this
 * object's classloader explicitly.
 *
 * NoMigrationsFoundRegressionTest asserts `runMigrations` returns >0 on
 * a fresh DB so future builds catch any reintroduction.
 */
private object FlywayClassloaderAnchor

fun runMigrations(
    jdbcUrl: String,
    user: String,
    password: String,
): Int {
    val flyway =
        Flyway.configure(FlywayClassloaderAnchor::class.java.classLoader)
            .dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .load()
    return flyway.migrate().migrationsExecuted
}

package com.dietician.server.db

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarInputStream

/**
 * Anchor object whose classloader / JAR-location backs the migration scan.
 * Council 1779188964 R3 surfaced that `Flyway.configure()` in any form —
 * with or without an explicit classloader — fails to scan `db/migration/`
 * inside Ktor fat-JARs built by the `ktor-gradle-plugin` on Flyway 10.20.x.
 * The fat-JAR layout, packaging metadata, or some interaction with Flyway
 * 10's switched-to-Java-8-resource-scanner leaves `getResources("db/migration")`
 * empty even though `unzip -l app.jar | grep db/migration` shows all SQL
 * files at the expected path.
 *
 * Workaround (verified working on the 2026-05-19 redeploy): extract every
 * `db/migration/V*.sql` entry from the running JAR to a temp directory
 * at startup, then point Flyway at the temp dir via `filesystem:`. Tests
 * (`runMigrations` against Testcontainers from `:server:test`) work via
 * the classpath path on a normal Gradle classpath; production via the
 * filesystem path on the fat-JAR. Both code paths are exercised by the
 * `NoMigrationsFoundRegressionTest`.
 */
private object FlywayClassloaderAnchor

private val log = LoggerFactory.getLogger("com.dietician.server.db.Flyway")

fun runMigrations(
    jdbcUrl: String,
    user: String,
    password: String,
): Int {
    val tempDir = extractMigrationsFromJar()
    val builder =
        Flyway.configure(FlywayClassloaderAnchor::class.java.classLoader)
            .dataSource(jdbcUrl, user, password)
            .baselineOnMigrate(false)
    // Flyway 10.20.x rejects MIXED locations when one resolves to zero
    // migrations (the classpath: scan returns empty on Ktor fat-JARs even
    // with the temp-extract workaround active). Use exactly one location
    // per invocation:
    //   - fat-JAR  → filesystem: only (the temp dir we just populated)
    //   - tests / `:server:run` → classpath: only (Gradle classpath layout)
    val configured =
        if (tempDir != null) {
            log.info("Flyway: scanning {} (extracted from JAR)", tempDir.absolutePath)
            builder.locations("filesystem:${tempDir.absolutePath}")
        } else {
            log.info("Flyway: not running from a JAR — using classpath:db/migration only")
            builder.locations("classpath:db/migration")
        }
    return configured.load().migrate().migrationsExecuted
}

/**
 * Returns the path to a fresh temp dir containing every `db/migration/V*.sql`
 * entry from the running JAR, or null when the application isn't running
 * from a JAR (e.g. `:server:run` with class output dir on classpath).
 */
private fun extractMigrationsFromJar(): File? {
    val codeSourceUrl = FlywayClassloaderAnchor::class.java.protectionDomain?.codeSource?.location ?: return null
    val sourcePath = URLDecoder.decode(codeSourceUrl.path, Charsets.UTF_8)
    val sourceFile = File(sourcePath)
    if (!sourceFile.isFile || !sourcePath.endsWith(".jar")) {
        // Classes are on the filesystem (Gradle classpath layout); classpath: works there.
        return null
    }
    val tempDir = Files.createTempDirectory("dietician-flyway").toFile()
    tempDir.deleteOnExit()
    var count = 0
    JarInputStream(sourceFile.inputStream()).use { jis ->
        while (true) {
            val entry = jis.nextJarEntry ?: break
            val name = entry.name
            if (!entry.isDirectory && name.startsWith("db/migration/") && name.endsWith(".sql")) {
                val target = File(tempDir, File(name).name)
                Files.copy(jis, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                target.deleteOnExit()
                count++
            }
        }
    }
    if (count == 0) {
        log.warn(
            "Flyway: no db/migration/V*.sql entries found inside {}; falling back to classpath: only",
            sourcePath,
        )
        return null
    }
    log.info("Flyway: extracted {} migration files from {} to {}", count, sourcePath, tempDir.absolutePath)
    return tempDir
}

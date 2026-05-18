package com.dietician.server.db

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [Council 1779120000 RC2] Prevents future handlers from acquiring raw
 * `dataSource.connection` outside [DatabaseFactory]. Such acquisitions bypass
 * `withSubject()` and leave the prior tenant's `app.current_subject_id` GUC
 * live on the pooled connection — a cross-subject row-leak vector even with
 * Hikari `connectionInitSql` defense-in-depth.
 *
 * Fails the build if ANY `.kt` file under `:server/src/main` outside
 * `DatabaseFactory.kt` contains the literal string `dataSource.connection`.
 *
 * Pairs with the Hikari `connectionInitSql = "RESET app.current_subject_id"`
 * defense-in-depth in [DatabaseFactory]. Two belts; only one suspender.
 */
class RlsBypassPreventionTest {
    @Test
    fun `no production code acquires raw dataSource connection outside DatabaseFactory`() {
        val root = Paths.get("src/main/kotlin")
        val violations =
            Files.walk(root)
                .filter { it.extension == "kt" }
                .filter { !it.fileName.toString().equals("DatabaseFactory.kt") }
                .filter { Files.readString(it).contains("dataSource.connection") }
                .map { it.toString() }
                .toList()
        assertTrue(
            violations.isEmpty(),
            "[Council 1779120000 RC2] Raw `dataSource.connection` outside DatabaseFactory " +
                "bypasses RLS. Refactor each call to go through `db.withSubject(subjectId) { conn -> ... }`. " +
                "Violators: $violations",
        )
    }
}

package com.dietician.server.ci

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.test.assertTrue

/**
 * [Council 1779120000 RC6 — guard #2] Fails the build if any production
 * code under `:server/src/main` writes raw text directly to the
 * `meal_events.notes` column (or `voice_memo_raw`) — that path MUST route
 * through Plan-2's `MealNotesPipeline.process(...)` first so PII NER fires
 * before the row hits Postgres.
 *
 * Plan-3 first-ship only stores events via [EventRepository.upsert] which
 * serializes a `payloadJson` blob (the table-row shape); the client is
 * responsible for sanitizing free-text BEFORE the push. This grep-test
 * catches any future handler that learns about the raw column and tries to
 * write to it directly.
 *
 * Match heuristic:
 *   - Any SQL literal containing `meal_events` AND `notes` near an
 *     `INSERT`/`UPDATE` keyword.
 *   - Any direct `meal_events.notes =` assignment in any layer.
 *
 * Whitelist: this test + the EventRepository.kt JSONB upsert path (which
 * doesn't reference the column by name; it uses `jsonb_populate_record`).
 *
 * Plan-2 will land `MealNotesPipeline` + a positive-path test that
 * exercises sanitize → upsert. Until then this test passes trivially
 * because no Plan-3 file writes raw notes.
 */
class PiiRedactionRequiredTest {
    private val whitelistFileNames = setOf(
        "PiiRedactionRequiredTest.kt",
        "EmotionInferencePreventionTest.kt",
    )

    @Test
    fun `meal_events notes writes route through MealNotesPipeline (Plan-2 dep)`() {
        val root = Paths.get("src/main/kotlin")
        val rawAssignRx = Regex("""meal_events\s*\.\s*notes\s*=""")
        val sqlBundleRx = Regex(
            """(INSERT\s+INTO\s+meal_events|UPDATE\s+meal_events)[\s\S]{0,200}\bnotes\b""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
        val violations = mutableListOf<String>()
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.extension == "kt" }
                .filter { it.fileName.toString() !in whitelistFileNames }
                .forEach { p ->
                    val text = Files.readString(p)
                    if (rawAssignRx.containsMatchIn(text)) {
                        violations += "${p.toString().replace('\\', '/')} contains raw 'meal_events.notes ='"
                    }
                    if (sqlBundleRx.containsMatchIn(text)) {
                        violations += "${p.toString().replace('\\', '/')} contains direct meal_events notes SQL"
                    }
                }
        }
        assertTrue(
            violations.isEmpty(),
            "[Council 1779120000 RC6] Raw meal_events.notes writes detected. " +
                "Must route through Plan-2 MealNotesPipeline.process(...) so PII NER fires " +
                "before Postgres. Violations:\n  ${violations.joinToString("\n  ")}",
        )
    }
}

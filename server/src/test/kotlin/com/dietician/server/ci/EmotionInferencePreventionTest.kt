package com.dietician.server.ci

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.test.assertTrue

/**
 * [Council 1779120000 RC6 — guard #1] Fails the build if any production
 * Kotlin file under `:server/src/main` references emotion/mood/sentiment
 * inference vocabulary.
 *
 * EU AI Act Art 5(1)(f) — emotion-inference-from-food-logging-gaps is a
 * prohibited AI practice. The Dietician system NEVER scores user emotion
 * from logging gaps; the closest we go is recording the EXPLICIT safeguard
 * rule that fired (kcal-floor, trigger-phrase, variety-drop) plus the
 * user's explicit response. This grep-test prevents accidental drift.
 *
 * Forbidden vocabulary list is intentionally narrow — the test itself
 * mentions these terms so they're whitelisted by FILE NAME.
 *
 * If a legitimate future use case requires one of these words (e.g. a
 * legally-required GDPR notice mentioning "no emotion data is stored"),
 * either:
 *   1. Add the file to the whitelist below with a comment explaining why.
 *   2. Re-word to avoid the term.
 */
class EmotionInferencePreventionTest {
    private val forbiddenPatterns = listOf(
        "emotion",
        "mood",
        "sentiment_inference",
        "sad_score",
        "happy_score",
        "compulsion_detected",
        "shame_signal",
        "mood_inferred",
    )

    private val whitelistFileNames = setOf(
        // The grep tests themselves must reference the vocabulary.
        "EmotionInferencePreventionTest.kt",
        "PiiRedactionRequiredTest.kt",
        // Audit-log doc references — both files NAME the AI Act Art 5(1)(f)
        // prohibition in KDoc + AuditLogActions explicitly lists which kinds
        // are absent ("no mood_inferred / compulsion_detected / shame_signal").
        // The presence of the words IS the compliance-statement contract.
        // If a future audit action is added that infers emotion, code-review
        // catches it BEFORE the file lands.
        "AuditLogActions.kt",
        "AuditLogWriter.kt",
        // Plan-3 Task 31 audit-export PDF renderer: KDoc explicitly states
        // the PDF has NO emotion-inferring column (Art 5(1)(f) compliance
        // statement, same pattern as AuditLogActions.kt above).
        "AuditPdfRenderer.kt",
    )

    @Test
    fun `no production code references emotion or mood inference fields`() {
        val root = Paths.get("src/main/kotlin")
        val violations = mutableListOf<String>()
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.extension == "kt" }
                .filter { p -> p.fileName.toString() !in whitelistFileNames }
                .forEach { p ->
                    val text = Files.readString(p)
                    forbiddenPatterns.forEach { needle ->
                        if (text.contains(needle, ignoreCase = true)) {
                            violations += "${relativeReport(p)} contains '$needle'"
                        }
                    }
                }
        }
        assertTrue(
            violations.isEmpty(),
            "[Council 1779120000 RC6] Emotion-inference patterns detected. " +
                "EU AI Act Art 5(1)(f) HARD BAN. Violations:\n  ${violations.joinToString("\n  ")}",
        )
    }

    private fun relativeReport(p: Path): String = p.toString().replace('\\', '/')
}

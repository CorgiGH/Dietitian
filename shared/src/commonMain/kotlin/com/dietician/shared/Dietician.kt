package com.dietician.shared

/**
 * Dietician — Kotlin Multiplatform shared module.
 *
 * Source-of-truth: docs/superpowers/specs/2026-05-17-dietician-design.md
 * Conventions:    AGENTS.md
 * Jarvis merge:   JARVIS_MERGE.md
 *
 * Modules to be populated (per Council 4 final layout):
 *   - data/        SQLDelight schema, repository, event-sourced ledger, sensor-fusion
 *   - domain/      Choco solver, planner, budget, prefs, boredom decay
 *   - knowledge/   wiki, BM25+embeddings, ingest pipelines (light parts)
 *   - llm/         LlmProvider sealed interface, router, circuit-breakers
 *   - network/     Ktor client, retry/backoff, sync protocol
 *   - ui/          Compose Multiplatform shared widgets + screens
 */
object Dietician {
    const val VERSION = "0.1.0-spec"
    const val SPEC_DATE = "2026-05-17"
}

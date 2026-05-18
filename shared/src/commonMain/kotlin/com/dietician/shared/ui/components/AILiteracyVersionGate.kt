package com.dietician.shared.ui.components

/**
 * RC18 (Council 1779120600) — AI Literacy text version bump policy.
 *
 * Policy lives in `docs/policies/AI_LITERACY_TEXT_VERSION.md` (source of truth). The
 * constant [CURRENT_VERSION] below MUST mirror that doc's `CURRENT_VERSION` line.
 * Bumping the doc REQUIRES bumping this constant (per the doc's procedure section)
 * and vice-versa.
 *
 * Bump only on **material substance changes** (new LLM provider, new capability
 * surface, new data sink, new legal-basis change). Typos / translation tweaks /
 * selector renames do NOT bump.
 *
 * On bump:
 * 1. `docs/policies/AI_LITERACY_TEXT_VERSION.md` updated (`CURRENT_VERSION` + history table).
 * 2. `Strings.ai_literacy_banner_*` updated if substance change.
 * 3. This constant updated.
 * 4. Re-acked: [AILiteracyStore] compares stored ack-version with [CURRENT_VERSION];
 *    mismatch → banner re-shown on next launch.
 *
 * Avoiding banner fatigue (per Round-2 FM-11): aim ≤2 bumps/year. Quarterly bumps
 * degrade AI Act Art 4 effectiveness.
 */
object AILiteracyVersionGate {

    /**
     * Mirrors `docs/policies/AI_LITERACY_TEXT_VERSION.md` `CURRENT_VERSION`.
     *
     * **2026-05-18 v1.0** — first-ship: Anthropic + Google + OpenRouter providers,
     * coach + weekly-narrative capabilities, ntfy + Tailscale sinks, GDPR Art 9 +
     * SCC+DPF cross-border mechanism.
     */
    const val CURRENT_VERSION: String = "1.0"

    /** Should the banner be displayed given a previously-acked version? */
    fun shouldShow(ackedVersion: String?): Boolean = ackedVersion != CURRENT_VERSION
}

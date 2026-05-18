# AI Literacy Text Version Policy

> Council 1779120600 RC18 (2026-05-18). Persistent policy doc for Plan-4-5 `AILiteracyBanner` (AI Act Art 4 surface).

## Current version

```
CURRENT_VERSION = 1.0
CURRENT_DATE    = 2026-05-18
```

## Bump policy

Bump `CURRENT_VERSION` (semver: MAJOR.MINOR) ONLY when material substance changes. NOT for typos, translation cleanup, or RO↔EN spelling fixes.

**Material substance changes (bump required):**
- New LLM provider added (e.g. Anthropic, Google, OpenRouter, Groq) — banner must enumerate which third-party processors receive user data.
- New capability surface (e.g. photo classification, voice transcription, weekly narrative LLM) — banner must list which user actions invoke LLM.
- New data sink (e.g. ntfy push notifications, Tailscale cross-border transfer mechanism change, new cloud storage backend) — banner must disclose new data flow.
- New legal-basis change (GDPR Art 9 consent scope, SCC + DPF mechanism change, retention-period change) — banner must reflect updated legal basis.

**NOT material (do NOT bump):**
- Typo fix in EN or RO copy.
- Translation cleanup (e.g. swapping "the" for "a" in EN, or improving RO grammar).
- Selector renames, layout tweaks, color/typography adjustments.
- Adding/removing supporting docs links (e.g. NEDA bigorexia page URL update).
- Bug-fix-only changes that don't alter the user's mental model.

## Bump procedure

1. Edit `CURRENT_VERSION` + `CURRENT_DATE` in this file.
2. Update `strings.en.xml` + `strings.ro.xml` `ai_literacy_banner_*` keys.
3. Commit with `docs(ai-literacy): bump to vX.Y — <reason>`.
4. On next user launch, `AILiteracyState` reads this file's `CURRENT_VERSION`, compares to Plan-1 `cache_metadata[ai_literacy_acked_version]`, and re-displays the banner on mismatch.

## Avoid banner fatigue

Per Risk Analyst Round-2 FM-11: if we bump quarterly, friends get banner-fatigue and dismiss without reading. AI Act Art 4 effectiveness degrades. **Bump only on material substance changes.** Aim for ≤2 bumps per year on a stable plan.

## Version history

| Version | Date       | Reason                                                    |
|---------|------------|-----------------------------------------------------------|
| 1.0     | 2026-05-18 | Initial banner — Plan-4-5 first-ship (Anthropic + Google + OpenRouter providers; coach + weekly-narrative capabilities; ntfy + Tailscale sinks; GDPR Art 9 consent + SCC+DPF cross-border mechanism). |

## Cross-references

- Plan-4-5 Task 8 (`AILiteracyBanner` modal-first-launch) — reads this file at app start.
- Plan-4-5 Task 20 (`SettingsAboutScreen`) — displays current version + "Re-acknowledge" button.
- Plan-1 `cache_metadata[ai_literacy_acked_version]` — per-subject ack record.
- AI Act Art 4 — first-launch transparency obligation.

## Council references

- Pre-impl council 1779120600 RC18 (2026-05-18) — `Risk Analyst FM-11` bump-cadence policy. See `.claude/council-cache/council-1779120600-plan-4-5-preimpl.md`.

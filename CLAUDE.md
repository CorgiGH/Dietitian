# CLAUDE.md — Dietician

Project-level Claude Code instructions for the Dietician repository at `C:\Users\User\Desktop\Dietician`. This file is read at session start. Keep concise; defer to `AGENTS.md` for agent conventions and to `docs/superpowers/specs/2026-05-17-dietician-design.md` for the binding product spec.

## Project orientation

Personal AI dietician for one user (Victor — UAIC year-1 AI student in Iași, lean-bulking 188 cm / 67.5 kg → 2750 kcal / 137 g protein). Kotlin Multiplatform Compose (Android phone + Windows desktop). VPS-canonical Postgres on `46.247.109.91` via Tailscale (`100.101.47.77:8081`). Mergeable into `jarvis-kotlin` as a Subsystem later.

Spec is source of truth: `docs/superpowers/specs/2026-05-17-dietician-design.md`. Read it before any non-trivial change. Agent conventions: `AGENTS.md`. Jarvis merge contract: `JARVIS_MERGE.md`. Runbooks: `docs/runbooks/`.

## Required reading order at session start

1. `~/.claude/projects/C--Users-User-Desktop-Dietician/memory/BRIDGE.md` — canonical session handoff, latest entry has live state, locked decisions, open items, hot work.
2. `docs/backlog.md` — **canonical production backlog**: the P0-P3 remaining-work + objectives list. Sole authority for what is left and in what priority. Read it to know what to work on next.
3. `~/.claude/projects/C--Users-User-Desktop-Dietician/memory/MEMORY.md` — memory index.
4. **This file** — project rules.
5. `AGENTS.md` — agent conventions (wiki maintenance, LLM routing, anti-patterns).
6. **For any frontend/UI/design work: `docs/design/index.md` — design wiki entry point.** See §"Design wiki" below.
7. Spec + plans + runbooks as the task demands.

**Doc ownership (no duplication):** `docs/backlog.md` owns *what is left + priority* (living, mutable). `BRIDGE.md` owns *what happened, per session* (append-only chronological handoff) — it points to the backlog, it does not restate the P0 list. The spec owns *what the product must do*. Update the backlog at `/wrap` (the `/wrap` command does this) — never let BRIDGE and the backlog disagree on priority.

## Design wiki — `docs/design/`

The product has an LLM-maintained design knowledge base at `docs/design/`. Six pages: `index.md`, `overview.md`, `visual-language.md`, `ux-patterns.md`, `components.md`, `references.md`. Built on the Karpathy LLM-Wiki pattern (three layers: raw → wiki → schema) and the AGENTS.md frontmatter schema.

### When to read it

**Always, before any of these:**

- Adding or modifying a screen (`shared/.../ui/screens/*`)
- Adding or modifying a reusable Composable (`shared/.../ui/components/*`)
- Touching the theme (`DieticianTheme.kt`, `DieticianColors.kt`, `DieticianTypography.kt`, `DieticianShapes.kt`)
- Adding navigation routes (`DieticianNav.kt`, `Routes.kt`, `PushedScreenScaffold.kt`)
- Changing user-facing copy (`Strings.kt`)
- Designing an empty-state, error-state, or loading-state surface
- Adding a new dashboard tile, chart, or data-display surface
- Wiring an AI Act / GDPR transparency surface (consent rows, audit log, per-call disclosure, AI literacy banner)
- Designing an input flow (voice, photo, barcode, manual, same-as-recent)
- Adding a refusal/safeguard surface (ED, bigorexia, planned-cut toggle)

**Read at minimum**: `docs/design/index.md` (index + log + schema rules) and the page closest to your task.

### When to update it

**Update the wiki in the same commit as the source change for any of:**

| Change | Page(s) to update |
|--------|-------------------|
| New screen | `ux-patterns.md` §Navigation map + §Screen archetypes + `components.md` new entries |
| New reusable Composable | `components.md` new entry with anatomy + do/don't + file:line anchor |
| Token change (color/type/spacing/shape) | `visual-language.md` token table inline value |
| New design source consumed (article, paper, case study) | `references.md` bibliography + cite from the page that used it |
| Spec §1 §11 §28 §30 changed | Re-read those sections, refresh `overview.md` + `ux-patterns.md` |
| Nav graph changed | `ux-patterns.md` §Navigation map + reconcile IA-drift table |
| User-facing copy changed (esp. EN + RO) | `ux-patterns.md` §Bilingual + cite `Strings.kt:21` anchor |
| Design rule added/changed | `overview.md` §North stars or §Non-goals — never silently |

**Always**: append a `## [YYYY-MM-DD] kind | summary` entry to the `## Log` section at the bottom of `docs/design/index.md`.

### Schema rules (mirror of `docs/design/index.md` §Schema)

- Every page carries YAML frontmatter per the schema in `AGENTS.md:36-60`. `domain:` is always `design` for these pages. `authority:` usually `derived` or `practitioner`.
- Cross-link with `[[slug]]` (Obsidian-style). Unresolved `[[name]]` links mark intent; treat as TODO, not error.
- Design tokens may be inlined as numbers (they are stable code constants, not live user data). Live user numbers stay in SQL per `AGENTS.md` storage decision rule.
- File anchors use `file:line` so a reader can jump to source. When you rename or move, update the anchors.
- Source dates: `accessed: YYYY-MM-DD` on every external URL in `references.md`. Re-fetch when >365 days old.

### Maintenance triggers (auto-fire)

When you touch any of these, the corresponding design wiki update is part of the same diff. CI cannot enforce this yet — discipline at commit time:

- Edit any file under `shared/src/commonMain/kotlin/com/dietician/shared/ui/` → check `docs/design/components.md` + `ux-patterns.md` for stale anchors.
- Edit `shared/.../ui/theme/Dietician{Colors,Typography,Shapes}.kt` → update `docs/design/visual-language.md` inline tokens in the same commit.
- Add a new bottom-nav tab or pushed screen → update `docs/design/ux-patterns.md` §Navigation map + the IA-drift table.
- Add a new external design reference → append to `docs/design/references.md`.

### Lint cadence

Every quarter (≥90 days since last `## [` log entry of kind `lint`):

1. Grep every `file:line` anchor in `components.md`; if a file moved or line shifted >20, refresh.
2. Confirm every page has ≥1 inbound `[[link]]` (no orphans).
3. Diff `DieticianColors.kt` / `DieticianTypography.kt` / `DieticianShapes.kt` against `visual-language.md` token tables.
4. Confirm no page contradicts `design-overview.md` §North stars or §Non-goals. Contradiction means either the page is wrong or the overview needs amending — never let drift sit.
5. For each external URL in `references.md`, if `accessed:` >365d, re-fetch and update.
6. Append a `## [YYYY-MM-DD] lint | summary` entry to the log in `index.md`.

## Storage decision rule (load-bearing)

(From `AGENTS.md`, repeated here because it interacts with the design wiki.)

- Postgres on VPS + SQLite per client — structured / numeric / transactional state (prices, pantry events, weight events, macros, budgets, baselines).
- Markdown wiki under `wiki/` — narrative reasoning, learned heuristics, rationale, methodology pages, summaries, "applied to you" sections. Two-file pattern: `slug.md` (narrative, no numbers) + `slug.data.md` (autogen, header `<!-- AUTOGENERATED. EDITS OVERWRITTEN. -->`).
- `raw/` — immutable inputs too large or unstructured for parsed ingestion. Indexed.
- **`docs/design/` design wiki** — narrative + design tokens (inline number constants allowed since tokens are code, not live data) + cross-links. Same frontmatter schema, no two-file split unless a token table ever needs autogen.

NEVER put live user numbers in markdown bodies. Design tokens are exempt because they are stable code constants and the wiki is the spec of record for them.

## Spec-first grep gate

Before drafting any clarifying question, grep existing artefacts (spec, plans, runbooks, design wiki, memory). Log `Spec-check: <q>` → `spec'd at <path>:<line>` or `no match; asking user`. Only ask genuine gaps. Cost of one bad question = trust + tokens. Cost of one grep = ~200 tokens. Always grep first.

For design decisions specifically: grep `docs/design/` BEFORE proposing a new pattern. If the pattern already lives in `ux-patterns.md` or `components.md`, follow it. If it contradicts what's there, surface the conflict in chat before writing code.

## Visible-on-first-paint gate (from user-level CLAUDE.md, restated)

Per spec §30: when a screen is shipped, every spec'd `[data-testid]` selector must paint on first render AND clicking each interactive selector must not produce 4xx/5xx network responses, "not found" / "404" / "error" text, or layout-blocking errors. Bundle + tests green ≠ feature shipped. Open the user-facing surface (live URL or live `:desktopApp:run` walk) and confirm visible before claiming shipped. The 2026-05-11 Slice 1 ghost-component lesson and the Slice 1.5 PDF-404 lesson both apply here.

For design pages specifically: when a `components.md` entry claims a Composable does X, and X is a user-visible behaviour, the entry is wrong until you've watched the Composable do X in a live build. Reading the source is not enough; component-shipped + tests-green is not enough.

## Build + test commands

```
./gradlew :shared:test
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:run
./gradlew :server:run
./gradlew ktlintCheck detekt
./gradlew :shared:test :androidApp:assembleDebug :desktopApp:assemble :server:assemble  # full preflight
```

Pre-commit hook (`.git/hooks/pre-commit`) runs `./gradlew ktlintFormat detekt :shared:test`. Must pass before commit lands.

Custom Detekt rule: `UnusedUnderscoreDestructuring` flags `_propName` destructure when constructor param still exists in source. Underscore-dead-prop is a workflow smell, per user-level CLAUDE.md.

## User feedback rules (from memory, always-on)

- **No time/duration estimates.** Never produce "this will take X minutes/hours/days." User reaction is violent.
- **No version phasing.** No `v0` / `v1` / `MVP` / `defer-to-v2`. Full scope one pass.
- **No relaunch confirm during user testing.** If user is mid-test and a desktop/server restart is needed, restart without asking.
- **Don't touch MC server config/heap/launch on VPS** without per-instance confirmation.
- **Council pattern**: convene 5-agent council BEFORE plan + AFTER impl for every Phase 2+ slice.
- **English default, Romanian only for source/SKU/dish terms** in UI copy.
- **Caveman tone (default full)** for chat responses — terse, fragments OK, no filler. Code / commits / docs / security warnings: normal prose.

## Slash commands available

- `/wrap` — `.claude/commands/wrap.md` (append-only session handoff entry to BRIDGE.md)
- `/sanity` — `.claude/commands/sanity.md` (deep memory audit, dispatches sub-agent ~5-10k tokens)

## Don't relitigate (locked decisions)

- Platform: Kotlin Multiplatform Compose Android + Windows Desktop. Not React Native, not Flutter, not native.
- VPS canonical: Postgres 16 on `46.247.109.91` via Tailscale `100.101.47.77:5432`.
- Write topology: LOCAL-FIRST + event-sourced ledger. Council 4 BREAK fix #1. Do NOT revert to VPS-canonical-for-writes.
- LLM router: shared Kotlin `:shared:llm` (NO LiteLLM). ClaudeMax CLI subprocess outside router (desktop-only).
- Vision OCR: ClaudeMax CLI (`claude --bare -p`) desktop-primary + OpenRouter Gemini 2.0 Flash Vision fallback.
- Push: self-hosted ntfy on VPS.
- Constraint solver: Choco-solver MIT JVM.
- Two-file wiki pattern: `slug.md` + `slug.data.md` with Obsidian transclusion.
- Telegram bot DROPPED — in-app Compose UI + ntfy push only.
- Navigation library: Voyager 1.x + Compose Multiplatform 1.7.0 — do NOT migrate to a NavHost-based approach without re-running the council.
- **No red/green pass-fail colour for any nutrition or weight metric.** `DieticianErrorRed = #B3261E` is for app errors only. Council 1779120600 R3 ruling. Baked into `DieticianColors.kt:4-9`.
- **No social feed, streaks, XP, badges, leaderboards, gamification of any kind.**
- **No auto-log from photo without confirmation.** Suggestions always.
- **No emotion inference from logging gaps.** EU AI Act Art. 5(1)(f).

## Hallucination triggers (warn off)

- jarvis-kotlin BRIDGE identity is "Alex"; Dietician identity is "Victor." Different project memories. Do not cross-contaminate.
- Repo name on GitHub is `Dietitian` (UK/medical spelling); local dir + Kotlin package use `Dietician`. Both refer to same project. See `~/.claude/projects/C--Users-User/memory/reference_dietician_github.md`.
- GROBID lives on DESKTOP, not VPS, since 2026-05-17 relocation.
- VPS Tailscale IP `100.101.47.77` (hostname `panel`). Desktop `100.80.132.115` (hostname `dell-g5`).
- `ClaudeMaxLlm.kt` is a CLI subprocess wrapper, NOT a web-session reverse-engineer. Mirror that pattern, never propose web-session approaches.
- Romanian diacritics: comma-below `ș ț` (U+0219 / U+021B), NEVER cedilla `şţ` (U+015F / U+0163).

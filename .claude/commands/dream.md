---
description: CURATE — out-of-band memory curation. Reads recent session transcripts, dedups/de-stales/verifies/learns, compacts BRIDGE-LOG, reconciles backlog. Run in a fresh session, periodically. Supersedes /sanity.
---

`/dream` is the out-of-band CURATE half of the handoff system. Objective: memory quality, decoupled from any task. Run it in a FRESH Claude Code session (its own context), hand-invoked — every few sessions, or when `/wrap` nags. It absorbs the old `/sanity` audit. It commits ONLY to the memory repo. `<MEM>` = `C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory`.

`/dream --dry-run` performs Phases 1–5 as ANALYSIS ONLY — it determines the curation diff and PRINTS it, writing no files and committing nothing (the old `/sanity` audit-only use). The default (no flag) applies and commits. Files are written ONLY in Phase 6 of a normal run.

The curation watermark is git-derived: the commit timestamp of the latest `dream:` commit in the memory repo. There is no watermark file-cell. A `dream:` commit IS the watermark advance — so a run that never reaches its Phase 6 commit leaves the watermark unmoved by construction.

## Pre-step — clean working tree

Run `git -C "<MEM>" status --porcelain`. If dirty, commit the existing state first: `git -C "<MEM>" add -A && git -C "<MEM>" commit -m "manual: checkpoint before /dream"`. This preserves user hand-edits or a crashed prior run's partial work. (A `manual:` commit is not a `dream:` commit, so it does not move the watermark.)

## Phase 1 — Window

Compute the watermark: `git -C "<MEM>" log --format='%cI %s' | grep -m1 -E '^[^ ]+ dream: ' | cut -d' ' -f1 || true` (empty → `/dream` has never run; take all transcripts). List `.jsonl` session transcripts directly under `~/.claude/projects/C--Users-User-Desktop-Dietician/` (the top-level per-session files) with modification time newer than that watermark. DEFENSIVE: if that directory is missing, or the files do not parse as JSONL, STOP and report — do not proceed.

## Phase 2 — Ingest

For each new transcript, dispatch one sub-agent (Agent tool, `subagent_type: general-purpose`) to digest it: events, repeated mistakes, claims that should be verified, candidate cross-session patterns. A sub-agent whose transcript is too large chunks it itself. Collect the digests — your own context sees digests, not raw transcripts.

## Phase 3 — Determine the curation diff

From the digests + BRIDGE-HEAD + BRIDGE-LOG + the `memory/` files + `git log` + backlog.md, determine the four curation operations — across BRIDGE-LOG.md, the typed memory files, and MEMORY.md. (Determine only; do not write files yet.)

- **Dedup** — redundant memory entries to consolidate into one.
- **De-stale** — claims that have drifted get a `[STALE: <reason>]` flag. NEVER a hard delete. A flag against a BRIDGE-HEAD fact is APPENDED to BRIDGE-HEAD's Flags section.
- **Verify** — claims confirmed accurate against the transcripts / current state get a `[VERIFIED: <date>]` note. Also check BRIDGE-HEAD.md against BRIDGE-LOG.md and the transcripts; note any drift as a `[STALE]` flag.
- **Learn** — cross-session patterns, repeated mistakes, strategies that worked → a new `feedback_*` or `project_*` memory entry (per the memory-type conventions in the global CLAUDE.md).

## Phase 4 — Determine BRIDGE-LOG compaction

Determine which old BRIDGE-LOG entries to shorten. NEVER compact: the newest 5 entries, or any entry that an unresolved BRIDGE-HEAD Flag or Open Item still references. Each entry to compact will get a `compacted: <date>` line (skip entries already marked).

## Phase 5 — Determine backlog reconciliation

Determine how to sort the `## TRIAGE` items of `<MEM>/backlog.md` into their P0–P3 buckets, plus any dedup and any stale `## Done` entries to prune.

## Phase 6 — Apply, commit, push, report

If `--dry-run`: print the full curation diff from Phases 3–5 and STOP. Write nothing. Commit nothing. (No `dream:` commit is made, so the watermark stays where it was.)

Otherwise (normal run):
1. Apply every change from Phases 3–5 to the files.
2. Make ONE atomic commit covering every applied change: `git -C "<MEM>" add -A && git -C "<MEM>" commit -m "dream: curate <date> — <N> transcripts"`. This `dream:` commit IS the watermark advance — there is no watermark cell to write. A crash before this commit leaves no `dream:` commit, so the watermark is unmoved and the next run re-processes cleanly.
3. Push (best-effort): `git -C "<MEM>" push`.
4. Print a summary of every change. A bad run is undone with a single `git -C "<MEM>" revert <commit>`.

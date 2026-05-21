# /wrap + /dream — Session-Handoff & Memory-Management Redesign

**Status:** DESIGN — approved for planning
**Date:** 2026-05-21
**Author:** Victor (with Claude)
**Scope:** The Dietician project's session-handoff and memory-management tooling — the `/wrap` and `/sanity` skills, the `BRIDGE.md` handoff file, the `memory/` directory, and `docs/backlog.md`.
**Review:** Five adversarial council rounds (see §11). Architecture confirmed; this document folds in every council fix.
**Not in scope:** Anything in the Dietician *product* (the Kotlin Multiplatform app). This is meta-tooling only.

---

## 1. Problem

The session-handoff system has accumulated failure modes. The `/wrap` skill runs at session end and does two jobs at once:

1. **Capture** — append a session block to `BRIDGE.md`, an append-only chronological handoff file (currently ~1228 lines / 162 KB, too large to read whole).
2. **Curate** — reconcile `docs/backlog.md`, the canonical P0–P3 priority list.

Documented gaps in the current system:

| # | Gap |
|---|-----|
| 1 | No rule for which git branch the backlog reconcile commit lands on — it has landed on a feature branch, leaving `master`'s canonical backlog stale. |
| 2 | No self-verify step — `/wrap` never checks its own written block, so a wrong commit SHA / stale fact / contradiction ships silently. |
| 3 | `BRIDGE.md` grows unbounded — append-only forever, already unreadable in one pass. |
| 4 | A single "git HEAD" field conflates the working-branch HEAD with `origin/master`. |
| 5 | Minor: VPS-probe timestamp not mandated; no required-field completeness check; no verbosity cap; hallucination-triggers derived ad hoc each session. |

## 2. The reframe

`/wrap` conflates two jobs with different objectives:

- **CAPTURE** — record what happened. Objective: *fidelity*. Runs now, in-band, cheap, every session.
- **CURATE** — keep memory coherent. Objective: *memory quality* (dedup, de-stale, verify, learn). No hot-path latency budget.

Anthropic's "memory + dreaming" pattern (from the *Code with Claude 2026: Memory and dreaming for self-learning agents* talk) says curation should be **out-of-band** — a batch process, separate from any task session, with memory quality as its own objective, that *actively curates* (deduplicates, removes/flags stale entries, adds verification notes, learns cross-session patterns) rather than appending forever.

The current `/wrap` is in-band, hand-invoked, and append-only — the opposite of all three. That single conflation explains the whole gap catalog. The fix is to split capture from curation.

**Constraint:** this project runs on Claude Code, not Anthropic's managed-agents API. The managed-agents memory/dreaming API is unavailable. Only the *pattern* ports — realised here with Claude Code primitives: fresh separate sessions, sub-agents, git, and (later, gated) `CronCreate`.

## 3. Goals & non-goals

**Goals**

- Split capture (in-band, cheap, every session) from curation (out-of-band, own objective).
- Bound the handoff file's size structurally.
- Make every memory write attributable and revertible.
- Give curation the four "dreaming" operations: dedup, de-stale, verify, learn.
- Close all five documented gaps.

**Non-goals**

- Scheduled/automated curation. `CronCreate` scheduling of `/dream` is explicitly gated and out of scope for this design (see §10).
- Multi-agent / concurrent-session support. This is a solo developer with strictly sequential sessions; no concurrency control is designed in.
- An intra-`memory/` read-only/read-write permission split (considered and rejected by council round 1 as over-engineering at this scale).
- Changing the Claude Code auto-memory convention or the cross-project memory directory.

## 4. Architecture

Two skills, one objective each.

| | `/wrap` = CAPTURE | `/dream` = CURATE |
|---|---|---|
| When | end of every session, in-band | its own fresh Claude Code session, hand-invoked, out-of-band |
| Objective | fidelity — record truth now, cheap | memory quality — dedup, de-stale, verify, learn |
| May be skipped? | never (must stay cheap) | may lag (must not corrupt freshness) |

**Governing principle.** If the next session reads a file at startup, `/wrap` owns that file's *freshness* — it is refreshed in-band, every session, so it is never stale by omission. Freshness ownership is **not** exclusive write ownership (see §6).

**Two handoff files** replace the single append-only `BRIDGE.md`:

- **`BRIDGE-HEAD.md`** — current-state only. Always read at session start. Rewritten in full by `/wrap` every session. Size-capped (§7).
- **`BRIDGE-LOG.md`** — append-only narrative session entries (the handoff story). Read on demand. `/wrap` appends one entry per session; `/dream` compacts old entries in place.

`git` is the history layer: `git log -p BRIDGE-HEAD.md` yields every past current-state, attributed and diffable. This satisfies the dreaming pattern's stated "version history + attribution" requirement without new machinery.

**`/dream` inputs.** Primary signal: raw Claude Code `.jsonl` session transcripts. Reading the transcripts — not `/wrap`'s own summaries — is what makes curation *dreaming* rather than mere log compaction; a curation pass over its own digests can only launder blind spots, never catch them. Secondary inputs: `BRIDGE-HEAD.md`, `BRIDGE-LOG.md`, the `memory/` directory, `backlog.md`, `git log`.

**`/dream` scope.** Project-scoped. Read-write: the project memory repo (§5). Read-only: the cross-project shared-rules memory directory at `~/.claude/projects/C--Users-User/memory/` — `/dream` may read it to detect contradictions but never mutates it (blast-radius containment; the cross-project memory belongs to other projects too, and CLAUDE.md forbids cross-contamination).

## 5. Storage layer

### 5.1 The memory repo

A **dedicated git repository**, created by `git init` at the existing memory directory:

```
~/.claude/projects/C--Users-User-Desktop-Dietician/memory/
```

It holds: `BRIDGE-HEAD.md`, `BRIDGE-LOG.md`, `MEMORY.md` (index), the typed memory files (`user_*`, `feedback_*`, `project_*`, `reference_*`), and **`backlog.md`** (relocated — see §5.3).

**Discovered constraint.** The memory directory currently sits inside the `C:/Users/User` home git repository's work tree, but no memory file is tracked there (verified 2026-05-21: `git ls-files` returns zero memory files; the path is not git-ignored, just never added). The council's "git is the history layer" was, until now, an assumption — false in fact. The migration (§9) makes it true.

The memory directory's location is fixed by the Claude Code auto-memory convention and cannot be moved outside the home repo's work tree. The dedicated memory repo is therefore **nested** inside the home repo's work tree. This is deliberate, not accidental: the home repo gets an explicit, commented `.gitignore` entry for the memory path so it cleanly ignores the nested repo, and the memory repo is treated as a standalone repository with its own history and its own backup (§5.2).

### 5.2 Backup — off-machine durability (council round 5, CRITICAL)

The memory repo is the **sole system of record** for both BRIDGE memory and the backlog. A local-only git repo protects against bad edits but not against disk loss. The memory repo therefore **must have an off-machine remote**:

- The migration configures a **private remote** for the memory repo (a private GitHub repository is the recommended choice — the user already uses GitHub).
- `/wrap` and `/dream` **push to the remote** after committing. The push is **best-effort**: the local commit is mandatory and always succeeds first; if the push fails (offline), the skill reports it and the next push catches up. A failed push never fails the capture or curation.

Done this way the system ends up *more* durable than today: at present the memory directory is entirely unbacked, and `backlog.md` has only the Dietician GitHub repo as an off-machine copy. After migration, both live in a backed-up repo.

### 5.3 Backlog relocation

`docs/backlog.md` moves out of the Dietician code repository and into the memory repo as `backlog.md`.

**Rationale.** The backlog is session-start required reading, is written by `/wrap`, `/dream`, and the user's own hand-edits, and is operational session state — not source code. Keeping it on a feature branch of the code repo was the root cause of gap #1: three uncoordinated writers on a branched file guarantee divergence and the reconcile landing on the wrong branch. In a single-branch memory repo the problem cannot occur. A P0–P3 priority list is project working-memory; it belongs with the memory, not coupled to the code's branch topology. (The design wiki under `docs/design/` is *not* relocated — it carries `file:line` anchors into the code and stays with the code.)

**Cost, accepted by the user:** `backlog.md` leaves the Dietician GitHub repo and is no longer visible there. Its prior history stays frozen in the code repo; new history begins in the memory repo. The off-machine backup (§5.2) replaces the durability the GitHub repo previously provided.

### 5.4 Attribution

Every commit to the memory repo is prefixed by write-type:

- `wrap: …` — a capture write.
- `dream: …` — a curation write.
- `manual: …` — a hand-edit checkpoint (see §8, pre-step).

git records author and time; the prefix records the *semantic* write-type, which git's author field cannot. This is the attribution layer the dreaming pattern calls for.

## 6. `BRIDGE-HEAD.md` schema

Fixed sections, so `/wrap` knows exactly what to rewrite and a reader knows exactly where to look.

```
# BRIDGE-HEAD — current state

<!-- Current-state only. Rewritten in full by /wrap every session.
     /dream writes ONLY the Flags section.
     The `last /dream` Pointer line is display-only — /wrap derives it from
       the memory repo's git log (the latest commit with a `dream:` subject); nothing depends on the cell.
     RECOVERY: this file is a derived cache. If it is wrong, revert it —
     `git -C <MEM> log -p BRIDGE-HEAD.md` shows every prior version — and re-run /wrap. -->

## Git state
- working branch: <name> @ <short-SHA>
- origin/master: <short-SHA>
- tree: clean — or — <N> modified, <M> untracked; branch ahead <N> / behind <M>

## Hot work
<what is in progress, where it stopped, the next concrete step>

## Open items
<session-level loose ends, blockers, pending decisions — NOT the backlog>

## Live external state
- VPS 46.247.109.91: <state> — probed <ISO-8601 timestamp>   [timestamp mandatory]
- desktop / server: <running?>

## Pointers
- newest BRIDGE-LOG entry: <id>
- last /wrap: <ISO>   last /dream: <ISO — display-only, derived from git log>   curation age: <derived from git log>
- active spec / plan paths
- new this session: <new memory files / new locked decisions, or "none">

## Flags
- [STALE: <reason>] <claim>          (written by /dream)
- [TRIAGE] <backlog item awaiting prioritisation>   (written by /dream)
- (empty → "none")
```

The split **Git state** fields close gap #4. The mandatory VPS probe timestamp closes part of gap #5. The HTML header comment is a fixed part of the schema — `/wrap` preserves it verbatim across rewrites; it is not a state section.

### 6.1 Write ownership of `BRIDGE-HEAD.md`

- **State sections** (Git state, Hot work, Open items, Live external state, Pointers) — `/wrap` is the sole writer. `/wrap` rewrites them in full every session.
- **Flags section** — `/dream` appends `[STALE]` and `[TRIAGE]` flags here; `/wrap` *resolves* them (see §8 step 2). This is `/dream`'s **only** write into `BRIDGE-HEAD.md`.
- **`last /dream` pointer cell** — display-only. The real curation watermark is **git-derived**: the committer-date of the most recent memory-repo commit whose **subject line** begins `dream:` (the exact subject-anchored query is in §8.2 Phase 1 — a body-scoped `git log --grep` is wrong, it would match a stray `dream:`-prefixed line in any commit's body). `/wrap` refreshes this line from `git log` for human readability; nothing depends on the cell's value. Because the watermark *is* the `dream:` commit, it advances atomically with curation by construction — a `/dream` run interrupted before its commit leaves the watermark unmoved, with no separate cell that a crashed run could leak. (Council round 7.)

**Why `/dream` does not rewrite state cells.** `/wrap` rewrites HEAD's state sections in full every session. If `/dream` also wrote a state cell, `/wrap`'s next full rewrite would temporally overwrite `/dream`'s correction with no signal — a sequential hazard (not a concurrency race; this is a solo, sequential-session system). Restricting `/dream` to the Flags section makes a wrong HEAD state cell always `/wrap`'s responsibility — trivially attributable — while still letting `/dream` surface a finding loudly, in the always-read file, where the next session cannot miss it.

**Rule:** `/dream` writes **only** the Flags section of `BRIDGE-HEAD.md` — no state cell, ever. The curation watermark is git-derived (above), so `/dream` needs no HEAD cell of its own.

### 6.2 Net-delete guard

`/wrap`'s full rewrite of HEAD must not *net-delete* an Open Item or a Flag line without an explicit `resolved:` or `carried-forward:` annotation on that line. A context-exhausted `/wrap` dropping a live item then shows up in the git diff instead of vanishing silently.

### 6.3 Size cap

`BRIDGE-HEAD.md` targets ≤ ~2 000 tokens. `/wrap` runs a literal `wc` check after writing; over budget, it must trim prose in Hot work / Open items. A cap with no check is not a cap.

## 7. `BRIDGE-LOG.md` format

Append-only narrative entries, one per session — the human-readable handoff story. Each entry is a dated block: what happened, decisions taken, user instructions quoted verbatim, builds produced, test counts, blockers. Entry format is narrative prose under a dated header (unlike HEAD, it is not a rigid field schema).

`/wrap` appends; it never edits existing entries. `/dream` compacts old entries in place (§8.2 phase 4). The newest N entries are never compacted.

## 8. `/wrap` — CAPTURE

In-band, runs at every session end. Commits only to the memory repo. Cheap by design — it must never be worth skipping.

**Step 1 — Probe.** Gather facts via tools; never hallucinate. From the Dietician repo (read-only): branch name, working HEAD short-SHA, `origin/master` short-SHA, tree clean/dirty, ahead/behind. VPS: `ssh` unit/`docker`/`free` checks and `curl` health endpoints, stamped with an ISO-8601 timestamp taken now. Builds/tests this session: best-effort; note explicitly if not run.

**Step 2 — Rewrite `BRIDGE-HEAD.md` state sections.** Full rewrite of Git state, Hot work, Open items, Live external state, Pointers. Apply the net-delete guard (§6.2). Resolve `/dream`'s Flags: for each `[STALE]`/`[TRIAGE]` flag, either resolve it (re-derive the fact, correct the relevant cell, drop the flag and note `resolved:` in the BRIDGE-LOG entry) or carry it forward unchanged — never silently drop it. Run the `wc` cap check.

**Step 3 — Append a `BRIDGE-LOG.md` entry.** One dated narrative block for the session. Append-only.

**Step 4 — Mechanical backlog touch.** In `backlog.md` (memory repo): tick items shipped this session into `## Done` (cite the PR); append newly-discovered items to a `## TRIAGE` bucket, **unsorted**; refresh the header (`Last updated`, `master HEAD`). `/wrap` does **not** re-prioritise, dedup, or prune — that is `/dream`'s judgement work (§8.2 phase 5). Sorting `[TRIAGE]` items into P0–P3 is judgement; ticking and appending is factual recall — only the latter belongs on the in-band hot path.

**Step 5 — Commit, push, self-verify.** One `wrap:` commit covering HEAD + LOG + backlog. Best-effort push to the remote (§5.2). Then self-verify (closes gap #2):

- HEAD's Git-state SHAs match a fresh `git rev-parse` (git is an independent oracle — a real check).
- The VPS line carries a timestamp.
- All HEAD sections are present and non-empty (field-completeness — closes part of gap #5).
- HEAD is within the `wc` budget.
- No Flag or Open Item was dropped without a `resolved:` / `carried-forward:` line.
- The BRIDGE-LOG entry has its required narrative fields.

On any failure, **fail loud** — report it; do not present a broken capture as done. Note: the SHA/timestamp/`wc` checks are mechanical and reliable. The "nothing dropped" check is a weaker self-check by the same context that wrote the output; its real backstop is the reviewable git diff plus `/dream`'s later verify pass. It is a tripwire, not a guarantee.

**Step 6 — Curation-staleness nag (council round 5).** `/wrap` derives the curation age from the latest `dream:` commit in the memory repo (the subject-anchored query of §8.2 Phase 1) and refreshes the display-only `last /dream` line in HEAD Pointers from it. If the age exceeds a threshold (e.g. N sessions or D days), `/wrap` prints a single-line reminder to run `/dream`. `/wrap` is the ritual that always runs; it is therefore the trigger for the one that does not. Without this the CURATE half of the system is dead code.

`/wrap` no longer does (these move to `/dream` or are dropped): dedup, re-prioritisation, compaction, hallucination-trigger derivation, deep memory verification. The current `/wrap`'s fields are reallocated — current-state fields to HEAD, narrative fields to the LOG entry, static facts (identity, "don't relitigate") dropped from per-session capture as they already live in `CLAUDE.md` and the memory files.

## 8.2 `/dream` — CURATE

Out-of-band. Runs in its own fresh Claude Code session, hand-invoked. Sole objective: memory quality. Commits only to the memory repo. **Absorbs `/sanity`** — the old `/sanity` skill is deleted; its memory-audit role becomes `/dream`'s verify operation, and its "propose, let the user choose" behaviour becomes `/dream`'s `--dry-run` mode.

**Pre-step — clean working tree.** If the memory repo's working tree is dirty on entry, `/dream` first commits the existing changes as a `manual:` checkpoint (preserving any user hand-edits or a crashed prior run's partial work), so the run starts from a clean, committed state.

**Phase 1 — Window.** Compute the curation watermark — the committer-date of the most recent memory-repo commit whose **subject line** begins `dream:`: `git -C "<MEM>" log --format='%cI %s' | grep -m1 -E '^[^ ]+ dream: ' | cut -d' ' -f1 || true` (empty → no `/dream` has run; take all transcripts). The match is subject-anchored deliberately: a body-scoped `git log --grep="^dream:"` would false-positive on any commit whose message body contained a `dream:`-prefixed line. Select the `.jsonl` session transcripts newer than that watermark (incremental — never a full-history sweep, which would be too token-expensive for a hand-invoked skill to ever be run). **Defensive parsing (council round 5):** if the transcript directory or `.jsonl` format does not match expectations, `/dream` fails loud and does not proceed — it must never curate over transcripts it could not read correctly.

**Phase 2 — Ingest.** Dispatch one sub-agent per transcript (a sub-agent that finds its transcript too large chunks it internally). Each sub-agent returns a compact digest — events, repeated mistakes, claims to verify, candidate cross-session patterns — so `/dream`'s own context sees digests, not raw transcripts. This is the scatter-gather the dreaming talk describes.

**Phase 3 — Curate.** From the digests plus HEAD, LOG, the `memory/` directory, `git log`, and `backlog.md`, produce the curation diff — the four dreaming operations, applied across `BRIDGE-LOG.md`, the typed memory files, and `MEMORY.md`:

- **Dedup** — consolidate redundant entries.
- **De-stale** — claims that have drifted get a `[STALE: <reason>]` flag; never a hard delete. A flag against a HEAD fact is appended to HEAD's Flags section.
- **Verify** — claims confirmed accurate against transcripts/current state get a `[VERIFIED: <date>]` note (the presence-of-trust signal that complements `[STALE]`; this is the old `/sanity` audit). This phase also checks `BRIDGE-HEAD.md` itself against the LOG and transcripts and flags any drift.
- **Learn** — cross-session patterns: repeated mistakes, strategies that worked, become a new `feedback`/`project` memory entry.

**Phase 4 — Compact `BRIDGE-LOG.md`.** Shorten old entries in place. Never compact an entry that an unresolved HEAD Flag or Open Item still references (low-water-mark — else compaction orphans a pointer). Each compacted entry gets a `compacted: <date>` marker so re-compaction is a no-op and over-aggressive/truncated compaction is detectable; a coarse integrity check (preserved entry-header count) runs after.

**Phase 5 — Reconcile `backlog.md`.** The judgement half of the backlog split: sort `## TRIAGE` items into P0–P3, dedup, prune.

**Phase 6 — Commit (atomic), push, report.** All of phases 3–5 land as **one atomic `dream:` commit**. That commit *is* the watermark advance — the watermark is the timestamp of the latest `dream:` commit (§6.1), so there is no separate cell to write. Then best-effort push (§5.2). `/dream` prints a summary of what it did.

- **`--dry-run`** performs phases 1–5, prints the curation diff, and commits nothing — the old `/sanity` audit-only use case. Default is apply.
- **Crash safety (council rounds 5 + 7).** The whole run is one atomic unit: a crash before the phase-6 commit leaves nothing committed and the watermark unmoved, so the next run re-processes cleanly from scratch — re-processing is safe precisely because nothing was applied. This supersedes the earlier "one commit per logical change" idea: per-change commits made a crashed run re-apply non-idempotent curation (duplicate learned entries, lossy re-compaction) on the next run. The atomic unit is the whole run, not the transcript, because the *learn* operation is inherently cross-transcript. The cost — the user reverts a whole run rather than a single operation — is mitigated by `--dry-run` review before apply. Because the watermark is git-derived (§6.1), this holds **by construction**: the only thing that advances the watermark is a successful `dream:` commit, so an interrupted run cannot move it — there is no watermark cell that a crashed run's dirty working tree could carry forward (a crashed run's dirty tree is preserved by the next run's pre-step as a `manual:` commit, which, not being a `dream:` commit, does not touch the watermark).

`/dream` writes only HEAD's Flags section — no state cell, and no watermark cell (the watermark is git-derived, §6.1).

## 9. Migration

One-time, performed by the implementation plan — not a skill feature.

1. `git init` the memory repo at `~/.claude/projects/C--Users-User-Desktop-Dietician/memory/`. Configure its private off-machine remote (§5.2). Add an explicit, commented `.gitignore` entry for the memory path to `C:/Users/User/.gitignore`.
2. Distill the latest existing `BRIDGE.md` entry into `BRIDGE-HEAD.md` (current-state fields only, per the §6 schema).
3. Rename `BRIDGE.md` → `BRIDGE-LOG.md`. Its existing entries become the initial LOG; append-only from here.
4. Move `docs/backlog.md` → `backlog.md` in the memory repo. The old file's history stays frozen in the Dietician code repo.
5. **Update every reference (grep-driven checklist).** The plan must grep the whole Dietician repo for `docs/backlog.md` and `BRIDGE.md` and enumerate *every* hit as an explicit task — at minimum `CLAUDE.md` (required-reading order, doc-ownership section), `AGENTS.md`, `MEMORY.md`, `wrap.md`, `sanity.md`. A missed reference is a dangling pointer; it fails loud on first read but should be caught at migration time.
6. Initial `wrap:`-prefixed commit of the memory repo; push to the remote. Update `MEMORY.md`'s index and `CLAUDE.md`'s required-reading paths (`BRIDGE.md` → `BRIDGE-HEAD.md`; `docs/backlog.md` → the memory-repo `backlog.md`).
7. Delete the `/sanity` skill file (its role is absorbed by `/dream`).

## 10. HEAD recovery (council round 5)

`BRIDGE-HEAD.md` is a distilled, derived artifact; a bad `/wrap` distill can poison the next session, and §8 step 5 self-verify catches only mechanical errors. The recovery path is named explicitly:

- The memory repo's git history holds **every prior `BRIDGE-HEAD.md`**. A HEAD discovered to be wrong is recovered by reverting it to the last good commit (`git log -p BRIDGE-HEAD.md` to find it) and re-running `/wrap`.
- `/dream`'s verify operation (§8.2 phase 3) explicitly checks `BRIDGE-HEAD.md` against `BRIDGE-LOG.md` and the transcripts, and flags drift. `/dream` is therefore the routine detector of a bad distill.

HEAD is a cache; the LOG, git history, and transcripts are the ground truth it is derived from, and it is always reconstructible from them.

## 11. Review history

This design was reviewed by a five-agent adversarial council seven times — five rounds on the design, two on the implementation plan. Transcripts in `.claude/council-cache/`.

| Round | File | Verdict | Outcome |
|-------|------|---------|---------|
| 1 — approach | `council-1779323815.md` | FLAWED | Chose this approach (split capture/curate) over a patch-only fix and a full storage re-architecture. Amendments: HEAD owned by `/wrap`; `/dream` non-destructive; `CronCreate` gated. |
| 2 — first draft | `council-1779325814.md` | FLAWED | `/dream` reads raw transcripts, not its own summaries; restore the verify + learn outputs; typed memory in `/dream` scope; collapse to two BRIDGE files. |
| 3 — revision | `council-1779327710.md` | FLAWED | `/dream` writes HEAD only via the Flags section (no temporal clobber); `wrap:`/`dream:` commit attribution; `/dream` incremental; LOG-compaction safety rules. |
| 4 — mechanics | `council-1779328445.md` | FLAWED | Backlog-on-a-branch is the root defect → relocate it to the memory repo (kills the `git worktree` mechanism and gap #1); `/dream` writes its own watermark; watermark integrity; `/dream` audit-only mode. |
| 5 — final | `council-1779330071.md` | FLAWED | Five residual fixes (all folded into this document): off-machine backup; crash-atomic `/dream` commit; `/wrap` staleness nag; named HEAD-recovery path; defensive transcript parsing + grep-checklist migration. Architecture confirmed sound. |
| 6 — plan | `council-1779355838.md` | FLAWED | Reviewed the implementation plan: commit + push the memory repo before any mutation; cross-repo `git mv` is broken (use `cp`/`rm`/`add`); Task 2 needs a human-review checkpoint; test the crash-atomic watermark. |
| 7 — plan revision | `council-1779356671.md` | FLAWED | Reviewed the revised plan. Produced the §6.1 / §8.2 amendment above — the curation watermark is **git-derived** (the latest `dream:` commit), not a HEAD cell, making crash-atomicity hold by construction. Plan: split the BRIDGE-HEAD-draft task at the human-review checkpoint into two tasks. |

## 12. Gap catalog → resolution

| Gap | Resolved by |
|-----|-------------|
| 1 — backlog reconcile lands on the wrong branch | `backlog.md` relocated to the single-branch memory repo (§5.3) — the failure cannot occur. |
| 2 — no self-verify | `/wrap` step 5 self-verify (§8). |
| 3 — `BRIDGE.md` unbounded growth | Split into bounded `BRIDGE-HEAD.md` (rewritten, `wc`-capped) + `BRIDGE-LOG.md` (compacted in place by `/dream`). |
| 4 — conflated git-HEAD field | HEAD schema Git state carries working-branch SHA and `origin/master` SHA as separate fields (§6). |
| 5 — minors | Mandatory VPS timestamp + field-completeness check + `wc` cap in `/wrap` (§8); hallucination-trigger derivation moves from ad-hoc `/wrap` work to `/dream`'s verify operation. |

## 13. Acceptance criteria

The implementation is complete when:

- `/wrap` and `/sanity` are replaced by `/wrap` (capture) and `/dream` (curate); the `/sanity` skill file is deleted.
- The memory repo exists, is git-initialised, has a working off-machine remote, and is git-ignored by the home repo.
- `BRIDGE.md` has been migrated to `BRIDGE-HEAD.md` + `BRIDGE-LOG.md`; `backlog.md` lives in the memory repo; every reference has been updated (grep for `docs/backlog.md` and `BRIDGE.md` returns only intentional/historical hits).
- A `/wrap` run produces a schema-valid `BRIDGE-HEAD.md` within the `wc` cap, appends a `BRIDGE-LOG.md` entry, touches `backlog.md`, self-verifies, commits with a `wrap:` prefix, and pushes.
- A `/dream` run reads the incremental transcript window (transcripts newer than the latest `dream:` commit), produces the four curation operations, compacts the LOG safely, reconciles the backlog, commits atomically with a `dream:` prefix, and pushes; `--dry-run` writes and commits nothing.
- A `/dream` run interrupted before its commit leaves the watermark unmoved and the next run re-processes cleanly — provable by construction, since the watermark is the latest `dream:` commit (§6.1).
- `/wrap` prints a staleness nag when curation age exceeds the threshold, and stays silent when curation is fresh.
- All seven council rounds' fixes are present (cross-check §11 against the implementation).

## 14. Out of scope / future

- **`CronCreate` scheduling of `/dream`.** Once `/dream` is proven non-destructive over real use, it may be scheduled as a recurring background agent via `CronCreate`. This is explicitly downstream of this design and gated on that proof — not in scope here.
- Curating the cross-project shared-rules memory directory (a separate, deliberate "global `/dream`" concern).

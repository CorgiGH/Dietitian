# /wrap + /dream Memory-System Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the conflated `/wrap` + `/sanity` session-handoff tooling with a split design — `/wrap` (in-band capture) and `/dream` (out-of-band curation) — backed by a dedicated, backed-up memory git repo.

**Architecture:** A one-time storage migration (create a dedicated memory git repo, snapshot it, give it an off-machine remote, split `BRIDGE.md` into `BRIDGE-HEAD.md` + `BRIDGE-LOG.md`, relocate `backlog.md` into the memory repo, update references), then two rewritten skill files (`wrap.md`, `dream.md`) and the deletion of `sanity.md`. See the binding spec: `docs/superpowers/specs/2026-05-21-wrap-memory-system-redesign-design.md`.

**Tech Stack:** Markdown slash-command skill files (`.claude/commands/`); git (two repos — the Dietician code repo and a new memory repo); `gh` CLI for the private remote; bash; Claude Code Agent/sub-agent tooling.

**Revised after council rounds 6 + 7** (`.claude/council-cache/council-1779355838.md`, `council-1779356671.md`): the memory repo is committed + pushed in Task 1 *before any file mutation*; the cross-repo backlog move uses `cp`+`rm`+`add` (a cross-repo `git mv` aborts); the BRIDGE-HEAD distillation is split into a draft task (Task 2) and an apply task (Task 3) with a human-review checkpoint at the task boundary; the `/dream` curation watermark is **git-derived** (the timestamp of the latest `dream:` commit — no HEAD cell), making crash-atomicity hold by construction.

**Two git repos, two commit targets — do not confuse them:**
- **Dietician code repo** — `C:/Users/User/Desktop/Dietician`. Holds the skill files and `CLAUDE.md`. Skill/doc edits commit here, on a branch `feat/wrap-dream-memory-redesign` off `master`.
- **Memory repo** — created in Task 1 at `~/.claude/projects/C--Users-User-Desktop-Dietician/memory/`. Holds `BRIDGE-HEAD.md`, `BRIDGE-LOG.md`, `MEMORY.md`, the typed memory files, `backlog.md`. Memory edits commit here.

Throughout, `<MEM>` means `C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory` and `<DIET>` means `C:/Users/User/Desktop/Dietician`.

**Data-safety invariant:** after Task 1, every memory file is version-controlled in the memory repo and pushed to an off-machine remote. No task mutates a memory file before Task 1 completes.

**Watermark model:** the `/dream` curation watermark is the commit timestamp of the latest `dream:`-prefixed commit in the memory repo — `git -C "<MEM>" log --format='%cI %s' | grep -m1 -E '^[^ ]+ dream: ' | cut -d' ' -f1 || true`. There is no watermark file-cell. The `last /dream` line in `BRIDGE-HEAD.md` is display-only, refreshed by `/wrap` from that query.

---

## Phase 1 — Storage migration (one-time)

### Task 1: Create the memory repo, snapshot it, configure the off-machine remote

This task brings the memory dir under version control and backs it up **before any file is renamed, moved, or edited**. Nothing downstream mutates memory until this completes.

**Files:**
- Create: the git repo at `<MEM>` (via `git init`)
- Modify: `C:/Users/User/.gitignore`
- Create: a private GitHub repo `dietician-memory` (off-machine backup remote)

- [ ] **Step 1: Confirm the memory-dir state**

Run: `git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" rev-parse --is-inside-work-tree 2>&1; ls "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory"`
Expected: prints `true` (the dir is inside the `C:/Users/User` home repo) and lists `BRIDGE.md`, `MEMORY.md`, and typed memory files. No `.git` directory inside `memory/` yet.

- [ ] **Step 2: Ignore the memory path from the home repo FIRST**

Append to `C:/Users/User/.gitignore` (create the file if absent):

```
# Dedicated Dietician memory repo (standalone — has its own git history + remote).
# Tracked independently; the home repo must not see it. See:
# Desktop/Dietician/docs/superpowers/specs/2026-05-21-wrap-memory-system-redesign-design.md
/.claude/projects/C--Users-User-Desktop-Dietician/memory/
```

This lands before `git init` so the home repo never sees the nested repo.

- [ ] **Step 3: Verify the home repo ignores the memory path**

Run: `git -C "C:/Users/User" status --porcelain ".claude/projects/C--Users-User-Desktop-Dietician/memory" 2>&1`
Expected: no output (the memory dir is fully ignored by the home repo).

- [ ] **Step 4: git init the memory repo with a pinned branch name**

Run: `git init -b main "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" && git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" rev-parse --show-toplevel`
Expected: `Initialized empty Git repository …`; `--show-toplevel` prints the memory dir path itself. The branch is `main` (pinned — not left to `init.defaultBranch`). The host runs git ≥ 2.28, so `-b` is supported.

- [ ] **Step 5: Snapshot commit — the memory dir exactly as it is now**

```bash
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" add -A
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" commit -m "wrap: snapshot the memory dir before migration"
```
Expected: one commit; `git -C "<MEM>" status` reports a clean tree. This is the recovery point — every later migration step is now revertible.

- [ ] **Step 6: Create the private remote**

Run: `gh repo create dietician-memory --private --source "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" --remote origin`
Expected: `✓ Created repository …/dietician-memory`; remote `origin` configured.
Note: this creates an external **private** GitHub repo holding personal memory + infra notes — private is required. If `gh` is not authenticated, run `gh auth login` first (the user can do this via `! gh auth login`), or create the repo manually in the GitHub UI and run `git -C "<MEM>" remote add origin <url>`.

- [ ] **Step 7: Push the snapshot — establish the off-machine backup**

Run: `git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" push -u origin main`
Expected: branch `main` pushed, tracking set. The off-machine backup now exists. If the push fails (auth/network), STOP and resolve it — the backup is a binding spec requirement (§5.2) and the data-safety invariant depends on it.

- [ ] **Step 8: Verify**

Run: `git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" log --oneline && git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" status -sb`
Expected: one `wrap: snapshot …` commit; `## main...origin/main`; clean tree.

- [ ] **Step 9: Commit the home-repo .gitignore change**

```bash
git -C "C:/Users/User" add .gitignore
git -C "C:/Users/User" commit -m "chore: ignore the dedicated Dietician memory repo"
```

---

### Task 2: Draft BRIDGE-HEAD.md and present it for review

Distilling current-state from the BRIDGE.md history is a judgement call, not a mechanical one. This task produces a DRAFT and **ends at a human-review checkpoint** — it does not rename or commit anything. Task 3 continues only after the user approves the draft.

**Files:**
- Create: `<MEM>/BRIDGE-HEAD.md` (draft — not yet committed)

- [ ] **Step 1: Read the current BRIDGE.md latest entry**

Read `<MEM>/BRIDGE.md`. Find the newest session block (its latest `## ` dated entry — the one the index marks newest). Note its current-state content: hot work, open items, blockers, VPS state, git state, active spec/plan paths.

- [ ] **Step 2: Probe current state**

Run, capturing output:
```bash
git -C "C:/Users/User/Desktop/Dietician" rev-parse --abbrev-ref HEAD
git -C "C:/Users/User/Desktop/Dietician" rev-parse --short HEAD
git -C "C:/Users/User/Desktop/Dietician" rev-parse --short origin/master
git -C "C:/Users/User/Desktop/Dietician" status --porcelain
```

- [ ] **Step 3: Create the draft BRIDGE-HEAD.md**

Create `<MEM>/BRIDGE-HEAD.md` with this exact schema, populated from Steps 1–2 (replace every `<…>`). Every section present and non-empty (`none` where genuinely empty):

```markdown
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
- tree: <clean | dirty (N files)>; branch ahead <N> / behind <M>

## Hot work
<what is in progress, where it stopped, the next concrete step>

## Open items
<session-level loose ends, blockers, pending decisions — NOT the backlog>

## Live external state
- VPS 46.247.109.91: <state> — probed <ISO-8601 timestamp>
- desktop / server: <running?>

## Pointers
- newest BRIDGE-LOG entry: <id>
- last /wrap: <ISO>   last /dream: never   curation age: n/a
- active spec / plan paths: <paths>
- new this session: none

## Flags
none
```

- [ ] **Step 4: CHECKPOINT — present the draft and stop**

This task ends here. Present the draft `BRIDGE-HEAD.md` to the user and ask them to confirm it accurately reflects current state — especially Hot work and Open items, which are judgement calls, not mechanical extractions. If the user wants changes, edit `<MEM>/BRIDGE-HEAD.md` on disk to match (apply the edits they describe, or let them edit the file directly). **The file at `<MEM>/BRIDGE-HEAD.md` after this review IS the approved draft — it is the durable input contract for Task 3, which reads that file, not this conversation.** Return control; do NOT rename `BRIDGE.md` or commit anything in this task. Task 3 runs only after the user approves.

---

### Task 3: Apply the approved draft and split BRIDGE.md

Runs only after the Task 2 checkpoint: the user has approved (and possibly corrected) the draft `BRIDGE-HEAD.md`.

**Files:**
- Finalize: `<MEM>/BRIDGE-HEAD.md` (apply any user corrections from the Task 2 review)
- Rename: `<MEM>/BRIDGE.md` → `<MEM>/BRIDGE-LOG.md`

- [ ] **Step 1: Confirm the approved draft is on disk**

The approved `BRIDGE-HEAD.md` is the file at `<MEM>/BRIDGE-HEAD.md` exactly as the Task 2 review left it — the user's corrections (if any) were already applied to that file at the Task 2 checkpoint. Read it; confirm it exists and is a complete, schema-valid draft (all six sections present). Do NOT re-derive or re-edit it from the conversation — the file on disk is the contract.

- [ ] **Step 2: Rename BRIDGE.md to BRIDGE-LOG.md**

Run: `git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" mv BRIDGE.md BRIDGE-LOG.md`
(Both paths are inside the memory repo — `git mv` works here. This is NOT a cross-repo move.)

- [ ] **Step 3: Verify**

Run: `ls "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" | grep -E "BRIDGE" && wc -w "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory/BRIDGE-HEAD.md"`
Expected: `BRIDGE-HEAD.md` and `BRIDGE-LOG.md` present; no `BRIDGE.md`; HEAD word count under ~1500.

- [ ] **Step 4: Commit and push**

```bash
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" add -A
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" commit -m "wrap: split BRIDGE into BRIDGE-HEAD + BRIDGE-LOG"
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" push
```

---

### Task 4: Relocate backlog.md into the memory repo

A cross-repo `git mv` aborts (`git mv` needs source and destination in one index). Use `cp` then `git rm` then `git add`. The memory repo's copy is committed + pushed BEFORE the Dietician repo's deletion is committed, so a crash between the two cross-repo commits never leaves the file committed in neither.

**Files:**
- Create: `<MEM>/backlog.md` (copied from `<DIET>/docs/backlog.md`)
- Delete: `<DIET>/docs/backlog.md`

- [ ] **Step 1: Create the work branch in the Dietician repo**

The feature branch's base is the `master` tip — the wrap/dream redesign is independent of any in-progress feature branch.
```bash
git -C "C:/Users/User/Desktop/Dietician" status --short
```
If that lists uncommitted *tracked* changes, stash them: `git -C "C:/Users/User/Desktop/Dietician" stash push -m "pre-wrap-dream-redesign"` (untracked files are fine to leave). Then:
```bash
git -C "C:/Users/User/Desktop/Dietician" checkout master
git -C "C:/Users/User/Desktop/Dietician" checkout -b feat/wrap-dream-memory-redesign
```
Expected: on a new branch `feat/wrap-dream-memory-redesign` based on the `master` tip.

- [ ] **Step 2: Copy backlog.md into the memory repo**

Run: `cp "C:/Users/User/Desktop/Dietician/docs/backlog.md" "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory/backlog.md"`

- [ ] **Step 3: Verify the copy is byte-identical**

Run: `diff "C:/Users/User/Desktop/Dietician/docs/backlog.md" "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory/backlog.md" && echo "COPY OK"`
Expected: `COPY OK` with no diff output. If `diff` prints anything, STOP — do not proceed.

- [ ] **Step 4: Fix backlog.md's internal self-references**

In `<MEM>/backlog.md`, update the two stale references (found by grep):
- Line ~3: the `BRIDGE.md` mention → `BRIDGE-LOG.md`.
- Line ~15: `~/.claude/projects/C--Users-User-Desktop-Dietician/memory/BRIDGE.md` → `…/memory/BRIDGE-LOG.md`.

- [ ] **Step 5: Commit + push the memory repo's copy FIRST**

```bash
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" add backlog.md
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" commit -m "wrap: relocate backlog.md into the memory repo"
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" push
```
The memory repo now owns `backlog.md`. Only after this is pushed does the Dietician repo lose its copy (next step).

- [ ] **Step 6: Delete the original from the Dietician repo and commit**

```bash
git -C "C:/Users/User/Desktop/Dietician" rm docs/backlog.md
git -C "C:/Users/User/Desktop/Dietician" commit -m "refactor: relocate backlog.md to the memory repo

Per docs/superpowers/specs/2026-05-21-wrap-memory-system-redesign-design.md §5.3.
History before this commit stays in this repo; new history is in the memory repo."
```

---

### Task 5: Update every live reference

Grep confirmed 7 live references plus 4 frozen historical artifacts. Update only the live ones; leave the historical artifacts (past plans, research) untouched.

**Files:**
- Modify: `<DIET>/CLAUDE.md`
- Modify: `<DIET>/docs/design/references.md`
- Modify: `<MEM>/MEMORY.md`

- [ ] **Step 1: Re-grep to confirm the hit list**

Run: `grep -rn "BRIDGE\.md\|docs/backlog\.md" "C:/Users/User/Desktop/Dietician" --include=*.md`
Expected hits to FIX: `CLAUDE.md` (lines ~13, ~14, ~21, ~139), `docs/design/references.md` (line ~41).
Expected hits to LEAVE (frozen historical artifacts — do not edit): the two files under `docs/superpowers/plans/`, the one under `docs/superpowers/research/`. The 2026-05-21 spec under `docs/superpowers/specs/` intentionally discusses `BRIDGE.md` as the thing being replaced — leave it.

- [ ] **Step 2: Update CLAUDE.md required-reading order**

In `CLAUDE.md`, the "Required reading order at session start" list:
- Item 1: `…/memory/BRIDGE.md — canonical session handoff…` → `…/memory/BRIDGE-HEAD.md — current state: git/VPS state, hot work, open items, flags. Read this every session.`
- Item 2: `` `docs/backlog.md` — **canonical production backlog**… `` → `` `~/.claude/projects/C--Users-User-Desktop-Dietician/memory/backlog.md` — **canonical production backlog**… `` (same description text, new path).

- [ ] **Step 3: Update CLAUDE.md doc-ownership paragraph + add the recovery note**

In `CLAUDE.md`, the "Doc ownership (no duplication)" paragraph: replace `` `docs/backlog.md` `` with the new memory-repo path, and `` `BRIDGE.md` owns *what happened, per session* `` with `` `BRIDGE-LOG.md` owns *what happened, per session*; `BRIDGE-HEAD.md` owns *current state* ``. Append one sentence: `BRIDGE-HEAD.md is a derived cache — if wrong, revert it via the memory repo's git history (it holds every prior version) and re-run /wrap.`

- [ ] **Step 4: Update CLAUDE.md slash-commands section**

In `CLAUDE.md`, the "Slash commands available" section:
- `` - `/wrap` — `.claude/commands/wrap.md` (append-only session handoff entry to BRIDGE.md) `` → `` - `/wrap` — `.claude/commands/wrap.md` (CAPTURE: rewrites BRIDGE-HEAD.md, appends a BRIDGE-LOG.md entry, touches backlog.md — run at session end) ``
- `` - `/sanity` — … `` → `` - `/dream` — `.claude/commands/dream.md` (CURATE: out-of-band memory curation — run in a fresh session, periodically) ``

- [ ] **Step 5: Update docs/design/references.md**

In `docs/design/references.md` line ~41: `…/memory/BRIDGE.md — append-only session handoff log…` → `…/memory/BRIDGE-LOG.md — append-only narrative session handoff log; BRIDGE-HEAD.md alongside it holds current state.`

- [ ] **Step 6: Update MEMORY.md in the memory repo**

In `<MEM>/MEMORY.md`, replace the `[BRIDGE.md](BRIDGE.md)` index line and any "read BRIDGE.md first" guidance with two lines: `BRIDGE-HEAD.md` (current state, read first) and `BRIDGE-LOG.md` (narrative history, on demand). Add a `[backlog.md](backlog.md)` index line. Update the "How to use this directory" steps to name `BRIDGE-HEAD.md`.

- [ ] **Step 7: Verify no live reference remains**

Run: `grep -rn "BRIDGE\.md" "C:/Users/User/Desktop/Dietician/CLAUDE.md" "C:/Users/User/Desktop/Dietician/docs/design/references.md" "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory/MEMORY.md"`
Expected: no output (every live `BRIDGE.md` reference is now `BRIDGE-HEAD.md` or `BRIDGE-LOG.md`).

- [ ] **Step 8: Commit both repos**

```bash
git -C "C:/Users/User/Desktop/Dietician" add CLAUDE.md docs/design/references.md docs/superpowers/specs/2026-05-21-wrap-memory-system-redesign-design.md docs/superpowers/plans/2026-05-21-wrap-dream-memory-redesign.md
git -C "C:/Users/User/Desktop/Dietician" commit -m "docs: point session-handoff references at the new memory layout

Also commits the redesign spec + plan onto the feature branch."
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" add -A
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" commit -m "wrap: update MEMORY.md index for the BRIDGE-HEAD/LOG split"
git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" push
```

---

### Task 6: Verify the migration state

A checkpoint task — no mutations, only verification that Phase 1 left both repos consistent.

- [ ] **Step 1: Memory repo is clean, committed, pushed**

Run: `git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" status -sb && git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" log --oneline`
Expected: `## main...origin/main` with no `ahead`, clean tree; four `wrap:` commits (snapshot, split, relocate, MEMORY.md). Files present: `BRIDGE-HEAD.md`, `BRIDGE-LOG.md`, `MEMORY.md`, `backlog.md`, the typed memory files.

- [ ] **Step 2: Dietician repo is on the feature branch with the migration commits**

Run: `git -C "C:/Users/User/Desktop/Dietician" log --oneline -3 && ls "C:/Users/User/Desktop/Dietician/docs/backlog.md" 2>&1`
Expected: on `feat/wrap-dream-memory-redesign`; recent commits include the backlog relocation and the reference updates; `docs/backlog.md` no longer exists (errors).

- [ ] **Step 3: No dangling references**

Run: `grep -rn "docs/backlog\.md" "C:/Users/User/Desktop/Dietician" --include=*.md | grep -v "superpowers/specs\|superpowers/plans\|superpowers/research\|council-cache"`
Expected: no output — every live `docs/backlog.md` reference is updated; only frozen historical artifacts may still mention the old path.

---

## Phase 2 — The skills

### Task 7: Rewrite the /wrap skill

**Files:**
- Modify (full rewrite): `<DIET>/.claude/commands/wrap.md`

- [ ] **Step 1: Overwrite wrap.md with the new CAPTURE skill**

Write `C:/Users/User/Desktop/Dietician/.claude/commands/wrap.md` with exactly this content:

```markdown
---
description: CAPTURE — session-end handoff. Rewrites BRIDGE-HEAD.md, appends a BRIDGE-LOG.md entry, touches backlog.md, commits + pushes the memory repo. Run at every session end.
---

`/wrap` is the in-band CAPTURE half of the handoff system. Objective: fidelity — record current truth now, cheaply. Run it at the end of every session. It commits ONLY to the memory repo at `~/.claude/projects/C--Users-User-Desktop-Dietician/memory/` (a dedicated git repo).

Do all six steps in order. Gather every fact via a tool call — never hallucinate. `<MEM>` below = `C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory`.

## Step 1 — Probe

- Git (Dietician repo at `C:/Users/User/Desktop/Dietician`): `git rev-parse --abbrev-ref HEAD` (branch), `git rev-parse --short HEAD` (working HEAD), `git rev-parse --short origin/master`, `git status --porcelain` (clean/dirty + file count), `git rev-list --left-right --count origin/master...HEAD` (behind/ahead).
- VPS: `ssh root@46.247.109.91 "systemctl is-active dietician-backend postgresql@16-main && docker ps --format '{{.Names}} {{.Status}}' && free -h | grep Mem"`. Health: `curl -sf http://100.101.47.77:8082/v1/health` and `curl -sf http://100.101.47.77:8081/health`. Take an ISO-8601 timestamp NOW for the probe.
- Curation watermark: `git -C "<MEM>" log --format='%cI %s' | grep -m1 -E '^[^ ]+ dream: ' | cut -d' ' -f1 || true` — the timestamp of the last `/dream` run (empty = never). Builds/tests this session: best-effort; if you did not run them, say so explicitly.

## Step 2 — Rewrite BRIDGE-HEAD.md

Full-rewrite the state sections of `<MEM>/BRIDGE-HEAD.md` to its fixed schema (Git state, Hot work, Open items, Live external state, Pointers, Flags). Every section present and non-empty (`none` where empty). Keep the title line and the HTML header comment block verbatim — they are fixed schema, not state sections.

- **Guard:** read the current BRIDGE-HEAD.md FIRST. Do NOT net-delete an Open Item or a Flag line unless you add an explicit `resolved:` or `carried-forward:` note on that line.
- **Flags:** you (`/wrap`) do not author flags — `/dream` does. For each existing `[STALE]`/`[TRIAGE]` flag: either resolve it (re-derive the fact, fix the relevant state cell, drop the flag, and note `resolved:` in the BRIDGE-LOG entry) or carry it forward unchanged. Never silently drop a flag.
- **Pointers:** `last /wrap` = now. `last /dream` and `curation age` are display-only — fill them from the watermark query in Step 1 (`last /dream` = that timestamp, or `never`; `curation age` = sessions/days since). Nothing depends on these cells; the real watermark is the git query itself.
- After writing, run `wc -w "<MEM>/BRIDGE-HEAD.md"`. If over ~1500 words, trim prose in Hot work / Open items and rewrite.

## Step 3 — Append a BRIDGE-LOG.md entry

Append ONE dated block to `<MEM>/BRIDGE-LOG.md` (append-only — never edit existing entries): a `## <ISO timestamp> — SESSION-NN` header, then narrative prose — what happened, decisions taken, user instructions quoted verbatim, builds produced, test counts, blockers, and any flag `resolved:` notes from Step 2.

## Step 4 — Touch backlog.md

In `<MEM>/backlog.md`: move items shipped this session to `## Done` (cite the PR number); append newly-discovered items to the `## TRIAGE` section, UNSORTED; refresh the header `Last updated` and `master HEAD`. Do NOT re-prioritise, dedup, or prune — that is `/dream`'s job.

## Step 5 — Commit, push, self-verify

- Commit the memory repo: `git -C "<MEM>" add BRIDGE-HEAD.md BRIDGE-LOG.md backlog.md && git -C "<MEM>" commit -m "wrap: session <NN> — <date>"`.
- Push (best-effort): `git -C "<MEM>" push`. If it fails (offline), report it and continue — the commit is local; the next push catches up. Do NOT fail the wrap over a failed push.
- Self-verify — re-read your outputs and check:
  - BRIDGE-HEAD Git-state SHAs equal a fresh `git rev-parse`.
  - The Live external state VPS line carries an ISO timestamp.
  - Every BRIDGE-HEAD section is present and non-empty.
  - `wc -w` of BRIDGE-HEAD.md is within budget.
  - No Open Item / Flag was dropped without a `resolved:`/`carried-forward:` line.
  - The BRIDGE-LOG entry exists with its narrative content.
- If ANY check fails: report it loudly. Do not present the wrap as done.

## Step 6 — Curation-staleness nag

Using the watermark from Step 1: if the last `/dream` run is more than 5 sessions OR 7 days old (or `/dream` has never run), print one line: `⚠ /dream is overdue — run /dream in a fresh session to curate memory.` If the last `/dream` run is within both thresholds, print nothing — do not nag when curation is fresh.
```

- [ ] **Step 2: Verify the file**

Run: `grep -c "^## Step" "C:/Users/User/Desktop/Dietician/.claude/commands/wrap.md" && head -3 "C:/Users/User/Desktop/Dietician/.claude/commands/wrap.md"`
Expected: `6` steps; the frontmatter `description:` line present.

- [ ] **Step 3: Commit**

```bash
git -C "C:/Users/User/Desktop/Dietician" add .claude/commands/wrap.md
git -C "C:/Users/User/Desktop/Dietician" commit -m "feat: rewrite /wrap as the in-band CAPTURE skill"
```

---

### Task 8: Create the /dream skill

**Files:**
- Create: `<DIET>/.claude/commands/dream.md`

- [ ] **Step 1: Write dream.md**

Write `C:/Users/User/Desktop/Dietician/.claude/commands/dream.md` with exactly this content:

```markdown
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
```

- [ ] **Step 2: Verify the file**

Run: `grep -c "^## Phase" "C:/Users/User/Desktop/Dietician/.claude/commands/dream.md" && grep -n "^## Pre-step\|^description:" "C:/Users/User/Desktop/Dietician/.claude/commands/dream.md"`
Expected: `6` phases; the `## Pre-step` heading and the frontmatter `description:` both present.

- [ ] **Step 3: Commit**

```bash
git -C "C:/Users/User/Desktop/Dietician" add .claude/commands/dream.md
git -C "C:/Users/User/Desktop/Dietician" commit -m "feat: add /dream — the out-of-band CURATE skill"
```

---

### Task 9: Delete the /sanity skill

**Files:**
- Delete: `<DIET>/.claude/commands/sanity.md`

- [ ] **Step 1: Confirm /sanity references are already handled**

Run: `grep -rn "sanity" "C:/Users/User/Desktop/Dietician/CLAUDE.md"`
Expected: no hits (Task 5 Step 4 already replaced the `/sanity` slash-command line with `/dream`). If any hit remains, fix it now.

- [ ] **Step 2: Delete the file**

Run: `git -C "C:/Users/User/Desktop/Dietician" rm .claude/commands/sanity.md`
Expected: `rm '.claude/commands/sanity.md'`.

- [ ] **Step 3: Commit**

```bash
git -C "C:/Users/User/Desktop/Dietician" commit -m "chore: remove /sanity — its memory audit is now /dream's verify phase"
```

---

## Phase 3 — End-to-end verification

### Task 10: Verify /wrap end-to-end

**Files:** none modified — this task runs the skill and checks the result.

- [ ] **Step 1: Run /wrap**

Invoke the `/wrap` skill in this session. Let it complete all six steps.

- [ ] **Step 2: Verify BRIDGE-HEAD.md**

Run: `grep -c "^## " "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory/BRIDGE-HEAD.md" && wc -w "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory/BRIDGE-HEAD.md"`
Expected: `6` sections; word count under ~1500. Read the file — Git-state SHAs and the VPS timestamp are present and current.

- [ ] **Step 3: Verify the BRIDGE-LOG append + backlog touch**

Run: `git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" log --oneline -1 && git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" show --stat HEAD`
Expected: the latest commit message starts with `wrap:`; the diff stat touches `BRIDGE-HEAD.md`, `BRIDGE-LOG.md`, and `backlog.md`.

- [ ] **Step 4: Verify the staleness nag fired (overdue path)**

No `dream:` commit exists yet, so `/wrap` Step 6 should have printed the `⚠ /dream is overdue` line. Confirm it appeared in the `/wrap` output. (The silent path is checked in Task 11 Step 7.)

- [ ] **Step 5: Verify the push**

Run: `git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" status -sb`
Expected: `## main...origin/main` with no `ahead`. If `ahead`, the push failed — investigate connectivity.

If any check fails, fix `wrap.md` (Task 7) and re-run.

---

### Task 11: Verify /dream end-to-end

**Files:** none modified — this task runs the skill and checks the result.

- [ ] **Step 1: Record the pre-run watermark**

Run: `git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" log --format='%s' | grep -cE '^dream: ' || true`
Expected: `0` — no `dream:` commit exists yet. This is the watermark baseline.

- [ ] **Step 2: Run /dream --dry-run and verify the crash-atomic property**

Invoke `/dream --dry-run` in a fresh context. Let it complete Phases 1–5 and print the curation diff. Then run:
`git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" log --format='%s' | grep -cE '^dream: ' || true && git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" status --porcelain`
Expected: still `0` `dream:` commits; the working tree is clean (no uncommitted edits).
This is the crash-atomic test: `--dry-run` is a `/dream` run that does NOT reach its Phase 6 commit. Because the watermark IS the latest `dream:` commit, a run that makes no `dream:` commit cannot move the watermark — exactly spec §13's "an interrupted `/dream` leaves the watermark unmoved", proven by construction.

- [ ] **Step 3: Run /dream for real**

Invoke `/dream` (no flag). Let it complete all phases including the Phase 6 commit.

- [ ] **Step 4: Verify the atomic curation commit advanced the watermark**

Run: `git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" log --format='%s' | grep -cE '^dream: ' || true && git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" show --stat HEAD`
Expected: now `1` `dream:` commit; it is ONE commit; `HEAD`'s message starts with `dream:`. The watermark (latest `dream:` commit) has advanced.

- [ ] **Step 5: Verify the push**

Run: `git -C "C:/Users/User/.claude/projects/C--Users-User-Desktop-Dietician/memory" status -sb`
Expected: `## main...origin/main` with no `ahead`.

- [ ] **Step 6: Verify no dangling references remain (whole-repo grep, per spec §13)**

Run: `grep -rn "docs/backlog\.md\|memory/BRIDGE\.md" "C:/Users/User/Desktop/Dietician" --include=*.md | grep -v "superpowers/specs\|superpowers/plans\|superpowers/research\|council-cache"`
Expected: no output — every live reference is updated; only frozen historical artifacts under `superpowers/` may still mention the old paths.

- [ ] **Step 7: Verify the staleness nag stays silent when curation is fresh**

`/dream` just ran (Step 3), so a `dream:` commit now exists and is recent. Re-invoke `/wrap`. In its Step 6, the nag must print NOTHING (curation is within both thresholds). Confirm no `⚠ /dream is overdue` line appears. This exercises the nag's silent path — the complement of Task 10 Step 4.

- [ ] **Step 8: Final acceptance check**

Confirm against spec §13: `/sanity` gone; the memory repo exists, `git init`'d on `main`, has a working `origin` remote, is git-ignored by the home repo; `BRIDGE-HEAD.md` + `BRIDGE-LOG.md` + `backlog.md` present in the memory repo; the whole-repo grep (Step 6) is clean; `/wrap` and `/dream` both ran clean; the watermark stayed put on the dry run (Step 2) and advanced on the real run (Step 4); the nag fired overdue (Task 10 Step 4) and stayed silent fresh (Step 7). If all hold, the implementation is complete.

---

## Self-Review

**1. Spec coverage.** §4 architecture → Tasks 7–8. §5.1 memory repo → Task 1. §5.2 backup/remote → Task 1 (Steps 6–7) + the push steps in Tasks 3/4/5/7/8. §5.3 backlog relocation → Task 4. §5.4 attribution (`wrap:`/`dream:`/`manual:` prefixes) → embedded in the skill files and every migration commit. §6 HEAD schema → Task 2 Step 3 + wrap.md. §6.1 git-derived watermark → wrap.md Steps 1/2/6, dream.md Phases 1/6. §7 LOG format → Task 3 + wrap.md Step 3. §8 `/wrap` → Task 7. §8.2 `/dream` → Task 8. §9 migration → Tasks 1–6. §10 HEAD recovery → Task 2 Step 3 (template comment) + Task 5 Step 3 (CLAUDE.md note) + dream.md Phase 3 verify. §13 acceptance → Tasks 6, 10, 11 (the crash-atomic watermark check is Task 11 Step 2; the nag paths are Task 10 Step 4 + Task 11 Step 7). §14 out-of-scope (`CronCreate`) → correctly absent. No gaps.

**2. Placeholder scan.** No "TBD"/"TODO". `<MEM>`, `<DIET>`, `<NN>`, `<date>`, `<ISO timestamp>` are runtime substitutions inside the skill files (correct — the skills run later), not plan placeholders. Concrete thresholds stated (≤~1500 words; nag at 5 sessions / 7 days; never compact the newest 5).

**3. Type consistency.** File names consistent: `BRIDGE-HEAD.md`, `BRIDGE-LOG.md`, `backlog.md`, `wrap.md`, `dream.md`. Commit-prefix vocabulary (`wrap:` / `dream:` / `manual:`) consistent across skill files and migration tasks. The subject-anchored watermark query is identical across the Watermark-model header, wrap.md Step 1, and dream.md Phase 1; Task 11 uses the matching subject-anchored `dream:`-count query. The memory-repo branch is `main` consistently.

**4. Council round 6 + 7 fixes.** Round 6: commit+push before mutation (Task 1) ✓; cross-repo move via cp/diff/rm/add (Task 4) ✓; BRIDGE-HEAD review checkpoint ✓; crash-atomic test ✓; whole-repo grep + silent-nag + gitignore-first + pinned branch ✓. Round 7: the checkpoint is now a TASK BOUNDARY — Task 2 ends at the checkpoint, Task 3 is gated on approval ✓; the watermark is git-derived — no HEAD cell, crash-atomicity by construction, verified by Task 11 Step 2 ✓; backlog task commits `<MEM>` before deleting from `<DIET>` (Task 4 Steps 5→6) ✓; feature-branch base pinned to the `master` tip with a clean-tree check (Task 4 Step 1) ✓.

**5. UI checks.** Build+mount pairing, component-reuse, and `data-testid` checks are N/A — this plan produces skill files and a storage migration, no UI.

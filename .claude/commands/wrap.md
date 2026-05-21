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

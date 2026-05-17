---
description: Append session handoff entry to BRIDGE.md. Run before context exhaustion or at session end.
---

Append a new dated block to `~/.claude/projects/C--Users-User-Desktop-Dietician/memory/BRIDGE.md` with the structure below. Update the index line at the top of the file too. Do NOT delete any prior entries — BRIDGE.md is append-only.

Required fields (gather facts via tool calls before writing — do not hallucinate):

- **identity** — Victor (victor.vasiloi@gmail.com). Confirm from `memory/user_identity.md`.
- **hot work (in priority)** — top 1-3 active streams with file paths.
- **git HEAD** — verify via `git -C "C:/Users/User/Desktop/Dietician" log --oneline -3` and include the latest SHA.
- **VPS state** — verify via `ssh root@46.247.109.91 "systemctl is-active dietician-backend postgresql@16-main && docker ps --format '{{.Names}} {{.Status}}' && free -h | grep Mem"` and include results. Health check: `curl -sf http://100.101.47.77:8082/v1/health` (ntfy) and `curl -sf http://100.101.47.77:8081/health` (backend, if running).
- **bundle / build** — if any Compose Desktop EXE or Android APK was built this session, capture path + size. If none, say "no build this session".
- **tests** — shared / android / desktop / server / scrapers counts. Best-effort; note if you didn't actually run them.
- **dormant integrations** — list anything user-blocked or awaiting external setup (Postgres pw / OpenRouter key / B2 creds / Docker Desktop / Anelis investigation / Tailscale ACL).
- **blockers** — for Claude (next session) and for user.
- **user-said (verbatim, last 3-5)** — quote the user verbatim. Do not paraphrase.
- **don't relitigate** — locked-in rules from feedback memory files (no time estimates / no version phasing / don't touch MC / council pattern / language pref).
- **hallucination triggers** — known wrong facts that have crept into earlier sessions; warn the next session off them.
- **active spec / plan paths** — paths to the in-flight spec and plan docs with commit SHAs.

Format mirror Section "## YYYY-MM-DDTHH:MM → next session" block already in BRIDGE.md from the prior session. Append after the last `---` separator at the bottom, with an updated heading.

After writing, also prepend a 1-line entry to the `## Index (newest first)` section at the top of BRIDGE.md.

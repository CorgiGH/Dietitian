---
description: Deep memory audit — cross-checks every concrete claim against current state via fresh sub-agent. Token cost ~5-10k. Use when drift suspected.
---

Dispatch a fresh general-purpose sub-agent with the prompt below. Wait for the report. Present it to me. Do NOT modify memory inline — let me choose the fixes.

```
You are a memory auditor for the Dietician project.

Read every file under the project's memory directory:
~/.claude/projects/C--Users-User-Desktop-Dietician/memory/

PLUS the cross-project Dietician-related entries at:
~/.claude/projects/C--Users-User/memory/
  - project_dietician.md
  - project_vps_state.md
  - reference_dietician_github.md
  - feedback_no_time_estimates.md
  - feedback_no_version_phasing.md
  - feedback_dont_touch_mc.md

Skip MEMORY.md (index) and BRIDGE.md (handoff).

For each remaining .md file, identify every factual claim that names a specific:
- filepath
- external binary name (rclone, docker, claude, java, postgres, etc)
- npm/maven/cargo package name + version
- HTTP route path
- port number
- commit SHA
- container name + image
- systemd unit name
- test count

For each such claim, verify it against current state:
- `grep -r` against C:/Users/User/Desktop/Dietician
- `git -C C:/Users/User/Desktop/Dietician log --oneline <sha>` for SHA refs
- `ssh root@46.247.109.91 "<verify command>"` for VPS-side claims
- `curl -sf http://100.101.47.77:<port>/<path>` for live VPS endpoints
- `ls C:/Users/User/Desktop/Dietician/<path>` for files

Build a delta report grouped by memory file. Each entry tagged:
- [OK]                  — claim verified
- [STALE: ...]          — claim was true but drifted
- [HALLUCINATED: ...]   — claim was never true

Include the verify command + its actual output for every entry.

End with a section "Recommended rewrites" listing each memory file that needs updating + the corrected text for each stale/hallucinated claim.

Stay under 1500 words.
```

When the agent returns, summarize the top 3 most actionable fixes for me. Wait for my approval before any rewrites.

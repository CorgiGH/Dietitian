# Dietician — Production Backlog

**Living doc — the sole authority for what is left and in what priority.** `BRIDGE.md`
is the chronological per-session handoff and points here; it does NOT restate the P0
list. The spec owns *what the product must do*. If BRIDGE and this file ever disagree
on what is done or what is next, **this file wins** — fix BRIDGE.

Kept current at `/wrap` (the `/wrap` command reconciles this file in the same step it
appends to BRIDGE): shipped items move to `## Done` with their PR number, new gaps get
added, the header below is refreshed.

- **Last updated:** 2026-05-20
- **master HEAD at last update:** `f8dc224` (PR #28 merged); PR #29 open + CI-green.
- **Binding spec:** `docs/superpowers/specs/2026-05-17-dietician-design.md`
- **Session handoff:** `~/.claude/projects/C--Users-User-Desktop-Dietician/memory/BRIDGE.md`

"Production" = Victor can rely on the app daily. Single-user personal app — not multi-tenant SaaS. Priority reflects that: surfaces Victor touches daily rank above enrichment/data pipelines.

---

## Done

Scaffold; Plan-1 event-sourced data ledger + sync; Plan-2 `:shared:llm` LLM router; Plan-3 `:server` Ktor backend; Plan-4-5 KMP Compose UI (all screens walkable); iter-11 Coach 2-phase commit; iter-11.5 LlmStream bridge; **magic-link desktop auth (PR #28)**; **desktop Coach via ClaudeMax CLI one-shot + dietician system prompt + ED/bigorexia safeguards (PR #29)**. App boots and is walkable end-to-end; server live on the VPS (`100.101.47.77:8081`). Compliance surfaces (AI Act audit log, GDPR redaction / DSAR / RoPA / consent, ED safeguards) are done.

---

## P0 — blocks reliable daily use

| Item | Status | Note |
|---|---|---|
| Merge PR #29 | READY | CI green |
| Client SQLDelight migration runner | NOT STARTED | the `.schema_applied` marker skips new tables (e.g. `0009_audit_pending_outbox`) on an existing client DB → silent data gaps after a schema bump |
| systemd auto-restart for the backend | PARTIAL | no restart-on-crash → backend stays down if it dies |
| Backend in-memory stores → Postgres (Plan-3.5) | PARTIAL | `SessionStore` / `MagicLinkService` / `RateLimiter` are `ConcurrentHashMap` → a backend restart logs Victor out and drops sessions |

## P1 — core features incomplete

| Item | Status | Note |
|---|---|---|
| Email delivery — Resend API key on VPS | PARTIAL | `ResendClient` coded; key unset → `NoopEmailSender`; magic-link token only via VPS log-grep |
| Receipt OCR wiring | STUB | `ReceiptUpload` route exists; file-save → ClaudeMax / Gemini Vision not wired |
| Shopping list builder (spec §20) | NOT STARTED | |
| SKU matching + price sensor-fusion (spec §9) | NOT STARTED | `price_observations` table exists; no ingest |
| WebSocket sync | STUB | server route returns 501 |
| iter-11.6 — real per-token streaming + route Coach to a non-reasoning model | DEFERRED | currently single-chunk; output clipped to 1024 tokens |
| Caveman-hook-leak isolation (`CLAUDE_CONFIG_DIR`) | FOLLOWUP (#29) | desktop Coach answers correct + safeguarded but in caveman register; see `docs/runbooks/claudemax-coach-context-isolation.md` |

## P2 — data + enrichment (Plans 6 & 7 — each needs its own council + plan)

| Item | Status |
|---|---|
| RO scraper chains — Mega / Carrefour / Auchan / Kaufland / Lidl / Bringo | STUB — `scrapers` module is a TODO, zero adapters |
| Knowledge corpus — USDA FDC + CIQUAL 2025 + Open Food Facts RO | NOT STARTED |
| BM25 + embedding index | NOT STARTED |
| Recipe ingest — article/URL, YouTube, PDF cookbook | NOT STARTED |
| Academic paper ingest — GROBID pipeline + Anelis auth + fetch-queue scheduler | STUB — GROBID on desktop, no pipeline |
| Nutrition-label OCR (spec §8.2) | NOT STARTED |

## P3 — ops, compliance hardening, future

| Item | Status | Note |
|---|---|---|
| Rotate OpenRouter + Groq API keys | DEFERRED | keys crossed chat; user chose "leave to the very end" |
| Automate tmpfs passphrase recreate on VPS reboot | PARTIAL | manual today (`/run/dietician-keys/*.passphrase`, wiped on reboot) |
| Tailscale ACL fragment deploy (spec §2) | NOT APPLIED | |
| Backup — rclone OneDrive remote init + restore test | PARTIAL | `BackupCron` coded; remote not set up |
| Passkey / WebAuthn auth (Plan-3.5) | NOT STARTED | `webauthn_credentials` table unused |
| Android Coach client wiring | PARTIAL | server path ready (iter-11.5); Android client pending |
| 5 dormant integrations — smart scale, training log, sleep, HRV, activity feed | FUTURE | architectural seats reserved, spec §10 |
| Parser cache-token under-reporting; real-`claude`-binary automated test | FOLLOWUP (#29) | audit under-reports prompt size on the cost=0 path; integration test uses a stub script |
| Small UI: Home empty-state copy; `ReceiptUpload` file-save wiring | MINOR | |

---

## Suggested sequence

1. Merge PR #29.
2. P0 — the four items are the reliability floor; do them before new features.
3. P1 — in spec order.
4. P2 — Plans 6 and 7; convene the 5-agent council before each plan (project rule).
5. P3 — opportunistic; key rotation last per the user's instruction.

## Maintenance

- When an item ships, move it to **Done** with the PR number — do not just delete it.
- When a new gap is found, add it under the right priority with a status tag (`NOT STARTED` / `PARTIAL` / `STUB` / `FOLLOWUP` / `DEFERRED`).
- Refresh the **Last updated** line and **master HEAD** at every `/wrap`.
- Council transcripts that drove decisions live in `.claude/council-cache/` (gitignored).

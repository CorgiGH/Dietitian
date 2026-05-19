# Runbook — First VPS deploy of dietician-backend.service

**Source of truth:** Plan-3 post-impl council 1779073963 + spec §A18 + Plan-3 systemd unit (in-repo source: [`infra/systemd/dietician-backend.service`](../../infra/systemd/dietician-backend.service); deployed at `/etc/systemd/system/dietician-backend.service`) + council 1779188964 (deploy scoping + heap caps).

**Symptom / trigger:** master has plan-1 + plan-2 + plan-3 + plan-4-5 merged + the server JAR is ready to ship to `46.247.109.91`. `dietician-backend.service` is currently `inactive (dead)` because `/opt/dietician/lib/` has no JAR yet.

**Scope of this runbook (council 1779188964 ruling):** This is the **tracer-bullet first deploy** — the smallest cut that retires the most risk. Goal: JAR + Postgres password + Tailscale bind + `/health` reachable + one end-to-end write round-trip (POST `/sync/push` from desktop → row in Postgres → desktop pull confirms shape). **Resend (magic-link real), OpenRouter (Coach real), Rclone (backup cron) are STUB-OK for this deploy** — each lands in its own follow-up smoke session. The router and email/cron DI gracefully degrade when env keys are absent (see "Stub-mode envs" below).

**Key invariants:**
- App-user password lives ONLY in `/run/dietician-keys/db.passphrase` (tmpfs). Set at first deploy. NOT in `/etc/dietician/env`.
- JVM heap is bounded in the systemd unit (`-Xms128m -Xmx384m`, `MemoryMax=512M`, `MemoryHigh=400M`) AND the unit has `OOMScoreAdjust=500` so the kernel prefers killing dietician-backend before the Minecraft co-tenant (locked at 4G, default OOMScoreAdjust=0). Council 1779188964 R1 (CRITICAL).
- Bind host is auto-discovered via `tailscale ip -4` subprocess in `TailnetDiscovery` (see `server/src/main/kotlin/com/dietician/server/Main.kt:25-31`). Override with env `DIETICIAN_HOST_OVERRIDE=<ip>` for dev / CI / unit tests. Port: `DIETICIAN_PORT` env, default 8081.
- Resend API key (magic-link email transport, free tier 100/day) → STUB-OK if blank; magic-link request still returns 202 + `emailSent=true` but no email actually fires (see "Stub-mode envs").
- OneDrive-crypt remote → STUB-OK if `DIETICIAN_DISABLE_INJVM_CRONS=true`; backup cron stays dormant.

---

## Stub-mode envs (council 1779188964 R2 — tracer-bullet first deploy)

The server DI gracefully degrades when these env vars are missing or blank — the JVM boots, `/health` returns 200, and routes that need the missing dependency either stub or 503. This means **most** services can be deferred to follow-up deploys without blocking the first start:

| Env var | Missing/blank behavior | DI hook |
|---|---|---|
| `RESEND_API_KEY` | `NoopEmailSender` — magic-link `request` still 202s + `emailSent=true`, but no email is actually sent. Token row still lands in `magic_link_tokens` and can be SQL-grepped. | `DieticianModule.kt:77-84` |
| `OPENROUTER_API_KEY` / `GROQ_API_KEY` | `llmAdaptersOnlyModule` instead of `llmModule` — Coach route returns 503 / stub, server still boots | `Application.kt:57-66` |
| `DIETICIAN_DISABLE_INJVM_CRONS=true` | AuditPruneCron + BackupCron both skip schedule — `rclone` is never invoked even if remote is unconfigured | `Application.kt:138-144` |
| `RCLONE_CONFIG_PASS_FILE` | If crons disabled (above), this is unused. If crons enabled and value missing, BackupCron fails per-tick (audit-logged + rethrown to cron loop); server stays up. | `BackupCron.kt` |

**Tracer-bullet env is in Step 2 below.** The round-trip in Step 8 requires a session cookie, which requires magic-link verify, which is most painless when `RESEND_API_KEY` is set (free Resend tier). The SQL-grep fallback path works without Resend but adds operator friction every smoke.

---

## Pre-deploy checklist (user-side)

- [x] PR #1 (plan-1 → master), PR #2 (plan-3 → master), PR #3 (plan-2 → master), PR #11 + iter-N PRs (plan-4-5 → master) all merged 2026-05-18 / 2026-05-19.
- [ ] Postgres app-user password chosen (32+ chars, password-manager stored).
- [ ] Resend account created at https://resend.com/signup. Free tier API key copied. From-address provisioned (`onboarding@resend.dev` works without a verified domain). _Skippable only if you prefer the SQL-grep workaround in Step 7 path (b)._
- [ ] (Deferred to follow-up deploy) OneDrive-crypt rclone remote per `rclone-onedrive-crypt-setup.md`.
- [ ] (Deferred to follow-up deploy) OpenRouter API key at `openrouter.ai/keys`. Coach stays stubbed until this lands.
- [ ] Tailscale ACL fragment applied at `login.tailscale.com/admin/acls` (allowing the dietician-backend tag — see spec §2).

---

## Step 1 — set the Postgres app-user password

SSH into VPS as root:

```bash
ssh root@46.247.109.91
```

Set the password (interactive, type when prompted):

```bash
sudo -u postgres psql <<EOF
ALTER USER dietician WITH PASSWORD '<paste-strong-passphrase>';
EOF
```

Write the SAME passphrase to tmpfs so the backend can read it at startup:

```bash
sudo mkdir -p /run/dietician-keys
sudo mount -t tmpfs -o size=10m,mode=0700 tmpfs /run/dietician-keys
echo -n '<paste-strong-passphrase>' | sudo tee /run/dietician-keys/db.passphrase > /dev/null
sudo chmod 0600 /run/dietician-keys/db.passphrase
sudo chown dietician:dietician /run/dietician-keys/db.passphrase
```

(The tmpfs mount is wiped on reboot — `restart.md` covers re-mount + re-write on every restart.)

---

## Step 2 — populate /etc/dietician/env

The env.example template ships these fields. Copy + fill secrets:

```bash
sudo cp /etc/dietician/env.example /etc/dietician/env
sudo chown root:dietician /etc/dietician/env
sudo chmod 0640 /etc/dietician/env
sudo nano /etc/dietician/env
```

**Tracer-bullet first deploy — minimum env (council 1779188964 R2):**
```
PG_PASSWORD=<paste-strong-passphrase>           # same as Postgres ALTER USER
RESEND_API_KEY=<your-resend-key>                # magic-link email (free tier 100/day)
RESEND_FROM=<noreply@your-domain.com>           # or onboarding@resend.dev (Resend default)
DIETICIAN_DISABLE_INJVM_CRONS=true              # skip audit-prune + backup cron until rclone configured
# OPENROUTER_API_KEY=                           # blank → llmAdaptersOnlyModule, Coach stays stub
# GROQ_API_KEY=                                 # blank → llmAdaptersOnlyModule
# RCLONE_CONFIG_PASS_FILE=                      # unused when DIETICIAN_DISABLE_INJVM_CRONS=true
```

**Why Resend is required for tracer-bullet (despite council R2 deferring "magic-link real"):**
`/sync/push` requires a valid session cookie (see `SyncRoutes.kt:63` `requireSubject`). The round-trip that proves Plan-3↔Plan-4-5 DTO alignment cannot run anonymously. Two paths to a session:
- **(a) Set RESEND_API_KEY** → real magic-link email arrives → click → desktop client gets cookie. ~5 min Resend signup, free.
- **(b) Leave RESEND_API_KEY blank** → NoopEmailSender no-ops the send → server still inserts a row in `magic_link_tokens` → operator must SQL-grep the latest token and manually POST `/auth/magic-link/verify` with it. Doable but adds friction every smoke.

Path (a) is recommended. The strict council R2 deferral of Resend assumed magic-link-real means "Victor opens his Gmail and clicks the link" — that's still the case here, but the alternative (path b) is more painful than just setting one env var.

**Follow-up deploys** (each its own contained smoke session — do NOT bundle):
1. OpenRouter + Groq → Coach real responses (set `OPENROUTER_API_KEY` + `GROQ_API_KEY`, restart, smoke `/coach/stream`).
2. Rclone OneDrive-crypt → backup cron (configure rclone per `rclone-onedrive-crypt-setup.md`, set `RCLONE_CONFIG_PASS_FILE=/run/dietician-keys/rclone.passphrase`, remove `DIETICIAN_DISABLE_INJVM_CRONS`, restart, force-tick via `BACKUP_TEST_INTERVAL_MIN=5`).
3. ntfy push wiring + Tailscale ACL refinement.

If `RESEND_API_KEY`, `RESEND_FROM`, `RCLONE_CONFIG_PASS_FILE` are missing from env.example, add them — Plan-3 introduced them but env.example dates from Session 1. (Backlog: update env.example to match.)

Reload the systemd unit so the env file is re-read:
```bash
sudo systemctl daemon-reload
```

---

## Step 3 — build + ship the fat JAR

On the dev machine:

```bash
cd C:/Users/User/Desktop/Dietician
./gradlew :server:buildFatJar
ls -lh server/build/libs/dietician-server.jar
# Expected: ~40-60 MB
```

scp to the VPS:

```bash
scp server/build/libs/dietician-server.jar root@46.247.109.91:/tmp/
ssh root@46.247.109.91 "sudo mv /tmp/dietician-server.jar /opt/dietician/lib/ && sudo chown dietician:dietician /opt/dietician/lib/dietician-server.jar && sudo chmod 0644 /opt/dietician/lib/dietician-server.jar"
```

Verify on VPS:
```bash
ssh root@46.247.109.91 "ls -la /opt/dietician/lib/dietician-server.jar"
# Expected: -rw-r--r-- 1 dietician dietician 40M+ ... dietician-server.jar
```

---

## Step 3.5 — install / refresh the systemd unit (council 1779188964 R1)

The source-of-truth unit lives in the repo at [`infra/systemd/dietician-backend.service`](../../infra/systemd/dietician-backend.service). Copy it into place on every deploy that changes JVM flags, env file location, or cgroup caps:

```bash
scp infra/systemd/dietician-backend.service root@46.247.109.91:/tmp/
ssh root@46.247.109.91 "sudo install -m 0644 -o root -g root /tmp/dietician-backend.service /etc/systemd/system/ && sudo systemctl daemon-reload"
```

**What the unit guarantees (council 1779188964 R1 — CRITICAL mitigations):**
- JVM heap bounded: `-Xms128m -Xmx384m -XX:+UseG1GC -XX:MaxGCPauseMillis=200`.
- cgroup memory cap: `MemoryHigh=400M` (throttle) + `MemoryMax=512M` (hard kill).
- `OOMScoreAdjust=500` — under host-wide memory pressure the kernel picks dietician-backend before Minecraft (locked at 4G, default `OOMScoreAdjust=0`). This is defense-in-depth on top of the cgroup cap.
- `Requires=postgresql.service` + `After=network-online.target` — backend won't even try to start if Postgres is down.
- Restart policy: `on-failure` with 10s backoff (not `always` — avoids tight restart loops on systematic failure).

Verify the unit was loaded:
```bash
ssh root@46.247.109.91 "systemctl cat dietician-backend | grep -E 'MemoryMax|OOMScoreAdjust|Xmx'"
# Expected lines from the repo unit visible.
```

---

## Step 4 — enable + start

```bash
sudo systemctl enable dietician-backend.service     # idempotent — sets it to start on next boot
sudo systemctl start dietician-backend.service
sudo systemctl status dietician-backend.service
# Expected: active (running).
```

Tail the logs for startup:
```bash
sudo tail -f /opt/dietician/logs/backend.log
# Watch for:
#  - "Flyway: 8 migrations applied" (V013-V020 first-batch)
#  - "Ktor: server listening on 100.101.47.77:8081"
#  - "BackupCron: scheduled with interval=24h"
# Ctrl-C to exit the tail.
```

---

## Step 5 — health smoke

```bash
curl -sf http://100.101.47.77:8081/health | jq .
# Expected (tracer-bullet first deploy with DIETICIAN_DISABLE_INJVM_CRONS=true):
# {
#   "service": "dietician-backend",
#   "version": "...",
#   "spec_date": "2026-05-17",
#   "status": "ok"
# }
# Note: extended /health fields (postgres_ok, tombstone_grace_stale_count,
# last_backup_at) ship in a follow-up deploy once HealthRepository is wired
# end-to-end; the top-level `status: ok` is the tracer-bullet criterion.
```

`last_backup_at` lands when (a) `DIETICIAN_DISABLE_INJVM_CRONS` is unset and (b) the first cron tick fires (default daily at 04:30). To force a faster tick for smoke purposes once you re-enable crons: set `BACKUP_TEST_INTERVAL_MIN=5` in `/etc/dietician/env`, restart the service, then watch `/health.last_backup_at` populate within 5 minutes. For the first deploy, leave both unset — backup proving comes in the rclone follow-up deploy.

---

## Step 6 — Tailscale ACL verify

From the dev machine (which is on the tailnet via dell-g5 Tailscale node `100.80.132.115`):

```bash
curl -sf https://dietician-vps.<your-tailnet>.ts.net/health | jq .
# Expected: same JSON as Step 5, served over TLS by Tailscale's MagicDNS+TLS.
```

If this returns 403 / connection-refused: the Tailscale ACL fragment isn't applied. Open `login.tailscale.com/admin/acls` and add the fragment from spec §2 under `tag:dietician-backend`.

---

## Step 7 — magic-link sign-in (Victor)

```bash
# Trigger a magic-link email to Victor's address (replace if different):
curl -X POST http://100.101.47.77:8081/auth/magic-link/request \
  -H "Content-Type: application/json" \
  -d '{"email":"victor.vasiloi@gmail.com"}'
# Expected: 202 Accepted with body {"emailSent": true}.
```

Check Gmail for the magic-link email. Click → should land on a deep-link to the desktop or Android app (whichever is registered as the URL handler) OR open the web magic-link verifier page. The desktop client should now hold a session cookie (verify in the app's network tab or by inspecting `~/.dietician/cookies` if persisted).

In Resend's dashboard, confirm the email shows `delivered` status within 30 seconds.

**If RESEND_API_KEY was left blank** (path (b) from "Why Resend is required" above):
```bash
# Find the latest unconsumed token via SQL:
sudo -u postgres psql -d dietician -c \
  "SELECT token FROM magic_link_tokens WHERE consumed_at IS NULL ORDER BY created_at DESC LIMIT 1;"
# Then verify it manually:
curl -X POST http://100.101.47.77:8081/auth/magic-link/verify \
  -H "Content-Type: application/json" \
  -d '{"token":"<paste-token>"}' \
  -c /tmp/dietician-session.txt
# Expected: 200 OK + Set-Cookie: dietician_session=... (saved to /tmp/dietician-session.txt).
```

---

## Step 8 — tracer-bullet write round-trip (council 1779188964 ruling)

The end-to-end test that exercises the Plan-3↔Plan-4-5 DTO seam. Send one synthetic event via POST /sync/push, verify it lands in Postgres, then GET /sync/pull to confirm it round-trips with the same shape.

**8a. Push one event** (uses cookie from Step 7 — replace `<COOKIE>` with the value of `dietician_session` from the magic-link verify response):

```bash
curl -X POST http://100.101.47.77:8081/sync/push \
  -H "Content-Type: application/json" \
  -H "Cookie: dietician_session=<COOKIE>" \
  -d '{
    "deviceId": "tracer-bullet-smoke",
    "events": [
      {
        "eventUuid": "11111111-1111-1111-1111-111111111111",
        "tableName": "food_log_events",
        "payloadJson": "{\"hlc\":\"2026-05-19T00:00:00Z|00000000\",\"food_name\":\"tracer\",\"kcal\":100,\"protein_g\":10,\"carbs_g\":5,\"fat_g\":3}"
      }
    ]
  }'
# Expected: 200 OK with body
# {"accepted":[{"eventUuid":"11111111-...","serverRecvAt":...}],"rejected":[]}
```

**8b. Confirm the row landed in Postgres:**

```bash
ssh root@46.247.109.91 "sudo -u postgres psql -d dietician -c \
  \"SELECT event_uuid, table_name, subject_id, server_recv_at FROM event_ledger WHERE event_uuid = '11111111-1111-1111-1111-111111111111';\""
# Expected: 1 row, subject_id = Victor's UUID, table_name = food_log_events, server_recv_at populated.
```

**8c. Pull it back** (round-trip):

```bash
curl -sf "http://100.101.47.77:8081/sync/pull?table=food_log_events" \
  -H "Cookie: dietician_session=<COOKIE>" | jq .
# Expected: a "rows" array containing the synthetic event with same eventUuid + payload.
# The "cursor" field in the response is what a real client would persist for resumption.
```

**8d. STOP.** Tracer-bullet round-trip green = deploy is shipped. Mark the following as deferred to follow-up deploys:
- Coach real responses (needs OPENROUTER_API_KEY + GROQ_API_KEY)
- Receipt OCR upload pipeline (needs ClaudeMax CLI subprocess + S3-equivalent)
- Backup cron + OneDrive-crypt (needs rclone configured + DIETICIAN_DISABLE_INJVM_CRONS removed)
- ntfy push delivery (needs Tailscale ACL refinement)

Do NOT iterate on UI (plan-4-5 iter-11+) until the round-trip is green — per council 1779188964 R3, additional UI iterations against stubs accrue DTO drift that this round-trip is the only signal for.

---

## Failure modes

**Service stays `activating (auto-restart)` with no log lines:**
- Most likely `/run/dietician-keys/db.passphrase` is missing or unreadable by the `dietician` user. Check `ls -la /run/dietician-keys/`. Re-run Step 1.

**Backend logs `FATAL: password authentication failed for user "dietician"`:**
- The Postgres `ALTER USER` and the tmpfs file have diverged. Re-run Step 1 with the SAME passphrase in both places.

**Backend logs `FATAL: Flyway migration 'V013' failed`:**
- A migration touches an extension that isn't installed. Verify on VPS:
  ```bash
  sudo -u postgres psql -d dietician -c "SELECT extname, extversion FROM pg_extension;"
  # Expected: vector + pgcrypto + pg_trgm all present.
  ```
- Install missing: `sudo -u postgres psql -d dietician -c "CREATE EXTENSION IF NOT EXISTS pgvector;"`.

**`curl /health` returns 502 / connection refused:**
- The backend bound to the wrong host. `TailnetDiscovery` runs `tailscale ip -4` on the VPS at startup; if Tailscale is down the discovery falls back to refuse-to-start (per `Main.kt:25-31` + council 1779120000 RC5). Verify `ssh root@46.247.109.91 "tailscale ip -4"` returns `100.101.47.77`.
- To override the auto-discovery (e.g. for a `127.0.0.1` smoke test), set `DIETICIAN_HOST_OVERRIDE=127.0.0.1` in `/etc/dietician/env`. Port comes from `DIETICIAN_PORT` (default 8081).

**Service refuses to start with "TailnetDiscovery: no IPv4 from `tailscale ip -4`" banner:**
- Tailscale daemon is down on VPS. Restart it: `ssh root@46.247.109.91 "sudo systemctl restart tailscaled"`. Retry `tailscale ip -4` until it returns `100.101.47.77`.
- If Tailscale is intentionally off (debugging localhost), set `DIETICIAN_HOST_OVERRIDE=127.0.0.1` in env file.

**Resend returns 401:**
- API key wrong or revoked. Verify in Resend dashboard. Regenerate + re-paste into `/etc/dietician/env`. Restart the service.

**Backend log `BackupCron: rclone exec failed`:**
- See `rclone-onedrive-crypt-setup.md` Step 4 smoke test. The cron uses the same config as the smoke test.

---

## Rollback

If the first start is unrecoverable in <10 minutes of debugging:

```bash
sudo systemctl stop dietician-backend.service
sudo systemctl disable dietician-backend.service
# State is now "no JAR running, no auto-start on boot". The VPS Postgres + ntfy + MC + jarvis-web are untouched.
```

The next deploy attempt can repeat Steps 3-4 with a corrected JAR / corrected env.

---

## Council references
- Plan-3 RC4: BackupCron is in-JVM (process-spawn for rclone), NOT a systemd timer.
- Plan-3 RC10: tmpfs key unlock pattern via `dietician-key-unlock.service` (manual on first deploy + every reboot — see `restart.md`).
- Plan-3 RC11: restore.md + this runbook + rclone-onedrive-crypt-setup.md + pg-dump-auth.md collectively demonstrate GDPR Art 32 restorability.
- §A18: backup destination = UAIC OneDrive 1TB.
- **Council 1779188964 (deploy scoping + heap caps):** runbook reframed around tracer-bullet first slice; JVM heap caps + `MemoryMax=512M` + `OOMScoreAdjust=500` (MC-protect) mandatory in systemd unit; in-repo source of truth at `infra/systemd/dietician-backend.service`; OpenRouter / Coach / Rclone / B2 / ntfy deferred to follow-up deploys.

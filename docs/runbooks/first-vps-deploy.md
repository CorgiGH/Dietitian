# Runbook — First VPS deploy of dietician-backend.service

**Source of truth:** Plan-3 post-impl council 1779073963 + spec §A18 + Plan-3 systemd unit (`/etc/systemd/system/dietician-backend.service`).

**Symptom / trigger:** master has plan-3 merged + the server JAR is ready to ship to `46.247.109.91`. `dietician-backend.service` is currently `inactive (dead)` because `/opt/dietician/lib/` has no JAR yet.

**Key invariants:**
- App-user password lives ONLY in `/run/dietician-keys/db.passphrase` (tmpfs). Set at first deploy. NOT in `/etc/dietician/env`.
- Resend API key is the magic-link email transport. Free tier (100 emails/day) covers all of Victor + 4 friends.
- OneDrive-crypt remote (see `rclone-onedrive-crypt-setup.md`) must be configured before the first backup tick fires.

---

## Pre-deploy checklist (user-side)

- [ ] PR #1 (plan-1 → master), PR #2 (plan-3 → master) both merged. PR #3 + #4 are merge-AFTER candidates and don't block this deploy.
- [ ] Postgres app-user password chosen (32+ chars, password-manager stored).
- [ ] Resend account created at https://resend.com/signup. Free tier API key copied. From-address provisioned (`noreply@<your-domain>` or use Resend's testing domain).
- [ ] OneDrive-crypt rclone remote configured per `rclone-onedrive-crypt-setup.md`. Verified via `rclone lsd onedrive-crypt:`.
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

Required edits:
```
PG_PASSWORD=<paste-strong-passphrase>           # same as Postgres ALTER USER
OPENROUTER_API_KEY=<your-openrouter-key>        # from openrouter.ai/keys
RESEND_API_KEY=<your-resend-key>                # NEW for Plan-3 magic-link email
RESEND_FROM=<noreply@your-domain.com>           # NEW for Plan-3
RCLONE_CONFIG_PASS_FILE=/run/dietician-keys/rclone.passphrase   # NEW for Plan-3 backup
```

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
# Expected:
# {
#   "status": "ok",
#   "postgres_ok": true,
#   "tombstone_grace_stale_count": 0,
#   "last_backup_at": null,
#   ...
# }
```

`last_backup_at` is null until the first cron tick (24h after first start). To force a faster tick for smoke purposes, set `BACKUP_TEST_INTERVAL_MIN=5` in `/etc/dietician/env`, restart the service, then watch `/health.last_backup_at` populate within 5 minutes.

---

## Step 6 — Tailscale ACL verify

From the dev machine (which is on the tailnet via dell-g5 Tailscale node `100.80.132.115`):

```bash
curl -sf https://dietician-vps.<your-tailnet>.ts.net/health | jq .
# Expected: same JSON as Step 5, served over TLS by Tailscale's MagicDNS+TLS.
```

If this returns 403 / connection-refused: the Tailscale ACL fragment isn't applied. Open `login.tailscale.com/admin/acls` and add the fragment from spec §2 under `tag:dietician-backend`.

---

## Step 7 — first user (Victor) end-to-end

```bash
# Trigger a magic-link email to Victor's address (replace if different):
curl -X POST http://100.101.47.77:8081/auth/magic-link \
  -H "Content-Type: application/json" \
  -d '{"email":"victor.vasiloi@gmail.com"}'
# Expected: 202 Accepted (queued for Resend).
```

Check Gmail for the magic-link email. Click → should land on a deep-link to the desktop or Android app (whichever is registered as the URL handler) OR open the web magic-link verifier page.

In Resend's dashboard, confirm the email shows `delivered` status within 30 seconds.

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

**`curl /health` returns 502:**
- The backend is up but the JAR didn't bind to `0.0.0.0` or to the Tailscale IP. Check `/etc/dietician/env` `DIETICIAN_BIND` value — should be `100.101.47.77` (the Tailscale IP), not `127.0.0.1`.

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

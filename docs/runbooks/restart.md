# Runbook — Dietician VPS Restart (post-reboot recovery)

**Source of truth:** Plan-3 Task 9 + Council 1779120000 RC10.

**Symptom:** VPS rebooted (planned or unplanned). The backend cannot start because the pgcrypto / database passphrase lives ONLY in tmpfs (`/run/dietician-keys/`) which is wiped on reboot. Operator must re-unlock manually post-SSH.

**Key invariant:** the master passphrase NEVER touches disk. If anything in this runbook suggests writing the passphrase to a file other than `/run/dietician-keys/*` on tmpfs, that change is a security regression.

---

## Restart sequence

1. **SSH to VPS.**

2. **Verify backend status** (expect `inactive (dead)` or `failed` since the key is missing):
   ```bash
   sudo systemctl status dietician-backend.service
   ```

3. **Re-establish Tailscale** (if not auto-started):
   ```bash
   tailscale status
   # If down:
   sudo tailscale up
   ```

4. **Probe baseline health** (should return 502/connection-refused — backend not yet up):
   ```bash
   curl https://dietician-vps.<tailnet-name>.ts.net/health
   ```
   If this returns 200 already, the previous reboot recovered the tmpfs from a snapshot — skip to step 8.

5. **Mount tmpfs for keys** (idempotent — `mount` no-ops if already mounted):
   ```bash
   sudo mkdir -p /run/dietician-keys
   sudo mount -t tmpfs -o size=10m,mode=0700 tmpfs /run/dietician-keys
   ```

6. **Unlock + load the master key:**
   ```bash
   sudo systemctl start dietician-key-unlock.service
   # This prompts for the master passphrase ONCE (interactive — operator types it now).
   # The service decrypts /etc/dietician/age-identity.age and writes:
   #   /run/dietician-keys/age-identity.txt   (Plan-3 age key)
   #   /run/dietician-keys/db.passphrase      (Postgres app-user password)
   #   /run/dietician-keys/credentials.passphrase  (pgcrypto BYOK passphrase)
   ```

   Verify:
   ```bash
   sudo ls -la /run/dietician-keys/
   # Expected files present, mode 0600 each.
   ```

7. **Start the backend:**
   ```bash
   sudo systemctl start dietician-backend.service
   sudo systemctl status dietician-backend.service
   # Expected: active (running).
   ```

8. **Smoke checks:**
   ```bash
   curl https://dietician-vps.<tailnet-name>.ts.net/health | jq .
   # Expected: {"status":"ok","postgres_ok":true,"tombstone_grace_stale_count":0,...}
   ```

9. **Victor-only deep diag** (verify queue depths nominal post-restart):
   ```bash
   curl -b "dietician_session=<JWT>" https://dietician-vps.<tailnet-name>.ts.net/diag | jq .
   ```

10. **Cohort notification** (optional, only for unplanned reboots > 10 minutes):
    Post in the friends group chat that the backend is back online and any sync attempts during the outage will succeed now via the outbox replay path.

---

## Failure modes

**Master passphrase forgotten / wrong:**
- The key unlock service fails. The recovery path is the offline-printed `docs/private/master-key-recovery-card.pdf` (Plan-3.5 will formalise this; currently the master key exists only in Victor's password manager).
- If neither source is recoverable: the `pg_dump` backups on OneDrive-crypt are still readable (they were encrypted with the rclone-crypt password, NOT the master key — see `docs/runbooks/onedrive-backup-setup.md`). The DATABASE rows that pgcrypto-encrypted with the master key (BYOK credentials, UAIC SAML cookies) are unrecoverable. Friends must re-provision their BYOK keys; UAIC SAML re-login required.

**Tmpfs mount fails:**
- Possible cause: `/run` is itself a tmpfs on most distros, so a sub-mount is unusual. Some hardened distros refuse nested tmpfs mounts. Fall back: `sudo mount -t ramfs ramfs /run/dietician-keys` and document the change in this file.

**Backend cannot connect to Postgres despite key loaded:**
- Check `/run/dietician-keys/db.passphrase` matches Postgres's app-user password (`sudo -u postgres psql -c "ALTER USER dietician_app PASSWORD ...;"` if password drifted).
- See `docs/runbooks/postgres-conn-refused.md`.

---

## Council 1779120000 references
- RC10: this runbook shipped with Task 9 (`DatabaseFactory.readPassword()` reads from `/run/dietician-keys/db.passphrase`).
- Risk Analyst FM-6: VPS reboot procedure becomes manual at 5 users — acceptable trade-off vs persistent-on-disk key.

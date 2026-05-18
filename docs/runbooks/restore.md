# Runbook — Dietician Database Restore (OneDrive-crypt → pg_restore)

**Source of truth:** Plan-3 Task 35 + Council 1779120000 RC11.
**Replaces:** previous Backblaze-B2 generic restore (deprecated 2026-05-18 — backup target is now UAIC OneDrive 1TB via `rclone crypt` per §A18).

**Symptom:** Lost / corrupted production Postgres on VPS. Need to restore from the most recent nightly OneDrive-crypt backup.

**Pre-requisites:**
- SSH into VPS (or new VPS provisioned to the same Tailscale tailnet with `tag:dietician-backend`).
- `rclone` installed + `onedrive-crypt:` remote configured per `docs/runbooks/rclone-onedrive-crypt-setup.md`.
- Operator-typed passphrase for the tmpfs key unlock (see `docs/runbooks/restart.md`).

---

## Restore steps

1. **Identify the most recent backup:**
   ```bash
   rclone ls onedrive-crypt:dietician-backups/ | sort -r | head -5
   ```
   Output example:
   ```
   123456789 2026-05-17T040000Z.dump.zst
   123456111 2026-05-16T040000Z.dump.zst
   ...
   ```

2. **Download the chosen backup to local tmpfs:**
   ```bash
   rclone copy onedrive-crypt:dietician-backups/2026-05-17T040000Z.dump.zst /tmp/
   ```

3. **Decompress:**
   ```bash
   unzstd /tmp/2026-05-17T040000Z.dump.zst
   # produces /tmp/2026-05-17T040000Z.dump
   ```

4. **Sanity-verify the dump is readable:**
   ```bash
   pg_restore --list /tmp/2026-05-17T040000Z.dump | wc -l
   # Expected: > 100 (varies; non-zero is the smoke check)
   ```

5. **Stop the backend service:**
   ```bash
   sudo systemctl stop dietician-backend
   ```

6. **Drop + recreate the database:**
   ```bash
   sudo -u postgres psql <<EOF
   DROP DATABASE IF EXISTS dietician;
   CREATE DATABASE dietician OWNER dietician_app;
   \c dietician
   CREATE EXTENSION pgvector;
   CREATE EXTENSION pgcrypto;
   CREATE EXTENSION pg_trgm;
   EOF
   ```

   Plan-1 V001-V012 + Plan-3 V013-V020 migrations re-apply automatically on next backend start via Flyway. If a restore should NOT re-run migrations (e.g. mid-migration corruption), set `DIETICIAN_FLYWAY_BASELINE=true` before starting the backend.

7. **Restore the dump:**
   ```bash
   sudo -u postgres pg_restore -d dietician /tmp/2026-05-17T040000Z.dump
   ```

8. **Row-count verification:**
   ```bash
   sudo -u postgres psql -d dietician <<'EOF'
   SELECT 'pantry_events' AS t, count(*) FROM pantry_events
   UNION ALL SELECT 'meal_events', count(*) FROM meal_events
   UNION ALL SELECT 'weight_events', count(*) FROM weight_events
   UNION ALL SELECT 'receipt_events', count(*) FROM receipt_events
   UNION ALL SELECT 'audit_log', count(*) FROM audit_log
   UNION ALL SELECT 'subjects', count(*) FROM subjects
   UNION ALL SELECT 'consent_records', count(*) FROM consent_records;
   EOF
   ```
   Compare to the pre-incident `/diag` output you (hopefully) captured. Counts should match ± in-flight delta during the outage window.

9. **Restart the backend:**
   ```bash
   # Re-unlock the tmpfs key first (see docs/runbooks/restart.md).
   sudo systemctl start dietician-backend
   ```

10. **Smoke checks:**
    ```bash
    curl https://dietician-vps.<tailnet>.ts.net/health | jq .
    # Expected: {"status":"ok","postgres_ok":true,"tombstone_grace_stale_count":0,...}

    # Victor-only diag for deeper aggregates:
    curl -b "dietician_session=<JWT>" https://dietician-vps.<tailnet>.ts.net/diag | jq .
    ```

11. **Notify cohort:** post in the friends group chat that the restore is complete + the data freshness (last successful backup timestamp). Apologise for any in-flight events lost between the backup and the incident.

---

## Council 1779120000 references
- RC11: this runbook shipped with Task 35 as GDPR Art 32 demonstrated-restorability evidence.
- RC4: backup cron runs in-JVM, not systemd. If the cron itself is suspected stuck, see `/health` field `last_backup_at` for the most recent successful emission.

## Periodic drill
Run this restore against a throwaway database (`dietician_restore_drill`) every 90 days to verify the rclone path + age-key + Postgres extension set still work end-to-end. Log the drill in `audit_log` with `action='restore_drill_completed'`.

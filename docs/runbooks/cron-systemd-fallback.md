# Runbook — Cron systemd fallback (documented-fallback only — NOT ENABLED IN PROD)

**Source of truth:** Plan-3 Tasks 33-36 + Council 1779120000 RC4.

**STATUS:** NOT ENABLED in production. The Dietician backend ships in-JVM cron scheduling via `com.dietician.server.cron.CronBootstrap.installCrons()` (Task 36). This file preserves the systemd `.service` + `.timer` snippets ONLY so a future operator can fall back if the in-JVM scheduler hits an unrecoverable bug. If you find yourself running these `systemctl enable` commands in production, also disable the in-JVM scheduler (set env `DIETICIAN_DISABLE_INJVM_CRONS=true`) — otherwise both paths fire and the audit-log emission doubles, polluting user-exported PDF history (Risk Analyst FM-3).

---

## Why in-JVM is preferred

Per Council 1779120000 RC4:

1. **AI Act Art 12 compliance.** In-JVM crons emit `AuditAction.AUDIT_PRUNE_COMPLETED` / `BACKUP_COMPLETED` / `ED_DETECTOR_*` rows via `AuditLogWriter`. The systemd-psql path below is raw SQL with NO audit emission. Running both simultaneously gives duplicate audit rows (which the user-exported PDF then shows as duplicates).
2. **Restart resilience.** `CronScheduler.daily()` recomputes the next-run-time from `now()` on JVM restart — no missed fires after a backend redeploy.
3. **One-place ops.** Operators inspect `journalctl -u dietician-backend` for cron status, NOT `systemctl list-timers --all | grep dietician`.

---

## Fallback procedure (if in-JVM scheduler ever fails)

1. **Disable in-JVM crons:**
   ```bash
   sudo systemctl edit dietician-backend.service
   # Add under [Service]:
   # Environment=DIETICIAN_DISABLE_INJVM_CRONS=true
   sudo systemctl restart dietician-backend
   ```

2. **Install the systemd units** (from the snippets below):
   ```bash
   sudo nano /etc/systemd/system/dietician-audit-prune.service   # paste snippet
   sudo nano /etc/systemd/system/dietician-audit-prune.timer     # paste snippet
   sudo nano /etc/systemd/system/dietician-backup.service        # paste snippet
   sudo nano /etc/systemd/system/dietician-backup.timer          # paste snippet
   sudo systemctl daemon-reload
   sudo systemctl enable --now dietician-audit-prune.timer dietician-backup.timer
   sudo systemctl list-timers --all | grep dietician
   ```

3. **Open a Plan-3.x fix-up ticket** describing the in-JVM failure mode + linking the systemd activation commit. Council 1779120000 explicitly rejected dual cron paths in production — the systemd fallback is meant to be temporary.

---

## Snippets (paste-source for fallback)

### `dietician-audit-prune.service`
```ini
[Unit]
Description=Dietician audit_log retention prune
After=postgresql.service

[Service]
Type=oneshot
ExecStart=/usr/bin/psql -h 127.0.0.1 -U dietician_app -d dietician -c "DELETE FROM audit_log WHERE occurred_at < NOW() - INTERVAL '12 months';"
EnvironmentFile=/run/dietician-keys/db.passphrase.env
```

### `dietician-audit-prune.timer`
```ini
[Unit]
Description=Run audit_log prune nightly at 04:00

[Timer]
OnCalendar=*-*-* 04:00:00
Persistent=true

[Install]
WantedBy=timers.target
```

### `dietician-backup.service`
```ini
[Unit]
Description=Dietician nightly OneDrive-crypt backup
After=postgresql.service

[Service]
Type=oneshot
ExecStart=/opt/dietician/bin/backup.sh
EnvironmentFile=/run/dietician-keys/db.passphrase.env
```

### `dietician-backup.timer`
```ini
[Unit]
Description=Run Dietician backup nightly at 04:30

[Timer]
OnCalendar=*-*-* 04:30:00
Persistent=true

[Install]
WantedBy=timers.target
```

---

## Audit gap when running fallback

The systemd path bypasses `AuditLogWriter`. If the fallback runs for more than 24h, manually emit a one-time `audit_log` row noting the bypass window so the AI Act Art 12 history remains accurate:

```sql
INSERT INTO audit_log (subject_id, action, context_json)
VALUES (NULL, 'cron_fallback_window',
        jsonb_build_object(
          'reason', 'in_jvm_scheduler_failure',
          'window_start', '<ISO timestamp>',
          'window_end', '<ISO timestamp>',
          'crons_affected', ARRAY['audit_prune', 'backup']
        ));
```

---

## Council 1779120000 references
- RC4: in-JVM scheduler is the SOLE production path; this file is documented-fallback only.
- Risk Analyst FM-3: dual-cron double-execute = audit-row pollution.

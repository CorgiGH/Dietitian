# Runbook 6 — Postgres conn refused from Ktor backend

**Symptom:** Ktor logs show `org.postgresql.util.PSQLException: Connection refused`. `/health` returns 503.

**Cause:**
- Postgres process down (crashed, OOM, manual stop)
- systemd `MemoryMax=600M` triggered (Postgres ate cap)
- Postgres listening on wrong address/port
- Postgres still booting after VPS reboot

**User action:**
1. SSH VPS: `systemctl status postgresql`
2. If `inactive (dead)`:
   ```bash
   journalctl -u postgresql --since=-30min
   ```
3. If OOM:
   - Increase MemoryMax: `systemctl edit postgresql` add `[Service]\nMemoryMax=800M`
   - Restart: `systemctl restart postgresql`
4. If still dead: check disk space `df -h`, check log dir permissions `ls -la /var/log/postgresql`
5. If listening on wrong address:
   ```bash
   grep listen_addresses /etc/postgresql/16/main/postgresql.conf
   # should be: listen_addresses = 'localhost,<tailscale-ip>'
   ```
6. Restart dietician-backend after Postgres is up: `systemctl restart dietician-backend`

**Prevention:**
- earlyoom `--avoid postgres` ensures it's last to be killed
- VPS-side cron `every 5min: pg_isready; if fail, systemctl restart postgresql` + send Telegram-equivalent alert
- After VPS reboot, systemd `After=postgresql.service` on `dietician-backend.service` enforces startup order

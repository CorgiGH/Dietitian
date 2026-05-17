# Runbook 1 — Tailscale route to VPS unreachable

**Symptom:** in-app banner "Tailscale route to VPS unreachable". `/diag` shows VPS Ktor health check failing 3 consecutive times.

**Likely cause:**
- tailscaled down on phone OR desktop
- VPS sshd/Postgres/Ktor crashed
- Network outage between client and VPS
- Tailscale ACL change blocked tag:dietician-client → tag:dietician-backend

**User action:**
1. On affected client: open Tailscale app. Confirm "Connected" green status.
2. From any client (phone or desktop): `ping <vps-tailscale-ip>` (or use Tailscale app's ping tool).
3. If ping works but `/health` still fails: VPS service likely down.
   - SSH to VPS: `ssh root@46.247.109.91`
   - Check services: `systemctl status dietician-backend postgresql ntfy grobid`
   - Restart whichever is down: `systemctl restart dietician-backend`
   - Tail logs: `journalctl -u dietician-backend --since=-10min`
4. If ping fails: VPS Tailscale daemon may be down.
   - SSH via public IP: `ssh root@46.247.109.91`
   - `systemctl status tailscaled`
   - `tailscale status` — verify peers
   - Restart: `systemctl restart tailscaled`
5. If Tailscale itself works but ACL blocks: check `tag:dietician-client` is assigned to phone+desktop nodes in https://login.tailscale.com/admin/machines.

**Prevention:**
- App-layer `/health` ping every 60s (foreground) / 15min (background) per spec §6.3.
- VPS systemd `Restart=on-failure` on dietician-backend.service.
- Tailscale `--accept-routes` enabled on all clients.

**Escalation:**
- If MC server is also down: VPS-wide issue. ByteHosting status page check.
- Persistent fail after restart: check disk full (`df -h`), OOM (`dmesg | tail`, earlyoom logs).

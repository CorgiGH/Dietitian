# Runbook 8 — ntfy push not delivered to phone

**Symptom:** Desktop finished a job, VPS published to ntfy, but phone shows no notification. `/diag` on phone shows last ntfy push > expected interval ago.

**Cause:**
- ntfy server down on VPS
- Phone ntfy app unsubscribed from topic
- Tailscale broken on phone (push from VPS Tailscale IP unreachable)
- Phone in Doze killing ntfy app (ntfy normally handles this but Android battery savers can interfere)

**User action:**
1. SSH VPS: `docker logs ntfy --tail 50` — verify recent publishes
2. From VPS: `curl -X POST -d "test" http://<tailscale-ip>:8080/dietician-v-broadcast`
3. On phone: open ntfy app, verify topic subscribed (`dietician-v-{your-device-id}`), verify "Test notification" reaches device
4. On phone: Android Settings → Apps → ntfy → Battery → "Unrestricted"
5. On phone: confirm Tailscale connected
6. If VPS ntfy down: `docker restart ntfy`

**Prevention:**
- Tailscale ACL allows `tag:dietician-client` to reach ntfy port
- ntfy systemd `Restart=on-failure`
- VPS-side cron probe every 5min; if down, restart container + log
- Phone ntfy app in battery whitelist

# Runbook 5 — Outbox depth > 50 on client

**Symptom:** `/diag` shows `outbox_depth` on a device > 50 entries. Sync not draining.

**Cause:**
- VPS Ktor backend returning 5xx
- Tailscale broken (covered by Runbook 1)
- Client's network code hung or backed off
- Postgres conn refused (covered by Runbook 6)

**User action:**
1. Check `/diag` for VPS health. If unreachable → Runbook 1.
2. If VPS reachable but Ktor returns errors:
   - SSH VPS: `journalctl -u dietician-backend --since=-30min`
   - Restart: `systemctl restart dietician-backend`
3. Force sync from client:
   - In-app: tap `/diag` → "Force sync now" button
   - Or restart app (drains outbox on launch)
4. If Postgres conn refused: → Runbook 6

**If outbox is corrupted (events that fail every attempt):**
- In-app: `/outbox inspect` shows top 10 with `last_error`
- `/outbox skip <event_uuid>` to discard a poisoned event (rare)

**Prevention:**
- Outbox retry policy: exponential backoff (1s, 4s, 16s, 64s, 256s, max 5min)
- After 10 failed attempts, event marked `permanent_failure`, surfaced in `/diag`
- WorkManager outbox-replay triggers on `NetworkType.CONNECTED` (Android)
- Desktop checks outbox every 30s when daemon running

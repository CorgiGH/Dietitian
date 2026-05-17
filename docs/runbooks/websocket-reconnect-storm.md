# Runbook 7 — WebSocket reconnect storm

**Symptom:** Ktor logs show same client opening/closing WS connection every few seconds. CPU spike on VPS. `/diag` shows excessive WS events.

**Cause:**
- Backend dropping connections (idle timeout too aggressive)
- Client retry-storm bug (no backoff)
- Network flapping (poor cell signal on phone)

**User action:**
1. Identify offending client from Ktor logs (look for repeated `device_id` in connection events).
2. If phone with bad signal: client should backoff exponentially.
3. Force-close offending client connection: `systemctl restart dietician-backend` (drops all WS, clients reconnect cleanly).
4. Verify client retry policy in `:shared:network.SyncClient`:
   - Initial: 1s
   - Exponential: 2x with jitter
   - Cap: 5min
5. If bug confirmed: file a custom Detekt rule via TBD.

**Prevention:**
- Ktor server WS `pingPeriod = 30.seconds`, `timeout = 60.seconds`
- Client backoff with jitter (Resilience4j Retry)
- Idle WS connections kept alive via app-layer ping every 30s

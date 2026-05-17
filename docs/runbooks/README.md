# Dietician Runbooks

One MD per operational failure mode. Per spec §24.

| # | File | Symptom |
|---|------|---------|
| 1 | [tailscale-route-broken.md](tailscale-route-broken.md) | App banner "Tailscale route to VPS unreachable" |
| 2 | [claudemax-budget-exhausted.md](claudemax-budget-exhausted.md) | /diag shows ClaudeMax 100% used |
| 3 | [desktop-offline-prolonged.md](desktop-offline-prolonged.md) | Desktop offline > 24h, jobs queue growing |
| 4 | [grobid-hung.md](grobid-hung.md) | /diag shows GROBID hung |
| 5 | [outbox-overflow.md](outbox-overflow.md) | Outbox depth > 50 on phone |
| 6 | [postgres-conn-refused.md](postgres-conn-refused.md) | Postgres conn refused from Ktor |
| 7 | [websocket-reconnect-storm.md](websocket-reconnect-storm.md) | WS reconnect storm |
| 8 | [ntfy-push-not-delivered.md](ntfy-push-not-delivered.md) | ntfy push not arriving on phone |
| 9 | [anelis-credential-rotation.md](anelis-credential-rotation.md) | Anelis auth failed on paper fetch |
| 10 | [scraper-sentinel-missing.md](scraper-sentinel-missing.md) | Sentinel selector missing on scraper |
| extra | [restore.md](restore.md) | Full VPS disaster restore from backup |

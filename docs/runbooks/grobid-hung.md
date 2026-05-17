# Runbook 4 — GROBID Docker container hung

**Symptom:** `/diag` shows GROBID status `DEGRADED` or `DOWN`. Paper ingestion jobs failing with timeout.

**Cause:**
- GROBID container deadlocked (known issue with malformed PDFs or memory pressure)
- VPS RAM pressure: GROBID OOM-killed by earlyoom or systemd

**User action:**
1. SSH to VPS: `ssh root@46.247.109.91`
2. Check container: `docker ps -a | grep grobid`
3. Check logs: `docker logs grobid --tail 100`
4. If hung: `docker restart grobid`
5. If recurring (>2/day): increase memory cap in compose:
   ```yaml
   grobid:
     image: lfoppiano/grobid:0.8.0
     deploy:
       resources:
         limits:
           memory: 800m   # bumped from 600m
           cpus: '1.5'
   ```
6. If still recurring: specific PDF may be malformed. Move offending PDF aside, retry.

**Prevention:**
- earlyoom priority: GROBID is allowed to be killed (not pinned). Postgres + Dietician backend pinned with `--avoid`.
- Per-paper budget cap: 8 LLM calls + 30K tokens (prevents runaway).
- VPS-side cron health check: every 15min ping `/api/isalive`; if fail 3x, auto-restart container.

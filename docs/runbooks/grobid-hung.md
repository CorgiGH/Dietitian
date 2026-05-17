# Runbook 4 — GROBID hung (DESKTOP-SIDE per 2026-05-17 relocation)

**Note:** GROBID was moved from VPS to desktop on 2026-05-17 due to VPS RAM constraint. This runbook describes desktop-side GROBID. See spec §4 errata #2.

**Symptom:** `/diag` shows GROBID status `DEGRADED` or `DOWN`. Paper ingestion jobs failing with timeout on desktop.

**Cause:**
- Docker Desktop not running on Windows (most common after user closes laptop)
- GROBID container deadlocked (known issue with malformed PDFs)
- Desktop RAM pressure (laptop running other apps)

**User action:**
1. Open Docker Desktop on Windows. Verify whale icon in system tray.
2. Check container: `docker ps -a | grep grobid`
3. Check logs: `docker logs grobid --tail 100`
4. If hung: `docker restart grobid`
5. If recurring (>2/day): increase memory cap in `desktopApp/docker/compose.yml`:
   ```yaml
   grobid:
     image: lfoppiano/grobid:0.8.0
     mem_limit: 2g   # bumped from 1.5g
     cpus: 2
   ```
6. If still recurring: specific PDF may be malformed. Move offending PDF aside, retry.

**Setup (one-time):**
- Install Docker Desktop for Windows: https://docs.docker.com/desktop/install/windows-install/ (~2GB download, requires WSL2)
- Auto-start Docker Desktop on Windows login (Settings → General → "Start Docker Desktop when you log in")
- Pull image once: `docker pull lfoppiano/grobid:0.8.0`

**Prevention:**
- Per-paper budget cap: 8 LLM calls + 30K tokens (prevents runaway).
- Desktop-side cron: every 15min ping `http://localhost:8070/api/isalive`; if fail 3x, auto-restart container.
- Paper queue gated on `desktop online` heartbeat — if desktop closed, papers queue, not lost.

**If desktop unavailable for prolonged period:** paper ingestion stalls. Acceptable per design (academic content is not time-critical). Surface in `/diag` with "GROBID requires desktop online; N papers queued".

# Runbook 3 — Desktop offline > 24h, jobs queue growing

**Symptom:** `/diag` shows `desktop-windows-laptop` heartbeat > 24h ago. `pending_jobs` table on VPS shows queue depth > 10 for `required_provider='desktop'` jobs.

**Cause:**
- Desktop closed (laptop sleep, hibernation, off)
- Desktop daemon (`Dietician.exe`) crashed or never started after reboot
- Network unreachable from desktop side
- User traveling

**User action:**
1. Open desktop laptop. Verify network connectivity.
2. Confirm Dietician desktop app running. If not, launch (`Dietician.exe` from Start menu or `./gradlew :desktopApp:run`).
3. Verify Tailscale active: tray icon, `tailscale status`.
4. Open `/diag` in desktop app — verify VPS reachable.
5. Job queue should drain automatically within minutes as desktop polls `pending_jobs`.

**If you need immediate processing (won't be at desktop for days):**
- In-app: `/process via gemini <job_id>` — forces VPS-side fallback to OpenRouter Gemini Vision for that specific Vision job.
- For paper fetches / recipe video ingest (genuinely desktop-only): no good fallback — wait or process later.

**Prevention:**
- Auto-launch Dietician on Windows startup (set during install).
- Desktop daemon heartbeat is the trigger for receipt OCR routing decision; absent heartbeat = VPS goes straight to Gemini fallback.

**If queue is critical:**
- Receipt OCR has automatic Gemini fallback already — those jobs process even without desktop.
- Video ingest + paper fetch queue safely; no data loss.

**Escalation:**
- If desktop unrecoverable (hardware failure): clone repo on new machine, set up KMP build, `./gradlew :desktopApp:run`, configure credentials, queue resumes.

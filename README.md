# Dietician

Personal Dietician for one user. Kotlin Multiplatform Compose (Android + Windows Desktop). VPS-canonical merge replica via Tailscale mesh.

## Status

Spec locked 2026-05-17. Implementation plan pending. Code not yet written.

## Quick links

- **Spec:** [`docs/superpowers/specs/2026-05-17-dietician-design.md`](docs/superpowers/specs/2026-05-17-dietician-design.md)
- **Agent conventions:** [`AGENTS.md`](AGENTS.md)
- **Jarvis merge plan:** `JARVIS_MERGE.md`
- **Runbooks:** [`docs/runbooks/`](docs/runbooks/)

## What it does

- Real RO supermarket prices (Mega Image, Carrefour, Auchan, Kaufland, Lidl) via 3-source sensor fusion
- Pantry awareness via event-sourced ledger (phone + desktop offline-friendly)
- Recipe ingestion from URL / YouTube / cookbook / paper / voice memo
- Macro-target-aware planner (Choco constraint solver + LLM ranker)
- Budget-aware shopping with sale-window detection
- Location-aware store catalog (auto-detects via GPS where appropriate)
- Mobile-first capture (Android + CameraX + ntfy push)
- Heavy compute on desktop (ClaudeMax CLI Vision, Playwright scrapers, GROBID, whisper.cpp, yt-dlp)
- LLM-Wiki knowledge base (~120 narrative pages instantiated to user)

## Topology

```
VPS (Tailscale-meshed, no public exposure)
 ├── Postgres 16 + pgvector (canonical merge replica)
 ├── Ktor REST + WebSocket on tag:dietician-backend:8081
 ├── GROBID Docker, ntfy Docker
 └── pg_dump → Backblaze B2 nightly

Desktop (Windows, Compose Multiplatform)
 ├── Thick client + heavy compute subprocesses
 └── ClaudeMax CLI, Playwright JAR, whisper.cpp, yt-dlp

Phone (Android, Compose Multiplatform)
 ├── Thin capture + planner query
 ├── CameraX, Android Keystore, ntfy app
 └── WorkManager event-driven sync only
```

## Build

```
./gradlew :shared:test
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:run
./gradlew :server:run
```

## Privacy + security

- VPS Dietician backend binds Tailscale IP only — no public internet exposure
- Credentials encrypted at rest (DPAPI / Android Keystore / age)
- Whisper transcription runs local (never sent to API)
- Vision OCR runs on ClaudeMax CLI (desktop, free under Max 20x Agent SDK credit) or Gemini fallback

## License

Personal use only. No license granted to third parties.

# Dietician Wiki — Log

Append-only chronological event log. Each entry starts with `## [YYYY-MM-DD] <type> | <summary>` so the log is parseable with `grep "^## \[" log.md`.

Types: `ingest` | `query` | `lint` | `instantiate` | `system`

---

## [2026-05-17] system | wiki initialized

Initial wiki tree created per spec `docs/superpowers/specs/2026-05-17-dietician-design.md`. Index stubbed. Knowledge corpus pages TBD. No sources ingested yet.

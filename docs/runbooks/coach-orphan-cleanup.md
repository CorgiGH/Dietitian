# Runbook — Coach orphan cleanup

**Slice:** Plan-iter-11 (2026-05-19, council 1779208184 option 4)

**Cron:** `CoachOrphanCleanupCron` registered by `CronBootstrap` at 30 s period.
**Trigger:** `SELECT refund_orphaned(60)` PG fn (V022).
**Symptom of failure:** `audit_log` rows pile up with `status='pending'` and old `reserved_until`. Budget reservations sit on `llm_budget.cost_cents_used` forever, eating cap.

## Manual orphan cleanup (operator)

```bash
ssh root@46.247.109.91 "sudo -u postgres psql -d dietician -c \"SELECT refund_orphaned(60);\""
```

Returns the number of rows compensated.

## Inspect pending rows

```bash
ssh root@46.247.109.91 "sudo -u postgres psql -d dietician -c \
  \"SELECT id, subject_id, occurred_at, reserved_until, cost_cents \
    FROM audit_log WHERE status = 'pending' \
    ORDER BY occurred_at;\""
```

## Inspect client-side outbox (desktop)

```bash
sqlite3 "%APPDATA%/Dietician/dietician.db" \
  "SELECT idempotency_key, started_at_ms, attempts, provider FROM audit_pending_outbox;"
```

A non-empty outbox after a clean desktop startup signals `DesktopOutboxReplay` is failing — check desktop logs for `replay failed for …` warnings.

## Cron health probe

```bash
ssh root@46.247.109.91 "journalctl -u dietician-backend --since '5 minutes ago' --no-pager | grep 'coach: orphaned'"
```

A line `coach: orphaned N pending rows (saga compensation)` appears whenever the cron compensates ≥1 row. Silence is correct under normal operation.

## Failure mode: `/coach/commit` on unknown key

Server returns `200 status='not_reserved'` when a commit key has no matching reserve row (gate-2 council mitigation). Outbox replay sees `not_reserved` as terminal and drains the row. If `/coach/commit` ever returns 500 for an unknown key in production, the desktop outbox will grow monotonically — file an incident.

## Failure mode: budget refund under-decremented

`refund_orphaned` joins `llm_budget` on `provider = COALESCE(audit_log.model, 'unknown')`. T1 contract: pending rows MUST write `model` = `llm_budget.provider` enum (`openrouter` / `anthropic` / `gemini` / `groq` / `claudemax`). A model-id like `claude-3-5-sonnet-20241022` would silently match zero `llm_budget` rows. Inspect:

```sql
SELECT model, COUNT(*) FROM audit_log WHERE status = 'orphaned' GROUP BY model;
```

Any value outside the provider enum = bug in the reserve-side caller.

-- V022__audit_log_coach_status.sql
-- iter-11 Coach activation — extends V018 audit_log with the 2-phase-commit
-- state machine (pending → success | failed | aborted | orphaned), the
-- idempotency key shared between /coach/reserve and /coach/commit, and the
-- reservation deadline driving the 60s saga-compensation cleanup cron.

ALTER TABLE audit_log
    ADD COLUMN status TEXT NOT NULL DEFAULT 'success'
        CHECK (status IN ('pending', 'success', 'failed', 'aborted', 'orphaned'));

ALTER TABLE audit_log
    ADD COLUMN idempotency_key UUID NULL;

ALTER TABLE audit_log
    ADD COLUMN reserved_until TIMESTAMPTZ NULL;

-- Partial unique index: NULL idempotency_key allowed for legacy rows + non-coach
-- audit events (sign-in, redact, etc); non-NULL keys are unique so commit retries
-- find the existing pending row via UPSERT semantics.
CREATE UNIQUE INDEX idx_audit_log_idempotency_key
    ON audit_log (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Saga-compensation entry point — flips pending rows >60s old to orphaned and
-- releases the reserved cost from llm_budget. Called by CoachOrphanCleanupCron
-- every 30s. Returns number of rows compensated.
--
-- audit_log has RLS enabled per V018 with policy
--   `subject_id IS NULL OR subject_id::TEXT = current_setting('app.current_subject_id', TRUE)`.
-- The cron calls this in system context with no GUC set, so a non-DEFINER
-- function would silently match zero rows once role separation lands
-- (RlsBypassPreventionTest already signals that intent).
-- SECURITY DEFINER + fixed search_path mirrors V021's prune fn — runs as the
-- table owner (BYPASSRLS), sees every subject's pending rows.
CREATE OR REPLACE FUNCTION refund_orphaned(stale_seconds INT DEFAULT 60)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    compensated_count INT := 0;
    r RECORD;
BEGIN
    FOR r IN
        SELECT id, subject_id, model, cost_cents, occurred_at
        FROM audit_log
        WHERE status = 'pending'
          AND reserved_until IS NOT NULL
          AND reserved_until < now()
          AND occurred_at < now() - make_interval(secs => stale_seconds)
        FOR UPDATE SKIP LOCKED
    LOOP
        UPDATE audit_log
        SET status = 'orphaned'
        WHERE id = r.id;
        -- CONTRACT: pending audit rows MUST write `cost_cents` = the estimated
        -- reservation amount (the same value passed to consume_or_fail). NULL =
        -- nothing to refund, which is only correct for the
        -- consume_or_fail-not-called path.
        IF r.cost_cents IS NOT NULL AND r.cost_cents > 0 THEN
            -- CONTRACT: pending audit rows MUST write `model` = the
            -- llm_budget.provider id (one of: openrouter, anthropic, gemini,
            -- groq). Writing the underlying model id
            -- (e.g. 'claude-3-5-sonnet-20241022') causes silent zero-row
            -- refunds because the UPDATE below joins on provider. T5 owns
            -- this contract on insert.
            UPDATE llm_budget
            SET cost_cents_used = GREATEST(cost_cents_used - r.cost_cents, 0)
            WHERE subject_id = r.subject_id
              AND provider = COALESCE(r.model, 'unknown')
              AND period_starts_at = date_trunc('month', r.occurred_at)::DATE;
        END IF;
        compensated_count := compensated_count + 1;
    END LOOP;
    RETURN compensated_count;
END;
$$;

-- Cron context calls this fn with no app.current_subject_id GUC set; EXECUTE
-- grant is wide so the future dietician_app role can call it. The fn itself
-- is read-write but scoped to pending → orphaned transitions only.
GRANT EXECUTE ON FUNCTION refund_orphaned(INT) TO PUBLIC;

-- V021__audit_log_prune_fn.sql
-- Plan-3 Task 33: nightly audit_log retention prune (12-month horizon).
--
-- audit_log has RLS enabled per V018 with policy
--   `subject_id IS NULL OR subject_id::TEXT = current_setting(...)`.
-- A bulk DELETE under the app role would only see NULL-subject rows
-- because the GUC is empty in system-context transactions.
--
-- SECURITY DEFINER lets the function execute with the migration-runner's
-- privileges (BYPASSRLS by default for the table owner), so the DELETE
-- sees every row across every subject. Returns the deleted row count.
--
-- GDPR Art 5(1)(e) storage limitation + AI Act Art 12 traceability
-- horizon: 12 months covers the longest known investigation window for
-- an n=1 personal system. The runbook
-- `docs/runbooks/restore.md` documents how to recover an older row if
-- one is required by a regulator query.

CREATE OR REPLACE FUNCTION prune_audit_log_older_than_12mo()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM audit_log
    WHERE occurred_at < NOW() - INTERVAL '12 months';
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$;

-- App role calls this function in cron context. EXECUTE grant is wide
-- since the function name self-describes its scope.
GRANT EXECUTE ON FUNCTION prune_audit_log_older_than_12mo() TO PUBLIC;

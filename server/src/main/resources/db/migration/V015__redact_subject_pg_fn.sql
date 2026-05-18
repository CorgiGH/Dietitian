-- V015__redact_subject_pg_fn.sql
-- Per Plan-3 council #2: GDPR Art 17 redaction via tombstone-event + cascade.
-- subject_redact(subject_id) emits a tombstone-event then nulls/deletes per-subject rows.
-- pgcrypto installed in V013 (gen_random_uuid available).

CREATE TABLE tombstone_events (
    event_uuid              UUID PRIMARY KEY,
    subject_id              UUID NOT NULL,
    redacted_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    redacted_by             TEXT NOT NULL DEFAULT 'subject_redact_fn',
    cascade_table_counts    JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_tombstone_events_subject ON tombstone_events (subject_id, redacted_at DESC);

CREATE OR REPLACE FUNCTION subject_redact(target_subject_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    counts JSONB := '{}'::jsonb;
    pantry_count INT;
    meal_count INT;
    weight_count INT;
    receipt_count INT;
BEGIN
    -- Count per-table rows before delete (for audit trail).
    SELECT count(*) INTO pantry_count  FROM pantry_events  WHERE subject_id = target_subject_id;
    SELECT count(*) INTO meal_count    FROM meal_events    WHERE subject_id = target_subject_id;
    SELECT count(*) INTO weight_count  FROM weight_events  WHERE subject_id = target_subject_id;
    SELECT count(*) INTO receipt_count FROM receipt_events WHERE subject_id = target_subject_id;

    counts := jsonb_build_object(
        'pantry_events',  pantry_count,
        'meal_events',    meal_count,
        'weight_events',  weight_count,
        'receipt_events', receipt_count
    );

    -- Emit tombstone FIRST — audit trail survives even if cascade fails partway.
    INSERT INTO tombstone_events (event_uuid, subject_id, cascade_table_counts)
    VALUES (gen_random_uuid(), target_subject_id, counts);

    -- Cascade delete from per-subject event tables.
    DELETE FROM pantry_events  WHERE subject_id = target_subject_id;
    DELETE FROM meal_events    WHERE subject_id = target_subject_id;
    DELETE FROM weight_events  WHERE subject_id = target_subject_id;
    DELETE FROM receipt_events WHERE subject_id = target_subject_id;

    -- Soft-delete subjects row (preserves tombstone reference + display_name for audit).
    UPDATE subjects SET deleted_at = now() WHERE subject_id = target_subject_id;

    RETURN counts;
END;
$$;

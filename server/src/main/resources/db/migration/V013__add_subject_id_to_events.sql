-- V013__add_subject_id_to_events.sql
-- Adds subject_id to every event table per Plan-3 council 5/5 must-ship #1.
-- RC7: backfill ALL 4 event tables, not just pantry_events.
-- Placeholder subject_id for existing rows: Victor's matricol-derived UUID.

-- pgcrypto required for gen_random_uuid() used by later migrations; install early.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- subjects table (canonical subject roster) — created first so devices + FKs work.
CREATE TABLE subjects (
    subject_id              UUID PRIMARY KEY,
    display_name            TEXT NOT NULL,
    email_for_magic_link    TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ NULL
);

INSERT INTO subjects (subject_id, display_name)
VALUES ('00000000-0000-0000-0000-000000000001'::uuid, 'Victor');

-- devices table (per-device install records)
CREATE TABLE devices (
    device_id      UUID PRIMARY KEY,
    subject_id     UUID NOT NULL REFERENCES subjects(subject_id),
    label          TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at   TIMESTAMPTZ NULL
);

-- pantry_events
ALTER TABLE pantry_events ADD COLUMN subject_id UUID;
UPDATE pantry_events SET subject_id = '00000000-0000-0000-0000-000000000001'::uuid WHERE subject_id IS NULL;
ALTER TABLE pantry_events ALTER COLUMN subject_id SET NOT NULL;
CREATE INDEX idx_pantry_events_subject_cursor ON pantry_events (subject_id, originated_at, event_uuid);
ALTER TABLE pantry_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY pantry_events_subject ON pantry_events
    USING (subject_id::TEXT = current_setting('app.current_subject_id', TRUE));

-- meal_events
ALTER TABLE meal_events ADD COLUMN subject_id UUID;
UPDATE meal_events SET subject_id = '00000000-0000-0000-0000-000000000001'::uuid WHERE subject_id IS NULL;
ALTER TABLE meal_events ALTER COLUMN subject_id SET NOT NULL;
CREATE INDEX idx_meal_events_subject_cursor ON meal_events (subject_id, originated_at, event_uuid);
ALTER TABLE meal_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY meal_events_subject ON meal_events
    USING (subject_id::TEXT = current_setting('app.current_subject_id', TRUE));

-- weight_events
ALTER TABLE weight_events ADD COLUMN subject_id UUID;
UPDATE weight_events SET subject_id = '00000000-0000-0000-0000-000000000001'::uuid WHERE subject_id IS NULL;
ALTER TABLE weight_events ALTER COLUMN subject_id SET NOT NULL;
CREATE INDEX idx_weight_events_subject_cursor ON weight_events (subject_id, originated_at, event_uuid);
ALTER TABLE weight_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY weight_events_subject ON weight_events
    USING (subject_id::TEXT = current_setting('app.current_subject_id', TRUE));

-- receipt_events
ALTER TABLE receipt_events ADD COLUMN subject_id UUID;
UPDATE receipt_events SET subject_id = '00000000-0000-0000-0000-000000000001'::uuid WHERE subject_id IS NULL;
ALTER TABLE receipt_events ALTER COLUMN subject_id SET NOT NULL;
CREATE INDEX idx_receipt_events_subject_cursor ON receipt_events (subject_id, originated_at, event_uuid);
ALTER TABLE receipt_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY receipt_events_subject ON receipt_events
    USING (subject_id::TEXT = current_setting('app.current_subject_id', TRUE));

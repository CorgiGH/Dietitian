-- V020__paper_fetch_queue_and_dedupe_logs.sql
-- A19 Anelis batch pull queue + A20 Mega CONNECT receipt dedupe + Plan-2 PII review + moderator sampling.

-- Anelis paper fetch queue (A19).
CREATE TABLE paper_fetch_queue (
    doi                         TEXT PRIMARY KEY,
    priority                    INT NOT NULL DEFAULT 50,
    requested_by_subject_id     UUID NOT NULL REFERENCES subjects(subject_id),
    requested_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                      TEXT NOT NULL DEFAULT 'queued'
                                CHECK (status IN ('queued', 'fetched', 'retry_next_run', 'permanent_fail')),
    last_attempt_at             TIMESTAMPTZ NULL,
    last_error                  TEXT NULL,
    attempts                    INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_paper_fetch_queue_status
    ON paper_fetch_queue (status, priority DESC, requested_at);

-- Mega CONNECT receipt dedupe log (A20).
-- dedup_key = composite (date_yyyyMMdd, store, total_centimes, chitanta_number).
-- receipt_event_uuid is a logical pointer (no FK due to RLS isolation on receipt_events).
CREATE TABLE mega_receipt_dedupe_log (
    dedup_key                   TEXT PRIMARY KEY,
    subject_id                  UUID NOT NULL REFERENCES subjects(subject_id),
    inserted_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    receipt_event_uuid          UUID NOT NULL
);

CREATE INDEX idx_mega_dedupe_subject
    ON mega_receipt_dedupe_log (subject_id, inserted_at DESC);

-- Plan-2 PII review queue (surfaced by council 1779062699 RC5).
CREATE TABLE pii_review_queue (
    pii_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id          UUID NOT NULL REFERENCES subjects(subject_id),
    raw_ref             TEXT NOT NULL,  -- pointer to llm-raw/<uuid>.txt
    context             TEXT NOT NULL,  -- 'voice_memo' | 'recipe_ingest' | ...
    queued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at         TIMESTAMPTZ NULL,
    reviewer            TEXT NULL,
    redacted_text       TEXT NULL
);

CREATE INDEX idx_pii_review_pending
    ON pii_review_queue (queued_at) WHERE reviewed_at IS NULL;

ALTER TABLE pii_review_queue ENABLE ROW LEVEL SECURITY;
CREATE POLICY pii_review_queue_subject ON pii_review_queue
    USING (subject_id::TEXT = current_setting('app.current_subject_id', TRUE));

-- Plan-2 moderator sampling queue (council 1779062699 RC6).
CREATE TABLE moderator_sampling_queue (
    sample_id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id                  UUID NOT NULL REFERENCES subjects(subject_id),
    moderator_verdict_id        UUID NOT NULL,  -- logical reference to audit_log.id
    source_authority            TEXT NOT NULL
                                CHECK (source_authority IN ('youtube', 'web_scrape', 'manual_entry')),
    sampled_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    review_outcome              TEXT NULL
                                CHECK (review_outcome IN ('confirmed_safe', 'false_safe', 'pending'))
);

CREATE INDEX idx_moderator_sampling_outcome
    ON moderator_sampling_queue (review_outcome, sampled_at);

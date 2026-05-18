-- V018__audit_log.sql
-- EU AI Act Art 12: every LLM call (and other security-sensitive action) logged.
-- RC3: RLS NULL-exception so system-level audit rows (cron, admin) remain readable.

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id      UUID NULL REFERENCES subjects(subject_id),  -- NULL = system-level
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    kind            TEXT NOT NULL,
    -- kind values include but not limited to:
    --   'llm_call', 'subject_redact', 'consent_grant', 'consent_withdraw',
    --   'sign_in', 'sign_out_all', 'credential_revoke', 'pii_review_decision'
    model           TEXT,
    prompt_hash     TEXT,
    response_hash   TEXT,
    input_tokens    INT,
    output_tokens   INT,
    cost_cents      INT,
    request_id      TEXT,
    extra           JSONB
);

CREATE INDEX idx_audit_log_subject_time ON audit_log (subject_id, occurred_at DESC);
CREATE INDEX idx_audit_log_kind_time    ON audit_log (kind, occurred_at DESC);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_log_subject ON audit_log
    USING (subject_id IS NULL OR subject_id::TEXT = current_setting('app.current_subject_id', TRUE));

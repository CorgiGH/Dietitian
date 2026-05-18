-- V017__ropa.sql
-- GDPR Art 30 Record of Processing Activities.
-- RC3: RLS NULL-exception — system-level (subject_id NULL) entries readable when GUC unset.

CREATE TABLE ropa_entries (
    ropa_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id          UUID NULL REFERENCES subjects(subject_id),  -- NULL = system-level
    category            TEXT NOT NULL,
    purpose             TEXT NOT NULL,
    legal_basis         TEXT NOT NULL,
    retention_policy    TEXT NOT NULL,
    recipients          TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ropa_entries_subject ON ropa_entries (subject_id);

ALTER TABLE ropa_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY ropa_entries_subject ON ropa_entries
    USING (subject_id IS NULL OR subject_id::TEXT = current_setting('app.current_subject_id', TRUE));

-- System-level seed entries (no subject) — required by GDPR Art 30 for the
-- categories of personal data the controller (Victor self-deploy) processes.
INSERT INTO ropa_entries (category, purpose, legal_basis, retention_policy, recipients) VALUES
(
    'nutrition_logs',
    'Self-coaching + meal planning',
    'Art 9(2)(a) explicit consent',
    '12 months auto-delete + DSAR-on-demand',
    ARRAY['Anthropic (via ClaudeMax CLI)', 'OpenRouter providers (user-specified BYOK)']
),
(
    'audit_log',
    'AI Act Art 12 compliance',
    'Art 6(1)(c) legal obligation',
    '12 months auto-delete',
    ARRAY['Subject only via DSAR export']
),
(
    'receipt_ocr',
    'Pantry tracking + price observation',
    'Art 9(2)(a) explicit consent',
    '12 months + dedupe-log retention',
    ARRAY['Anthropic (ClaudeMax) primary', 'Google (Gemini Vision) fallback']
);

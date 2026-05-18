-- V016__explicit_consent.sql
-- GDPR Art 9 health-adjacent data: explicit consent + withdrawal log per scope.
-- RC3: RLS NULL-exception so system rows (NULL subject) remain queryable when
-- the GUC is unset (e.g. cron jobs, admin migrations).

CREATE TABLE consents (
    consent_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id      UUID NOT NULL REFERENCES subjects(subject_id),
    scope           TEXT NOT NULL CHECK (scope IN (
        'process_meal_data',
        'process_weight_data',
        'process_voice_memos',
        'export_to_anelis',
        'share_with_friends'
    )),
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    withdrawn_at    TIMESTAMPTZ NULL,
    version_hash    TEXT NOT NULL
);

CREATE INDEX idx_consents_subject ON consents (subject_id);
CREATE INDEX idx_consents_scope_active ON consents (subject_id, scope) WHERE withdrawn_at IS NULL;

ALTER TABLE consents ENABLE ROW LEVEL SECURITY;
CREATE POLICY consents_subject ON consents
    USING (subject_id IS NULL OR subject_id::TEXT = current_setting('app.current_subject_id', TRUE));

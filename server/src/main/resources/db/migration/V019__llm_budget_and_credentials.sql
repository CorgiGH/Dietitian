-- V019__llm_budget_and_credentials.sql
-- Plan-2/3 two-phase reserve + per-provider tracking + credential storage.
-- Supersedes the Plan-1 V003 llm_budget (provider-only PK) — Plan-3 needs
-- per-subject + per-period semantics. Plan-1 had no shipped data so the
-- destructive replacement is safe; Flyway tracks the V003 history row.

DROP TABLE IF EXISTS llm_budget;

-- Encrypted BYOK key storage. encrypted_key = pgcrypto pgp_sym_encrypt result.
CREATE TABLE subject_credentials (
    subject_id      UUID NOT NULL REFERENCES subjects(subject_id),
    provider        TEXT NOT NULL CHECK (provider IN ('openrouter', 'anthropic', 'gemini', 'groq')),
    encrypted_key   BYTEA NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ NULL,  -- per Plan-2 RC12 audit-log action
    PRIMARY KEY (subject_id, provider)
);

ALTER TABLE subject_credentials ENABLE ROW LEVEL SECURITY;
CREATE POLICY subject_credentials_owner ON subject_credentials
    USING (subject_id::TEXT = current_setting('app.current_subject_id', TRUE));

-- Two-phase budget reserve (Plan-2 §4.4) keyed per subject + provider + monthly period.
CREATE TABLE llm_budget (
    subject_id          UUID NOT NULL REFERENCES subjects(subject_id),
    provider            TEXT NOT NULL,
    period_starts_at    DATE NOT NULL,
    period_ends_at      DATE NOT NULL,
    reserved_tokens     INT NOT NULL DEFAULT 0,
    finalized_tokens    INT NOT NULL DEFAULT 0,
    cost_cents_used     INT NOT NULL DEFAULT 0,
    cost_cents_cap      INT NULL,  -- NULL = unbounded; non-NULL enforces ceiling
    PRIMARY KEY (subject_id, provider, period_starts_at)
);

CREATE OR REPLACE FUNCTION consume_or_fail(
    target_subject UUID,
    target_provider TEXT,
    tokens_needed INT,
    cost_cents_estimated INT
) RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    current_period_starts DATE := date_trunc('month', now())::DATE;
    current_period_ends   DATE := (date_trunc('month', now()) + INTERVAL '1 month - 1 day')::DATE;
    row_cap INT;
    row_used INT;
BEGIN
    -- Ensure period row exists.
    INSERT INTO llm_budget (subject_id, provider, period_starts_at, period_ends_at)
    VALUES (target_subject, target_provider, current_period_starts, current_period_ends)
    ON CONFLICT DO NOTHING;

    SELECT cost_cents_cap, cost_cents_used INTO row_cap, row_used
    FROM llm_budget
    WHERE subject_id = target_subject
      AND provider = target_provider
      AND period_starts_at = current_period_starts
    FOR UPDATE;

    IF row_cap IS NOT NULL AND (row_used + cost_cents_estimated) > row_cap THEN
        RETURN FALSE;
    END IF;

    UPDATE llm_budget
    SET reserved_tokens = reserved_tokens + tokens_needed,
        cost_cents_used = cost_cents_used + cost_cents_estimated
    WHERE subject_id = target_subject
      AND provider = target_provider
      AND period_starts_at = current_period_starts;

    RETURN TRUE;
END;
$$;

-- ClaudeMax per-subject 5-hour message counter (Anthropic Max plan rate-limit awareness).
CREATE TABLE claudemax_message_counter (
    subject_id      UUID NOT NULL REFERENCES subjects(subject_id),
    window_start    TIMESTAMPTZ NOT NULL,
    message_count   INT NOT NULL DEFAULT 0,
    PRIMARY KEY (subject_id, window_start)
);

CREATE INDEX idx_claudemax_counter_window ON claudemax_message_counter (window_start);

-- In-flight reservation table (Plan-2 IdempotencyCache + Router fallback recovery).
CREATE TABLE reservations (
    reservation_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id              UUID NOT NULL REFERENCES subjects(subject_id),
    provider                TEXT NOT NULL,
    reserved_tokens         INT NOT NULL,
    reserved_cost_cents     INT NOT NULL,
    reserved_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL,  -- swept nightly if no finalize
    finalized_at            TIMESTAMPTZ NULL,
    final_tokens            INT NULL,
    final_cost_cents        INT NULL
);

CREATE INDEX idx_reservations_expires
    ON reservations (expires_at)
    WHERE finalized_at IS NULL;

-- Passkey credentials (RC1: STAYS as unused-until-Plan-3.5).
-- Schema reserved; Plan-3 magic-link-only auth does not write to this table.
CREATE TABLE webauthn_credentials (
    credential_id       BYTEA PRIMARY KEY,
    subject_id          UUID NOT NULL REFERENCES subjects(subject_id),
    public_key_cose     BYTEA NOT NULL,
    sign_count          BIGINT NOT NULL DEFAULT 0,
    transports          TEXT[],
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ NULL
);

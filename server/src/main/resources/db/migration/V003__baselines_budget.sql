CREATE TABLE budgets (
  week_iso          TEXT PRIMARY KEY,   -- '2026-W21'
  target_lei        REAL NOT NULL,
  actual_lei        REAL DEFAULT 0,
  set_at            TIMESTAMPTZ NOT NULL
);

CREATE TABLE llm_budget (
  provider          TEXT PRIMARY KEY,   -- 'claudemax-sdk' | 'openrouter'
  reset_at          TIMESTAMPTZ NOT NULL,
  ceiling_cents     INTEGER NOT NULL,   -- $200 → 20000 cents for claudemax-sdk Max 20x
  used_cents        INTEGER NOT NULL DEFAULT 0,
  reserved_cents    INTEGER NOT NULL DEFAULT 0  -- two-phase reserve
);

CREATE TABLE model_price_table (
  provider          TEXT NOT NULL,
  model_id          TEXT NOT NULL,
  input_cents_per_mtok  REAL NOT NULL,
  output_cents_per_mtok REAL NOT NULL,
  refreshed_at      TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (provider, model_id)
);

CREATE TABLE llm_calls (
  call_uuid         UUID PRIMARY KEY,
  provider          TEXT NOT NULL,
  model_id          TEXT NOT NULL,
  prompt_hash       TEXT NOT NULL,
  reserved_cents    INTEGER NOT NULL,
  actual_cents      INTEGER NULL,
  input_tokens      INTEGER NULL,
  output_tokens     INTEGER NULL,
  started_at        TIMESTAMPTZ NOT NULL,
  completed_at      TIMESTAMPTZ NULL,
  status            TEXT NOT NULL,        -- 'reserved' | 'in_flight' | 'completed' | 'failed' | 'timeout'
  error             TEXT NULL,
  request_ref       TEXT NULL,            -- raw request archive
  response_ref      TEXT NULL             -- raw response archive
);

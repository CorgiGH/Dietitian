-- Canonical SKU registry
CREATE TABLE sku_canonical (
  uuid              UUID PRIMARY KEY,
  display_name      TEXT NOT NULL,
  category          TEXT NOT NULL,        -- 'protein', 'carb', 'fat', 'dairy', 'veg', etc.
  unit              TEXT NOT NULL,
  normalized_name   TEXT NOT NULL,
  normalizer_version INTEGER NOT NULL DEFAULT 1,
  size_g            REAL NULL,            -- canonical package size if branded
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_sku_canonical_lookup ON sku_canonical(category, normalized_name, COALESCE(size_g, 0), unit);

-- Per-source ID mapping (cross-namespace)
CREATE TABLE sku_source_id (
  canonical_uuid    UUID REFERENCES sku_canonical(uuid),
  source            TEXT NOT NULL,        -- 'mega-vtex' | 'carrefour-vtex' | 'auchan-playwright' | 'kaufland-flyer' | 'lidl-flyer' | 'bringo' | 'monitorul' | 'receipt-mega' | etc.
  source_id         TEXT NOT NULL,
  gtin              TEXT NULL,
  name_raw          TEXT NOT NULL,
  last_seen         TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (source, source_id)
);
CREATE INDEX idx_sku_source_gtin ON sku_source_id(gtin) WHERE gtin IS NOT NULL;

-- Receipt aliases learned per chain (Council 3 fix #6)
CREATE TABLE receipt_aliases (
  store_id          TEXT NOT NULL,
  receipt_line_text TEXT NOT NULL,
  canonical_uuid    UUID NOT NULL REFERENCES sku_canonical(uuid),
  confirmed_count   INTEGER NOT NULL DEFAULT 1,
  last_seen         TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (store_id, receipt_line_text)
);

-- SKU match review queue
CREATE TABLE sku_match_queue (
  id                BIGSERIAL PRIMARY KEY,
  source            TEXT NOT NULL,
  source_id         TEXT NOT NULL,
  name_raw          TEXT NOT NULL,
  candidate_canonical_uuid UUID NULL,    -- T2 best guess
  jaccard_score     REAL NULL,
  size_match_ok     BOOLEAN NULL,
  queued_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at       TIMESTAMPTZ NULL,
  resolved_action   TEXT NULL             -- 'linked', 'new', 'skipped'
);

CREATE TABLE price_observations (
  id                  BIGSERIAL PRIMARY KEY,
  sku_uuid            UUID NOT NULL REFERENCES sku_canonical(uuid),
  store_id            TEXT NOT NULL,
  source              TEXT NOT NULL,
  observed_at         TIMESTAMPTZ NOT NULL,
  price_minor         INTEGER NOT NULL,   -- bani (lei × 100)
  currency            CHAR(3) NOT NULL DEFAULT 'RON',
  in_promo            BOOLEAN NOT NULL DEFAULT false,
  source_confidence   REAL NOT NULL,      -- 0..1
  extraction_call_id  UUID NULL,          -- if from LLM Vision
  raw_evidence_ref    TEXT NULL           -- /storage/llm-raw/{uuid}.txt
);
CREATE INDEX idx_price_obs_sku_store_time ON price_observations(sku_uuid, store_id, observed_at DESC);

-- Promo isolation (Council 3 fix #5 — Council 2 fix amended)
CREATE TABLE promo_observations (
  id                  BIGSERIAL PRIMARY KEY,
  sku_uuid            UUID NOT NULL REFERENCES sku_canonical(uuid),
  store_id            TEXT NOT NULL,
  source              TEXT NOT NULL,
  price_minor         INTEGER NOT NULL,
  start_date          DATE NOT NULL,
  end_date            DATE NOT NULL,
  discovered_at       TIMESTAMPTZ NOT NULL,
  source_confidence   REAL NOT NULL,
  raw_evidence_ref    TEXT NULL,
  UNIQUE (sku_uuid, store_id, start_date, end_date)
);

-- Posterior (recomputed by background coroutine every 5 min)
CREATE TABLE price_posterior (
  sku_uuid              UUID NOT NULL REFERENCES sku_canonical(uuid),
  store_id              TEXT NOT NULL,
  point_estimate_minor  INTEGER NOT NULL,
  confidence_score      REAL NOT NULL,
  n_observations        INTEGER NOT NULL,
  span_days             INTEGER NOT NULL,
  bootstrap_phase       TEXT NOT NULL,    -- 'initial' (n<5 OR span<14d) | 'stable'
  computed_at           TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (sku_uuid, store_id)
);

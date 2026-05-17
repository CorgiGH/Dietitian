-- Spec §3: event-sourced ledger tables (Postgres canonical, mirrored on per-client SQLite).

CREATE TABLE pantry_events (
  event_uuid     UUID PRIMARY KEY,
  device_id      TEXT NOT NULL,
  originated_at  TIMESTAMPTZ NOT NULL,
  synced_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  sku_uuid       UUID NOT NULL,
  delta_qty      REAL NOT NULL,
  unit           TEXT NOT NULL,
  reason         TEXT,
  evidence_ref   TEXT
);
CREATE INDEX idx_pantry_events_sku ON pantry_events(sku_uuid);
CREATE INDEX idx_pantry_events_originated ON pantry_events(originated_at);
CREATE INDEX idx_pantry_events_synced_at_uuid ON pantry_events(synced_at, event_uuid);

CREATE TABLE meal_events (
  event_uuid       UUID PRIMARY KEY,
  device_id        TEXT NOT NULL,
  originated_at    TIMESTAMPTZ NOT NULL,
  synced_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  meal_label       TEXT NOT NULL,
  recipe_id        UUID,
  ingredients_json JSONB NOT NULL,
  kcal_actual      REAL,
  protein_actual   REAL,
  rating_1_5       INTEGER,
  notes            TEXT
);
CREATE INDEX idx_meal_events_synced_at_uuid ON meal_events(synced_at, event_uuid);

CREATE TABLE weight_events (
  event_uuid    UUID PRIMARY KEY,
  device_id     TEXT NOT NULL,
  originated_at TIMESTAMPTZ NOT NULL,
  synced_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  weight_kg     REAL NOT NULL,
  time_of_day   TEXT,
  conditions    TEXT
);
CREATE INDEX idx_weight_events_synced_at_uuid ON weight_events(synced_at, event_uuid);

CREATE TABLE receipt_events (
  event_uuid       UUID PRIMARY KEY,
  device_id        TEXT NOT NULL,
  originated_at    TIMESTAMPTZ NOT NULL,
  synced_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  store_id         TEXT NOT NULL,
  total_lei        REAL,
  image_ref        TEXT NOT NULL,
  ocr_status       TEXT NOT NULL,
  ocr_provider     TEXT,
  line_items_json  JSONB
);
CREATE INDEX idx_receipt_events_synced_at_uuid ON receipt_events(synced_at, event_uuid);

CREATE TABLE pantry_metadata (
  sku_uuid             UUID PRIMARY KEY,
  expiry_date          DATE,
  open_status          TEXT NOT NULL DEFAULT 'sealed',
  open_date            DATE,
  hlc_wall_ms          BIGINT NOT NULL,
  hlc_seq              INTEGER NOT NULL,
  last_modified_device TEXT NOT NULL,
  server_recv_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

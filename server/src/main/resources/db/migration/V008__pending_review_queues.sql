CREATE TABLE receipt_review_queue (
  id                BIGSERIAL PRIMARY KEY,
  store_id          TEXT NOT NULL,
  receipt_line_text TEXT NOT NULL,
  candidate_sku_uuid UUID NULL,
  receipt_event_uuid UUID REFERENCES receipt_events(event_uuid),
  queued_at         TIMESTAMPTZ NOT NULL,
  resolved_at       TIMESTAMPTZ NULL,
  resolved_action   TEXT NULL
);

CREATE TABLE flyer_review_queue (
  id                BIGSERIAL PRIMARY KEY,
  source            TEXT NOT NULL,
  raw_payload_json  JSONB NOT NULL,
  rejection_reason  TEXT NOT NULL,        -- 'low_confidence', 'unit_missing', 'price_outlier', 'date_invalid'
  raw_evidence_ref  TEXT NOT NULL,
  queued_at         TIMESTAMPTZ NOT NULL,
  resolved_at       TIMESTAMPTZ NULL,
  resolved_action   TEXT NULL
);

CREATE TABLE vision_anomaly_queue (    -- Council 3 BREAK #3 anomaly gate
  id                BIGSERIAL PRIMARY KEY,
  price_observation_id BIGINT REFERENCES price_observations(id),
  z_score           REAL NOT NULL,
  baseline_median   INTEGER NOT NULL,
  detected_at       TIMESTAMPTZ NOT NULL,
  resolved_at       TIMESTAMPTZ NULL,
  resolved_action   TEXT NULL               -- 'kept', 'rejected', 'rescraped'
);

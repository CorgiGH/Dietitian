-- Council-required tables for /diag aggregation across devices.
-- These are VPS-side mirrors of per-client tables, populated by /sync/push when the
-- client reports a dead-letter or sync_log entry.

CREATE TABLE outbox_dead_vps (
  event_uuid       UUID NOT NULL,
  device_id        TEXT NOT NULL,
  table_name       TEXT NOT NULL,
  payload_json     JSONB NOT NULL,
  first_failed_at  TIMESTAMPTZ NOT NULL,
  last_attempt_at  TIMESTAMPTZ NOT NULL,
  attempt_count    INTEGER NOT NULL,
  last_error       TEXT NOT NULL,
  reported_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at      TIMESTAMPTZ,
  resolved_action  TEXT,
  PRIMARY KEY (event_uuid, device_id)
);

CREATE TABLE sync_log_vps (
  id              BIGSERIAL PRIMARY KEY,
  device_id       TEXT NOT NULL,
  trigger_source  TEXT NOT NULL,        -- 'ws' | 'ntfy' | 'manual' | 'periodic'
  fired_at        TIMESTAMPTZ NOT NULL,
  debounced_to    TIMESTAMPTZ,
  pull_started_at TIMESTAMPTZ,
  pull_ended_at   TIMESTAMPTZ,
  events_pulled   INTEGER,
  error           TEXT,
  reported_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sync_log_vps_device_fired ON sync_log_vps(device_id, fired_at DESC);

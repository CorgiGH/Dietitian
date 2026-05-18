CREATE TABLE pending_jobs (
  id                BIGSERIAL PRIMARY KEY,
  job_type          TEXT NOT NULL,          -- 'ocr_receipt' | 'ingest_video' | 'fetch_paper' | 'parse_flyer'
  payload_json      JSONB NOT NULL,
  required_provider TEXT,                   -- 'desktop' | 'vps' | 'any'
  status            TEXT NOT NULL,          -- 'queued', 'in_progress', 'completed', 'failed'
  created_at        TIMESTAMPTZ NOT NULL,
  started_at        TIMESTAMPTZ NULL,
  completed_at      TIMESTAMPTZ NULL,
  attempts          INTEGER NOT NULL DEFAULT 0,
  last_error        TEXT NULL,
  result_ref        TEXT NULL
);

CREATE TABLE device_heartbeat (
  device_id         TEXT PRIMARY KEY,
  last_heartbeat_at TIMESTAMPTZ NOT NULL,
  current_status    TEXT NOT NULL           -- 'online', 'offline', 'degraded'
);

CREATE TABLE credential_heartbeat (    -- Council 3 fix #7
  credential_name   TEXT PRIMARY KEY,     -- 'anelis', 'lidl-plus', 'mega-connect', 'openrouter'
  last_used_at      TIMESTAMPTZ,
  last_success_at   TIMESTAMPTZ,
  expected_to_work_at TIMESTAMPTZ NOT NULL,
  status            TEXT NOT NULL           -- 'ok', 'degraded', 'broken'
);

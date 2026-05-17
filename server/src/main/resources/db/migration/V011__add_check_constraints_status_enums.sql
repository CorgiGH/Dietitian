-- Council #1 fix #2: cheap defense against typo'd status enum values entering the DB.
--
-- Flyway never edits past migrations, so this V011 adds CHECK constraints retroactively to
-- TEXT columns that act as enums in V001-V008. Enumerations sourced from spec §3 where
-- explicit, otherwise inferred from inline migration comments + code usage.
--
-- For columns whose enumeration might still grow, we use `col IN (...)` (NOT NULL already
-- enforced by the original CREATE TABLE) so any future value addition will surface as a
-- migration-time error rather than silently corrupt the table.

-- receipt_events.ocr_status — spec §3 line 213: 'pending', 'processed', 'failed' (explicit)
ALTER TABLE receipt_events
  ADD CONSTRAINT receipt_events_ocr_status_chk
  CHECK (ocr_status IN ('pending', 'processed', 'failed'));

-- pantry_metadata.open_status — spec §3 line 1579: 'sealed' | 'opened' (explicit)
ALTER TABLE pantry_metadata
  ADD CONSTRAINT pantry_metadata_open_status_chk
  CHECK (open_status IN ('sealed', 'opened'));

-- pending_jobs.status — V007 inline comment: 'queued', 'in_progress', 'completed', 'failed' (inferred from migration)
ALTER TABLE pending_jobs
  ADD CONSTRAINT pending_jobs_status_chk
  CHECK (status IN ('queued', 'in_progress', 'completed', 'failed'));

-- device_heartbeat.current_status — V007 inline comment: 'online', 'offline', 'degraded' (inferred from migration)
ALTER TABLE device_heartbeat
  ADD CONSTRAINT device_heartbeat_current_status_chk
  CHECK (current_status IN ('online', 'offline', 'degraded'));

-- credential_heartbeat.status — V007 inline comment: 'ok', 'degraded', 'broken' (inferred from migration)
ALTER TABLE credential_heartbeat
  ADD CONSTRAINT credential_heartbeat_status_chk
  CHECK (status IN ('ok', 'degraded', 'broken'));

-- llm_calls.status — spec §3 line 411: 'reserved' | 'in_flight' | 'completed' | 'failed' | 'timeout' (explicit)
ALTER TABLE llm_calls
  ADD CONSTRAINT llm_calls_status_chk
  CHECK (status IN ('reserved', 'in_flight', 'completed', 'failed', 'timeout'));

-- recipes.status — V005 inline comment: 'active', 'retired-boredom', 'failed-ingest' (inferred from migration)
ALTER TABLE recipes
  ADD CONSTRAINT recipes_status_chk
  CHECK (status IN ('active', 'retired-boredom', 'failed-ingest'));

-- shopping_lists.status — V005 inline comment: 'draft', 'active', 'completed' (inferred from migration)
ALTER TABLE shopping_lists
  ADD CONSTRAINT shopping_lists_status_chk
  CHECK (status IN ('draft', 'active', 'completed'));

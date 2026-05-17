-- Council #1 fix #3: composite cursor + partial unsynced indexes.
--
-- (1) The PullCoordinator / selectPantryEventsSince family sorts by
--     (originated_at ASC, event_uuid ASC). Existing V001 indexes are single-column
--     `idx_*_events_originated (originated_at)` (pantry only) and composite
--     `idx_*_events_synced_at_uuid (synced_at, event_uuid)` — neither matches the
--     cursor sort key. Add `(originated_at, event_uuid)` composites on all 4 event
--     tables so the Task 22 property test (10k row pulls) and prod cursor scans
--     don't regress as data grows.
--
-- (2) Partial `WHERE synced_at IS NULL` indexes accelerate outbox-drain scans for
--     unsynced events. The SQLDelight side already has one for pantry; mirror it on
--     Postgres for all 4 event tables. (Server-side "unsynced" semantics differ —
--     V001 defaults synced_at to now() — but we keep the index symmetric for the
--     schema-parity mental model and for any future server-side outbox flow.)

CREATE INDEX IF NOT EXISTS idx_pantry_events_cursor
  ON pantry_events (originated_at, event_uuid);
CREATE INDEX IF NOT EXISTS idx_meal_events_cursor
  ON meal_events (originated_at, event_uuid);
CREATE INDEX IF NOT EXISTS idx_weight_events_cursor
  ON weight_events (originated_at, event_uuid);
CREATE INDEX IF NOT EXISTS idx_receipt_events_cursor
  ON receipt_events (originated_at, event_uuid);

CREATE INDEX IF NOT EXISTS idx_pantry_events_unsynced
  ON pantry_events (originated_at) WHERE synced_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_meal_events_unsynced
  ON meal_events (originated_at) WHERE synced_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_weight_events_unsynced
  ON weight_events (originated_at) WHERE synced_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_receipt_events_unsynced
  ON receipt_events (originated_at) WHERE synced_at IS NULL;

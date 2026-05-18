CREATE TABLE stores (
  store_id          TEXT PRIMARY KEY,       -- 'mega-carol-i', 'kaufland-pacurari', 'carrefour-iulius'
  chain             TEXT NOT NULL,          -- 'mega-image', 'carrefour', 'kaufland', 'lidl', 'auchan', 'piata'
  address           TEXT,
  latitude          REAL,
  longitude         REAL,
  hours_json        JSONB,                  -- {mon: '00:00-24:00', tue: '07:00-22:30', ...}
  has_bringo        BOOLEAN DEFAULT false,
  notes             TEXT
);

CREATE TABLE user_location_state (
  location_id       TEXT PRIMARY KEY,
  label             TEXT NOT NULL,          -- 'iasi-tudor-vladimirescu', 'iasi-fii', 'home-moldova'
  latitude          REAL,
  longitude         REAL,
  store_priority_json JSONB NOT NULL,       -- ordered list of store_ids accessible from this location
  notes             TEXT
);

CREATE TABLE user_location_current (
  device_id         TEXT PRIMARY KEY,
  location_id       TEXT NOT NULL REFERENCES user_location_state(location_id),
  set_at            TIMESTAMPTZ NOT NULL,
  source            TEXT NOT NULL           -- 'manual', 'gps-auto'
);

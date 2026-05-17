CREATE TABLE food_composition (        -- merged USDA SR Legacy + CIQUAL + Open Food Facts RO + local_nutrition override
  food_id           TEXT PRIMARY KEY,    -- 'usda:171686', 'ciqual:20021', 'off:4056489083061', 'local:uuid'
  source            TEXT NOT NULL,
  name_en           TEXT,
  name_ro           TEXT,
  kcal_per_100g     REAL,
  protein_g_per_100g REAL,
  fat_g_per_100g    REAL,
  saturated_fat_g_per_100g REAL,
  carb_g_per_100g   REAL,
  fiber_g_per_100g  REAL,
  sugar_g_per_100g  REAL,
  sodium_mg_per_100g REAL,
  -- ... ~74 cols per CIQUAL
  last_verified     TIMESTAMPTZ NOT NULL
);

CREATE TABLE local_nutrition (         -- user-photographed labels, overrides cross-DB lookup
  sku_uuid          UUID PRIMARY KEY REFERENCES sku_canonical(uuid),
  kcal_per_100g     REAL,
  protein_g_per_100g REAL,
  fat_g_per_100g    REAL,
  carb_g_per_100g   REAL,
  fiber_g_per_100g  REAL,
  source_photo_path TEXT NOT NULL,
  ocr_call_uuid     UUID REFERENCES llm_calls(call_uuid),
  observed_at       TIMESTAMPTZ NOT NULL,
  confirmed_by_user BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE local_nutrition_pending ( -- diff-review before overwrite
  sku_uuid          UUID NOT NULL,
  proposed_kcal     REAL,
  proposed_protein  REAL,
  proposed_fat      REAL,
  proposed_carb     REAL,
  ocr_call_uuid     UUID NOT NULL,
  proposed_at       TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (sku_uuid, proposed_at)
);

CREATE TABLE nutrient_dri (
  nutrient          TEXT NOT NULL,
  sex               CHAR(1) NOT NULL,
  age_min           INTEGER NOT NULL,
  age_max           INTEGER NOT NULL,
  ear              REAL,
  rda_ai           REAL,
  ul               REAL,
  unit             TEXT,
  source           TEXT NOT NULL,         -- 'efsa', 'iom', 'insp'
  PRIMARY KEY (nutrient, sex, age_min, age_max, source)
);

CREATE TABLE protein_quality (
  food_id           TEXT NOT NULL REFERENCES food_composition(food_id),
  pdcaas            REAL,
  diaas             REAL,
  leucine_per_100g_protein REAL,
  source            TEXT,
  PRIMARY KEY (food_id)
);

CREATE TABLE food_safety_temps (
  food_category     TEXT PRIMARY KEY,
  max_fridge_days   INTEGER,
  max_freezer_months INTEGER,
  safe_internal_temp_c REAL,
  source            TEXT NOT NULL          -- 'fda', 'fsis', 'insp'
);

CREATE TABLE food_storage_open (
  food_id           TEXT NOT NULL REFERENCES food_composition(food_id),
  days_after_open   INTEGER NOT NULL,
  storage_location  TEXT NOT NULL,
  PRIMARY KEY (food_id, storage_location)
);

CREATE TABLE substitution_rules (
  orig_food_id      TEXT NOT NULL REFERENCES food_composition(food_id),
  sub_food_id       TEXT NOT NULL REFERENCES food_composition(food_id),
  ratio             REAL NOT NULL DEFAULT 1.0,
  context           TEXT,
  notes             TEXT,
  PRIMARY KEY (orig_food_id, sub_food_id, context)
);

CREATE TABLE cooking_methods (
  food_id           TEXT NOT NULL REFERENCES food_composition(food_id),
  method            TEXT NOT NULL,        -- 'air-fryer', 'microwave', 'no-cook'
  temp_c            INTEGER,
  time_min          INTEGER,
  nutrient_retention_pct REAL,
  PRIMARY KEY (food_id, method)
);

CREATE TABLE glycemic_index (
  food_id           TEXT NOT NULL PRIMARY KEY REFERENCES food_composition(food_id),
  gi                INTEGER,
  gl_per_100g       REAL,
  source            TEXT
);

CREATE TABLE drug_food_interactions (
  drug_class        TEXT NOT NULL,
  food_trigger      TEXT NOT NULL,
  severity          TEXT NOT NULL,        -- 'critical', 'major', 'moderate', 'minor'
  mechanism         TEXT,
  source            TEXT NOT NULL,
  PRIMARY KEY (drug_class, food_trigger)
);

CREATE TABLE deficiency_symptoms (
  symptom           TEXT NOT NULL,
  candidate_nutrient TEXT NOT NULL,
  escalation_level  TEXT NOT NULL,        -- 'info', 'consider-bloodwork', 'see-doctor'
  source            TEXT NOT NULL,
  PRIMARY KEY (symptom, candidate_nutrient)
);

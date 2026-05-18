CREATE TABLE recipes (
  recipe_id         UUID PRIMARY KEY,
  name              TEXT NOT NULL,
  slug              TEXT UNIQUE NOT NULL,
  source_url        TEXT,
  prep_min          INTEGER,
  cook_min          INTEGER,
  servings_base     INTEGER NOT NULL DEFAULT 1,
  kcal_per_serving  REAL,
  protein_per_serving REAL,
  fat_per_serving   REAL,
  carb_per_serving  REAL,
  fiber_per_serving REAL,
  equipment_tags    TEXT[],               -- ['air-fryer', 'microwave', 'no-cook']
  cuisine_tags      TEXT[],
  satiety_score     REAL,
  variety_seed      TEXT,                 -- for boredom randomization
  -- embedding_recipe added in V010__pgvector_indexes.sql
  authority         TEXT NOT NULL,        -- 'user', 'youtube', 'article', 'cookbook', 'thealdb', 'derived'
  ingested_at       TIMESTAMPTZ NOT NULL,
  last_verified     TIMESTAMPTZ NOT NULL,
  status            TEXT NOT NULL DEFAULT 'active'  -- 'active', 'retired-boredom', 'failed-ingest'
);
CREATE INDEX idx_recipes_equip ON recipes USING GIN(equipment_tags);
-- idx_recipes_embedding ivfflat index moved to V010__pgvector_indexes.sql

CREATE TABLE recipe_ingredients (
  recipe_id         UUID NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
  ingredient_idx    INTEGER NOT NULL,
  food_id           TEXT REFERENCES food_composition(food_id),
  sku_uuid          UUID REFERENCES sku_canonical(uuid),
  qty               REAL,
  unit              TEXT,
  qty_raw           TEXT,                 -- '1 handful', 'a pinch'
  qty_confidence    TEXT NOT NULL,        -- 'high', 'medium', 'low'
  optional          BOOLEAN NOT NULL DEFAULT false,
  PRIMARY KEY (recipe_id, ingredient_idx)
);

CREATE TABLE recipe_steps (
  recipe_id         UUID NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
  step_idx          INTEGER NOT NULL,
  text              TEXT NOT NULL,
  est_min           INTEGER,
  PRIMARY KEY (recipe_id, step_idx)
);

CREATE TABLE recipe_ratings (
  recipe_id         UUID NOT NULL REFERENCES recipes(recipe_id),
  rated_at          TIMESTAMPTZ NOT NULL,
  taste_1_5         INTEGER NOT NULL,
  satiety_1_5       INTEGER NOT NULL,
  energy_1_5        INTEGER NOT NULL,
  notes             TEXT,
  PRIMARY KEY (recipe_id, rated_at)
);

CREATE TABLE boredom_rolling (
  recipe_id         UUID PRIMARY KEY REFERENCES recipes(recipe_id),
  last_eaten        TIMESTAMPTZ,
  served_count_21d  INTEGER NOT NULL DEFAULT 0,
  boredom_score     REAL NOT NULL DEFAULT 0,    -- recomputed nightly: Σ over last 21d of exp(-days_ago/half_life) × served_flag
  half_life_days    REAL NOT NULL DEFAULT 7,    -- per-recipe override; staples 14, distinctive 5
  hard_exclude_until DATE NULL                  -- explicit /bored cmd → 14d hard exclusion
);

CREATE TABLE adherence (
  date              DATE NOT NULL,
  planned_kcal      REAL,
  actual_kcal       REAL,
  planned_protein   REAL,
  actual_protein    REAL,
  variance_pct      REAL,
  PRIMARY KEY (date)
);

CREATE TABLE meal_plans (
  plan_id           UUID PRIMARY KEY,
  generated_at      TIMESTAMPTZ NOT NULL,
  starts_on         DATE NOT NULL,
  ends_on           DATE NOT NULL,
  slots_json        JSONB NOT NULL,         -- [{day, meal_label, recipe_id, planned_kcal, planned_protein, ...}]
  budget_target_lei REAL,
  cost_estimate_lei REAL,
  cost_lower_lei    REAL,
  cost_upper_lei    REAL,
  unknown_ratio     REAL,                   -- known/unknown price split
  rationale_md      TEXT                    -- LLM-generated explanation
);

CREATE TABLE shopping_lists (
  list_id           UUID PRIMARY KEY,
  generated_at      TIMESTAMPTZ NOT NULL,
  plan_id           UUID REFERENCES meal_plans(plan_id),
  items_json        JSONB NOT NULL,
  total_lei         REAL,
  status            TEXT NOT NULL           -- 'draft', 'active', 'completed'
);

CREATE TABLE loss_leader_alerts (
  id                BIGSERIAL PRIMARY KEY,
  sku_uuid          UUID NOT NULL REFERENCES sku_canonical(uuid),
  store_id          TEXT NOT NULL,
  discount_pct      REAL NOT NULL,
  detected_at       TIMESTAMPTZ NOT NULL,
  action_taken      TEXT                    -- 'plan-pivoted', 'noted', 'ignored'
);

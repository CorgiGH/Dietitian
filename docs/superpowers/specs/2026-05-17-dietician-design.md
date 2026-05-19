# Dietician — Design Spec

**Date:** 2026-05-17
**Status:** Locked, ready for plan-writing.
**Confidence:** 10/10 after 4 council passes + 3 research passes + Choco-on-Android smoke test + ntfy push decision.

---

## 0. Quick orientation

Personal Dietician at `C:\Users\User\Desktop\Dietician`. Kotlin Multiplatform Compose (Android phone + Windows desktop). VPS-canonical store on user's existing ByteHosting VPS (`46.247.109.91`) via Tailscale mesh (already configured for jarvis-kotlin). **Local-first writes + event-sourced ledger** — each client (phone + desktop) is the canonical writer for events it originates; VPS Postgres is the merged read replica + cross-device coordinator. Mergeable into `jarvis-kotlin` life-OS later as a Subsystem.

**Goal:** lessen friction with eating + cooking. Real RO supermarket prices. Weight tracking. Equipment-aware recipes. Boredom-aware variety. Budget-aware shopping. Pantry-aware planner.

**User profile:** UAIC year-1 AI student in Iași, Romania. 188cm / 67.5kg / lean-bulk / 2750 kcal / 137g protein. Air fryer + microwave only. Mobile-first capture, deep-work on laptop.

**Top non-negotiables (Council-locked):**
1. Local-first writes with event-sourced ledger (Council 4 BREAK fix #1).
2. Vision JSON corruption gate: cross-source confirm before baseline write, immutable raw archive, anomaly review queue >2.5σ (Council 3 BREAK #3).
3. Playwright out-of-process subprocess JAR with RSS ceiling (Council 3 BREAK #16).
4. Two-phase budget reserve + queue-time provider re-eval (Council 3 BREAK #5).
5. Sensor-fusion price model: append-only `price_observations` + posterior view per-source half-life (Council 3 STRUCTURAL).
6. App-layer health check (NOT Tailscale self-report) (Council 4 BREAK #3).
7. Event-driven sync (ntfy push + WS + outbox-replay) — NOT periodic WorkManager (Council 4 BREAK #12 #22).
8. Move GROBID + backups + corpus embeddings to VPS (Council 4 BREAK #14 #18).
9. Local nutrition override table populated from label OCR (decoupled from pantry-add) (Council 3 BREAK #7).
10. `/diag` first-class command + 10-failure-mode runbook (Council 4 mandates).

---

## 1. Personal context (LLM identity locked here)

Identity baked into LLM system prompts and `user_profile.yml`:

```yaml
identity:
  name: Victor
  age: 19
  sex: M
  location_primary: Iași, RO
  latitude: 47.16
  jurisdiction: RO/MD (per memory: Moldova residence, UAIC studies in Iași)
anthropometrics:
  height_cm: 188
  weight_kg: 67.5
  weight_date: 2026-05-17
  body_fat_pct: ~          # optional
goal:
  active: lean-bulk
  rate_kg_per_week: 0.2-0.3
  weight_target_kg: 75     # tentative
training:
  modality: resistance
  sessions_per_week: 4
  activity_multiplier: 1.5
equipment:
  air-fryer: true
  microwave: true
  stove: false
  oven: false
budget:
  monthly_ron: ~           # user-set
constraints:
  allergies: []
  intolerances: []
  dislikes: []             # populated from ratings
  ethical: []
clinical:
  conditions: []
  meds: []
  supplements: []
language:
  primary: en
  cook_with: en+ro
stores_primary: [mega-image, carrefour, auchan]
stores_flyer: [kaufland, lidl]
stores_fresh: [piata-nicolina, piata-alexandru-cel-bun]
```

**Computed at run-time (recompute on weight Δ≥1kg sustained 2wk OR training volume Δ≥20%):**
- BMR (Mifflin-St Jeor M, age 19): `10×67.5 + 6.25×188 − 5×19 + 5 = ~1760 kcal`
- TDEE estimate: BMR × 1.5 = ~2640 kcal (calibrated against observed weight trend)
- Lean-bulk target kcal: 2750 (=TDEE + ~110 surplus → ~0.2 kg/wk)
- Protein: 2.0 g/kg = 135-137 g
- Fat floor: 0.8 g/kg = 54 g
- Carbs training day: `(2750 - 137×4 - 54×9) / 4 = ~429 g`
- Per-meal protein floor: 0.4 g/kg = 27 g
- Hydration: 30-35 ml/kg = 2.0-2.4 L + 500-1000 ml/h training
- Fiber: ≥39 g/day
- Vit D supplement: 1000-2000 IU Oct 1 → Apr 1 (Iași 47°N = 5-mo vit D winter)
- Creatine: 5 g/day

---

## 2. Architecture overview

```
VPS (46.247.109.91, Ubuntu 22.04, ByteHosting, Tailscale-meshed)
├── Postgres 16 + pgvector (canonical merge-replica + cross-device coordinator)
├── Ktor backend on Tailscale IP only, port 8081 (REST + WebSocket)
├── GROBID Docker (paper PDF parse, ~400MB, throttled cpus=1.5)
├── ntfy Docker (self-hosted push, ~10MB, Tailscale-bound)
├── earlyoom (prefers MC/trading-bot, avoids Postgres/Dietician)
├── pg_dump → rclone → Backblaze B2 nightly cron
├── VPS-side scraper crons (Monitorul mobile-app HTTP, VTEX search API)
└── Existing co-tenants: MC Paper (heap 3G post-reduction), jarvis-web :8080, trading-bot, study-proxy

Desktop (Windows, Compose Multiplatform — KMP)
├── :shared:* business logic (data + domain + knowledge + llm + network + ui-components)
├── :desktopApp Compose UI (thick client)
├── Local SQLite cache (read snapshot) + event-sourced ledger (write canonical for desktop-originated)
├── Subprocesses: ClaudeMax CLI (claude --bare -p), Playwright JAR (per-chain), whisper.cpp, yt-dlp
├── Obsidian browses wiki/ (git-pulled from VPS canonical)
└── HTTP+WS client to VPS over Tailscale IP

Phone (Android, Compose Multiplatform — KMP)
├── :shared:* same business logic (desktop-only modules excluded at module level)
├── :androidApp Compose UI (thin capture + planner query)
├── Local SQLite cache + event-sourced ledger (write canonical for phone-originated)
├── CameraX, EncryptedSharedPreferences + Android Keystore, ntfy Android client app
├── WorkManager — outbox-replay-on-network-available ONLY (NOT periodic)
└── HTTP+WS client to VPS over Tailscale IP
```

**KMP module layout (Gradle):**

```
:shared
  :shared:data           — SQLDelight schema, repository, ledger, sensor-fusion
  :shared:domain         — Choco solver, planner, budget, prefs, boredom decay
  :shared:knowledge      — wiki, BM25+embeddings, ingest pipelines (light parts)
  :shared:llm            — LlmProvider sealed interface, router, circuit-breakers
  :shared:network        — Ktor client, retry/backoff, sync protocol
  :shared:ui-components  — Compose Multiplatform shared widgets
:androidApp              — Android-specific actuals
:desktopApp              — Windows-specific actuals
:server                  — VPS Ktor backend (JVM-only, depends on :shared minus client-only modules)
```

**Tailscale ACL (user must apply via login.tailscale.com/admin/acls):**

```jsonc
{
  "tagOwners": {
    "tag:dietician-backend": ["autogroup:admin"],
    "tag:dietician-client":  ["autogroup:admin"]
  },
  "acls": [
    { "action": "accept",
      "src": ["tag:dietician-client"],
      "dst": ["tag:dietician-backend:8081"] }
  ]
}
```

VPS gets `tag:dietician-backend`. Phone + desktop nodes get `tag:dietician-client`. No wildcard, deny-by-default for everything else (jarvis nodes can't reach Dietician backend, etc.).

---

## 3. Write topology — local-first event-sourced

**The core insight (Council 4):** pantry inventory is a SUM of deltas, not a state assignment. LWW conflict resolution loses one of two simultaneous "I ate X" events. Event-sourcing makes the merge a SUM, conflict-free.

**Event log tables (same schema on all three: phone SQLite, desktop SQLite, VPS Postgres):**

```sql
CREATE TABLE pantry_events (
  event_uuid     UUID PRIMARY KEY,
  device_id      TEXT NOT NULL,           -- phone-android-pixel7 / desktop-windows-laptop / vps-cron
  originated_at  TIMESTAMPTZ NOT NULL,
  synced_at      TIMESTAMPTZ NULL,        -- NULL = pending in outbox
  sku_uuid       UUID NOT NULL,
  delta_qty      REAL NOT NULL,           -- positive = added (purchase), negative = consumed
  unit           TEXT NOT NULL,           -- g, ml, buc
  reason         TEXT,                    -- 'receipt', 'voice:I used the chicken', 'manual edit'
  evidence_ref   TEXT                     -- receipt_uuid, voice_memo_uuid, etc.
);
CREATE INDEX idx_pantry_events_sku ON pantry_events(sku_uuid);
CREATE INDEX idx_pantry_events_originated ON pantry_events(originated_at);

CREATE TABLE meal_events (
  event_uuid    UUID PRIMARY KEY,
  device_id     TEXT NOT NULL,
  originated_at TIMESTAMPTZ NOT NULL,
  synced_at     TIMESTAMPTZ NULL,
  meal_label    TEXT NOT NULL,            -- 'breakfast', 'lunch', 'dinner', 'snack-1', etc.
  recipe_id     UUID NULL,                -- if planned
  ingredients_json JSONB NOT NULL,        -- [{sku_uuid, qty, unit}] actual eaten
  kcal_actual   REAL,
  protein_actual REAL,
  rating_1_5    INTEGER NULL,
  notes         TEXT
);

CREATE TABLE weight_events (
  event_uuid    UUID PRIMARY KEY,
  device_id     TEXT NOT NULL,
  originated_at TIMESTAMPTZ NOT NULL,
  synced_at     TIMESTAMPTZ NULL,
  weight_kg     REAL NOT NULL,
  time_of_day   TEXT,                     -- 'morning-fasted', 'evening', etc.
  conditions    TEXT
);

CREATE TABLE receipt_events (
  event_uuid    UUID PRIMARY KEY,
  device_id     TEXT NOT NULL,
  originated_at TIMESTAMPTZ NOT NULL,
  synced_at     TIMESTAMPTZ NULL,
  store_id      TEXT NOT NULL,
  total_lei     REAL,
  image_ref     TEXT NOT NULL,            -- VPS-side /storage/receipts/{uuid}.jpg
  ocr_status    TEXT NOT NULL,            -- 'pending', 'processed', 'failed'
  ocr_provider  TEXT,                     -- 'claudemax-cli', 'gemini-vision', 'manual'
  line_items_json JSONB                   -- parsed
);

CREATE TABLE outbox (
  event_uuid    UUID PRIMARY KEY,
  table_name    TEXT NOT NULL,
  payload_json  JSONB NOT NULL,
  queued_at     TIMESTAMPTZ NOT NULL,
  attempts      INTEGER NOT NULL DEFAULT 0,
  last_error    TEXT NULL
);
```

**Pantry "current quantity" derived view (per-client + VPS):**

```sql
CREATE VIEW pantry_current AS
SELECT sku_uuid, SUM(delta_qty) AS qty, unit, MAX(originated_at) AS last_event_at
FROM pantry_events
WHERE synced_at IS NOT NULL OR device_id = current_device_id()
GROUP BY sku_uuid, unit
HAVING SUM(delta_qty) > 0;
```

**Mutable metadata (food name, expiry, open-status):** stored in separate `pantry_metadata(sku_uuid, expiry_date, open_status, last_modified_at, last_modified_device)` with LWW per-row (correct here — it's an assignment, not a delta).

**Sync protocol:**

1. Each client (phone, desktop) writes to its local SQLite immediately, queues row in `outbox`.
2. WorkManager / scheduler: when network available, drain outbox via `POST /sync/push` to VPS Ktor.
3. VPS merges into Postgres canonical, sets `synced_at`, returns ACK.
4. VPS broadcasts via WebSocket (foreground) + ntfy push (background): "new events from device_X, table=Y, count=N".
5. Other clients pull via `POST /sync/pull?since=<last_synced_at>` and merge into their local SQLite.
6. **Idempotency:** `event_uuid` is the dedup key. Replay-safe.

**Conflict resolution:**
- Event tables (pantry/meal/weight/receipt): no conflicts — SUM/append-only.
- Metadata (`pantry_metadata`, `user_profile`, `equipment_registry`, `prefs`): LWW per row, surfaced in UI when divergence detected ("desktop changed expiry while you were offline — keep yours or theirs?").

**Outbox health surface:** `/diag` shows `outbox_depth` per device; alert if > 50 entries for > 24h.

---

## 4. Postgres canonical schema (VPS-side, extends per-client SQLite)

Beyond event tables (§3), VPS Postgres has:

### 4.1 SKU + price tables (sensor-fusion per Council 3)

```sql
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
```

### 4.2 Price observations (append-only sensor log per Council 3 STRUCTURAL)

```sql
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

-- Per-source half-life (seconds), used by posterior recomputation
-- Bringo (Playwright live): 4d half-life
-- Per-chain Playwright (live): 4d
-- Monitorul Prețurilor (gov aggregated): 7d
-- Flyer Vision (printed brochure): 14d
-- User receipt OCR (canonical at that moment): 2d (sharpest)
```

**Vision JSON corruption gate (Council 3 BREAK #3):**
- Every `llm:vision` row in `price_observations` MUST also be confirmed by Monitorul OR Playwright observation within ±7d before contributing to `price_posterior`.
- Weekly anomaly batch: any `price_observations.price_minor` > 2.5σ from 30d same-SKU median → enqueue to review.

### 4.3 Baselines + budget

```sql
CREATE TABLE budgets (
  week_iso          TEXT PRIMARY KEY,   -- '2026-W21'
  target_lei        REAL NOT NULL,
  actual_lei        REAL DEFAULT 0,
  set_at            TIMESTAMPTZ NOT NULL
);

CREATE TABLE llm_budget (
  provider          TEXT PRIMARY KEY,   -- 'claudemax-sdk' | 'openrouter'
  reset_at          TIMESTAMPTZ NOT NULL,
  ceiling_cents     INTEGER NOT NULL,   -- $200 → 20000 cents for claudemax-sdk Max 20x
  used_cents        INTEGER NOT NULL DEFAULT 0,
  reserved_cents    INTEGER NOT NULL DEFAULT 0  -- two-phase reserve
);

CREATE TABLE model_price_table (
  provider          TEXT NOT NULL,
  model_id          TEXT NOT NULL,
  input_cents_per_mtok  REAL NOT NULL,
  output_cents_per_mtok REAL NOT NULL,
  refreshed_at      TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (provider, model_id)
);

CREATE TABLE llm_calls (
  call_uuid         UUID PRIMARY KEY,
  provider          TEXT NOT NULL,
  model_id          TEXT NOT NULL,
  prompt_hash       TEXT NOT NULL,
  reserved_cents    INTEGER NOT NULL,
  actual_cents      INTEGER NULL,
  input_tokens      INTEGER NULL,
  output_tokens     INTEGER NULL,
  started_at        TIMESTAMPTZ NOT NULL,
  completed_at      TIMESTAMPTZ NULL,
  status            TEXT NOT NULL,        -- 'reserved' | 'in_flight' | 'completed' | 'failed' | 'timeout'
  error             TEXT NULL,
  request_ref       TEXT NULL,            -- raw request archive
  response_ref      TEXT NULL             -- raw response archive
);
```

> **Errata 2026-05-17 #1 — MC heap NOT reduced.** Spec originally proposed reducing MC heap 4G→3G. User vetoed mid-execution. MC stays at 4G original config. See [[feedback-dont-touch-mc]].
>
> **Errata 2026-05-17 #2 — GROBID MOVED TO DESKTOP.** With MC at 4G (not reduced), GROBID's 1.5GB peak resident eats too much VPS headroom. User chose to relocate GROBID to desktop. Effects:
> - GROBID Docker NOT installed on VPS. ntfy stays.
> - Desktop requires Docker Desktop install (~2GB on Windows) before paper-fetch pipeline works. Documented as user task.
> - Paper ingestion gated on desktop being online (queue via `pending_jobs` table on VPS Postgres; desktop polls and processes).
> - VPS footprint reduced from ~860MB to ~460MB (Postgres 200 + Ktor 250 + ntfy 10). Leaves ~1.9GB buffer.
> - earlyoom NOT installed (no longer needed since headroom is comfortable).
> - Runbook 4 (grobid-hung) updated to reflect desktop-side location.

### 4.4 Knowledge corpus tables (~15 per research pass 1)

```sql
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
  embedding_recipe  VECTOR(384),          -- pgvector — for "similar to X"
  authority         TEXT NOT NULL,        -- 'user', 'youtube', 'article', 'cookbook', 'thealdb', 'derived'
  ingested_at       TIMESTAMPTZ NOT NULL,
  last_verified     TIMESTAMPTZ NOT NULL,
  status            TEXT NOT NULL DEFAULT 'active'  -- 'active', 'retired-boredom', 'failed-ingest'
);
CREATE INDEX idx_recipes_equip ON recipes USING GIN(equipment_tags);
CREATE INDEX idx_recipes_embedding ON recipes USING ivfflat (embedding_recipe vector_cosine_ops);

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
```

### 4.5 Pending review queues (Council 2 + 3 fixes)

```sql
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

CREATE TABLE credential_heartbeat (    -- Council 3 fix #7
  credential_name   TEXT PRIMARY KEY,     -- 'anelis', 'lidl-plus', 'mega-connect', 'openrouter'
  last_used_at      TIMESTAMPTZ,
  last_success_at   TIMESTAMPTZ,
  expected_to_work_at TIMESTAMPTZ NOT NULL,
  status            TEXT NOT NULL           -- 'ok', 'degraded', 'broken'
);
```

---

## 5. Per-client SQLite cache schema (Android + Desktop)

Mirror VPS schema with these additions:
- `outbox` table — pending writes not yet synced
- `cache_metadata(table_name, last_sync_at, last_event_uuid)` — sync state per table
- WAL mode mandatory: `PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA busy_timeout=5000; PRAGMA cache_size=-64000; PRAGMA foreign_keys=ON`
- Read-pool 4 connections, write-pool 1 (SQLite serializes writes)
- HikariCP `connectionInitSql` for the above

Phone CACHES (read-only mirror, refreshed on sync): `sku_canonical`, `sku_source_id`, `price_posterior`, `food_composition`, `nutrient_dri`, `food_safety_temps`, `substitution_rules`, `cooking_methods`, `recipes`, `recipe_ingredients`, `recipe_steps`, `stores`, `user_location_state`, `boredom_rolling`, recent `meal_plans`, recent `shopping_lists`.

Phone WRITES (canonical for phone-originated, syncs to VPS): `pantry_events`, `meal_events`, `weight_events`, `receipt_events` (image uploaded to VPS first, event references it), `pantry_metadata` edits, `user_location_current` for this device, `recipe_ratings`.

---

## 6. Sync protocol

### 6.1 REST endpoints (VPS Ktor on Tailscale IP:8081)

```
POST /sync/push
  body: { device_id, events: [ { table_name, event_uuid, payload_json } ] }
  resp: { accepted: [event_uuid], rejected: [{event_uuid, reason}] }

POST /sync/pull
  body: { device_id, cursors: { table_name: { timestampMs: Long, eventUuid: String } } }
  resp: { rows: [{ table_name, event_uuid, originated_at_ms, payload_json, server_recv_at }], server_time_ms: Long }
  # Council #1 BREAK #3 fix (post-impl council #2 amendment): the cursor is a
  # (timestampMs, eventUuid) tuple rather than a bare `timestamptz`. Half-open
  # `> cursor` semantics with the uuid tiebreaker guarantee no row is served
  # twice and no row is dropped when multiple events share an originated_at_ms
  # millisecond. See PullCursorPropertyTest for the property proof and
  # api/Cursor.kt for the canonical Kotlin shape.

POST /receipts/upload
  multipart: image
  resp: { receipt_uuid, image_ref }

GET  /health
  resp: { vps_time, postgres_ok, grobid_ok, ntfy_ok, llm_budget_remaining_cents, last_scraper_status }

GET  /diag/{device_id}
  resp: { last_seen_other_devices, outbox_depth_per_device, pending_jobs_count, scraper_status, llm_budget_per_provider, last_3_errors }

WS   /ws/sync
  push: { type: 'new_events', tables: [..], counts: {..} }
        { type: 'job_completed', job_id, result_ref }
        { type: 'price_alert', sku_uuid, store_id, discount_pct }

POST /jobs/queue
  body: { job_type, payload_json, required_provider? }

POST /jobs/{id}/result
  body: { result_json }
```

### 6.2 ntfy push topic structure

ntfy topics (URL-published from VPS, subscribed by phone via Tailscale-IP ntfy URL):
- `dietician-v-{device_id}` — per-device push (private, hashed device_id)
- `dietician-v-broadcast` — system-wide (loss-leader alerts, scraper down)

Phone ntfy app configured with VPS Tailscale IP + topic name; subscribes silently in background.

### 6.3 Health check (Council 4 BREAK #3)

Both clients run a coroutine:
- Foreground: ping `/health` every 60s. 3 failures → OFFLINE banner.
- Background (Android): every 15min via WorkManager one-shot.

Banner text: "Tailscale route to VPS unreachable. Check Tailscale daemon + VPS status."

DO NOT trust `tailscaled` status — control plane up ≠ data plane reachable.

---

## 7. LLM provider design

### 7.1 Sealed interface (`:shared:llm`)

```kotlin
sealed interface LlmProvider {
    val id: String
    val supports: Set<Capability>     // {TEXT, VISION, TOOL_USE, STREAMING, EMBEDDINGS}
    val state: ProviderState           // OK | DEGRADED | DOWN (Resilience4j CircuitBreaker)

    suspend fun complete(
        request: LlmRequest,
        @RequiresNoActiveTransaction _guard: Unit = Unit
    ): LlmResponse

    suspend fun completeStream(request: LlmRequest): Flow<LlmStreamChunk>
    suspend fun embeddings(texts: List<String>): List<FloatArray>   // only if EMBEDDINGS in supports
}

class ClaudeMaxCliProvider : LlmProvider {
    override val id = "claudemax-cli"
    // Only on :desktopApp. Subprocess: claude --bare -p ... --allowedTools "Read"
    // For Vision: prompt references file path, model invokes Read tool internally.
    // Quota tracked against $200/mo Max 20x Agent SDK credit (post-June-15-2026 split).
}

class OpenRouterProvider(model: String) : LlmProvider {
    override val id = "openrouter:$model"
    // HTTP client to openrouter.ai. Both platforms.
    // For Vision: model = "google/gemini-2.0-flash-exp" (cheap) or "anthropic/claude-3.5-sonnet" (fallback).
}

class OllamaLocalProvider(model: String) : LlmProvider {
    override val id = "ollama:$model"
    // HTTP to localhost:11434 (desktop only). Used for embeddings fallback if Voyage/OpenAI unavailable.
}
```

### 7.2 Router (~300 LOC in `:shared:llm`)

```kotlin
class LlmRouter(
    private val providers: List<LlmProvider>,
    private val budget: BudgetLedger,
    private val callStore: LlmCallStore,        // persisted to canonical Postgres
    private val config: RouterConfig             // TOML: fallback chains per Capability
) {
    suspend fun call(request: LlmRequest, capability: Capability): LlmResponse {
        val chain = config.chainFor(capability)         // ordered list of providers
        val idempotencyKey = IdempotencyKey(promptHash(request), modelClass(capability))

        // Two-phase budget reserve (Council 3 BREAK #5)
        val maxPriceCents = chain.first().let { p ->
            val price = lookupModelPrice(p.id, request.model)
            (price.input * request.estTokensIn + price.output * request.estMaxTokensOut).toInt()
        }
        budget.reserve(provider = chain.first().id, cents = maxPriceCents, callUuid = idempotencyKey)
            ?: throw BudgetExceededException(chain.first().id)

        try {
            for (provider in chain) {
                if (provider.state == ProviderState.DOWN) continue
                try {
                    val resp = withTimeout(120.seconds) { provider.complete(request) }
                    val actualCents = computeActualCost(provider.id, resp)
                    budget.reconcile(provider = provider.id, reservedCents = maxPriceCents, actualCents = actualCents, callUuid = idempotencyKey)
                    callStore.recordSuccess(idempotencyKey, provider.id, resp)
                    return resp
                } catch (e: ProviderError) {
                    callStore.recordFailure(idempotencyKey, provider.id, e)
                    continue   // fall through to next provider in chain
                }
            }
            throw AllProvidersFailedException(chain.map { it.id })
        } finally {
            budget.releaseUnused(callUuid = idempotencyKey)
        }
    }
}
```

### 7.3 Router config (TOML at `state/llm-router.toml`)

```toml
[fallback_chain.VISION]
chain = ["claudemax-cli", "openrouter:google/gemini-2.0-flash-exp"]
# claudemax-cli only on desktop; phone always uses openrouter:google/gemini-2.0-flash-exp

[fallback_chain.TEXT_HARD]
chain = ["openrouter:anthropic/claude-3.5-sonnet", "claudemax-cli"]
# Hard nutrition reasoning, planner ranking, recipe ingestion

[fallback_chain.TEXT_MECHANICAL]
chain = ["openrouter:google/gemini-2.0-flash-exp", "openrouter:anthropic/claude-3.5-haiku"]
# Wiki edits, index updates, format conversion

[fallback_chain.EMBEDDINGS]
chain = ["openrouter:voyage/voyage-3-lite", "ollama:nomic-embed-text"]

[fallback_chain.WHISPER]
chain = ["local-whispercpp"]
# Local only, never API by default

[router]
default_timeout_sec = 120
two_phase_reserve_enabled = true
idempotency_persist_pre_call = true
log_raw_responses = true     # required for Vision corruption gate
raw_response_dir = "state/llm-raw"
```

### 7.4 ClaudeMax CLI subprocess (desktop-only)

```kotlin
class ClaudeMaxCliProvider(
    private val binary: String = "claude",
    private val workspaceDir: File,        // sandbox for Read-tool path access
    private val budget: ClaudeMaxBudget    // $200/mo Max 20x credit ledger
) : LlmProvider {

    override suspend fun complete(request: LlmRequest): LlmResponse {
        val args = buildList {
            add(binary); add("--bare"); add("-p")
            add("--output-format"); add("stream-json")
            add("--verbose")
            request.allowedTools.takeIf { it.isNotEmpty() }?.let {
                add("--allowedTools"); add(it.joinToString(","))
            }
            request.model?.let { add("--model"); add(it) }
        }
        val process = ProcessBuilder(args)
            .directory(workspaceDir)
            .redirectErrorStream(false)
            .start()

        process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.prompt) }

        // Parse stream-json for api_retry / completion events
        val output = StringBuilder()
        var lastError: String? = null
        process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val event = Json.parseToJsonElement(line).jsonObject
                when (event["type"]?.jsonPrimitive?.content) {
                    "system" -> {
                        event["api_retry"]?.let { retry ->
                            lastError = retry.jsonObject["error"]?.jsonPrimitive?.content
                            if (lastError in setOf("rate_limit", "billing_error")) {
                                budget.markDegraded()
                                throw QuotaExceededException(lastError!!)
                            }
                        }
                    }
                    "result", "completion" -> output.appendLine(event["text"]?.jsonPrimitive?.content)
                }
            }
        }
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw TimeoutException("claude --print")
        }
        return LlmResponse(text = output.toString(), provider = id, ...)
    }
}
```

### 7.5 Two-phase budget reserve

```kotlin
class BudgetLedger(private val db: Database) {

    fun reserve(provider: String, cents: Int, callUuid: UUID): ReservationToken? = db.transaction {
        val row = exec("SELECT used_cents, reserved_cents, ceiling_cents FROM llm_budget WHERE provider=? FOR UPDATE", provider)
            ?: return@transaction null
        val available = row.ceiling_cents - row.used_cents - row.reserved_cents
        if (cents > available) {
            log.warn("budget_reservation_rejected", provider, cents, available)
            return@transaction null
        }
        exec("UPDATE llm_budget SET reserved_cents = reserved_cents + ? WHERE provider=?", cents, provider)
        exec("INSERT INTO llm_calls (call_uuid, provider, reserved_cents, started_at, status) VALUES (?, ?, ?, now(), 'reserved')", callUuid, provider, cents)
        ReservationToken(callUuid, cents)
    }

    fun reconcile(callUuid: UUID, actualCents: Int) = db.transaction {
        val reserved = exec("SELECT reserved_cents FROM llm_calls WHERE call_uuid=?", callUuid)?.reserved_cents ?: error("no reservation")
        exec("UPDATE llm_budget SET reserved_cents = reserved_cents - ?, used_cents = used_cents + ? WHERE provider = (SELECT provider FROM llm_calls WHERE call_uuid=?)", reserved, actualCents, callUuid)
        exec("UPDATE llm_calls SET actual_cents=?, status='completed', completed_at=now() WHERE call_uuid=?", actualCents, callUuid)
    }
}
```

Alert thresholds:
- 80% used → UI yellow badge, log
- 95% used → UI red badge, queued Vision auto-routes to Gemini fallback regardless of chain order
- 100% used → reject new reservations, surface in `/diag`

### 7.6 Anti-recommend source exclusion list

Hardcoded into LLM router pre-prompt:
- carnivore-only / fruitarian / detox-cleanse / fad-elimination evangelists
- MLM nutrition (Herbalife, Plexus, Isagenix, Beachbody, Modere)
- "Anti-inflammatory diet" influencer content without RCT backing
- Adrenal fatigue / leaky gut non-clinical experts
- Pegan / Whole30 absolutism
- Carnivore MD / Liver King personas
- "Studies show" without DOI/PubMed link
- Naturopath sources
- Recipe blogs with no nutrition breakdown
- Fasts >36h non-medical recommendation
- Pre-2018 T-Nation legacy

### 7.7 iter-11 amendment — Coach routing + 2PC audit (2026-05-19, council 1779208184)

Coach text routing splits along the platform axis:

- **Desktop client** routes ClaudeMax CLI subprocess locally (uses Max-20x credit) bookended by `POST /coach/reserve` + `POST /coach/commit` against the server. Server inserts a pending `audit_log` row (`status='pending'`) at reserve time and updates it at commit time — Art 13 disclosure is recorded BEFORE the LLM call returns. The 60s saga-compensation cron (`refund_orphaned`, V022) flips never-committed rows to `orphaned` while refunding the budget reservation (Stripe-shaped auth-hold + capture). Reservation TTL is 120 s, > the 90 s SSE idle-timeout, so the saga never reclaims a still-streaming reservation.
- **Android client** and **Desktop-non-ClaudeMax fallback** route through `POST /coach/stream` SSE. Server pairs reserve + LlmRouter + commit in one coroutine. 25 s heartbeat + 90 s idle-timeout on the SSE response avoid ktor-cio proxy buffering. A terminal `event: end` frame signals clean completion. **Status (iter-11):** SSE route + 2PC plumbing are live; the server-side `LlmStream` binding is a fail-loud noop pending the real `LlmRouterStream` wire from the `:shared:llm` provider table — Android Coach surfaces a 500 with "not wired" until that lands (iter-11.5). Desktop ClaudeMax path is unaffected and ships activated.
- ClaudeMax CLI participates only in the Desktop Coach text path. Server-side `LlmRouter` chain for Coach is OpenRouter → Groq fallback; ClaudeMax remains in the Vision OCR chain unchanged.

`audit_log.status` state machine: `pending → success | failed | aborted | orphaned`. The `orphaned` value is set exclusively by the saga compensation cron when a `pending` row exceeds its `reserved_until` deadline. Clients implement an `audit_pending_outbox` table (SQLDelight, client-side) so that desktop crashes between reserve and commit replay the commit on next startup; `/coach/commit` returns `200 status='not_reserved'` for unknown keys so orphan outbox rows can drain without infinite-retry loops.

---

## 8. Vision OCR flows

### 8.1 Receipt OCR (per user's locked Q answer)

```
Phone takes receipt photo (CameraX)
  → Phone HTTP POST /receipts/upload (multipart, Tailscale)
  → VPS stores at /storage/receipts/{uuid}.jpg
  → VPS inserts receipt_event with ocr_status='pending'
  → VPS checks: is any device with capability='vision-claudemax' online (heartbeat < 60s)?
      YES → VPS inserts pending_jobs(job_type='ocr_receipt', required_provider='desktop')
             Desktop polls jobs every 30s, picks up, runs ClaudeMax CLI on image
             Posts result via POST /jobs/{id}/result
             VPS updates receipt_event.ocr_provider='claudemax-cli', line_items_json=...
      NO → VPS calls OpenRouter Gemini Vision directly via shared LlmRouter
            VPS updates receipt_event.ocr_provider='gemini-vision', line_items_json=...
  → VPS emits ntfy push to phone: {type:'receipt-processed', event_uuid, line_items_count}
  → Phone displays parsed items, user reviews
  → User confirms → derive pantry_events (positive deltas per line item) + insert (locally + outbox)
```

**Per-chain receipt prompt template** (`templates/receipt/<chain>.md`):
- Mega Image / Carrefour: 3-col format
- Kaufland: unit-price separately
- Lidl: SKU printed (often poor thermal quality → preprocess with imgscalr contrast/threshold)
- Auchan: separate VAT-rate columns

Output schema enforced via prompt:
```json
{
  "store_id": "mega-carol-i",
  "date": "2026-05-17T18:42:00",
  "total_lei": 87.42,
  "line_items": [
    {"line_text_raw": "...", "name_normalized": "...", "qty": 1.5, "unit": "kg",
     "unit_price_lei": 29.99, "total_lei": 44.99, "confidence": 0.95}
  ]
}
```

### 8.2 Nutrition label OCR (decoupled from pantry-add, Council 3 BREAK #7)

- `/photo X` in-app cmd OR batched-when-idle from `pending_jobs(job_type='ocr_label', sku_uuid=X)`
- Dual-pass Vision agreement within 5% → write `local_nutrition`
- Disagreement → `local_nutrition_pending` for user review (never silent overwrite)
- Reject if any value not parseable (don't fall to USDA on bad scan; mark `nutrition_source='fallback'` only when no local row exists)
- Loose produce (no label) → hardcoded reference macros per category (`vegetables_leafy`, `vegetables_root`, `fruit_temperate`)

### 8.3 Flyer Vision (weekly, ClaudeMax CLI desktop-only)

- Cron Mon 06:00 on VPS: `monitorul_fetch.sh` downloads weekly flyer PDFs from kaufland.ro/cataloage-cu-reduceri.html + lidl.ro/c/cataloage-online/...
- VPS publishes to `pending_jobs(job_type='parse_flyer', required_provider='desktop')`
- Desktop picks up, runs ClaudeMax CLI per-page with structured output schema:
  ```json
  {
    "price_value": 12.99, "price_unit": "lei/kg",
    "pack_size": "500g", "multibuy": null,
    "valid_from": "2026-05-19", "valid_to": "2026-05-25",
    "confidence": 0.92, "raw_text": "..."
  }
  ```
- Validation: reject if `confidence < 0.7` OR unit missing OR price `> 2x baseline` OR `< 0.3x baseline` OR `valid_from > today+14d` → `flyer_review_queue`
- Dual-pass agreement within 5% required before write to `promo_observations`

### 8.4 Vision JSON corruption gate (Council 3 BREAK #3 — load-bearing)

Every Vision call:
1. Raw response written to `state/llm-raw/{call_uuid}.txt` BEFORE parse
2. Strict-mode parser (strip fences, JSON-schema validate)
3. On parse failure: log + skip + queue for review, NEVER partial-extract
4. On parse success but value out-of-band: `vision_anomaly_queue`
5. `price_observations` row with `extraction_call_id=call_uuid` + `raw_evidence_ref=state/llm-raw/{call_uuid}.txt`
6. **`price_posterior` recomputation EXCLUDES `source='llm:vision'` UNLESS confirmed by Monitorul OR Playwright observation within ±7d.**

---

## 9. Sensor-fusion price model

### 9.1 Three-tier SKU match (Council 2 fix #2, refined Council 3 #9)

```
For each scraper-emitted (source, source_id, name_raw, gtin?, size_g?):
  T1: if gtin: lookup sku_source_id WHERE gtin=? → if hit, link to canonical_uuid
  T2: else: normalize_name(name_raw) = strip RO stopwords (de|fără|cu|și|la|în|pe|proaspăt|congelat|fresh)
         + extract size+unit tokens
         + Jaccard(normalized_tokens) against all sku_canonical.normalized_name where category matches
         + if best_score > 0.85 AND size_within(5%) → link
  T3: else: queue to sku_match_queue
         For non-receipt sources, first-scrape bypasses T3 and auto-creates canonical with confidence=unverified
         Surface as daily digest "review N new SKUs" (not 5/day cap)
```

### 9.2 Per-source confidence + half-life (Council 4 fix #4 refined)

| Source | Confidence | Half-life |
|--------|-----------|-----------|
| User receipt OCR | 1.0 | 2 days (sharpest, but localized) |
| Bringo Playwright (live) | 0.85 | 4 days |
| Per-chain Playwright (live, JSON-LD) | 0.9 | 4 days |
| Monitorul Prețurilor (gov aggregated) | 0.75 | 7 days |
| Flyer Vision (printed brochure) | 0.6 | 14 days |

### 9.3 Posterior recomputation

Background coroutine on VPS, every 5 min:

```kotlin
// For each (sku_uuid, store_id) with new observations since last_computed:
val obs = price_observations
    .filter { it.observed_at > now() - 30.days && it.source != "llm:vision" || confirmedByOther(it) }
val weights = obs.map { (now() - it.observed_at).inSeconds.exp(-1 / it.source.halfLifeSec) * it.source.confidence }
val pointEstimate = obs.zip(weights).sumOf { (o, w) -> o.price * w } / weights.sum()
val confidence = weights.sum().clamp(0, 1)
val n = obs.size; val span = obs.maxOf { it.observed_at } - obs.minOf { it.observed_at }
val phase = if (n >= 5 && span >= 14.days) "stable" else "initial"
price_posterior.upsert(sku_uuid, store_id, pointEstimate, confidence, n, span.days, phase)
```

### 9.4 Median-bootstrap promo isolation (Council 3 fix #5)

- Bootstrap: when n < 5, posterior = `median(observed)` not max (Council 4 fixed Council 3's `max` to `median`).
- Sale detection (`current < 0.9 * baseline`) fires ONLY when `phase = 'stable'`. Under `initial`: surface "first-seen price, no baseline yet" instead.
- Secondary heuristic: any observation `< 0.7 * rolling_median_30d` flagged `suspected_promo` regardless of source flag → routed to `promo_observations`.

---

## 10. Scrape execution

### 10.1 Per-chain Playwright (out-of-process per Council 3 BREAK #16)

**Subprocess JAR at `scrapers/playwright-scraper.jar`** (separate Gradle module `:scrapers:playwright`):
- Launched by VPS-side cron OR by Desktop when desktop-online
- Single Chromium process reused, `--single-process --disable-dev-shm-usage`
- Sequential per-chain (Mega-VTEX → Carrefour-VTEX → Auchan → Kaufland → Lidl)
- Persistent storageState per chain at `storage/<chain>.json`
- Per-chain `expectedMinResults`; result < expected*0.5 AND result_delta_yesterday > 0.5 → `scrape_health=broken`
- Sentinel selectors per page (product-level anchor `.product-card` with `price-class` AND `name-class` children)
- 3 consecutive broken runs → auto-disable, ntfy alert; manual `/scraper enable mega` resume
- Auto-re-enable probe after 7 days disabled
- RSS cap 4GB → kill youngest Chromium, reschedule
- Retry policy: 1-attempt-per-6h hard cap for personalized-discount auths (Lidl Plus / Mega CONNECT)

### 10.2 VTEX adapter (Mega Image + Carrefour)

```kotlin
class VtexAdapter(private val baseUrl: String, private val storeId: String) {
    suspend fun searchProducts(query: String): List<VtexProduct> {
        val url = "$baseUrl/api/catalog_system/pub/products/search/$query"
        // VTEX standard JSON: { items: [{ productId, productName, brand, ean, items: [{ sellers: [{ commertialOffer: { Price, ListPrice, ... }}] }] }] }
    }
    suspend fun fetchProductBySku(sku: String): VtexProduct? { ... }
}

// Configurations
val megaVtex = VtexAdapter("https://www.mega-image.ro", "mega")
val carrefourVtex = VtexAdapter("https://www.carrefour.ro", "carrefour")
```

VTEX adapter does NOT need Playwright — pure HTTP. Runs on VPS or either client.

### 10.3 Monitorul Prețurilor

- Decompile `ro.ingr.pmonand` Android APK (jadx-gui) to extract HTTP endpoints
- Likely no cert pinning (gov-funded consumer app); confirm via mitmproxy
- Build Kotlin HTTP adapter, runs on VPS-side cron (daily)
- Treat schema as fragile (may break on app release); circuit-breaker + fallback to per-chain Playwright

### 10.4 Bringo (Playwright desktop-only)

- Dedicated Bringo account (NOT user's primary Carrefour identity)
- Persistent storageState
- Polite ceiling: ≤1 req/10s, ≤300 req/day
- Tagged demoted to "Playwright" tier in source-confidence ladder (no longer "API")

### 10.5 Flyer download (VPS cron)

- `kaufland.ro/cataloage-cu-reduceri.html` HTML scrape → PDF URLs
- `lidl.ro/c/cataloage-online/s10019911` HTML scrape → PDF URLs
- Download to `/storage/flyers/{store}/{week_iso}.pdf`
- Enqueue `pending_jobs(job_type='parse_flyer', payload={pdf_path}, required_provider='desktop')`

### 10.6 Scraper health-check contract

```kotlin
interface Scraper {
    val source: String
    val expectedMinResults: Int
    val sentinelSelectors: List<String>
    suspend fun scrape(): ScrapeResult
}

data class ScrapeResult(
    val items: List<RawProduct>,
    val healthStatus: HealthStatus,    // OK | DEGRADED | BROKEN
    val sentinelsMet: Boolean,
    val rawHtmlRef: String?            // last-known-good HTML stored for diff
)
```

3-strike rule:
- 1 broken → log warning + reduce cadence to hourly + diff vs last-known-good HTML
- 2 broken → reduce cadence again
- 3 broken → disable, ntfy alert, require manual `/scraper enable {source}` in app

Re-enable: after disabled-window (7d default), fresh sentinel-only probe; if pass, cautious single scrape; user gets digest of re-enable attempts (no required response, only override).

---

## 11. Knowledge corpus

### 11.1 Wiki structure

```
wiki/
├── recipes/              # one MD per recipe; .data.md sibling per Council 4 two-file pattern
├── ingredients/          # one MD per (chicken-breast.md + chicken-breast.data.md)
├── equipment/
├── stores/
├── body/
├── prefs/
├── meal-plans/           # one MD per week
├── shopping-lists/       # one MD per week
├── summaries/            # LLM-generated narrative ("breakfasts-that-work-for-me.md")
├── knowledge/
│   ├── nutrition/
│   │   ├── macronutrients/{protein,fat,carbs,fiber,alcohol}.md
│   │   ├── micronutrients/{vit-d,vit-b12,iron,zinc,magnesium,...}.md
│   │   ├── bioavailability/{heme-iron,calcium-iron-competition,vit-c-iron,...}.md
│   │   ├── protein-quality.md
│   │   ├── leucine-mps.md
│   │   └── glycemic-response.md
│   ├── methodology/
│   │   ├── lean-bulk-principles.md     # instantiated to user
│   │   ├── cut-principles.md
│   │   ├── recomp-conditions.md
│   │   ├── iifym.md
│   │   ├── carb-cycling.md
│   │   ├── refeeds-diet-breaks.md
│   │   ├── mini-cut-criteria.md
│   │   ├── reverse-diet.md
│   │   └── rp-template-structure.md
│   ├── cooking/
│   │   ├── salt-fat-acid-heat.md
│   │   ├── air-fryer-mastery.md        # user-equipment-specific
│   │   ├── microwave-mastery.md
│   │   ├── air-fryer-time-temp-matrix.md
│   │   ├── maillard-and-browning.md
│   │   ├── seasoning-timing.md
│   │   ├── brining.md
│   │   ├── flavor-pairing.md
│   │   ├── umami-stacking.md
│   │   └── texture-contrast.md
│   ├── food-safety/
│   │   ├── temperature-fundamentals.md
│   │   ├── danger-zone.md
│   │   ├── safe-internal-temps.md
│   │   ├── leftovers-shelf-life.md
│   │   ├── freezer-quality-table.md
│   │   ├── reheating-rules.md
│   │   ├── cross-contamination.md
│   │   ├── eggs-ro-handling.md
│   │   └── mold-discard-rules.md
│   ├── meal-prep/
│   │   ├── batch-cooking-strategy.md
│   │   ├── component-cooking.md
│   │   ├── sunday-prep-playbook.md
│   │   ├── freezer-vs-fridge-decision.md
│   │   ├── reheat-friendliness-rules.md
│   │   └── mid-week-refresh.md
│   ├── shopping/
│   │   ├── ro-supermarket-map.md
│   │   ├── mega-image-strategy.md
│   │   ├── carrefour-strategy.md
│   │   ├── auchan-strategy.md
│   │   ├── kaufland-flyer-cadence.md
│   │   ├── lidl-flyer-cadence.md
│   │   ├── piata-vs-supermarket.md
│   │   ├── unit-price-math.md
│   │   ├── brand-vs-private-label.md
│   │   ├── loss-leader-triggers.md
│   │   ├── whole-bird-economics.md
│   │   └── bulk-buy-break-evens.md
│   ├── budget/
│   │   ├── cost-per-100g-protein-rankings.md
│   │   ├── cost-per-1000-kcal-rankings.md
│   │   ├── substitution-ladder-protein.md
│   │   ├── substitution-ladder-carb.md
│   │   ├── substitution-ladder-fat.md
│   │   └── food-waste-economics.md
│   ├── sports-nutrition/
│   │   ├── pre-workout-meal.md
│   │   ├── intra-workout.md
│   │   ├── post-workout-window.md
│   │   ├── training-day-vs-rest-day.md
│   │   ├── hydration-protocol.md
│   │   ├── electrolytes.md
│   │   ├── creatine.md
│   │   ├── caffeine-pre-workout.md
│   │   └── supplements/{whey,creatine,vit-d,omega-3,magnesium,caffeine}.md
│   ├── recovery/
│   │   ├── sleep-and-appetite.md
│   │   ├── sleep-hygiene.md
│   │   ├── recovery-markers.md
│   │   └── overtraining-warning-signs.md
│   ├── ro-context/
│   │   ├── seasonal-produce-calendar-ro.md
│   │   ├── ro-traditional-macro-friendly.md
│   │   ├── orthodox-fasting-impact-on-prices.md
│   │   ├── ro-ingredient-equivalents.md
│   │   ├── iasi-specific-stores.md
│   │   ├── iasi-cantine.md
│   │   └── iasi-piete.md
│   ├── clinical/
│   │   ├── refusal-triggers.md
│   │   ├── eating-disorder-red-flags.md
│   │   ├── drug-food-interactions.md
│   │   ├── deficiency-symptom-flagging.md
│   │   ├── when-to-see-doctor.md
│   │   └── disclaimers.md
│   ├── behavioral/
│   │   ├── adherence-science.md
│   │   ├── variety-and-hedonic-adaptation.md
│   │   ├── habit-loop-design.md
│   │   ├── friction-reduction.md
│   │   └── pre-commitment-devices.md
│   ├── recipe-science/
│   │   ├── satiety-drivers.md
│   │   ├── volumetrics.md
│   │   ├── garnish-discipline.md
│   │   └── recipe-construction-template.md
│   ├── pantry/
│   │   ├── fifo-fefo.md
│   │   ├── pantry-zoning.md
│   │   ├── open-package-shelf-life.md
│   │   └── inventory-audit.md
│   ├── special-diets/
│   │   ├── vegan-protein-stacking.md
│   │   ├── vegan-supplement-essentials.md
│   │   ├── gluten-free-swaps.md
│   │   ├── lactose-intolerance-workarounds.md
│   │   ├── low-fodmap.md
│   │   ├── keto-math.md
│   │   ├── mediterranean-profile.md
│   │   └── halal-kosher-ro-sourcing.md
│   └── meta/
│       ├── source-anti-recommend-list.md
│       ├── source-authority-ranking.md
│       └── citation-style.md
├── index.md             # auto-maintained index of all wiki pages
└── log.md               # append-only chronological event log
```

### 11.2 Two-file pattern (Council 2 fix #3)

For every wiki page that references live data:
- `chicken-breast.md` — LLM narrative, user-editable, NO numeric facts
- `chicken-breast.data.md` — autogen table with header `<!-- AUTOGENERATED. EDITS OVERWRITTEN. Last refresh: TIMESTAMP -->`
- Narrative uses Obsidian transclusion: `![[chicken-breast.data]]`
- Daemon writes only to `.data.md` files
- File-watcher: if user edited narrative within last hour, suppress data regenerate that session

### 11.3 YAML frontmatter

```yaml
---
title: "Lean Bulk Principles"
slug: lean-bulk-principles
domain: methodology
applies_to: [user-victor]
sources:
  - name: "ISSN Position Stand: Protein and Exercise"
    url: "https://pmc.ncbi.nlm.nih.gov/articles/PMC5477153/"
    citation: "Jäger et al. 2017"
    accessed: 2026-05-17
authority: peer-reviewed         # peer-reviewed | textbook | practitioner | gov-guideline | user-note | derived
confidence: high
last_verified: 2026-05-17
review_cadence_days: 365
instantiated_for_user: true
user_numbers:
  bw_kg: 67.5
  bulk_kcal_target: 2750
  protein_g_target: 137
  fat_g_min: 54
related: [refeeds-diet-breaks, mini-cut-criteria, rp-template-structure]
contradicts: []
supersedes: []
tags: [hypertrophy, surplus, macros]
---
```

### 11.4 Datasets (raw/)

```
raw/
├── recipes-src/{youtube/, articles/, cookbooks/, voice-notes/}
├── flyers/{kaufland,lidl}/{YYYY-WW.pdf}
├── receipts/{store_id}/{YYYY-MM-DD-uuid.jpg}
├── voice-notes/{uuid.ogg}
├── llm-raw/{call_uuid.txt}           # immutable raw LLM responses (Vision corruption gate)
├── textbooks/{krause-mahan-16e.pdf, mcgee-on-food-and-cooking.pdf, ...}
├── papers/{issn/, fao/, efsa/, ro/}/{slug.pdf}
└── datasets/
    ├── usda-fdc/{full-download.json, foundation.csv, sr-legacy.csv, branded.csv, fndds.csv}
    ├── ciqual-2025/{table-ciqual-2025.xlsx}
    ├── open-food-facts/{ro-products.csv}
    └── efsa-drv/{drv-finder-export.csv}
```

### 11.5 Refresh cadence per source

- USDA FDC: yearly (Apr release check)
- CIQUAL: every 2y, check Jan + Jul
- EFSA DRVs: rare, news feed
- Open Food Facts RO subset: weekly delta sync
- Examine.com per supplement: re-fetch on user query, flag if last_verified > 90d
- ISSN position stands: yearly check
- MASS Research Review: monthly issue ingest (Anelis paywall — see §13)
- RO seasonal produce calendar: monthly review
- User baseline (BMR/TDEE/macros): on weight Δ≥1kg sustained 2wk OR training Δ≥20%

---

## 12. Recipe ingestion pipelines

### 12.1 Article URL (lightweight, either platform)

`Phone/Desktop: paste URL → :shared:knowledge.ingestArticle()`
- Jsoup + readability extract
- `recipe-schema.org` JSON-LD parse if present
- LLM summarize + structure → `recipes` row + `recipe_ingredients` + `recipe_steps`
- Macros via `food_composition` lookup per ingredient + qty
- Embeddings computed locally (Ollama nomic-embed-text on desktop, Voyage via OpenRouter on phone if no local)
- Wiki page generated: `wiki/recipes/{slug}.md` + `.data.md`

### 12.2 YouTube video (heavy, desktop-only, queued from phone)

```
Phone: paste URL → POST /jobs/queue {job_type='ingest_video', payload={url}, required_provider='desktop'}
Desktop: polls pending_jobs, picks up
  → yt-dlp --skip-download --write-subs --write-auto-subs --sub-lang ro,en,es --convert-subs srt --write-info-json --write-description {url}
  → if no usable subs: yt-dlp -x --audio-format wav {url} → whisper.cpp large-v3-turbo → transcript
  → Diacritic normalize: ş→ș, ţ→ț (Council 2 fix)
  → info.json chapters → split into N candidate recipes
  → LLM structure each candidate (with sponsor-content detection prompt: "ignore segments containing 'use my discount code' or 'sponsored by'")
  → Frame OCR pass on key timestamps (Vision LLM) for overlay-text quantities (TikTok-style)
  → User-confirm draft via in-app UI before persist
  → Channel allowlist: Israetel, Nippard, Buttermore, Norton, Helms, MASS, Kenji, Ragusea, Chlebowski, JamilaCuisine, Adamache, Savori Urbane
  → POST /jobs/{id}/result {recipes_drafted: N}
  → VPS ntfy push to phone: "Video ingested, N recipe drafts pending review"
```

### 12.3 Cookbook PDF photo (heavy, desktop-only)

- Camera photo → upload to VPS → desktop pulls
- Tesseract OCR (Council 2 path) → LLM clean → identify chapter vs recipe → ingest via respective pipeline
- Source frontmatter: `{source: 'book-title', page: N, accessed: date}`

### 12.4 Academic paper PDF (VPS-side GROBID per Council 4 fix #4)

```
User cites DOI in voice/text → POST /jobs/queue {job_type='fetch_paper', payload={doi}}
VPS:
  1. Semantic Scholar /paper/DOI:{doi}  → metadata, similar papers
  2. Unpaywall /v2/{doi}?email=victor.vasiloi@gmail.com  → OA PDF URL if available
  3. If OA: fetch PDF; else Anelis Plus session (TBD investigation §13)
  4. GROBID /api/processFulltextDocument → TEI XML (title/authors/abstract/sections/refs)
  5. Per-section LLM summarize via cheapest OpenRouter model
  6. Synthesis call → wiki page `wiki/knowledge/{domain}/papers/{slug}.md`
  7. Cross-link via embeddings (pgvector similarity) — top-5 candidates, ONE LLM call confirms+writes backlinks
  8. Per-paper budget cap: 8 LLM calls + 30K tokens
```

### 12.5 Nutrition label OCR (decoupled from pantry-add)

See §8.2.

### 12.6 Voice memo

```
Phone: in-app voice record (.ogg/Opus via Android MediaRecorder)
  → Upload to VPS POST /voice/upload
  → VPS routes to desktop pending_jobs OR (if desktop offline) VPS ffmpeg + whisper.cpp
  → Mandatory pre-process: ffmpeg arnndn noise suppression
  → whisper.cpp large-v3-turbo with --language ro,en
  → Diacritic normalize ş→ș, ţ→ț
  → LLM intent classify (free text): {recipe-note, preference, clinical-context, shopping-thought, weight-log, pantry-event}
  → Route:
      preference → append to wiki/prefs/{topic}.md
      clinical: append to wiki/clinical/personal-restrictions.md
      recipe idea: draft new wiki/recipes/{slug}.md
      weight: weight_event INSERT (with confidence flag)
      shopping: append wiki/shopping-lists/inbox.md
      pantry: pantry_event INSERT (with FEFO disambiguation flow §16)
  → Push notification: "Voice processed: 'You ate the chicken — decremented oldest batch.'"
```

### 12.7 Forwarded message / link (phone Share Intent)

- Android: Share Intent → app handler
- Triage: recipe / supplement claim / restaurant / store tip / paper / article
- Route to respective pipeline

### 12.8 Open Food Facts barcode

- Phone CameraX → barcode scan
- HTTP GET `https://world.openfoodfacts.net/api/v2/product/{barcode}`
- If hit: prefill nutrition label data
- If miss: queue for user nutrition photo

---

## 13. Anelis Plus paper-fetch (TBD investigation)

**Status:** auth model unknown to user. Investigate before implementation.

**Possible models:**
- Shibboleth/SAML federated login (typical for RO academic libraries) → complex headless Playwright flow + 2FA handling
- Simple username/password portal → simpler
- IP-auth via UAIC campus network → unusable from VPS unless VPN to UAIC

**Investigation plan (gated for impl phase, NOT spec phase):**
1. User logs into Anelis Plus via browser on desktop
2. Inspect network: where does auth flow go? Cookies / SAML / IP-auth?
3. If Shibboleth: capture session cookie after manual login → daemon refreshes weekly via user-initiated browser session → no password storage
4. If simple portal: store password via Jagged age + per-platform secure store
5. If IP-auth: requires Tailscale exit-node on UAIC network OR manual session export

**Spec interface:** `AnelisPaperFetcher.fetch(doi: String): Result<File>` — implementation TBD. Until wired, `paperFetcher.fetch()` returns `Result.failure(Unavailable("Anelis investigation pending"))` and pipeline falls through to Unpaywall-only.

---

## 14. Pantry module

### 14.1 Event-sourced ledger (see §3)

### 14.2 FEFO + open-status disambiguation (Council 2 + Council 4)

```sql
CREATE TABLE pantry_metadata (
  sku_uuid          UUID PRIMARY KEY REFERENCES sku_canonical(uuid),
  expiry_date       DATE,
  open_status       TEXT NOT NULL DEFAULT 'sealed',  -- 'sealed' | 'opened'
  open_date         DATE,
  last_modified_at  TIMESTAMPTZ NOT NULL,
  last_modified_device TEXT NOT NULL
);

-- Each pantry_event can have multiple "batches" (purchase events of same SKU at different times).
-- When user says "I used the chicken":
--   1. Find all pantry_events with SUM(delta) > 0 grouped by purchase_date
--   2. Order by (open_status = 'opened' DESC, expiry_date ASC NULLS LAST, originated_at ASC)
--   3. Decrement first batch in priority order
--   4. Respond: "Decremented from batch DATE. Reply 'no, the other one' to swap within 1h."
```

### 14.3 Voice "I ate X" flow

See §12.6.

### 14.4 Receipt-derived pantry_events

Mode A (default): unmapped lines optimistically decrement most-likely match with `confidence=guessed` flag. Mode B (opt-in): pure-queue.

Auto-promote receipt aliases after 3 confirmed matches.

Daily summary surfaces "we guessed N decrements — review?" rather than blocking each.

Weekly batch-review UI on localhost dashboard (now in-app Compose) for plow-through-50-at-once.

---

## 15. Body log + macro targets

See §1 for computed user variables.

**Cascade triggers:**
- Weight Δ≥1kg sustained 2wk → recompute BMR/TDEE/macros, regenerate all "applied-to-you" wiki sections, log entry
- Training volume Δ≥20% → recompute activity multiplier
- Cut/maintenance/bulk goal switch → switch active methodology page
- New equipment → unlock previously-blocked recipes
- Pantry vacation mode → suppress perishable recommendations
- Travel mode → switch to "restaurant-friendly target" with relaxed bounds

---

## 16. Equipment registry

```
wiki/equipment/
├── air-fryer.md          # model + capacity + typical wattage + time/temp matrix
├── microwave.md          # wattage + max time + plate dimensions
└── none.md               # what's NOT available (stove, oven, blender, ...)
```

Recipe filter: `equipment_tags ⊆ user_equipment ∪ {no-cook}` AND every step's required device is owned.

Equipment-fail fallback rules in `:shared:domain.EquipmentFallback`:
- Air fryer broken → oven 200°C +15% time (but user has no oven, so → pan-fry / microwave + browning step skipped)
- Microwave broken → steam reheat in covered pan + splash water (no stove → cold reheat)

---

## 17. Preferences + boredom

```sql
CREATE TABLE preferences (
  pref_id           UUID PRIMARY KEY,
  pref_type         TEXT NOT NULL,        -- 'exclude', 'crave', 'prefer-cuisine'
  target            TEXT NOT NULL,        -- food_id, recipe_id, cuisine_tag, sku_uuid, ingredient_category
  level             TEXT NOT NULL,        -- 'hard' (never) | 'soft' (penalize) | 'positive' (boost)
  reason            TEXT,
  set_at            TIMESTAMPTZ NOT NULL,
  expires_at        TIMESTAMPTZ NULL,     -- /bored cmd = +14d
  source            TEXT NOT NULL         -- 'manual', 'voice', 'inferred'
);
```

**Boredom decay** (Council 2 fix):
- Ingredient-specific half-life: staples 14d (chicken_breast, rice, eggs), distinctive 5d (lasagna, curry_thai), default 7d
- `boredom_score = Σ over last 21d of exp(-days_ago/half_life_per_recipe) * served_flag`, capped at 1.0
- `final_score_in_planner = preference_weight - 0.4 * boredom_score - explicit_dislike_penalty`
- Skipped meal logged → +0.5
- Rating <3/5 → +1.0
- Explicit `/bored chicken` cmd → 14d hard exclusion via `preferences` row
- `/why-not chicken` cmd shows score breakdown

Weights user-tunable via `prefs/boredom.toml`.

---

## 18. Planner

### 18.1 Constraint solver (Choco-solver, JVM MIT)

```kotlin
class ChocoMealPlanner(
    private val recipes: List<Recipe>,
    private val pantry: PantryCurrent,
    private val equipment: Set<String>,
    private val macroTargets: MacroTargets,
    private val timeSlots: List<TimeSlot>,
    private val prefs: List<Preference>,
    private val boredomScores: Map<UUID, Double>,
    private val budgetTargetLei: Double?,
    private val priceEstimator: PriceEstimator
) {
    fun solve(): List<MealPlan> {
        val model = Model("DieticianMealPlan")
        val mealVars = timeSlots.map { slot ->
            model.intVar("meal_${slot.day}_${slot.slotIdx}", filteredRecipeIds(slot, equipment, prefs).toIntArray())
        }
        // Equipment compatibility
        // Time-slot compatibility (recipe.prep+cook <= slot.minutes_available)
        // Pantry-covers OR shoppable-in-budget
        // Daily macro sums in [target ± 5% kcal, target+ ≥ 100% protein, fat ≥ floor]
        // Per-recipe per-week max (boredom)
        // Hard-exclude prefs
        // Cost ≤ budget_target * 1.05 (upper bound from §19)
        // Find top-K plans
        // Return ordered by (variety_index, soft_preference_match, cost)
    }
}
```

**RuleBasedPlanner fallback** — always available, no LLM:
- Pure SQL filter: `SELECT FROM recipes WHERE equipment_tags <@ user_equipment AND ingredients_in_pantry_or_in_budget`
- Greedy fit by macro target
- Worst-case usable when LLM stack down

### 18.2 LLM-as-ranker (post-Choco)

After Choco returns top-K candidate plans:
- LLM ranks by qualitative fit (preference alignment, variety vibes, cuisine rotation)
- Output explanation written to `meal_plans.rationale_md`
- Macros computed deterministically post-pick — if off, regenerate

### 18.3 Trigger-based actions (Council 2)

- If protein <80% target by 18:00 → suggest protein-dense snack
- If weight loss >0.5% BW/wk 2 consecutive weeks during bulk → kcal +150
- If weight gain >0.7% BW/wk during bulk → kcal -100
- If user logs <70% meals 3+ days → simplify recommendations
- If 2+ recipes rated <3/5 in week → regenerate plan with input
- Pantry item <72h to expiry → priority recipe slot

---

## 19. Budget

```sql
-- See budgets, model_price_table, llm_budget tables in §4.3
```

### 19.1 Cost estimator (split known/unknown)

```kotlin
fun estimateRecipeCost(recipe: Recipe, location: UserLocation): RecipeCostEstimate {
    val known = mutableListOf<Pair<Ingredient, Double>>()
    val unknown = mutableListOf<Ingredient>()
    for (ing in recipe.ingredients) {
        val sku = ing.sku_uuid ?: ing.matchHeuristic()
        val price = priceCache.currentPriceAtNearestStore(sku, location)
        if (price != null) known += ing to (price.minor / 100.0 * ing.qty)
        else unknown += ing
    }
    val knownSum = known.sumOf { it.second }
    val unknownEstimate = unknown.sumOf { categoryMedianPrice(it.foodCategory) * it.qty }
    val sigma = unknown.sumOf { categoryPriceStdev(it.foodCategory) * it.qty }
    return RecipeCostEstimate(
        knownSum = knownSum, unknownEstimate = unknownEstimate, sigma = sigma,
        upperBound = knownSum + unknownEstimate + sigma,
        knownCount = known.size, unknownCount = unknown.size
    )
}
```

UI surfaces: "Recipe cost: 32.50 lei ± 8.00 lei (5 of 8 priced)".

### 19.2 Overpriced filter

Active only when `n_observations_in_nutritional_category >= 8`. Metric: `cost_per_100g_protein > 1.5 * category_median_per_100g_protein` for protein sources. Same shape for `cost_per_kcal` on carb staples. Below threshold, bypass with logged reason `insufficient_baseline_data`.

### 19.3 Budget solver

In Choco constraint: `sum(plan_cost_upper_bound) <= budget_target * 1.05`. Recipes with `unknown_ratio > 0.5` demoted but not removed; trigger scrape priority bump.

UI explanation: "Fits 14 meals @ 180 lei OR 14 meals + 1 treat @ 210 lei".

---

## 20. Shopping list builder

```kotlin
fun buildShoppingList(plan: MealPlan, pantry: PantryCurrent, location: UserLocation): ShoppingList {
    val required = plan.aggregateIngredients()
    val toBuy = required.minus(pantry)
    val byStore = toBuy.groupBy { ing -> cheapestStore(ing.sku_uuid, location) }
    // Annotate per item: best store, price posterior, sale window if any, alternative SKUs if expensive
    return ShoppingList(items = byStore.flatMap { ... }, total = ...)
}
```

Store priority comes from `user_location_current` → `user_location_state.store_priority_json`.

---

## 21. Location-aware store catalog (user requirement 2026-05-17)

See `user_location_state` + `user_location_current` schema in §4.4.

Phone auto-detection via Android Location Services (Compose Multiplatform → `androidApp` actual). Fallback `/where iasi-tudor` cmd in-app.

Per-location attributes:
- Nearby store_ids
- 24h availability flags
- Bringo delivery eligibility
- Piață days
- Recipe constraints (e.g., when home for weekend, scope to home pantry + home-stores; no Bringo)

---

## 22. Notifications (Council 4 + ntfy locked)

**Push channel:** self-hosted ntfy on VPS (`docker run binwiederhier/ntfy serve`, bound to Tailscale IP).

**Phone:** ntfy Android app subscribes to topic `dietician-v-{device_id_hashed}`.

**Tiers (in-app implementation, ntfy carries `priority` header):**
- P0 (stockout / safety): ntfy `priority=urgent`, immediate, bypass quiet hours
- P1 (sale window / time-sensitive): `priority=high`, awake window only
- P2 (adherence reminder): `priority=default`, batch in AM/PM digest
- P3 (info / weekly trend): `priority=low`, weekly Sun AM digest

**Quiet hours:** 22:00-08:00 default, configurable. P0 ignores; P1-P3 queue until exit.

**Fatigue cap:** 5 messages/day default. Excess auto-batches into next digest.

**`/snooze 4h`:** silences all but P0 for 4h.

---

## 23. `/diag` command (Council 4 mandate)

In-app, one-screen output (same on phone + desktop):

```
DIETICIAN /diag — 2026-05-17 16:42:17

CONNECTIVITY
  VPS Ktor (Tailscale 100.x.y.z:8081)    : OK   (last ping 14s ago)
  Tailscale daemon                        : OK   (control plane), ROUTABLE (data plane)
  Postgres                                : OK
  ntfy topic dietician-v-pixel7-victor    : SUBSCRIBED (last push 2h ago)

WRITES
  Last successful write                   : 2 min ago (pantry_event, this device)
  Outbox depth                            : 0
  Last sync push                          : 14s ago
  Last sync pull                          : 14s ago

OTHER DEVICES
  desktop-windows-laptop                  : ONLINE (last heartbeat 32s ago)

LLM BUDGET
  claudemax-sdk  (Max 20x)                : $43 / $200 used (21%), reset 2026-06-15
  openrouter                              : 92¢ / 200¢ used (46%, monthly cap user-set)
  reservations in-flight                  : 0¢ across 0 calls

SCRAPER STATUS
  monitorul                               : OK   (last scrape 6h ago, 28,402 SKUs)
  bringo (carrefour iulius)               : OK   (last scrape 4h ago, 2,103 watched-SKUs)
  mega-vtex                               : OK   (last scrape 4h ago)
  carrefour-vtex                          : OK   (last scrape 4h ago)
  auchan-playwright                       : DEGRADED (1 broken run; next probe 1h)
  kaufland-playwright                     : OK
  lidl-playwright                         : OK
  flyer-vision-kaufland                   : OK   (last run Mon 06:00, parsed 18/20pp)
  flyer-vision-lidl                       : OK   (last run Mon 06:00, parsed 22/24pp)

LAST 3 ERRORS
  16:38  llm:openrouter      timeout after 120s
  14:21  scraper:auchan      sentinel selector .product-card missing
  09:14  ocr:gemini-vision   confidence 0.43, queued to flyer_review_queue

PENDING JOBS
  ocr_receipt                              : 0
  ingest_video                             : 2 (waiting for desktop)
  fetch_paper                              : 0
  parse_flyer                              : 0
```

---

## 24. Runbook (10 failure modes — Council 4 mandate)

Stored at `docs/runbooks/` with one MD per scenario.

| # | Symptom | Likely cause | User action |
|---|---------|--------------|-------------|
| 1 | App banner "Tailscale route to VPS unreachable" | tailscaled down on phone OR VPS sshd/Postgres/Ktor crashed OR network outage | `tailscale status` on phone; SSH to VPS `systemctl status dietician-backend postgresql ntfy`; check VPS `tailscale ip` |
| 2 | `/diag` shows ClaudeMax budget 100% used | $200/mo Agent SDK credit exhausted | Wait for monthly reset, OR enable Anthropic "extra usage" billing, OR queued Vision will auto-route to Gemini |
| 3 | Desktop offline > 24h, jobs queue growing | Desktop closed, network unreachable, or daemon crashed | Open desktop, verify `dietician-desktop` app running; if crash, restart; if jobs critical, use `/process via gemini` in-app to force VPS-side processing |
| 4 | `/diag` shows GROBID hung | Docker container deadlocked | SSH to VPS `docker restart grobid`; if recurring, increase `--memory` allocation |
| 5 | Outbox depth > 50 on phone | VPS Ktor returning 5xx OR Tailscale broken OR app's network code hung | Check `/diag` for VPS health; force sync `/sync push`; if Postgres conn refused, SSH VPS `systemctl restart postgresql` |
| 6 | Postgres `conn refused` from Ktor backend | Postgres process down OR systemd `MemoryMax` killed it | SSH `journalctl -u postgresql --since=-10min`; if OOM, check earlyoom log + adjust `MemoryMax` |
| 7 | WebSocket reconnect-storm | Backend dropping connections OR client retry storm bug | Check Ktor logs; client should backoff exponentially — verify in `LlmRouter`/`SyncClient` retry policy |
| 8 | ntfy push not delivered to phone | ntfy server down OR phone unsubscribed OR Tailscale broken | SSH `docker logs ntfy`; verify phone Tailscale + ntfy app subscription |
| 9 | Anelis credential rotation (auth fails on paper fetch) | UAIC password changed OR session cookie expired | Re-export Anelis session via desktop browser; `/credentials rotate anelis` in-app |
| 10 | Sentinel selector missing on scraper | Chain redesigned page layout | Check `state/scraper-last-known-good/<chain>.html` diff; update scraper selectors in `:scrapers:playwright/<chain>/Selectors.kt` |

---

## 25. Backup + DR

- **Primary:** VPS-side cron `pg_dump -Fc dietician | rclone rcat b2:dietician-backups/{date}.dump.zst` nightly
- **Secondary verify:** desktop weekly `rclone copy b2:dietician-backups/ {OneDrive}/dietician-backups/`
- **Wiki:** git repo `/opt/dietician/wiki/` on VPS, pushed to private GitHub remote (user creates)
- **Raw files:** `/storage/{flyers,receipts,llm-raw}/` rsync to B2 weekly
- **Cost:** Backblaze B2 at $0.006/GB/mo + $0.01/GB egress; estimated ~$0.10-0.50/mo

**Restore drill** (documented in `docs/runbooks/restore.md`):
1. Provision new VPS / Postgres
2. `rclone copy b2:dietician-backups/{latest}.dump.zst .`
3. `pg_restore -d dietician {latest}.dump.zst`
4. `git clone {wiki remote}`
5. `rclone copy b2:dietician-raw/ /storage/`
6. Re-apply Tailscale auth, ntfy config, scraper credentials
7. Restart `dietician-backend.service`

---

## 26. Credential storage

**Per-platform actuals** (KMP `expect`/`actual`):

```kotlin
// commonMain
expect class SecureCredentialStore {
    suspend fun put(key: String, value: ByteArray)
    suspend fun get(key: String): ByteArray?
    suspend fun delete(key: String)
}

// androidMain
actual class SecureCredentialStore {
    // EncryptedSharedPreferences with MasterKey from Android Keystore (AES-256-GCM)
}

// desktopMain (Windows)
actual class SecureCredentialStore {
    // DPAPI via windpapi4j (CryptProtectData/CryptUnprotectData), per-user-per-machine
    // Mirror also written age-encrypted (Jagged) at state/credentials.age
    // Passphrase NEVER on disk — user's password manager only
}

// serverMain (VPS)
actual class SecureCredentialStore {
    // age-encrypted file at /etc/dietician/credentials.age
    // Passphrase read from /run/dietician/passphrase (tmpfs, populated on daemon start)
    // Daemon start path: user SSHs in, populates the tmpfs via /opt/dietician/bin/unlock
}
```

**Credentials to store:**
- OpenRouter API key
- Anelis Plus session cookie / password (TBD)
- Lidl Plus account
- Mega Image CONNECT account
- Bringo account
- B2 application key (VPS only)
- ntfy auth token (if user enables ntfy auth)
- GitHub remote PAT (if wiki pushed)

**Recovery flow:**
- Daemon start: try platform-native (DPAPI/Keystore); on fail Telegram-equivalent in-app prompt OR `/credentials restore <passphrase>` cmd
- `credential_heartbeat` table tracks per-credential expected-to-work timestamps; absence >7d → alert

---

## 27. Security model

- VPS Dietician backend binds Tailscale IP only (no public exposure, no nginx route)
- Tailscale ACL: `tag:dietician-client` → `tag:dietician-backend:8081` only; deny everything else
- Postgres: bound to `127.0.0.1` + Tailscale IP only; never `0.0.0.0`
- ntfy: same
- GROBID: same
- All credentials encrypted at rest via §26
- BitLocker on Windows desktop (system drive)
- Android: full-disk encryption (default on modern Android)
- SSH key-only auth on VPS (password leak rotation history per memory)
- ufw still INACTIVE on VPS (independent of Dietician, advise enabling)

---

## 28. Refusal triggers + macro guardrails (LLM prompt baked)

**Hardcoded into LLM system prompt:**

```
Refuse / escalate when user:
- Asks for kcal below BMR sustained → "see RDN"
- Asks for weight loss > 1 kg/week sustained → "see RDN"
- Asks to eliminate entire food group without medical reason → "see RDN" + cite wiki/clinical/eating-disorder-red-flags.md
- Reports clinical symptoms (faints, chest pain, sustained GI distress) → "see a doctor immediately"
- Asks for fasts > 36h non-religious / non-medical → "see RDN"
- Asks for diagnosis or "do I have X deficiency" → "I flag patterns; only bloodwork + clinician diagnoses"
- Asks about prescription drug dosing → "talk to pharmacist/MD"
- Pattern matches 3+ items from eating-disorder-red-flags.md → gentle escalation, NEVER gamify weight loss

Hardcoded guardrails (NEVER suggest):
- kcal outside [BMR × 1.0, BMR × 2.5]
- Protein < 1.2 g/kg OR > 3.5 g/kg
- Single food > 50% daily kcal sustained
- Zero-fat or zero-carb day
- A recipe with food user has logged dislike on
- Items in user allergens / clinical exclusions
```

---

## 29. Quality signals + measurement

System self-measures via:
- Macro hit rate (daily kcal ±5%, protein -0/+15%, fat ≥min) — 7d/30d rolled
- Per-meal protein distribution ≥0.3 g/kg; flag if any meal <0.2
- Cost variance vs budget — weekly
- Variety index (Shannon entropy of recipes rolling 30d)
- Micronutrient coverage vs DRV (flag <70% for 2 consecutive weeks)
- Cost per 100g-protein trend — monthly
- Recipe-rating distribution (median ≥3.8/5)
- Adherence (planned vs logged) — alert <70% for 3+ days
- Pantry expiry waste (<5% of purchases by value)
- Loss-leader capture rate
- Boredom trajectory per recipe

Surfaced in-app via "Quality" tab; weekly Sun digest pushed via P3 ntfy.

---

## 30. Acceptance criteria (visible-on-first-paint per CLAUDE.md gate)

When user navigates to in-app screens, these `data-testid` selectors MUST paint and be interactive:

**Home (default screen):**
- `[data-testid="diag-status-banner"]` — VPS reachable y/n
- `[data-testid="today-macros-progress"]` — kcal + protein progress bars
- `[data-testid="next-meal-card"]` — what to eat now
- `[data-testid="quick-log-button"]` — open quick-log sheet
- `[data-testid="quick-photo-button"]` — open camera for receipt/label

**Pantry tab:**
- `[data-testid="pantry-list"]` — current pantry items
- `[data-testid="pantry-add-button"]`
- `[data-testid="pantry-low-stock-section"]` — items < threshold

**Planner tab:**
- `[data-testid="weekly-plan-grid"]` — 7×4 plan
- `[data-testid="regenerate-plan-button"]`
- `[data-testid="budget-tracker"]` — week-to-date spend vs target

**Shopping tab:**
- `[data-testid="shopping-list-active"]`
- `[data-testid="shopping-by-store"]` — grouped by closest store
- `[data-testid="loss-leader-alerts"]` — current sales worth pivoting for

**Diag screen (`/diag` cmd):**
- `[data-testid="diag-vps"]`, `[data-testid="diag-tailscale"]`, `[data-testid="diag-postgres"]`, `[data-testid="diag-ntfy"]`
- `[data-testid="diag-outbox"]`, `[data-testid="diag-sync-times"]`
- `[data-testid="diag-llm-budget-claudemax"]`, `[data-testid="diag-llm-budget-openrouter"]`
- `[data-testid="diag-scraper-status"]` (one per scraper)
- `[data-testid="diag-last-errors"]`
- `[data-testid="diag-pending-jobs"]`

**Final SDD review (per CLAUDE.md `Interaction-smoke gate`):**
1. All N spec'd `[data-testid]` selectors visible on first paint of each surface
2. ZERO 4xx/5xx network responses during first paint (capture via `page.on('response', …)`)
3. Click every interactive selector
4. After each click: no on-screen text matches `/404|HTTP \d{3}|not found|error/i` AND no new 4xx/5xx network responses

---

## 31. Jarvis merge plan

Documented in `JARVIS_MERGE.md`:

1. Rename Gradle modules `:shared:llm` etc. → fold into `jarvis-kotlin/dietician/`
2. Drop `:shared:llm` adapter, wire to `jarvis.Llm` interface directly
3. Register `DieticianSubsystem : Subsystem` in `jarvis.subsystem.Subsystems.all`
4. `SubsystemOutput.text` = markdown rendering of `DieticianResult`
5. `SubsystemOutput.wikiEntry` = rationale section only
6. Structured channel: `state/meal-plan-current.json` already canonical, jarvis reads directly
7. **Upstream proposal to jarvis:** add `structured: Map<String, Any>? = null` field to `SubsystemOutput` (additive, all existing subsystems unchanged) — defer until upstream-needed
8. Web surface: add `DieticianRoutes.kt` to `jarvis/web/` mirroring `TutorRoutes.kt` pattern
9. Optional: re-render Dietician UI in React to match tutor-web, OR keep Compose Desktop as standalone surface and embed iframe — accepted debt
10. Postgres + ntfy + GROBID stay on VPS; jarvis-web (also on VPS) connects via same Tailscale ACL

**Verified contract (must not break):**
- `Subsystem.run(client: Llm, input: SubsystemInput): SubsystemOutput`
- `SubsystemOutput(text: String, wikiEntry: String?)`

---

## 32. Open questions (gated for impl phase)

1. **Anelis Plus auth model** — see §13
2. **`claude --print` exit-code on quota hit** — single test invocation when first ClaudeMax CLI call wired (Council 3 wanted this for confidence-to-10; Choco smoke gave us 9→10 already, this remains low-priority)
3. **Bringo actual auth posture** — header inspection of first request (Council 3)
4. **Monitorul Prețurilor cert pinning posture** — mitmproxy probe when decompile starts
5. **User's primary location at planner-query time** — auto-detect via Android GPS share OR manual `/where` cmd; both supported, default = `/where iasi-tudor`
6. **OneDrive vs git for wiki version control** — git chosen (better history + diffs); OneDrive = secondary backup only

---

## 33. Project ergonomics

- **Build/test commands:**
  - Root: `./gradlew :shared:test :androidApp:assembleDebug :desktopApp:run :server:run`
  - Lint: `./gradlew ktlintCheck detekt`
  - Pre-commit hook: `./gradlew ktlintFormat detekt :shared:test`
- **Custom Detekt rule** (per CLAUDE.md): `UnusedUnderscoreDestructuring` flags any destructured `_propName` whose constructor param still exists in source
- **CI:** GitHub Actions on private repo (free tier 2000 min/mo). Workflow: `detekt + ktlintCheck + :shared:test + :server:assemble + :androidApp:assembleDebug`
- **Local dev:** desktop daemon `:desktopApp:run` against local VPS Tailscale IP; Android via Android Studio emulator (Tailscale-mesh-aware)

---

## 34. Anti-patterns explicitly avoided (per CLAUDE.md + Council passes)

- ✘ Underscore-dead-prop on Compose / data class destructure → custom Detekt rule
- ✘ Speculative jarvis-merge driving architecture → jarvis-merge is documented adapter, not architecture driver
- ✘ Premature subsystem split before code lands → modules organized by domain coupling, not speculative cleanliness
- ✘ Abstraction-before-second-use → `LlmProvider` sealed interface has 3 impls (ClaudeMax CLI, OpenRouter, Ollama); justified
- ✘ Wiki-as-database for structured data → SQLite/Postgres source-of-truth, wiki = narrative shell with transclusion
- ✘ Periodic WorkManager for background sync → event-driven (ntfy + WS + outbox-replay)
- ✘ Tailscale self-report as health signal → app-layer ping `/health`
- ✘ LWW on inventory deltas → event-sourced SUM
- ✘ Stringifying structured data into `SubsystemOutput.text` → side-channel JSON at `state/meal-plan-current.json` with `plan_id` UUID + `generated_at` cross-stamp
- ✘ Single-CLI in-process Playwright → out-of-process subprocess JAR with RSS ceiling
- ✘ Telegram-as-only-UI nag fatigue → in-app Compose + dashboard; ntfy push for time-sensitive only

---

## END OF SPEC

**This document is the source of truth. Code that contradicts this spec is wrong.**

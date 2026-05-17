# Dietician Wiki — Index

Auto-maintained catalog of all wiki pages. LLM agents update this on every ingest. Last manually-rewritten 2026-05-17.

## Entities

### Recipes
*(populated on first ingest)*

### Ingredients
*(populated on first ingest)*

### Equipment
- `equipment/air-fryer.md` — Heinner/Daewoo/Tefal/Philips etc. (user-confirmed model TBD)
- `equipment/microwave.md` — dorm-tier 700-900W common
- `equipment/none.md` — no stove, no oven, no blender

### Stores
*(populated on first scrape)*

## Concepts (knowledge/)

### Nutrition
- `knowledge/nutrition/protein-quality.md` (TBD)
- `knowledge/nutrition/leucine-mps.md` (TBD)
- `knowledge/nutrition/macronutrients/{protein,fat,carbs,fiber,alcohol}.md` (TBD)
- `knowledge/nutrition/micronutrients/{vit-d,vit-b12,iron,zinc,magnesium,...}.md` (TBD)
- `knowledge/nutrition/bioavailability/{heme-iron,calcium-iron-competition,vit-c-iron,...}.md` (TBD)

### Methodology
- `knowledge/methodology/lean-bulk-principles.md` (TBD — instantiated to user)

### Cooking
- `knowledge/cooking/salt-fat-acid-heat.md` (TBD)
- `knowledge/cooking/air-fryer-mastery.md` (TBD)
- `knowledge/cooking/air-fryer-time-temp-matrix.md` (TBD)
- `knowledge/cooking/microwave-mastery.md` (TBD)

### Food Safety
- `knowledge/food-safety/temperature-fundamentals.md` (TBD)
- `knowledge/food-safety/leftovers-shelf-life.md` (TBD)

### Shopping
- `knowledge/shopping/ro-supermarket-map.md` (TBD)

### RO Context
- `knowledge/ro-context/seasonal-produce-calendar-ro.md` (TBD)
- `knowledge/ro-context/iasi-piete.md` (TBD)
- `knowledge/ro-context/iasi-cantine.md` (TBD)
- `knowledge/ro-context/orthodox-fasting-impact-on-prices.md` (TBD)

### Clinical
- `knowledge/clinical/refusal-triggers.md` (TBD)
- `knowledge/clinical/eating-disorder-red-flags.md` (TBD)
- `knowledge/clinical/drug-food-interactions.md` (TBD)

### Meta
- `knowledge/meta/source-anti-recommend-list.md` (TBD)
- `knowledge/meta/source-authority-ranking.md` (TBD)
- `knowledge/meta/citation-style.md` (TBD)

## Body / Prefs

- `body/weight-log.md` (auto-generated from SQL, transcluded)
- `body/macro-targets.md` (instantiated to user)
- `prefs/exclusions.md` (TBD)
- `prefs/cravings.md` (TBD)
- `prefs/boredom.md` (TBD)
- `prefs/boredom.toml` (config — boredom decay weights, user-tunable)

## Meal Plans / Shopping Lists / Summaries

*(populated weekly)*

---

**See also:**
- `log.md` — chronological event log (ingests, queries, lint passes)
- `../docs/superpowers/specs/2026-05-17-dietician-design.md` — full design spec
- `../AGENTS.md` — agent conventions for wiki maintenance

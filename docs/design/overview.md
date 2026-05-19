---
title: "Design Overview — Mission, Principles, Non-goals"
slug: design-overview
domain: design
applies_to: [user-victor]
sources:
  - name: "Dietician design spec §0–§1 §11 §28 §30"
    url: "docs/superpowers/specs/2026-05-17-dietician-design.md"
    citation: "Dietician design spec 2026-05-17"
    accessed: 2026-05-19
  - name: "Round 3 — UX + A11y + Regulation + ED-Safeguards"
    url: "docs/superpowers/research/2026-05-17-round-3-ux-regulation.md"
    citation: "Dietician research round 3 2026-05-17"
    accessed: 2026-05-19
  - name: "Karpathy LLM Wiki gist"
    url: "https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f"
    citation: "Karpathy A. — Building an LLM-maintained wiki"
    accessed: 2026-05-19
authority: derived
confidence: high
last_verified: 2026-05-19
review_cadence_days: 90
instantiated_for_user: true
user_numbers:
  height_cm: 188
  weight_kg: 67.5
  kcal_target: 2750
  protein_g_target: 137
related: [design-visual-language, design-ux-patterns, design-components, design-references]
contradicts: []
supersedes: []
tags: [design, overview, principles, non-goals]
---

# Design Overview

This page is the north star. Every other page in this wiki defers to it. If something on this page changes, every other page is suspect until re-verified.

## What this product is

A **personal AI dietician for one user**, Victor — a year-1 AI student at UAIC in Iași, Romania, currently lean-bulking (188 cm / 67.5 kg → 2750 kcal / 137 g protein), cooking with only an air fryer and a microwave. The app runs as Kotlin Multiplatform Compose on Android (phone, capture-first) and Windows Desktop (laptop, deep-work-first). A VPS-canonical Postgres (Tailscale-meshed) merges events across devices. The architecture is local-first, event-sourced, GDPR + EU AI Act compliant from day one, and designed to fold into the larger `jarvis-kotlin` life-OS later as a Subsystem.

Read `docs/superpowers/specs/2026-05-17-dietician-design.md` §0–§2 for the full product context, §28 for refusal triggers, §30 for the acceptance-criteria selectors that must paint on first render.

This is **not a consumer product for a market**. It serves one person — plus a small circle of friends/family (~5) at most. That single fact licenses design decisions that would be commercial suicide at scale: aggressively niche copy, no onboarding hand-holding past the first paint, no telemetry, no advertising layer, no growth loops, no social, no shame, no nag.

## Who the user is (and what that implies)

- **Technical fluency**: AI student. Comfortable with terminals, JSON, audit logs, model names, token counts. Will read the per-call disclosure pane. Will use the `/diag` command. Surfacing technical detail is welcome, not noise.
- **Romanian + English bilingual**: defaults to English; Romanian for source labels (Mega Image, Auchan, Carrefour), SKU names, dish names (`ciorbă de văcuță`), grocery receipts. Diacritic-correct (`ăâîșț`) is non-negotiable.
- **Lean-bulking, not cutting**: explicitly muscle-gain-oriented. The matched eating-disorder risk profile is **bigorexia / muscle dysmorphia**, not anorexia. Design must defuse over-eating-as-virtue and frame-around-numbers spirals just as carefully as it would defuse under-eating ones. See [[design-ux-patterns]] §Refusals + safeguards.
- **Equipment-constrained**: air fryer + microwave. Stove + oven absent. Recipe surfaces must filter on equipment tags before ranking; suggesting an unreachable recipe is a bug, not a "stretch goal."
- **Mobile-first capture, desktop-first reasoning**: the phone is for "I just ate this, log it"; the desktop is for "show me my week, why is protein off, plan next week, ingest this paper." UI should not pretend phone and desktop have the same job.

## Design north stars

Each is a rule with a **why** and a **how to apply**. Order is intentional: if two principles conflict, the higher one wins.

### 1. Calm beats clever

Nutrition data is loaded. The app is consulted multiple times a day, often when the user is hungry, tired, or post-workout. Design must register as a comfort surface, not a performance theatre. **No streaks, no leaderboards, no "you crushed it!" microcopy, no confetti, no haptic celebrations.**

**Why**: research on the MyFitnessPal user cohort shows ~73 % of those with eating-disorder symptoms perceived the app as contributing to them; the apportioned blame falls heavily on gamification, social comparison, and red/green pass-fail framing (Levinson et al. via [PMC clinical sample study](https://pmc.ncbi.nlm.nih.gov/articles/PMC5700836/); summarised in `docs/superpowers/research/2026-05-17-round-3-ux-regulation.md` §1.2). Victor is lean-bulking, but the same neural pathway gets activated whether the chase is downward or upward.

**How to apply**: when in doubt about whether a UI element adds celebration or judgement, remove it. Replace with a neutral text statement. See [[design-ux-patterns]] §Data display.

### 2. Neutral framing for all nutrition data — no red/green pass/fail

Macros under target are not "bad." Macros over target are not "good." Both are facts. Display them as deltas, not verdicts.

**Why**: Council 1779120600 R3 ruling, baked into `shared/.../ui/theme/DieticianColors.kt:4-9` as a hard rule: "No red-green pass/fail axis for nutritional choices. Macro under-target NOT red; over-target NOT green." `DieticianErrorRed = #B3261E` exists only for actual app errors — `RESERVED FOR ACTUAL ERRORS, NOT NUTRITION` (`DieticianColors.kt:50`).

**How to apply**: use the warm Amber primary or the `NeutralChip` token (`DieticianColors.kt:109`) for above/below-target labels. Write microcopy as `"above target by 6%"` not `"⚠️ over!"`. See [[design-visual-language]] §Color and [[design-ux-patterns]] §Data display.

### 3. Transparency is the product, not a feature

Every LLM call discloses model, input/output tokens, cost in cents, timestamp, and call_uuid in a per-call disclosure pane. The user can click through to the audit log row. Consent for special-category-health data (GDPR Art. 9) and cross-border transfer (SCC + DPF) are separate togglable rows. The first-paint AI Literacy banner (AI Act Art. 4) is non-dismissable on back-press; the user must acknowledge before chatting.

**Why**: EU AI Act Art. 4 (in force since Feb 2 2025), Art. 13 + 14 (transparency to deployers, human oversight), Art. 50 (the "AI assistant" identity disclosure that comes into force 2 Aug 2026). Beyond regulation, the user is an AI student; they expect to see the cost and model behind every answer.

**How to apply**: never hide a call. Never collapse a disclosure. When in doubt, surface more, not less. See [[design-ux-patterns]] §Transparency surfaces and [[design-components]] `PerCallDisclosurePane`, `AILiteracyBanner`, `ConsentRow`.

### 4. Logging friction is the enemy

Five primary capture methods, in priority order: **voice → photo → barcode → manual → "same as recent"**. Voice is roadmapped (Whisper.cpp JNI, Plan-7); the others ship. The phone's `FoodLogScreen` is built as five tall 80 dp buttons because the goal is "log this in two seconds while still chewing." Friction is measured in taps, not features.

**Why**: research §1 across 12 nutrition apps consistently identifies low-friction capture as the only behavioural predictor of sustained logging. Carbon, MacroFactor, MyFitnessPal all converge here. The only twist for Dietician: voice ranks above photo because Victor cooks alone in a kitchen with greasy hands; photo recognition is "best for restaurant plates," voice is "best for things you assembled yourself."

**How to apply**: every new capture surface gets a max-tap budget; if it exceeds it, simplify or kill it. See [[design-ux-patterns]] §Input patterns and [[design-components]] `VoiceRecordButton`, `PhotoCaptureButton`, `BarcodeScanButton`, `ManualEntryField`, `SameAsRecentButton`.

### 5. Photo recognition is a suggestion, never a blind log

Food-photo recognition models top out around 46–63 % top-1 accuracy on mixed dishes ([PMC comparison](https://pmc.ncbi.nlm.nih.gov/articles/PMC7752530/); details in research §1.5 and §1.7). Auto-logging from a photo without confirmation is a data-integrity bomb that compounds into wrong macros, wrong adaptive expenditure, wrong recommendations.

**Why**: garbage-in / garbage-out is acute for adaptive-expenditure algorithms (MacroFactor-style Bayesian smoothing in `AdaptiveExpenditureChart`). One bad week of auto-logged photos drags the rolling expenditure estimate by 100–200 kcal, which then drags every recommendation for the next month.

**How to apply**: every photo result shows N candidates with confidence %, plus "None of these" as a first-class escape. Pattern is implemented in `PhotoSuggestionList.kt`; see [[design-components]] `PhotoSuggestionCard`. Never auto-commit.

### 6. The wiki is the brain; SQL is the heart

The product has two persistent layers: structured numeric/transactional state in SQLite-per-client + Postgres-on-VPS, and narrative reasoning in Markdown under `wiki/`. **Never put live numbers in wiki narrative bodies** (per `AGENTS.md` "Storage decision rule"). The `wiki/` is what the LLM thinks; the database is what is true.

**Why**: contradicts at the schema level. Numbers in markdown rot. Numbers in SQL stay accurate, can be aggregated, can be transcluded into markdown via the `.data.md` two-file pattern.

**How to apply**: design surfaces that show "current state" pull from SQL. Design surfaces that show "what we know about chicken-breast nutrition" pull from `wiki/ingredients/chicken-breast.md` + transcluded `chicken-breast.data.md`. The boundary is enforced by the layer, not by guidelines. See [[design-ux-patterns]] §Data sources.

### 7. Bilingual on purpose, not by accident

English is the default UI locale. Romanian appears in three contexts only: (a) source-of-record labels (`Mega Image`, `Auchan`, `Bonurile Mele.pdf`), (b) SKU descriptions and dish names as they appear on packaging or in cookbooks (`ciorbă de văcuță`), (c) the entire app when the user toggles `LocaleSwitcherRow`. Diacritic-correct everywhere (`ăâîșț`), never the cedilla fallback (`şţ`).

**Why**: Romanian users hate seeing cedilla `ş ţ` instead of comma-below `ș ț` — historically an artefact of pre-Unicode-3 fonts but still legible as "this app doesn't care about my language." See [[design-visual-language]] §Typography for the system font stack that handles this correctly.

**How to apply**: all user-facing strings live in `Strings.kt` with EN + RO variants. Never inline an English string in a Composable. The `BigorexiaCopyTest` enforces this at the test level for safety-critical EN+RO copy.

### 8. Defer to source-of-truth

This wiki is a synthesis. Spec wins. Code wins. Council decisions win. When this wiki contradicts spec, fix the wiki, do not "interpret" the spec. When code contradicts this wiki, the code is right unless this wiki cites a council decision with a higher confidence rating.

## Non-goals (explicit)

These are temptations to refuse. Each cites why.

1. **No social feed, ever.** Not for friends-and-family, not for "share your recipe." Research §1.2 — MyFitnessPal retired its feed in June 2024 under cover of "cost," almost certainly under liability pressure. Don't reinvent.
2. **No streaks, no XP, no badges, no leaderboards.** Research §1.13 dark-pattern ban list item 1. Bigorexia-specific risk: turning protein-floor adherence into a streak weaponises Victor's own goal.
3. **No red/green pass-fail color coding** for any nutrition or weight metric. See §2 above. `DieticianErrorRed` is for crashes only.
4. **No body-weight or BMI as primary metric.** Weight is a noisy daily signal; the adaptive-expenditure rolling 7-day mean is the real signal. The `WeightTrendChart` shows weekly aggregates, not daily. BMI is not displayed anywhere.
5. **No emotion inference from logging gaps.** EU AI Act Art. 5(1)(f) prohibits emotion inference in this consumer context. Logging gaps mean the user was busy, not "sad about food."
6. **No paywall on any safety-critical surface.** This is a personal product (no payment layer) but the principle stands for the future jarvis merge: refusal triggers, ED safeguards, audit log, consent withdraw, DSAR export, `/diag` — never gated.
7. **No auto-logging from photos.** §5 above. Suggestions, always.
8. **No "always-on" listening voice mode.** Voice is press-and-hold or tap-to-toggle. Background listening is a different product class with different consent obligations.
9. **No generic stock food photography in cards.** Either show the user's own captured photo or no photo. Stock photography reads as "consumer app" and breaks the personal-tool feel.
10. **No animations longer than 400 ms on critical paths.** See [[design-visual-language]] §Motion. The user is consulting the app from a hungry, distracted state; speed matters more than polish.

## What success looks like

A design pass is successful when a person who knows nothing about the spec, opens the app cold on the phone for the first time, and:

1. Sees the AI Literacy banner (Art. 4 disclosure) and reads it.
2. Lands on `HomeScreen`, sees their current macros for today, sees the next-meal suggestion, sees a "Log a meal" CTA.
3. Taps the bottom-nav `FoodLog` tab, sees five tall buttons, taps voice (when shipped), speaks a meal, sees a draft entry to confirm.
4. Never sees a red or green colour for a nutrition value.
5. Never sees a streak counter, XP score, or comparison to "other users."
6. When they ask Coach a question, the AI Literacy banner has primed them that this is an AI; the per-call disclosure pane shows model + cost + tokens; clicking it deep-links to the audit log row.

Every other detail is downstream of those six.

## Where to look next

- For colour, type, spacing, shape, motion → [[design-visual-language]].
- For navigation, screen archetypes, input patterns, refusal flows → [[design-ux-patterns]].
- For "what Composable do I use for X" → [[design-components]].
- For "where does that claim come from" → [[design-references]].
- For the schema rules + log → [[design-index]].

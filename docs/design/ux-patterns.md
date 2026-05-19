---
title: "UX Patterns — Nav, Screen archetypes, Data display, Inputs, Safeguards"
slug: design-ux-patterns
domain: design
applies_to: [user-victor]
sources:
  - name: "Dietician design spec §30 acceptance criteria"
    url: "docs/superpowers/specs/2026-05-17-dietician-design.md"
    citation: "Dietician design spec 2026-05-17 §30"
    accessed: 2026-05-19
  - name: "Dietician research round 3 §1 (12 nutrition apps)"
    url: "docs/superpowers/research/2026-05-17-round-3-ux-regulation.md"
    citation: "Dietician research round 3 2026-05-17 §1"
    accessed: 2026-05-19
  - name: "Smashing — Practical Interface Patterns for AI Transparency Pt 2"
    url: "https://www.smashingmagazine.com/2026/05/practical-interface-patterns-ai-transparency/"
    citation: "Smashing Magazine 2026-05"
    accessed: 2026-05-19
  - name: "Smashing — Designing for Agentic AI"
    url: "https://www.smashingmagazine.com/2026/02/designing-agentic-ai-practical-ux-patterns/"
    citation: "Smashing Magazine 2026-02"
    accessed: 2026-05-19
  - name: "EU AI Act — Art 50 transparency UX deadlines"
    url: "https://agentmodeai.com/eu-ai-act-article-50-transparency-disclosure/"
    citation: "agentmodeai 2026"
    accessed: 2026-05-19
  - name: "PMC — MyFitnessPal clinical ED sample (Levinson)"
    url: "https://pmc.ncbi.nlm.nih.gov/articles/PMC5700836/"
    citation: "Levinson CA et al., PMC5700836"
    accessed: 2026-05-19
  - name: "BJPsych Open — diet/fitness apps + ED behaviours"
    url: "https://pmc.ncbi.nlm.nih.gov/articles/PMC8485346/"
    citation: "BJPsych Open 2021 (PMC8485346)"
    accessed: 2026-05-19
  - name: "Mobbin — Tab Bar UI patterns"
    url: "https://mobbin.com/glossary/tab-bar"
    citation: "Mobbin 2026 Tab bar glossary"
    accessed: 2026-05-19
authority: derived
confidence: high
last_verified: 2026-05-19
review_cadence_days: 60
instantiated_for_user: false
user_numbers: {}
related: [design-overview, design-visual-language, design-components, design-references]
contradicts: []
supersedes: []
tags: [design, ux, navigation, ia, safety, ai-act, gdpr, bilingual, inputs]
---

# UX Patterns

This is the "how does the app behave" layer. Components are the words; this page is the grammar.

## Navigation model

### Bottom nav (5 tabs)

Implemented at `DieticianNav.kt:124`, ordered list at `Routes.kt:210`. Five tabs, Voyager-managed, `navigator.replaceAll(screen)` on tap (shallow root stack):

| Index | Tab | Icon (current) | Screen | Purpose |
|-------|-----|----------------|--------|---------|
| 0 | Home | Home | `HomeScreen.kt:38` | Today's snapshot + entry CTA |
| 1 | FoodLog | List | `FoodLogScreen.kt:44` | Five capture methods |
| 2 | Pantry | ShoppingCart (semantic mismatch) | `PantryScreen.kt:52` | FEFO-sorted inventory + FAB |
| 3 | CoachChat | Person | `CoachChatScreen.kt:49` | AI assistant chat |
| 4 | Settings | Settings | `SettingsScreen.kt:50` | Locale, theme, accessibility, audit, papers |

**Five-tab limit** is industry consensus ([Mobbin 2026 — tab bar best practice](https://mobbin.com/glossary/tab-bar)): more than five and tap-targets get hard to hit, cognitive load rises sharply. We sit at the cap.

### Pushed screens (no tab)

Reached via `navigator.push(screen)`. All wrap content in `PushedScreenScaffold` (`PushedScreenScaffold.kt:29`) which provides a Material 3 `TopAppBar` + back arrow with `testTag("pushed-screen-back")`.

- `Cookbook` (`Routes.kt:101`) — pushed from `PantryScreen` "Open Cookbook" button
- `MealDetail(mealId)` (`Routes.kt:193`) — pushed from `CookbookScreen` recipe tap
- `PaperSearch` (`Routes.kt:118`) — pushed from `SettingsScreen`
- `AuditLog` (`Routes.kt:162`) — pushed from `SettingsScreen`
- `ReceiptUpload` (`Routes.kt:176`) — pushed from `FoodLogScreen` "Photo" button
- `Byok` — not currently in nav graph (Composable exists at `ByokScreen.kt:50`, route missing). Spec gap.

### Pre-auth + blockers

Outside the nav graph entirely:

- `SplashScreen` — single centred text, very brief
- `TailscaleDisconnectedScreen` (`TailscaleDisconnectedScreen.kt:35`) — RC16 blocker, retry button
- `OnboardingScreen` (`auth/OnboardingScreen.kt:43`) — magic-link entry, two-state (EmailEntry → CheckEmail) + dev-only simulate-verify
- `MagicLinkVerifyScreen` (`auth/MagicLinkVerifyScreen.kt:32`) — 3-state (Loading/Success/Error), mounted by platform shells on deep-link intent
- `AILiteracyBanner` — modal layered on top once after first sign-in, non-dismissable on back (Art. 4 mandate)

### IA drift (spec → impl)

The spec `§30 acceptance criteria` names four bottom-nav tabs: **Home / Pantry / Planner / Shopping**, plus a `/diag` screen reachable as a command. The current impl ships **Home / FoodLog / Pantry / CoachChat / Settings**. The drift:

- `Planner` (spec: `[data-testid="weekly-plan-grid"]`, `regenerate-plan-button`, `budget-tracker`) — not a tab in current impl. Pieces partially exist (`AdaptiveExpenditureChart`, `MealDetailScreen`, `Choco` planner backend behind `:shared:domain`). Status: deferred until Plan-1 + Plan-7 data lands.
- `Shopping` (spec: `[data-testid="shopping-list-active"]`, `shopping-by-store`, `loss-leader-alerts`) — not a tab in current impl. Status: deferred until Plan-6 (scrapers) lands price data.
- `/diag` — not a tab; intended as a slash-command from `CoachChat` or a footer button in Settings. Acceptance criteria call for one screen with `diag-vps`, `diag-tailscale`, etc. selectors. Spec gap: surface needs to exist.
- `CoachChat` + `FoodLog` — extra tabs not in spec §30 (which spec'd four tabs). Justified by Council 7/10 RC1 decision: voice/chat are first-class capture surfaces, not nested under "Planner."

**Recommended reconciliation** (for a future iteration): expand to either (a) 5 bottom-nav tabs + `Planner` and `Shopping` reachable as sections inside `Home` ("This week's plan" + "Shopping list" cards that push to detail screens), or (b) collapse `FoodLog` into a Quick-Log modal triggered from `Home`'s FAB, freeing a tab slot for `Planner`. Option (a) preserves the 5-tab cap; option (b) matches spec §30 more literally. Don't ship either without bringing it back through brainstorming.

### Navigation library

[Voyager](https://github.com/adrielcafe/voyager) 1.x. Voyager `Navigator` wraps the `Scaffold` content; each tab tap calls `navigator.replaceAll(screen)`. Pushed screens are `navigator.push(screen)`. `BackHandler` integrations sit inside `PushedScreenScaffold`. Per Council RC3, we stay on Voyager + Compose Multiplatform 1.7.0 — do **not** migrate to a NavHost-based approach without re-running that council.

### Desktop windowing

Single window, no multi-window yet. The desktop entry point at `desktopApp/.../Main.kt:34` mounts the same `DieticianApp()` root composable. Plan-7 may add `WindowState` resize handling + a desktop-only command palette (`Ctrl+K`); pattern would mirror Raycast / Linear (`Cmd+K`).

## Screen archetypes

Each screen falls into one of seven archetypes. Knowing the archetype tells you the structural rules.

### 1. Dashboard (Home)

A vertical scroll of small read-only cards plus one primary CTA. Each card surfaces one metric or one suggestion. Cards are not actionable beyond a single tap-to-expand or tap-to-detail; the dashboard itself does not own edit affordances. Currently: `SubjectCard` → `AdaptiveExpenditurePreview` → `TodayNutrientsCard` → `PlannedCutToggle` → "Log a meal" Button. Add: `NextMealCard` (spec §30 `next-meal-card`), `BentoGrid` layout option (see [[design-components]] §Planned).

Bento grid (the 2026-defining dashboard pattern) is a good fit here when the spec-mandated dashboard tiles are all built: `today-macros-progress` (hero, spans 2 cols), `next-meal-card` (1 col), `quick-log-button` + `quick-photo-button` (small tiles). 2×3 bento on phone portrait, 4×2 on desktop. Don't introduce bento until ≥4 dashboard tiles exist — premature.

### 2. Capture (FoodLog)

Vertical stack of large primary buttons, each one input modality. Buttons are 80 dp tall (`FoodLogScreen.kt:54`), full-width, clear icon + label. Buttons that are not yet wired show "coming soon" toast — never disabled-greyed-out (the affordance to tap-and-be-told is friendlier than the absence). Order: voice → photo → barcode → manual → same-as-recent.

### 3. List (Pantry, Cookbook, AuditLog, PaperSearch)

`LazyColumn` of `Card` rows. Each row condenses to two-to-three lines of information (title, secondary line, badges). Header row above the list holds filters + entry actions (FAB for add, search field, ingest button). Empty state is a `Card` with helpful copy + a primary CTA, never a blank screen.

For sorted lists (Pantry FEFO), the sort logic lives in the ViewModel (`PantryViewModel.kt:42`). The UI never re-sorts on render.

### 4. Detail (MealDetail, future ReceiptDetail, future RecipeDetail)

Pushed via Voyager with `PushedScreenScaffold`. Single primary action floats as a `Button` at bottom; secondary actions sit in the `TopAppBar` actions slot. Inline scroll, no tabs (avoid tab-in-detail — confusing nav level). Loading state = centred spinner; not-found state = centred text + "Back" CTA.

### 5. Chat (CoachChat)

`LazyColumn` reverse-pinned to bottom. User messages right-aligned on `primaryContainer`, assistant messages left-aligned on `surfaceVariant`. Max bubble width 280 dp. Streaming render token-by-token with a blinking caret at the in-progress position; `Cancel` button visible during streaming. Per-call `PerCallDisclosurePane` hangs below each assistant bubble (model, tokens, cost, call_uuid, deep-link to audit log). Footer rotates: `CoachDisabledNotice` (RC9) / streaming state + `Cancel` / input row.

### 6. Settings (Settings)

Vertical scroll of `ToggleRow`, `LocaleSwitcherRow`, full-width Buttons, and an `AboutCard`. No `Scaffold` `TopAppBar` (settings is a bottom-nav root, not a pushed screen). Sections separated by `HorizontalDivider`. Avoid nested sub-settings screens until the count of toggles forces it; currently fewer than 10 → flat is fine.

### 7. Audit / Compliance (AuditLog, ConsentRow, ReceiptUpload, BYOK)

These screens carry legal weight (GDPR Art. 9, AI Act Art. 13/14, SCC/DPF cross-border). They look identical to other screens (warm comfort palette) but the affordances are stricter: every action is logged, every state change emits an audit row, every consent toggle takes both a grant and a withdraw timestamp + version hash.

## Data display rules

### No red, no green

Repeating from [[design-overview]] §2 because this is the rule designers fight hardest. Nutrition values use the `NeutralChip` token (`DieticianColors.kt:109`) or the warm Amber/Sage primary/secondary, never `DieticianErrorRed`, never a green that reads as success. Above-target uses the same chip styling as below-target.

### Above/below framing

Copy template: `"{value} {unit} {kcal|protein|carbs|fat} — {above|below} target by {N}%"`. Implementation at `TodayNutrientsCard.kt:45` via `formatMacroLabel`. The word "target" is deliberate — neither "goal" (which sounds achievement-y) nor "limit" (which sounds restrictive). Romanian variant: `"{N}% {peste|sub} țintă"`.

### Visual cap on bars

`NutrientBar.kt` uses a `displayCapMultiplier = 1.5×` so a nutrient at 300 % of target does not render as a bar that breaks the layout — it caps at 150 % visual width and shows the numeric `300%` text label inside. Prevents over-target alarm from being visually amplified.

### Weekly aggregates, not daily

Daily weight is noise; the meaningful signal is the 7-day rolling mean. `WeightTrendChart` (`WeightTrendChart.kt`) plots weekly aggregates with a subtitle "weekly aggregate, not daily" (RC5). The user can switch 4wk / 12wk / 26wk windows via `FilterChip` row. Never display daily weight in a chart; it invites the wrong response.

### Adaptive expenditure as the truth

`AdaptiveExpenditureChart` plots a Bayesian rolling 7-day expenditure estimate (MacroFactor-style — see research §1.3). The static Mifflin-St-Jeor value used at onboarding becomes the prior; observed weight × intake updates the posterior weekly. The chart shows the estimate as a solid line, the target as a dashed line, observed intakes as dots. Never show the "raw daily expenditure" — it doesn't exist; expenditure is a posterior, not a measurement.

### Cost framing

Recipe and shopping cost surfaces show `"32.50 lei ± 8.00 lei (5 of 8 priced)"` per spec §19.1. Never round-up "to be safe" — the ± explicitly carries the uncertainty. When fewer than 50 % of ingredients are priced (`unknown_ratio > 0.5`), the recipe gets a visual badge but is not removed from suggestions; the planner demotes its rank instead.

### Logging gaps ≠ emotion

EU AI Act Art. 5(1)(f) prohibits emotion inference in consumer contexts. A skipped meal log means the user was busy or chose not to log. Never display "You seem to be having a tough week" or any inferred mood. If `adherence < 70 %` for 3+ days, surface a neutral fact ("3 of last 5 days had incomplete logs") and offer to simplify recommendations — never frame it as a feeling.

## Input patterns

### Five capture methods

Priority order, identical to the FoodLog screen layout:

1. **Voice** (`VoiceRecordButton`) — press-and-hold or tap-to-toggle, never always-on. Pipeline: capture → `whisper.cpp large-v3-turbo` on-device → transcript → LLM-parse-to-meal → confirm-or-edit screen. Romanian + English mixed allowed. Friction: 0 taps to start (lockscreen widget planned), 1 tap to confirm. **Not yet shipped** — currently fires "coming soon" toast.
2. **Photo** (`PhotoCaptureButton`) — opens camera (Android `CameraX`) or file picker (desktop `AWT FileDialog`). Pipeline: capture → ClaudeMax CLI Vision (desktop) or OpenRouter Gemini Vision (phone) → N candidates with confidence % → user confirms one or "None of these" (`PhotoSuggestionList.kt`). **Suggestion only, never auto-log.**
3. **Barcode** (`BarcodeScanButton`) — CameraX `BarcodeScanning` → Open Food Facts lookup → confirm portion. **Not yet shipped** — toast. Plan-7.
4. **Manual** (`ManualEntryField`) — `OutlinedTextField` with autocomplete from recent meals + USDA/CIQUAL DB. Always available, never blocked.
5. **Same as recent** (`SameAsRecentButton`) — bottom sheet showing last 3 meals of this slot (breakfast/lunch/dinner/snack). One-tap re-log. **Currently shows empty placeholder.**

### Confirmation flow for fuzzy inputs

Voice transcript, photo recognition, and barcode lookup all produce **drafts**, never finalised logs. The confirmation surface shows: parsed result, source-of-record (transcript text / photo thumbnail / barcode + matched product), confidence %, edit fields, primary `Confirm` button + secondary `Edit` button + tertiary `Wrong, redo` button. The "Wrong, redo" affordance is critical — its presence is the reason users trust the suggestion, because being-wrong is cheap. Pattern reference: research §1.5 (Bite AI), §1.7 (Foodvisor).

### Cost-of-undo

Every commit is undoable for 60 seconds via a Snackbar with `UNDO` action. Beyond 60 s, the action is in the event log and must be reversed by inserting a compensating event (event-sourcing — see spec §3). The 60 s window covers the "wait, I misclicked" case without invalidating the audit trail.

## Transparency surfaces (AI Act + GDPR)

### First-paint banner (Art. 4)

`AILiteracyBanner.kt` — `AlertDialog`, mounted by `AILiteracyVersionGate` at `DieticianNav.kt:78`. Content explains the AI is a probabilistic system, surfaces the cost/transparency commitments, links to the audit log. Non-dismissable on back-press until acknowledged. Re-fires when `AI_LITERACY_TEXT_VERSION` (defined at `docs/policies/AI_LITERACY_TEXT_VERSION.md`) bumps. Per RC18: the version bump itself is gated by a policy doc, not a code constant alone.

### Per-call disclosure (Art. 13)

`PerCallDisclosurePane.kt` — sits below every assistant message in `CoachChatScreen`. Displays model name, input/output tokens, cost in cents, timestamp, `call_uuid`. Includes "Open audit row" deep-link to the matching `AuditLogRow` filtered by call_uuid (RC7). Never collapsed by default; user can hide via Settings (`PerCallDisclosureVisibility` toggle, planned).

### Audit log (Art. 14)

`AuditLogScreen.kt:51` — list of `AuditLogRow` cards (`kind`, occurred-at-ms, model, cost cents, summary, optional RC10 "emotion disabled" AssistChip). Filterable by `FilterChip` row (kinds: llm_call / subject_redact / consent_grant / sign_in / planned_cut_activated) and free-text call_uuid filter. Footer holds Export PDF / JSON / DSAR ZIP actions. ConsentRow widgets (Art. 9 health, SCC/DPF cross-border) sit inside this screen as two distinct toggles per RC19.

### Consent (Art. 9, SCC, DPF)

Two `ConsentRow` widgets in `AuditLogScreen`:

1. Art. 9 health-data processing — required for nutrition + weight + supplement data.
2. Cross-border transfer (SCC + DPF, since LLM providers route through US data centres) — required for ClaudeMax / OpenRouter calls.

Each shows: label, grant timestamp, withdraw timestamp (nullable), version hash of the consent text, `Switch`. Withdrawing #1 disables logging surfaces; withdrawing #2 disables Coach (falls back to on-device Ollama if available). State transitions emit `consent_grant` / `consent_withdraw` audit rows.

### DSAR + delete

`AuditLogScreen` footer holds DSAR ZIP export (everything-about-me dump) + delete-my-data CTA (planned, gated by typed-name confirmation per destructive-action UX patterns).

## Refusal + safeguards (ED, bigorexia)

### Hardcoded refusal triggers (spec §28)

Baked into LLM system prompt, **not** UI logic — the model is supposed to refuse and emit a structured `RefusalReason` that the UI surfaces. Triggers:

- Asks for kcal below BMR sustained → "see RDN"
- Weight loss > 1 kg/week sustained → "see RDN"
- Eliminating a food group without medical reason → "see RDN" + cite `wiki/clinical/eating-disorder-red-flags.md`
- Clinical symptoms reported → "see a doctor immediately"
- Fasts > 36 h non-religious / non-medical → "see RDN"
- Diagnostic asks ("do I have X deficiency") → "I flag patterns; only bloodwork + clinician diagnoses"
- Prescription drug dosing → "talk to pharmacist/MD"
- 3+ items from `eating-disorder-red-flags.md` matched → gentle escalation, **never gamify weight loss**

### EDSafeguardModal

`EDSafeguardModal.kt` — fires on `EDDetectorHook` match. Two severity tiers: `HardRefuse` (kcal/protein outside guardrails, fast > 36 h non-religious, etc.) and `SoftWarn` (bigorexia-pattern recognition, repeated obsessive protein questions). Hard refusals block the action; soft warnings surface a banner with `Adjust target` / `Pause Coach for 24h` / `Dismiss` actions. Bigorexia is framed as **process target** ("show me my protein floor 5 days in a row" instead of "I need to hit 200 g protein today"), per RC5.

### Planned-cut 7-day toggle

`PlannedCutToggle.kt` — Switch with a 7-day countdown. Activating "lean cut for 1 week" is allowed (legitimate use case); the toggle auto-expires after 7 days, requires re-activation by the user (cannot be left on indefinitely). Audit row emitted on activation/deactivation. This is the only context where a "below maintenance" recommendation is allowed; outside the 7-day window the planner enforces bulk targets.

### Macro guardrails (hardcoded, never suggest)

- kcal outside `[BMR × 1.0, BMR × 2.5]`
- Protein outside `[1.2 g/kg, 3.5 g/kg]`
- Single food > 50 % daily kcal sustained
- Zero-fat or zero-carb day
- Recipe with logged dislike
- Items in allergens / clinical exclusions

These live in `:shared:domain` constraint code, not UI. UI surfaces refusals + cites the rule.

## Bilingual EN/RO

### Default + switch

Default `en`. User toggles via `LocaleSwitcherRow` in `SettingsScreen`. State held in `SettingsState.locale` (`SettingsStore.kt:21`), persisted via `PersistedSettingsStore`. `LocalLocale` `CompositionLocal` propagates from `DieticianLocaleProvider` at `DieticianNav.kt:60`.

### When Romanian appears at default-EN

- Source-of-record labels: `Mega Image`, `Auchan`, `Carrefour`, `Bonurile Mele.pdf`, `piața Nicolina`. These are proper nouns — never translate.
- SKU descriptions as on packaging: `Telemea de oaie`, `Brânză de Branza`. The label is the contract.
- Dish names from cookbooks: `ciorbă de văcuță`, `sarmale`, `mămăligă`. Romanian where the dish has no idiomatic English name.

### Diacritic rule

Comma-below `ș ț`, never cedilla `ş ţ`. See [[design-visual-language]] §Typography for the font-stack contract.

### Copy enforcement

All UI strings live in `Strings.kt:21` (EN + RO maps). `BigorexiaCopyTest` enforces exact-match for safety-critical EN+RO strings (refusal copy, ED warnings, planned-cut toggle copy). Adding a new user-facing string requires both locales; CI fails if RO is missing.

## Empty states

Every list and dashboard has an empty state. Never a blank `LazyColumn`. Pattern:

- Centred `Card` with `Icon` (24 dp), `Text` headline (titleMedium), `Text` body (bodyMedium), and a primary `Button` CTA to populate the list.
- Headline copy is action-oriented: "No meals logged today" not "Empty." "Pantry is empty" not "No items."
- The CTA does something concrete: "Log a meal" pushes FoodLog; "Add to pantry" opens the AddPantryItemDialog.

Cold-start friendliness matters most on Pantry (starts empty until first scan/manual entry) and Cookbook (3 seed recipes; user ingests more).

## Anti-patterns (do not do)

Mirror of [[design-overview]] §Non-goals at the pattern level:

- ✘ Streak counters anywhere (FoodLog, Settings, Audit)
- ✘ XP, badges, achievements, level-ups
- ✘ Red / green colour for nutrition or weight
- ✘ Comparison to "other users" (the product has one user)
- ✘ Auto-log from photo without confirmation
- ✘ Hidden cost on AI calls
- ✘ Hidden consent toggles (every consent must be a visible row)
- ✘ Modal dialogs for non-critical confirmations (use Snackbar `UNDO` instead)
- ✘ Hover-required affordances on phone (touch-first)
- ✘ Native iOS-style elements on Android or vice versa (Material 3 across the board)
- ✘ Loading indicators with no progress for operations > 2 s
- ✘ Emoji microcopy ("You crushed it!" 🎉) — never
- ✘ Cedilla `ş ţ` instead of comma-below `ș ț`

## Implementation

Full code + library decisions: [[design-implementation]]. Quick pointers:

### Navigation
- **Voyager 1.1.0-beta02** locked per Council 4. 1.1.0-beta03 (Oct 2024) safe minor bump if needed; 2.0.0-alpha01 breaks (Kotlin 2.2 / CMP 1.8 — out of scope).
- **Use `CurrentScreen()`** (NOT `navigator.lastItem.Content()`) — wraps in `SaveableStateHolder.SaveableStateProvider(key = screen.key)` to preserve Compose state.
- **`replaceAll()` discards sub-stacks on tab swap** — Pantry → Cookbook → MealDetail then Home tap then Pantry tap = Pantry re-roots. This is the explicit non-goal of the flat-Navigator design (Council 4 lock). `TabNavigator` would preserve but doesn't handle back press.
- **`BackHandler` in commonMain** — `androidx.compose.ui.backhandler.BackHandler` in CMP 1.7; Android-only impl, desktop no-op. Wire `PushedScreenScaffold` with `BackHandler { navigator.pop() }`.
- **AILiteracyBanner needs `BackHandler(enabled = !acknowledged) { /* swallow */ }`** — currently MISSING (drift #2). Without it Android system-back bypasses the modal.
- **Deep-link routing** — `ExternalUriHandler` singleton in commonMain; Android `MainActivity.onNewIntent` (requires `launchMode = singleTask` in manifest); Desktop `args[0]` parse in `main()`. `MagicLinkVerify(token)` + `Byok` routes MISSING from `DieticianScreen` sealed hierarchy — drifts #10, #11.
- **Modal layered over Navigator** — render `AILiteracyBanner` AS SIBLING of `Navigator` inside theme scope, NOT inside Scaffold content (avoids re-mount on every screen swap).

### Input patterns
- **expect/actual `captureImage()`** — Android: `CaptureHost` `CompositionLocal` at app root owning a registered `ActivityResultLauncher`; bridge via `suspendCancellableCoroutine`. Desktop: `withContext(Dispatchers.IO) { pickImageBytesAwt() }` using `java.awt.FileDialog`.
- **CameraX** — `LifecycleCameraController` + `PreviewView` in `AndroidView`. `takePicture → OnImageCapturedCallback → ImageProxy` — COPY bytes BEFORE `img.close()` (direct ByteBuffer = use-after-free).
- **ML Kit barcode (Android)** — `com.google.mlkit:barcode-scanning:17.3.0` bundled. `MlKitAnalyzer` requires CameraX 1.3.0+ (have 1.3.4). Debounce/unbind after first hit (fires continuously).
- **ZXing desktop fallback** — `com.google.zxing:javase:3.5.3` + `com.google.zxing:core:3.5.3` for file-picker path (no webcam in spec).
- **Open Food Facts** — anonymous; `User-Agent: Dietician/0.1 (victor.vasiloi@gmail.com)` REQUIRED. Rate 15 req/min/IP product, 10 req/min/IP search. Romanian field `product_name_ro` thin coverage — fall back to `product_name`. Cache aggressively (SQLite, 30-day TTL).
- **Coil 3 KMP** — `io.coil-kt.coil3:coil-compose:3.4.0` + `coil-network-ktor3:3.4.0`. Use `LocalPlatformContext.current` NOT `LocalContext`.
- **whisper.cpp** — JNI on Android + Windows (NOT onnxruntime — wrong model format). Large-v3-turbo q5_0 = 547 MiB disk, ~1.5 GB RAM; medium q5_0 = 187 MiB safer for phone. First-launch download from HuggingFace `ggerganov/whisper.cpp`; bundle adds 5-10 min NDK build.
- **Press-and-hold mic** — `Modifier.pointerInput { detectTapGestures(onPress = { ... tryAwaitRelease() ... }) }`. `tryAwaitRelease() == false` = "user dragged away → discard recording" (matches WhatsApp/Telegram).
- **Android permissions** — `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` DIRECTLY. Don't add Accompanist Permissions (still `@ExperimentalPermissionsApi` in 2026). Track "have I asked at least once" in SharedPreferences — `shouldShowRationale` returns `false` BOTH before first ask AND after permanent deny.
- **Autocomplete** — `ExposedDropdownMenuBox` + `Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)` REQUIRED or dropdown floats at (0,0). 250 ms debounce via `snapshotFlow { query }.debounce(250L).distinctUntilChanged().mapLatest { ... }`.

### Data display
- **Charts** — keep hand-rolled `Canvas`. Vico / KoalaPlot / ComposeCharts comparison + reasoning in [[design-implementation]] §Charts. Wrap every Canvas in `Modifier.semantics(mergeDescendants = true) { contentDescription = "Adaptive expenditure: 2750 kcal posterior, target 2750 kcal, 5 observations" }` — TalkBack/Narrator gets nothing otherwise.
- **MetricRingNeutral sweep** — gate on `var hasUserInteracted by remember`; static at `progress = 1f` on first paint, animate via tap only.

### Transparency surfaces
- **AILiteracyBanner non-dismissable** — `properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)`. Empty `onDismissRequest` alone is INSUFFICIENT on Android 14+ predictive back. Drift #1.
- **Version gate** — cap bumps at ≤2/year per Council R2 FM-11 (banner fatigue).
- **Per-call disclosure** — truncate `call_uuid.take(3) + "…" + takeLast(2)` for visual density; full UUID stays as `testTag` suffix.
- **Audit log filter** — `combine(kindFilter, callUuidFilter).debounce(150L).mapLatest { ... }`. Current `AuditLogViewModel.kt:76-84` fires per-keystroke without debounce — drift #3.
- **Consent rows** — two independent toggles, NEVER combined (GDPR Art 7(2) dark pattern, noyb enforcement cases). Withdrawal as easy as granting — no confirm dialog (Art 7(3)).
- **DSAR PDF** — desktop `pdfbox:3.0.3` (wired); Android `android.print.pdf.PrintedPdfDocument` (in-platform, zero new dep). `pdfbox-android` GPL+classpath-exception — audit before bundling.
- **DSAR ZIP** — `java.util.zip.ZipOutputStream` works both targets. Include `README.txt` plain-language file index + `manifest.sha256`.
- **EDSafeguardModal `Dismiss`** — MUST emit audit row (currently doesn't — drift #4). For `HardRefuse` swap `confirmButton`/`dismissButton` so `Pause` is primary, `Adjust target` secondary.
- **BYOK paste** — `ClipDescription.EXTRA_IS_SENSITIVE = true` on Android 13+; NEVER log key value, only `sha256(key).take(12)` as `key_version_hash`.
- **AI Act Art 50 (Aug 2026)** — "Powered by Claude" alone is INSUFFICIENT (EC May 2026 draft guidelines call vendor jargon insufficient). Layered: TopAppBar subtitle "AI assistant" + first-message disclosure + per-message `AssistChip("AI")` badge.
- **Withdraw consent** — block FUTURE processing only, NEVER delete prior data (Art 7(3)).
- **Audit row enforcement** — single chokepoint `AuditedMutator` wrapper. Future custom Detekt rule `MissingAuditOnRegulatedMutation`.

### Accessibility (WCAG 2.2)
- **`semantics(mergeDescendants = true)`** for full-card a11y; `clearAndSetSemantics` for icon-only buttons.
- **Live region** — chat streaming tokens use `liveRegion = LiveRegionMode.Polite` (NEVER `Assertive`).
- **`CustomAccessibilityAction`** for `PhotoSuggestionCard` (Confirm / Edit / Wrong) — pair with `clearAndSetSemantics` or TalkBack double-announces children.
- **Desktop focus order follows PLACEMENT not visual** — always override with `focusProperties { next = … }` when columns are involved.
- **Windows Compose Desktop a11y** goes through **Java Access Bridge** (NOT directly to Narrator). Run `%JAVA_HOME%\bin\jabswitch.exe /enable` once + add `modules("jdk.accessibility")` to `compose.desktop.application.nativeDistributions {}`. Validated JAWS + NVDA. Linux NOT supported (moot per Windows-only desktop spec).
- **Chat bubble color-blind safety** — alignment + colour + semantic prefix triplet (Amber-user vs Sage-assistant invisible under deuteranopia 8% of male users).
- **Romanian TTS** — `SpanStyle(localeList = LocaleList("ro-RO"))` switches TalkBack voice. Narrator via JAB does NOT switch voice mid-string (known gap).
- **A11y audit test** — walk semantics tree with `useUnmergedTree = true`; assert every interactive node has `contentDescription` OR `text` + touch target ≥ 48 dp.

### Desktop windowing
- **Keep native title bar** — undecorated has resize bug #178, animation bug #3388, loses Windows 11 Snap Layouts.
- **Window state persistence** — extend `SettingsState` with `windowSize` / `windowPosition` / `windowPlacement`; `snapshotFlow { Triple(...) }.debounce(300).collect { save(...) }`.
- **Min size** — `LaunchedEffect(Unit) { window.minimumSize = Dimension(1024, 720) }` (AWT, after Window realised).
- **`onPreviewKeyEvent`** — FILTER on `KeyEventType.KeyDown` first or shortcuts fire twice. Only return `true` for handled keys.
- **Command palette** — `AlertDialog` (NOT separate `Window`). Hand-rolled fuzzy scorer; seed index with screens + recipes + pantry + audit + `/diag`. Defer `kt-fuzzy`/`sublime-fuzzy` until index >500.
- **System tray** — `Tray { Item }` inside `application { }` scope. 16/22/24 px icons for Win/macOS/GNOME.

### i18n + Markdown
- **KEEP sealed-interface `Strings`** (compile-time exhaustiveness wins over runtime locale switch pain). Refactor 588-line `Strings.kt` into per-feature interfaces extending one root.
- **Markdown** — `com.mikepenz:multiplatform-markdown-renderer-m3:0.33.0+`. Pipeline: `raw → TransclusionResolver.expand → WikilinkPreprocessor.rewrite → Markdown(state)`.
- **`[[link]]` resolver** — pre-process to `[slug](dietician://wiki/slug)` standard link; custom `LocalUriHandler` routes via Voyager.
- **`![[slug.data]]` transclusion** — regex pre-process, strip `<!-- AUTOGENERATED -->` header; cache by `(slug, mtime)`; `directory-watcher` invalidate; depth cap 3 against recursion.

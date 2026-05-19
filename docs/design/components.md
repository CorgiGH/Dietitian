---
title: "Components — Anatomy, usage rules, accessibility notes"
slug: design-components
domain: design
applies_to: []
sources:
  - name: "Dietician frontend codebase 2026-05-19 map"
    url: "shared/src/commonMain/kotlin/com/dietician/shared/ui/"
    citation: "Explore agent map 2026-05-19"
    accessed: 2026-05-19
  - name: "Material 3 Compose component reference"
    url: "https://developer.android.com/develop/ui/compose/designsystems/material3"
    citation: "Material 3 Compose"
    accessed: 2026-05-19
  - name: "WCAG 2.2 — touch target + focus"
    url: "https://www.w3.org/TR/WCAG22/"
    citation: "W3C WCAG 2.2 2023-10-05"
    accessed: 2026-05-19
authority: derived
confidence: high
last_verified: 2026-05-19
review_cadence_days: 30
instantiated_for_user: false
user_numbers: {}
related: [design-overview, design-visual-language, design-ux-patterns, design-references]
contradicts: []
supersedes: []
tags: [design, components, composables, atoms, molecules]
---

# Components

Every reusable Composable that ships in two or more screens (or is structurally load-bearing for one) gets an entry. Entries are alphabetical within section. Each entry: purpose, anatomy, props (informal), do, don't, accessibility note, source anchor.

For where each is currently used and what it visually looks like, run the app — these are spec entries, not screenshots. The source files are the visual source of truth.

## Sections

- [Cards](#cards)
- [Buttons + capture](#buttons--capture)
- [Inputs](#inputs)
- [Modals + notices](#modals--notices)
- [Charts + data viz](#charts--data-viz)
- [Chat](#chat)
- [Navigation + scaffolding](#navigation--scaffolding)
- [Compliance + audit](#compliance--audit)
- [Planned (not yet built)](#planned-not-yet-built)

---

## Cards

### `SubjectCard`

`shared/.../ui/components/SubjectCard.kt`

**Purpose**: identify the user on the Home dashboard. Display name + truncated subject id + Settings shortcut.

**Anatomy**: `Card` (12 dp radius) containing a `Row`: text column on the left (`displayName` title + `subjectId` caption, e.g. `Victor` / `stub-subject-0000`), `IconButton` on the right (Settings gear → push `Settings`).

**Props**: `displayName`, `subjectId`, `onSettingsTap`.

**Do**: keep concise; this is identity + escape hatch, nothing else. Show only the first 8 chars of subject id with `…` suffix.

**Don't**: surface avatar, badges, or "level." This is not a profile card.

**Accessibility**: Settings gear has `contentDescription = "Settings"` (i18n via `Strings`). Touch target 48 dp via `IconButton`.

### `TodayNutrientsCard`

`shared/.../ui/components/TodayNutrientsCard.kt`

**Purpose**: snapshot of today's kcal + macros against targets. Hero card on Home.

**Anatomy**: `Card` (12 dp). Vertical stack of `MacroRow` (one per nutrient: kcal, protein, carbs, fat). Each row: label (left), value/target (centre), neutral-framed delta chip (right).

**Props**: `state: TodayNutrientsState` (`TodayNutrientsCard.kt:56` — kcal/protein/carbs/fat + their targets as Doubles).

**Do**: use `formatMacroLabel` (`TodayNutrientsCard.kt:45`) for the "above/below target by N%" copy. Always neutral.

**Don't**: colour rows red/green based on delta. The `NeutralChip` token is the only acceptable styling. See [[design-overview]] §2 and [[design-ux-patterns]] §Data display.

**Accessibility**: each row reads as a single accessibility node with full text (label + value + target + delta). Tabular figures planned (Plan-7).

### `PantryItemCard`

`shared/.../ui/components/PantryItemCard.kt`

**Purpose**: a single pantry SKU in the Pantry list, sorted FEFO.

**Anatomy**: `Card` (12 dp). Row with display name + qty/unit on the left, two optional `AssistChip`s on the right: `open` (if package opened) and `expires in Nd` (if expiring soon).

**Props**: `item: PantryItemView` (`PantryViewModel.kt:102`).

**Do**: keep both chips neutral (NeutralChip styling). The expiry chip is a fact, not a warning — copy is `"expires in 2d"` not `"⚠ expires soon"`.

**Don't**: highlight expired items in red. Use the same neutral chip with copy `"expired 1d ago"`. The action is "should I cook with this," not "panic."

**Accessibility**: tap target is the full card row (48 dp+ via `Card.onClick`). Chip text is accessibility-included in the card description.

### `RecipeCard`

`shared/.../ui/components/RecipeCard.kt`

**Purpose**: a recipe row in Cookbook.

**Anatomy**: M3 `Card(onClick = onTap)`. Title (titleMedium) + servings (bodySmall caption).

**Props**: `recipe`, `onTap`.

**Do**: keep tap-target the entire card. Use `onClick` lambda, not a child `Button` (Material 3 best practice).

**Don't**: add nutrition summary or thumbnails yet — the seed corpus is too small to be meaningful. Plan-7 task: add nutrient hero metric (`kcal/serving`) and an optional user-captured photo when one exists.

**Accessibility**: card content description combines title + servings.

### `AuditLogRow`

`shared/.../ui/components/AuditLogRow.kt`

**Purpose**: one audit event in the Audit Log screen.

**Anatomy**: `Card` (12 dp). Row with kind label + occurred-at timestamp + model (if llm_call) + cost cents + summary + optional `AssistChip` for RC10 "emotion disabled" status.

**Props**: `row: AuditRow` (`AuditRepository.kt:46`).

**Do**: timestamp uses 24 h format with seconds (technical user). Cost in cents shown as `"42¢"` not `"$0.42"` — cleaner and emphasises the precision.

**Don't**: collapse fields. The point is full transparency; every field shows.

**Accessibility**: full row reads as one node; chip read as a separate node.

### `ConsentRow`

`shared/.../ui/components/ConsentRow.kt`

**Purpose**: a single consent grant/withdraw row in the Audit Log screen.

**Anatomy**: `Row` containing label + grant/withdraw timestamp + version hash + `Switch`.

**Props**: scope, granted boolean, grantedAtMs?, withdrawnAtMs?, versionHash?, onToggle.

**Do**: explicit version hash visible — when the consent text changes, the user sees the hash change. Two rows in `AuditLogScreen` (Art. 9 + SCC/DPF) — never one combined toggle (RC19).

**Don't**: collapse into a single "I agree to terms" checkbox. Granular consent is the legal requirement; one consent per scope.

**Accessibility**: `Switch` has explicit `contentDescription`; version hash is a single accessibility node.

### `PaperResultCard`

`shared/.../ui/components/PaperResultCard.kt`

**Purpose**: paper search result in PaperSearch.

**Anatomy**: `Card` (12 dp). Title + abstract snippet + score % + `TextButton` "Open detail."

**Props**: `result: PaperResult`.

**Do**: keep snippet to 3 lines max with `…` truncation.

**Don't**: open the paper in-app yet (Plan-7); button currently routes to a 501-stub screen.

### `ReceiptPreviewCard`

`shared/.../ui/components/ReceiptPreviewCard.kt`

**Purpose**: confirm a captured receipt photo before upload.

**Anatomy**: `Card`. Image thumbnail + byte-count caption + two buttons: `Retake` (`OutlinedButton`) + `Upload` (`Button`).

**Props**: `imageBytes`, `onRetake`, `onUpload`.

**Do**: show byte count — gives the user an honest sense of upload cost on metered networks.

**Don't**: auto-upload on capture. Always confirm.

### `PhotoSuggestionCard`

`shared/.../ui/components/PhotoSuggestionCard.kt`

**Purpose**: one candidate result from photo-recognition.

**Anatomy**: `Card`. 48 dp icon/thumbnail placeholder + label + confidence % + three `TextButton`s: `Confirm`, `Edit`, `Wrong`.

**Props**: `suggestion: PhotoSuggestion`, callbacks.

**Do**: always show confidence %. Always have three actions (confirm / edit / wrong-redo).

**Don't**: auto-confirm even at 99 % confidence. See [[design-overview]] §5.

### `PhotoSuggestionList`

`shared/.../ui/components/PhotoSuggestionList.kt`

**Purpose**: scrollable list of N suggestions + RC11 "None of these" escape.

**Anatomy**: `LazyColumn` of `PhotoSuggestionCard` + footer `TextButton` "None of these."

**Do**: "None of these" routes to manual-entry, never silently dismisses.

**Don't**: limit to one suggestion. Three to five candidates is the right count (research §1.5).

### `ChatMessageBubble`

`shared/.../ui/components/ChatMessageBubble.kt`

**Purpose**: a single chat message in CoachChat.

**Anatomy**: `Card`. `primaryContainer` background for user messages, `surfaceVariant` for assistant. `widthIn(max = 280.dp)`. Right-aligned for user, left-aligned for assistant.

**Props**: `message: ChatMessage`.

**Do**: keep max width 280 dp on phone — wider reads as a wall of text. Desktop can relax to 480 dp via a `widthIn` override (planned).

**Don't**: distinguish user/assistant only by colour. The alignment + colour pairing carries the role; either alone fails colour-blind tests.

**Accessibility**: bubble accessibility node carries role prefix ("You said: …" / "Coach said: …").

### `PerCallDisclosurePane`

`shared/.../ui/components/PerCallDisclosurePane.kt`

**Purpose**: AI Act Art. 13 per-call disclosure attached to every assistant message.

**Anatomy**: small `Row` below the assistant bubble. Model name, input tokens, output tokens, cost in cents, call_uuid (truncated), "Open audit row" `TextButton`.

**Props**: `disclosure: DisclosureInfo` (`CoachChatViewModel.kt:257`).

**Do**: always visible by default. Click "Open audit row" pushes `AuditLog` with `callUuid` filter pre-set (RC7).

**Don't**: collapse into an icon-only "info" affordance. The disclosure must be glanceable, not hidden behind a tap.

---

## Buttons + capture

### `VoiceRecordButton`

80 dp full-width `Button` with Mic icon (currently Phone icon — see [[design-visual-language]] §Iconography) + label.

**Do**: fire haptic on press, release-to-finalise. Show waveform during recording (Plan-7).

**Don't**: ship always-on listening. Press-to-record only.

### `BarcodeScanButton`

80 dp full-width `Button` with barcode icon (currently using Refresh — same icon-mismatch issue).

**Do**: open camera with a clear scan-frame overlay. Haptic + sound on successful scan.

**Don't**: open without permission check.

### `PhotoCaptureButton`

80 dp full-width `Button`. Currently uses Face icon. Pushes `ReceiptUpload` on tap (Android: CameraX; desktop: AWT FileDialog via expect/actual `captureImage()`).

**Do**: route through the suggestion-confirm flow (`PhotoSuggestionList`), not the receipt-upload flow, when the use case is "what am I eating right now."

**Don't**: silently send the photo to vision OCR without an in-flight progress indicator.

### `SameAsRecentButton`

80 dp full-width `Button`. Currently shows empty BottomSheet placeholder (`FoodLogScreen.kt:80-87`).

**Do**: when wired, show last 3 meals of the matching slot (breakfast/lunch/dinner/snack inferred from time of day) + an "older" expansion.

**Don't**: show recent meals across all slots — too much. Slot-scoped.

### `ManualEntryField`

`OutlinedTextField` + autocomplete dropdown rows.

**Do**: surface recent matches first, then USDA/CIQUAL DB, then "create new." Debounce 250 ms.

**Don't**: auto-commit on Enter; require explicit confirm.

### `IngestUrlButton`

`OutlinedButton` opening an `AlertDialog` with URL entry.

**Do**: validate `http`/`https` prefix client-side. Show snackbar result on close ("Queued" / "501 — pipeline pending" / "Failed: …").

**Don't**: block on slow ingest; queue and return.

### `JustTellMeButton`

`OutlinedButton` in CoachChat actions — AI Act Art. 14 bypass for "skip the careful flow, just give me an answer."

**Do**: log the use of this affordance to the audit log with a distinct `kind`.

**Don't**: hide it. The point is that the user can always opt out of guardrails for a single query.

### `PlannedCutToggle`

`Card` containing a `Switch` + countdown text. Companion `PlannedCutController` (`PlannedCutToggle.kt:87`) owns the 7-day window logic.

**Do**: auto-expire at 7 days; emit audit row on activation + deactivation.

**Don't**: allow "remember choice" — re-activation must be deliberate.

---

## Inputs

### `AddPantryItemDialog`

`AlertDialog` (`PantryScreen.kt:143`). Name + qty (digits-only `KeyboardOptions`) + unit text fields.

**Do**: digits-only filter on qty enforced via `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)` + visualTransformation that rejects letters.

**Don't**: free-form unit input long-term; should be a dropdown of known units (g/ml/buc/L/kg) once unit normalisation lands.

### `BYOK provider dropdown + masked password field`

`ByokScreen.kt:50`. `DropdownMenu` for provider, `OutlinedTextField` with `PasswordVisualTransformation` for the key, Save button.

**Do**: RC13 paste-detection — on clipboard paste, fire snackbar "key pasted from clipboard; clipboard cleared." Audit row on save + on delete.

**Don't**: log the key value anywhere. Hash for `version_hash` only.

---

## Modals + notices

### `AILiteracyBanner`

`AlertDialog`. AI Act Art. 4. **Non-dismissable on back-press**, only via explicit acknowledge button.

**Do**: re-fire on `AI_LITERACY_TEXT_VERSION` bump (see `docs/policies/AI_LITERACY_TEXT_VERSION.md`).

**Don't**: convert to a snackbar or a banner-strip — the regulatory weight requires modal treatment.

### `EDSafeguardModal`

`AlertDialog` with severity banner (`HardRefuse` red / `SoftWarn` neutral). Bigorexia-aware framing (process target). Evidence line cites the pattern matched. Inline `PlannedCutToggle` for legitimate 7-day cut activation. Three actions: `Adjust target`, `Pause`, `Dismiss`.

**Do**: log every fire to audit log. Soft-warn 3+ in a week escalates to hard refuse.

**Don't**: dismiss-without-trace. Even `Dismiss` emits an audit row.

### `CoachDisabledNotice`

`Card` with copy "Coach is disabled in Settings" + `TextButton` "Re-enable in Settings" pushing `Settings`.

**Do**: surface in `CoachChatScreen` and at the bottom of `FoodLogScreen` (so the user is reminded why no Coach suggestions appear). RC9.

**Don't**: nag — show once per session, dismissable.

---

## Charts + data viz

### `AdaptiveExpenditureChart`

Compose `Canvas` line chart. Neutral colours (Amber line, dashed target line, Sage dots for observed intakes).

**Do**: render statically on first paint; animate only on user interaction (4 wk/12 wk/26 wk toggle). Cap visible range to last 90 days unless user expands. **Currently not mounted on Home** — only a placeholder card; this is the top design debt for the dashboard.

**Don't**: animate the line drawing as a "celebration." See [[design-overview]] §1.

### `WeightTrendChart`

Compose `Canvas` line chart. 4wk/12wk/26wk `FilterChip` toggle. RC5 subtitle "weekly aggregate, not daily."

**Do**: never plot daily values. Weekly mean is the signal.

**Don't**: highlight individual weekly points with colour change. Trend line + dots, uniform styling.

### `NutrientBar`

120 dp name + filled bar + numeric label. `displayCapMultiplier = 1.5×` (visual cap; numeric continues past).

**Do**: use for the full 84-nutrient view (Plan-7 expansion from current 25-entry skeleton at `NutrientCatalog.kt:45`). Bar fill is Amber Primary, background NeutralChip.

**Don't**: green-fill at-target, red-fill over-target.

### `NutrientChip`

Text-only neutral chip. Font-weight contrast only (no colour change between roles).

**Do**: use for compact nutrient summaries inside `RecipeCard` and `MealDetail` (Plan-7).

---

## Chat

(See [Cards](#cards) above for `ChatMessageBubble` and `PerCallDisclosurePane`.)

---

## Navigation + scaffolding

### `DieticianBottomNav`

`DieticianNav.kt:124`. `NavigationBar` with 5 `NavigationBarItem`s. Selected state per `navigator.lastItem.key`.

**Do**: `replaceAll` on tap (shallow stack at the root). `testTag("nav-{key}")` per item.

**Don't**: nest sub-tabs. Pushed screens are pushed, not tabbed.

### `PushedScreenScaffold`

`PushedScreenScaffold.kt:29`. Wraps pushed-screen content with `Scaffold` + `TopAppBar` + back arrow.

**Do**: every pushed screen wraps in this. The back arrow has `testTag("pushed-screen-back")` and `contentDescription = Strings.back`.

**Don't**: build a custom TopAppBar per screen.

### `DieticianApp`

`DieticianNav.kt:52`. Root composable. Wraps `DieticianLocaleProvider → DieticianTheme → Scaffold(bottomBar = DieticianBottomNav)`. `OnboardingScreen` or `Navigator(screen = DieticianScreen.Home)`.

---

## Compliance + audit

(See [Cards](#cards) above for `AuditLogRow`, `ConsentRow`, `PerCallDisclosurePane`.)

### `LocaleSwitcherRow`

`SettingsScreen` row with two buttons (EN / RO), disabled-when-selected pattern.

**Do**: persist immediately via `SettingsStore`.

**Don't**: rebuild the whole tree on switch — use `LocalLocale` for runtime swap.

### `ToggleRow`

`SettingsScreen` reusable row: label left, `Switch` right.

**Do**: log toggles to audit when they cross a compliance threshold (`Coach disabled`, `Accessible typography` arguable; `Dark theme` no).

---

## Planned (not yet built)

### `BentoMetricCard`

Hero dashboard tile, variable span (1×1, 2×1, 2×2). For the spec §30 acceptance criteria `today-macros-progress` as hero (spans 2 cols on desktop), with smaller tiles for `next-meal-card`, `quick-log-button`, `quick-photo-button`. Defer until ≥4 dashboard tiles exist; bento prematurely is just a 4-card grid.

### `MetricRingNeutral`

Donut/ring chart for kcal progress (the "rings" pattern). **Must** stay neutral — Amber fill on NeutralChip background; never red/green segments. Compose-Canvas implementation, motion only on first paint (single arc-sweep over 250 ms), then static.

### `EmptyStateCard`

Centralised empty-state pattern (icon, headline, body, optional CTA). Replaces the bespoke empty Card each screen invents.

### `SkeletonRow`

Shimmer-rectangle loading placeholder. Use during real-data-arriving on Pantry, Cookbook, AuditLog, PaperSearch.

### `CommandPalette` (desktop)

Raycast / Linear `Ctrl+K` pattern. Desktop-only. Searches: screens, recipes, pantry items, audit rows, `/diag`, slash commands. Fuzzy-match. Recent-first. Defer until Plan-7+.

### `ModalBottomSheetPantryDetail`

Replace the inline detail Card in `PantryScreen.kt:103-118` with a proper M3 `ModalBottomSheet`. Includes consume / discard / mark-open actions and a small chart of "qty over last 30 days."

### `NextMealCard`

`HomeScreen` tile per spec §30 `next-meal-card`. Shows planner's top recommendation: dish name + cook time + macros snapshot + "Start cooking" → `MealDetail`.

### `QuickLogModal`

Phone-only bottom sheet triggered from a `HomeScreen` FAB. Wraps the FoodLog 5-capture choices in a sheet rather than a full screen. Trade-off: lighter for "log a snack" use cases, but adds a layer for the deep-log case. Brainstorm before shipping.

### `BudgetTrackerCard` + `ShoppingListByStore` + `LossLeaderAlertsCard`

For the eventual `Shopping` tab per spec §30. Defer until Plan-6 (scrapers) lands price data.

### `DiagScreen`

For the spec §30 `/diag` acceptance criteria. One screen mirroring the §23 `/diag` shell output. Sections: Connectivity, Writes, Other devices, LLM budget, Scraper status, Last 3 errors, Pending jobs. Each with the `data-testid` selectors from §30.

---

## Implementation

Full code patterns + library decisions: [[design-implementation]]. Per-component-class pointers:

### Cards
- **`Card(onClick = …)`** — full-card tap target. Child `AssistChip(onClick)` consumes the press (parent doesn't fire). DO NOT pass `onClick = onTap` to both card AND chip. Current `PantryItemCard.kt:63, 72` does this — harmless until Card.onClick is added.
- **`AssistChip` neutral colours** — `AssistChipDefaults.assistChipColors(containerColor = NeutralChip.backgroundLight, labelColor = NeutralChip.foregroundLight)`. Current `PantryItemCard.kt:65, 73` ships no-arg → defaults to `colorScheme.surface` (R3 spec drift, fix #7 in [[design-implementation]]).
- **`Card.surfaceTint`** is currently M3 brand purple because `surfaceTint` isn't overridden in `DieticianColors.kt` (drift #6) — visible on every elevated Card.

### Buttons + capture
- **`ExtendedFloatingActionButton`** — `PantryScreen.kt:127` passes `icon = {}` which reserves 12 dp start-padding and pushes text off-centre. Pass a real `Icon` or use the no-icon overload. Drift #9.
- **`VoiceRecordButton`** — `Modifier.pointerInput { detectTapGestures(onPress = { ... tryAwaitRelease() ... }) }`. `tryAwaitRelease() == false` = drag-away → discard recording.
- **`PhotoCaptureButton`** + **`BarcodeScanButton`** — expect/actual `captureImage()` pattern; CameraX `LifecycleCameraController` + `PreviewView` in `AndroidView` on Android; AWT `FileDialog` on desktop.
- **Icons** — adopt `dev.seyfarth:tabler-icons-kmp:1.0.0` (drift fix for current Phone/Refresh/Face semantic mismatches per `DieticianNav.kt:97` comment). 20-glyph mapping in [[design-implementation]] §Iconography.
- **`BarcodeScanButton`** — Android `com.google.mlkit:barcode-scanning:17.3.0` via `MlKitAnalyzer`; debounce/unbind after first hit. Desktop fallback `com.google.zxing:javase:3.5.3` for picked-photo decode.

### Inputs
- **`AddPantryItemDialog`** — `KeyboardOptions(keyboardType = KeyboardType.Decimal)` is a hint; OS keyboard CAN still emit letters (paste, hardware kb). Filter in `onValueChange` + re-validate at ViewModel.
- **BYOK masked password** — `PasswordVisualTransformation()`. DO NOT also set `KeyboardType.Password` — triggers password-manager save prompts, leaks API key to Google Password Manager. Use `KeyboardType.Text` + `imeAction = ImeAction.Done`.
- **`ManualEntryField` autocomplete** — `ExposedDropdownMenuBox` + `Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)` REQUIRED or dropdown floats at (0,0). 250 ms debounce via `snapshotFlow.debounce.distinctUntilChanged.mapLatest`.

### Modals + notices
- **`AILiteracyBanner`** — `properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)` REQUIRED. Empty `onDismissRequest = {}` alone insufficient on Android 14+ predictive back. Drift #1. Also needs `BackHandler(enabled = !acknowledged) { /* swallow */ }` at the version-gate block in `DieticianNav.kt:78` (drift #2).
- **`EDSafeguardModal`** — same `DialogProperties` for `HardRefuse`. Pair severity colour with Icon (`Icons.Outlined.WarningAmber` for HardRefuse, `.Info` for SoftWarn) — NEVER red. `Dismiss` action MUST emit audit row (drift #4). For HardRefuse swap confirmButton/dismissButton: `Pause` becomes primary.
- **Snackbar UNDO** — `SnackbarDuration.Long = 10_000 ms` ≠ spec 60 s. Use `Indefinite` + `launch { delay(60_000); snackbarHostState.currentSnackbarData?.dismiss() }`. Drift #5.

### Charts + data viz
- **Decision: keep hand-rolled `Canvas`** over Vico / KoalaPlot / ComposeCharts. Library comparison + reasoning in [[design-implementation]] §Charts.
- **Resolve theme colours in PARENT `@Composable`** and pass into `DrawScope` helper (DrawScope isn't `@Composable`).
- **Accessibility overlay** — `Modifier.semantics(mergeDescendants = true) { contentDescription = "Adaptive expenditure: 2750 kcal posterior, target 2750 kcal, 5 observations" }`. Per-point exploration: invisible focusable `Box`es at each data point. Per Eevis Panula 2023.
- **`NutrientBar` tabular figures** — `style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "\"tnum\"")`. Verify on `:desktopApp:run` (Skia historically drops the feature).
- **`MetricRingNeutral`** — `drawArc(useCenter = false, style = Stroke(12.dp, cap = StrokeCap.Round))`; `var hasUserInteracted by remember`; static at progress=1 on first paint, animate only on tap.

### Chat
- **`ChatMessageBubble`** — alignment + colour + semantic prefix triplet for color-blind safety. Pattern in [[design-implementation]] §Accessibility.
- **`PerCallDisclosurePane`** — truncate `call_uuid` display to `take(3) + "…" + takeLast(2)`; keep full UUID as `testTag` suffix.
- **Token-stream auto-scroll** — `derivedStateOf { nearBottom }`; coalesce rapid tokens via `snapshotFlow { lastMessage.text }.conflate().sample(33.ms)`.
- **Blinking caret** — `rememberInfiniteTransition` + `infiniteRepeatable(tween(400, LinearEasing), RepeatMode.Reverse)`. Wrap in `if (isStreaming)` so it tears down on cancel.

### Navigation + scaffolding
- **`DieticianBottomNav`** — `NavigationBarItemDefaults.colors(indicatorColor = primaryContainer)` to recolor to Amber. testTag `nav-{key}` already correct.
- **`PushedScreenScaffold`** — add explicit `BackHandler { navigator.pop() }` for defence-in-depth (drift #12). `TopAppBar` `enterAlwaysScrollBehavior` does NOTHING without `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` — easy to miss.
- **`DieticianApp`** — render `AILiteracyBanner` AS SIBLING of `Navigator` inside theme scope (not inside Scaffold content — avoids re-mount on every screen swap).

### Compliance + audit
- **`AuditLogRow`** — already correct (timestamps with seconds, cost in cents `"42¢"`).
- **`ConsentRow`** — two independent toggles, NEVER combined (GDPR Art 7(2) dark pattern). Withdrawal MUST be as easy as granting (Art 7(3)) — no confirm dialog.
- **`PerCallDisclosurePane`** deep-link — `navigator.push(AuditLogScreen(initialCallUuidFilter = uuid))` then `AuditLogViewModel` applies filter via `combine(...).debounce(150L)` flow (drift #3 — current per-keystroke fetch).

### Planned
- **`BentoMetricCard`** — use `LazyVerticalGrid` + `GridItemSpan(2)` NOT `LazyVerticalStaggeredGrid` (only `FullLine` span available — no `Span(n)`). Accept fixed row heights. Defer until ≥4 dashboard tiles exist.
- **`MetricRingNeutral`** — see Charts above.
- **`EmptyStateCard`** — `Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow))` + Icon (24 dp) + titleMedium + bodyMedium + primary Button CTA.
- **`SkeletonRow`** — `com.valentinilk.shimmer:compose-shimmer:1.4.0` (KMP). Skip entirely when `reduceMotion`.
- **`CommandPalette`** — `AlertDialog` (NOT separate `Window`). Hand-rolled fuzzy scorer; seed with screens + recipes + pantry + audit + `/diag`. Recents in SQLite.
- **`ModalBottomSheetPantryDetail`** — `@ExperimentalMaterial3Api`. `skipPartiallyExpanded = true` REQUIRED on desktop or sheet sticks mid-screen.
- **`NextMealCard`** — image via Coil 3 `AsyncImage` with `LocalPlatformContext.current`.
- **`DiagScreen`** — `LazyColumn` of cards; one card per §23 section; testTag selectors per §30.

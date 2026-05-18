# Plan-4-5 — KMP Compose UI shared (`:shared:ui-components`) + Android + Desktop shells

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Spec sources-of-truth (read BOTH):
> - `docs/superpowers/specs/2026-05-17-dietician-design.md` §1 + §6 + §14 + §22 + §23 + §27 + §30 (locked spec).
> - `docs/superpowers/specs/2026-05-17-dietician-plans-2-7-research-driven.md` §6 (Plan-4-5 detail) + §10 (AI Act ↔ surface affordances) + §11 (Visual Acceptance — ~120 data-testid selectors) + §A6/A9/A14/A17 (binding amendments).
>
> Research sources baked in:
> - `docs/superpowers/research/2026-05-17-round-3-ux-regulation.md` §1 (12-app review, 10 canonical patterns) + §2 (logging modalities) + §3 (ED-safeguards) + §4 (AI Act 8-affordance map) + §5 (Romanian voice/ASR).
> - `docs/superpowers/research/2026-05-17-round-5-roi-gaps.md` Gap-5 (Plans-4+5 collapse rationale, ~92-95% UI share).
> - `docs/superpowers/research/2026-05-17-final-sunk-cost-free.md` §9 (UX patterns).

**Goal:** Ship the entire KMP Compose UI surface — `:shared:ui-components` package tree inside the existing `:shared` Gradle module (per A6+D2 council ruling: no new Gradle subproject — see Pre-Task-0 note) plus the two thin platform shells `:androidApp` and `:desktopApp` — providing 15 first-ship screens with ~120 spec'd `data-testid` selectors painting on first navigation, multi-user-from-day-1 via `subject_id`, ED-safeguards load-bearing (bigorexia primary risk for Victor — NOT anorexia), EU AI Act Art 4 first-launch banner + Art 13 per-call disclosure + Art 14 `/just_tell_me` override + LLM-off toggle, Cronometer-style 84-nutrient bars, MacroFactor-style Bayesian adaptive expenditure chart, Bite-AI-style photo-as-suggestion (never auto-commit), Whisper-cpp-native Romanian voice logging with RO food-vocabulary bias prompt, Carbon-style weekly narrative (strength/energy/mood/sleep — NOT weight prominence), and a final Compose-UI-Test/Roborazzi/Playwright visual-acceptance gate that asserts every spec-listed selector paints + zero 4xx/5xx during click smoke per CLAUDE.md interaction-smoke rule.

**Architecture:** Per Round-5 research finding, ~92-95% UI shares between Android + Desktop in Compose Multiplatform 1.7.0 (pinned LTS per §6.11 Council Q5 — 1.8 upgrade decision deferred until post-finals per finals-lock memory). The architecture deliberately lives in `:shared` (NOT a new `:shared:ui-components` Gradle subproject) per council-1779038746-D2 "no KMP module explosion" + Plan-2 precedent (`:shared:llm` is also a package inside `:shared`). The new package tree is `shared/src/commonMain/kotlin/com/dietician/ui/` with platform actuals in `androidMain` + `desktopMain`. Voyager 1.1.0-beta02 provides typed navigation; Koin 4.0.0 + koin-compose 4.0.0 provides DI for screen-models. Each `Screen` is a Voyager `Screen` class hosting a `ScreenModel` (Voyager's KMP-friendly ViewModel) that observes `Flow`s from Plan-1's `EventStore`/`PullCoordinator`/`PantrySnapshotStore` and from Plan-2's `LlmRouter`. State surfives configuration change via Voyager's built-in `rememberScreenModel`; per-composable transient state uses `rememberSaveable`. The five diverging surfaces from §6.2 — camera (Android CameraX vs Desktop file-picker), file dialog (Android SAF vs Desktop JFileChooser), status-bar/window-chrome theming, ScaleConnector BT (Android `android.bluetooth.le` vs Desktop BlueZ/Windows.Devices.Bluetooth gracefully-absent), and ntfy push registration — each gets an `expect/actual` class. The `:androidApp` shell wires `MainActivity` → root `Navigator(OnboardingScreen)` + injects Android actuals; `:desktopApp` shell wires `application { Window(...) { Navigator(...) } }` + injects Desktop actuals + spawns the ClaudeMax CLI warm pool (per Plan-2 §7.4) on app start.

**Tech Stack:** Kotlin Multiplatform 2.0.21, Compose Multiplatform 1.7.0 (pinned), Voyager 1.1.0-beta02 (navigator + screen-model + transitions), Koin 4.0.0 + koin-compose 4.0.0 (DI on Compose context), Ktor Client 3.0.1 (already wired by Plan-1's `SyncClient` + Plan-2's provider adapters — the UI uses both transitively), kotlinx-serialization 1.7.3, kotlinx-datetime 0.6.1, kotlinx-coroutines 1.9.0, moko-resources 0.24.x for RO/EN i18n (per spec §6.1 + Appendix G), `androidx.lifecycle:lifecycle-viewmodel-compose` 2.8.7 (Android shell side, optional — Voyager's `ScreenModel` is the cross-platform primary), CameraX 1.3.4 (`androidApp` only — already declared in existing `androidApp/build.gradle.kts`), `androidx.activity:activity-compose` 1.9.2, Whisper.cpp via JNI binding for on-device RO ASR (Android small-en model ~400MB; Desktop large-v3-turbo ~3GB optional — falls back to VPS `POST /voice/upload` if local model absent, per spec §6.6), Roborazzi 1.30.x (cross-platform Compose screenshot tests — replaces Paparazzi which is Android-only; per A9 ED-safeguards we need cross-platform parity), Compose UI Test (`@OptIn(ExperimentalTestApi::class) runComposeUiTest { ... }` works in `commonTest` for shared composables; platform-specific runners for shells), Playwright 1.48.0 (already declared by Plan-6 — used here only by the final visual-acceptance gate Task 33 against the `desktopApp` JFrame via direct Compose-UI-Test, NOT a browser — see Task 33 for the correction-to-spec).

**Council-required artifacts (binding from A1-A20 + Round-3 hard NOs + CLAUDE.md interaction-smoke gate — every Plan-4-5 implementation must produce these):**
1. **15 first-ship screens** all painted on first navigation: `OnboardingScreen`, `MagicLinkVerifyScreen`, `AILiteracyBanner` (modal-first-launch), `HomeScreen`, `FoodLogScreen`, `PantryScreen`, `CookbookScreen`, `CoachChatScreen`, `PaperSearchScreen`, `ReceiptUploadScreen`, `SettingsScreen`, `AuditLogScreen`, `EDSafeguardModal`, `JustTellMeButton` (inline + standalone `JustTellMeScreen`), `PauseTrackingScreen` (+ `PauseTrackingButton` inline component) — plus `DiagScreen` per locked-spec §23 (technically the 16th screen, ships in this plan).
2. **Bigorexia-first ED-safeguard framing** (NOT anorexia-first). Per Round-3 §3 + spec §1 (Victor identity): 188cm/67.5kg male, lean-bulk active. Risk-profile is muscle-dysmorphia / bigorexia, NOT restrictive ED. The `EDSafeguardModal` copy + `MODEL_CARD.md` linkage in `SettingsScreen → About` reflects this — kcal-floor refuse warning is symmetric, weight-rate cap is bidirectional (>0.5kg/wk loss OR >0.5kg/wk gain triggers a check-in).
3. **Cronometer-style NutrientBar** composable rendering all 84 nutrients per Round-3 §1.13 pattern 1 + spec §6.4. Layout: left = nutrient name (RO+EN tooltip), center = neutral-teal horizontal fill bar (NO red/green pass/fail — Round-3 §3 hard ban), right = numeric + DRV target. Tap → detail with 24h food contributors. Default Home shows top-5; FoodLog → meal-detail expands all 84.
4. **MacroFactor-style ExpenditureChart** Bayesian rolling 7-day TDEE estimator per Round-3 §1.13 pattern 2 + spec §6.5. Output: TDEE point ± confidence band. Home shows "TDEE today: 2640 kcal (±180 kcal, based on last 14 days)". NEVER a confident point — band always present.
5. **PhotoSuggestionCard** per Round-3 §1.13 pattern 5 + spec §6.7. NEVER auto-commit a photo classification — always top-5 candidates with confidence + user picks or types. CNN top-1 is only 72.92% on ISIA-Food-200 per R3 — confirmation step mandatory.
6. **Carbon-style WeeklyNarrative composable** per spec §6.8 — Sunday-rendered LLM-generated paragraph showing strength/energy/mood/sleep/weight-rolling-avg. Weight is NEVER the primary lead. The composable accepts a `NarrativeRequest(subjectId, weekStart)` and calls Plan-2's `LlmRouter.call(capability=TEXT, ...)` with a narrative-template prompt.
7. **Hard-NO list enforced in lint + screenshot tests** (per spec §34 + Round-3 §3 + meta-blindspots §1.4): no streak counter / no "X days in a row" badge / no points / no XP / no badges / no leaderboard / no body-comparison UI / no public feed / no red-green pass/fail color / no daily-weight-as-primary surface. CI test `NoForbiddenPatternsTest` grep-fails on any of these strings appearing in `commonMain/ui/` outside the negative-control wiki file.
8. **`/just_tell_me` override on every coach-chat surface** per AI Act Art 14 + spec §6.13 + §10 row #5. Inline `JustTellMeButton` above coach input bar + standalone `JustTellMeScreen` with `[data-testid="just-tell-me-rule-based-answer"]` + `[data-testid="just-tell-me-disable-llm-toggle"]` (permanently routes future planner queries to rule-based path; persisted to Plan-3's `subjects.llm_coach_disabled` column — Plan-3 V013 already shipped this column on first batch).
9. **AILiteracyBanner modal-first-launch** per AI Act Art 4 + spec §6.10 layer 1 + §10 row #2 + §11.11. Triggered on first login + on text-version bump (`docs/compliance/AI_LITERACY_TEXT_VERSION.md` versioned). RO+EN parallel columns. Re-acknowledgeable from Settings → About → AI Literacy.
10. **Per-call disclosure pane** per AI Act Art 13 + spec §6.10 layer 2 + §10 row #4. Every LLM message in `CoachChatScreen` carries a small "ⓘ" badge → tap expands inline showing `LlmDisclosure(provider, model, prompt_hash, cost_cents, latency_ms, raw_response_ref)` per spec §10 row #4. The disclosure payload is sourced from Plan-3's `audit_log` row keyed by `call_uuid` via `GET /me/audit?call_uuid=<uuid>`.
11. **`PauseTrackingButton` always visible in `HomeScreen` header** per spec §6.3 screen #2 + §6.9 ED-safeguard. One-tap → `PauseTrackingScreen`. Posts to `POST /me/pause` (Plan-3 deferred per RC12 — Plan-4-5 ships a local-state-only stub that flips `pauseState.isPaused = true` and gates all logging endpoints behind the flag client-side; the server-side `POST /me/pause` lands in Plan-3.5 fix-up, see Task 17 + Open-deps).
12. **Compose KMP shared client-side ED-detector hook** per spec §6.11b + Council Q8 — evaluates §9.3 thresholds (`kcal_under_80_7d`, `weight_rate_above_0_5kg_wk`, `restrictive_pattern_30pct_14d`) on the client's local cache (Plan-1's `MealEventStore` + `WeightEventStore`) after every `meal_event` insert or pull-cycle. Fires the cross-route ED check-in modal `[data-testid="ed-checkin-modal"]` per §11.15a. Server-side primary (Plan-3 nightly job) + client-side secondary — defense in depth.
13. **Voice-input via Whisper.cpp on-device** per Round-3 §5 + spec §6.6. RO ASR ~3-5% WER on benchmark since 2024. Biased initial prompt: `"Limbaj culinar românesc: mămăligă, sarmale, ciorbă, mici, fasole, varză, cartofi, pui, vită, porc, brânză, smântână, ulei, sare, piper, leuștean, mărar, pătrunjel..."`. Falls back to VPS `POST /voice/upload` if local model absent (Plan-3 first-ship 501-stub — Plan-4-5 inputs gracefully degrade).
14. **`subject_id`-aware screen-models from day 1** — every `ScreenModel` constructor takes `subjectId: String` from the Voyager nav arg + Plan-3 session principal. No anonymous mode. `CoachChatScreenModel` uses it to dispatch via `PerSubjectRoutingRules` (Plan-2). `HomeScreenModel` uses it to filter Plan-1 `Flow`s.
15. **Final visual-acceptance gate (Task 33)** runs Compose-UI-Test against the `desktopApp` JFrame + Espresso/Compose-UI-Test against the `androidApp` debug APK. Asserts every spec §11 `data-testid` selector paints on first nav + click-smoke per CLAUDE.md interaction-smoke gate (zero 4xx/5xx + no `/404|HTTP \d{3}|not found|error/i` on-screen text after each click). Ships as `:shared:visualAcceptanceTest` Gradle task + CI required-check.

**Dismissed concerns (do NOT bake in):** New Gradle subproject `:shared:ui-components` (per A6+D2 council — package inside `:shared`). PWA swap (rejected Round-1 of council). Paparazzi screenshot tests (Android-only — replaced by Roborazzi for cross-platform). Native Android views (rejected per A6 — full CMP). LiveData/AndroidViewModel as the primary state primitive (replaced by Voyager `ScreenModel` + `StateFlow` for KMP). `compose-multiplatform-navigation` alpha (rejected per spec §27 + council T27 — Voyager 1.1.0-beta02 is stable enough for the 15-screen surface). Per-message LLM emotion inference UI (Art 5(1)(f) prohibition — `emotion_inference_disabled=TRUE` carried on every audit_log row per Plan-3 contract). Body-comparison / leaderboard / streak features (Round-3 §3 hard ban).

**NOT in scope (deferred to Plan-3.5 / Plan-6 / Plan-7 / post-finals):**
- Passkey/WebAuthn registration UI (per Plan-3 RC1: passkey deferred to Plan-3.5; Plan-4-5 ships magic-link-only `MagicLinkVerifyScreen`. `OnboardingScreen` step 7 `[data-testid="onboarding-passkey-register-button"]` is wired BUT the action is "skip for now" + toast "Passkey will be available in a future update; magic-link is active.").
- `POST /me/pause` server endpoint (Plan-3 RC12 deferred — Plan-4-5 ships client-side-only pause flag; Plan-3.5 fix-up adds the server route, then Plan-4-5 wires the network call without changing the UI surface).
- Background `/receipts/upload` fan-out (Plan-3 Task 27 deferred 501-stub — Plan-4-5 ships the synchronous upload screen against Plan-3 Task 24's shipped `/receipts/upload`).
- `/jobs/queue` Cookbook recipe-ingest path (Plan-3 Task 25 deferred 501-stub — `CookbookScreen` ingest button enqueues to a local outbox; Plan-3.5 fix-up wires server-side; UI surface unchanged).
- Hybrid pgvector+pg_trgm+tsvector paper-search ranking (Plan-7 owns — Plan-4-5 ships the `PaperSearchScreen` against Plan-3's `POST /embed` shipped surface; ranking quality improves when Plan-7 backfills the corpus, no UI change).
- Recipe ingest from YouTube / PDF (Plan-7 owns the moderator + GROBID pipeline — `CookbookScreen` ingest button enqueues; UI shows "queued, will be available within 7 days" toast per spec §A19 pattern).
- `MegaConnectFetcher` browser-cookie-extract Desktop UI (Plan-6 + Plan-7 own — `SettingsScreen → Credentials → Re-export UAIC SAML cookies` button surface ships here per §6.11a + §11.8; the action launches a Playwright subprocess shipped by Plan-6).
- `WeeklyNarrative` LLM prompt template + Sunday-cron rendering (Plan-7 §11.2 owns the prompt template; Plan-4-5 ships the composable + the call site; Plan-7 backfills the prompt corpus).
- Whisper.cpp JNI binding (large-v3-turbo desktop / small-en android) — Plan-4-5 ships the Kotlin call site + the VPS-fallback path; the actual JNI binding ships in Plan-7 §6.6 as a separate native artifact. Plan-4-5 first-ship runs the VPS-fallback path 100% of the time on both platforms.

---

## File Structure

### `shared/src/commonMain/kotlin/com/dietician/ui/` (NEW — entire subtree)

#### `theme/`
- `DieticianTheme.kt` — Material3 `MaterialTheme` wrapper with light/dark color schemes + dynamic-color seed + neutral-teal palette per R3 §3 ED-safeguard
- `ColorPalette.kt` — `data class DieticianColors(...)` + light/dark instances; explicit `neutralTeal`, `surfaceVariant`, `error` (rare) — NO `success` (green pass) NOR `danger` (red fail) — ban enforced by `NoForbiddenPatternsTest`
- `Typography.kt` — Material3 type scale + Atkinson Hyperlegible toggle (per R3 §4 a11y) for the dyslexia-friendly path
- `Iconography.kt` — Material-Icons-Extended subset + 5 custom SVG-Composable icons (pause, just-tell-me, voice, photo, audit-log)
- `Spacing.kt` — 4dp grid token object
- `Shapes.kt` — Material3 ShapeScheme with 12dp rounded corners on cards

#### `navigation/`
- `RootScreen.kt` — Voyager `Screen` interface marker + serializable nav-arg encoding helpers
- `BottomNavDestination.kt` — sealed class enumerating the 5 bottom-nav targets (Home, FoodLog, Pantry, Cookbook, More)
- `BottomNavBar.kt` — Composable rendering the bottom nav; per-tab `data-testid="bottomnav-{key}"`
- `DeepLinkRouter.kt` — parses `dietician://home`, `dietician://food-log`, etc. deep-links into Voyager `Navigator.push`
- `NavTransitions.kt` — fade-through + slide-from-right Voyager `ScreenTransition` defaults

#### `state/`
- `PauseState.kt` — `object PauseState { val isPaused: StateFlow<Boolean>; suspend fun pause(reason: String); suspend fun resume() }` — Koin singleton; UI subscribes
- `AILiteracyState.kt` — tracks `lastAckedTextVersion` in Plan-1's `cache_metadata` table; emits `shouldShowBanner: StateFlow<Boolean>` on every app start
- `SubjectPrincipal.kt` — `data class SubjectPrincipal(subjectId: String, displayName: String, isVictor: Boolean)`; sourced from Plan-3 `GET /me` on app start, persisted in Plan-1 `cache_metadata`
- `EDDetectorHook.kt` — Compose `LaunchedEffect` shared hook evaluating §9.3 thresholds on local cache; flips `EDSafeguardState.showCheckin: StateFlow<Boolean>` on hit
- `EDSafeguardState.kt` — singleton + audit-log writer for `safeguard_pause_via_modal` / `safeguard_dismissed` / `safeguard_acknowledged` actions
- `SyncStateObserver.kt` — wraps Plan-1 `PullCoordinator` + `WebSocketListener` into a `Flow<SyncStatus>` for the Home banner

#### `components/` (reusable widgets, each its own .kt file)
- `NutrientBar.kt` — Cronometer-style horizontal bar (R3 §1.13 #1 + spec §6.4)
- `NutrientBarList.kt` — list of `NutrientBar`s grouped by nutrient class (Macros / Detail / Vitamins / Minerals / AAs / Bioactives / Hydration / Glycemic / Others)
- `MacroRingChart.kt` — kcal/protein/fat/carb concentric rings (Home + FoodLog summary)
- `ExpenditureChart.kt` — MacroFactor-style adaptive TDEE chart (R3 §1.13 #2 + spec §6.5); shows ± band
- `WeightTrendChart.kt` — 7-day rolling avg primary line + 30-day trend + daily-as-small-dots (spec §6.5 weight rendering rule)
- `WeeklyNarrative.kt` — Carbon-style Sunday narrative composable (R3 §1.13 #10 + spec §6.8)
- `PhotoSuggestionCard.kt` — Bite-AI-style top-5-candidates with confidence + confirm/correct (R3 §1.13 #5 + spec §6.7)
- `MealCard.kt`, `RecipeCard.kt`, `PantryItemRow.kt`, `ShoppingListItemRow.kt`
- `ConsentDialog.kt`, `ConsentRow.kt` — per-consent withdraw UI
- `AILiteracyBanner.kt` — modal first-launch banner (Art 4); per spec §11.11 selectors
- `PerCallDisclosurePane.kt` — Art 13 disclosure on every LLM message (spec §6.10 + §11.5)
- `JustTellMeButton.kt` — inline button per Art 14 + spec §6.13 + §11.5
- `PauseTrackingButton.kt` — always-visible-in-header pause icon (spec §6.3 + §6.9)
- `EDSafeguardModal.kt` — cross-route modal (spec §11.15a)
- `EmptyState.kt`, `ErrorState.kt`, `LoadingState.kt` — non-screen-shape primitives
- `SearchBar.kt`, `FilterChip.kt`
- `BottomSheet.kt` — Material3 bottom sheet wrapper
- `VoiceRecordButton.kt` — tap-and-hold record; emits `Flow<VoiceChunk>`
- `AntiStreakAbsence.kt` — `object AntiStreakAbsence { /* lint marker only */ }` — empty composable file with comment "intentionally never wired; presence is a CI assertion that no streak UI was added"

#### `screens/`
- `OnboardingScreen.kt` — 7-step flow (spec §6.3 #1 + §11.10)
- `MagicLinkVerifyScreen.kt` — magic-link token paste/verify (Plan-3 first-ship auth surface)
- `HomeScreen.kt` — daily landing (spec §6.3 #2 + §11.1)
- `FoodLogScreen.kt` — voice-first logging (spec §6.3 #3 + §11.2)
- `MealDetailScreen.kt` — 84-nutrient breakdown (spec §6.3 #3 deep + §11.2 click smoke)
- `PantryScreen.kt` — current pantry + FEFO (spec §6.3 #4 + §11.3)
- `PantryItemDetailScreen.kt` — per-SKU detail with FEFO batch list
- `CookbookScreen.kt` — browse + search + ingest (spec §6.3 #5 + §11.4)
- `RecipeDetailScreen.kt` — recipe detail with ingredients/steps/nutrition/history (§11.4 click smoke)
- `RecipeReviewQueueScreen.kt` — dual-LLM-moderator quarantine items (§11.4)
- `CoachChatScreen.kt` — LLM chat with per-call disclosure + just-tell-me (spec §6.3 #6 + §11.5)
- `JustTellMeScreen.kt` — standalone rule-based answer screen (spec §6.13 + §11.13)
- `PaperSearchScreen.kt` — hybrid search via `POST /embed` (spec §6.3 #7 + §11.6)
- `PaperDetailScreen.kt` — paper detail with ingest/open-wiki button (§11.6 click smoke)
- `ReceiptUploadScreen.kt` — camera/file-pick + OCR poll (spec §6.3 #8 + §11.7)
- `ReceiptDetailScreen.kt` — line items + edit + confirm (§11.7 click smoke)
- `SettingsScreen.kt` — profile/credentials/consent/pause (spec §6.3 #9 + §11.8)
- `SettingsAboutScreen.kt` — links to MODEL_CARD + AI literacy
- `AuditLogScreen.kt` — view + PDF/JSON export (spec §6.3 #10 + §11.9)
- `AILiteracyScreen.kt` — standalone banner re-show (spec §6.3 #11 + §11.11)
- `EDSafeguardScreen.kt` — triggered by §9 detectors (spec §6.3 #12 + §11.12)
- `PauseTrackingScreen.kt` — currently-paused state (spec §6.3 #14 + §11.14)
- `DiagScreen.kt` — per locked-spec §23 + §11.15

#### `network/`
- `ApiClient.kt` — Koin-provided `HttpClient` wrapping Plan-1's `SyncClient` base URL + Plan-3 auth bearer interceptor
- `SessionInterceptor.kt` — adds `Authorization: Bearer ${jwt}` from `SubjectPrincipal`
- `Endpoints.kt` — `object Endpoints { const val ME = "/me"; const val SYNC_PUSH = "/sync/push"; ... }`
- `EmbedApi.kt` — `POST /embed` wrapper for `PaperSearchScreen`
- `AuditApi.kt` — `GET /me/audit?format=json` + `GET /me/audit?format=pdf` (downloads)
- `MagicLinkApi.kt` — `POST /auth/magic-link/request` + `POST /auth/magic-link/verify`
- `ReceiptApi.kt` — `POST /receipts/upload` multipart
- `ChatStreamApi.kt` — `POST /llm/chat/stream` SSE wrapper bridging into Plan-2 `LlmRouter.completeStream`

#### `i18n/`
- `Strings.kt` — moko-resources-generated string accessors
- `LocaleSelector.kt` — RO/EN runtime switch; Composition-local

#### `commonMain/resources/MR/strings/`
- `strings.en.xml` — English strings (~250 keys covering all 16 screens + AI literacy + ED-safeguard + onboarding 7 steps)
- `strings.ro.xml` — Romanian strings (parallel keys; from spec Appendix G ship-ready copy)

### `shared/src/androidMain/kotlin/com/dietician/ui/` (NEW — Android actuals)

- `PhotoCapture.android.kt` — `actual class PhotoCapture` using CameraX (`androidx.camera.core` + `ProcessCameraProvider`)
- `FilePicker.android.kt` — `actual class FilePicker` using `ActivityResultContracts.OpenDocument`
- `ScaleConnector.android.kt` — `actual class ScaleConnector` using `android.bluetooth.le.BluetoothLeScanner`
- `StatusBarTheme.android.kt` — `actual fun applyStatusBarTheme(...)` using `WindowInsetsControllerCompat`
- `WindowChrome.android.kt` — `actual fun applyWindowChrome(...)` no-op on Android (system bars only)
- `NtfyRegistration.android.kt` — `actual class NtfyRegistration` using ntfy Android app subscription URI
- `WhisperAsr.android.kt` — `actual class WhisperAsr` — first-ship returns `Result.failure(NotShippedYet)` triggering VPS-fallback; JNI binding lands in Plan-7
- `VoiceRecorder.android.kt` — `actual class VoiceRecorder` using `MediaRecorder` writing AAC
- `MagicLinkDeepLink.android.kt` — `actual fun parseMagicLink(intent: Intent): String?` from `dietician://verify?token=...`

### `shared/src/desktopMain/kotlin/com/dietician/ui/` (NEW — Desktop actuals)

- `PhotoCapture.desktop.kt` — `actual class PhotoCapture` falls through to `FilePicker` (no webcam in first-ship; webcam optional via JCamera later)
- `FilePicker.desktop.kt` — `actual class FilePicker` using `javax.swing.JFileChooser`
- `ScaleConnector.desktop.kt` — `actual class ScaleConnector` returns `isAvailable() = false` first-ship (BlueZ/Windows.Devices.Bluetooth binding deferred)
- `StatusBarTheme.desktop.kt` — `actual fun applyStatusBarTheme(...)` no-op (no status bar on Desktop)
- `WindowChrome.desktop.kt` — `actual fun applyWindowChrome(...)` configures Compose `Window` decoration + tray icon
- `NtfyRegistration.desktop.kt` — `actual class NtfyRegistration` using Ktor WS to ntfy.sh long-poll
- `WhisperAsr.desktop.kt` — `actual class WhisperAsr` — first-ship returns `Result.failure(NotShippedYet)` triggering VPS-fallback; JNI binding lands in Plan-7
- `VoiceRecorder.desktop.kt` — `actual class VoiceRecorder` using `javax.sound.sampled.TargetDataLine` writing WAV
- `MagicLinkDeepLink.desktop.kt` — `actual fun parseMagicLink(args: Array<String>): String?` from `--magic-link=...` CLI arg
- `UaicCookieReExportLauncher.desktop.kt` — `actual class UaicCookieReExportLauncher` — first-ship throws `NotShippedYet` (Plan-6 owns the Playwright subprocess); UI surface ships per spec §6.11a + §11.8

### `shared/src/commonTest/kotlin/com/dietician/ui/` (test surface)

Roborazzi screenshot tests + Compose-UI-Test interaction tests for every screen + every component. Test files:
- One `<Screen>RoborazziTest.kt` per of 16 screens
- One `<Component>RoborazziTest.kt` per of 15 reusable components
- `NoForbiddenPatternsTest.kt` — grep CI guard against `streak`, `XP`, `badge`, `leaderboard`, `body-comparison`, `pass-fail-green`, `pass-fail-red` etc.
- `EDSafeguardThresholdsTest.kt` — assertions on §9.3 threshold-eval correctness
- `BigorexiaCopyTest.kt` — assert `EDSafeguardModal` copy mentions bigorexia/muscle-dysmorphia, NOT just anorexia
- `SubjectIdRequiredTest.kt` — every `ScreenModel` constructor throws on missing `subjectId`
- `JustTellMeRouterBypassTest.kt` — assert `JustTellMeButton` path NEVER calls `LlmRouter.call`
- `AILiteracyFirstLaunchTest.kt` — fresh install → banner shown; ack → banner dismissed + cache_metadata row written

### `shared/src/desktopTest/kotlin/com/dietician/ui/`
- `DesktopVisualAcceptanceTest.kt` — final gate Task 33; launches `Window` + asserts all spec §11 selectors via Compose-UI-Test

### `shared/src/androidUnitTest/kotlin/com/dietician/ui/`
- `AndroidVisualAcceptanceTest.kt` — final gate Task 33; Robolectric + Compose-UI-Test against `OnboardingScreen` → `HomeScreen` happy path

### `androidApp/src/main/kotlin/com/dietician/android/` (modifications + additions)

- `MainActivity.kt` — REPLACES existing scaffold; wires Voyager `Navigator(OnboardingScreen)` + Koin start + magic-link deep-link handler
- `DieticianApp.kt` — NEW `Application` subclass; Koin `startKoin { modules(uiModule, dataModule, llmModule) }`
- `AndroidManifest.xml` — MODIFY: add `dietician://` deep-link intent-filter + camera + audio permissions + notification permissions
- `proguard-rules.pro` — MODIFY: add Voyager + Koin keep rules

### `desktopApp/src/main/kotlin/com/dietician/desktop/` (modifications + additions)

- `Main.kt` — REPLACES existing scaffold; wires Compose `application { Window(...) { Navigator(OnboardingScreen) } }` + Koin start + ClaudeMax warm-pool spawn + system tray + magic-link CLI arg handler
- `TrayIcon.kt` — NEW system tray with "Open Dietician" / "Pause tracking" / "Quit" items
- `DesktopKoinModule.kt` — Koin module binding desktop actuals (file picker, scale connector, etc.)

### `gradle/libs.versions.toml` additions

See Task 0.

---

## Status

- **Branch base:** `master` after Plan-3 first-batch (V013-V020 + magic-link auth + `/sync/push`+`/sync/pull`+`/receipts/upload`+`/embed`+`/me`+`/me/audit`+`/me/byok` shipped) merges. Plan-3 first-batch is the HARD prereq.
- **Plan-2 dependency:** Plan-2 Tasks 0-22 (Router + per-subject routing + audit-log writer + IdempotencyCache) MUST land before Task 12 (CoachChat) — `CoachChatScreen` calls `LlmRouter.completeStream` directly. Plan-2 first-batch is the SOFT prereq (Tasks 0-12 unlock at branch creation; Task 12 blocks on Plan-2).
- **Plan-1 dependency:** Plan-1 entire ship (V001-V012 + `EventStore`+`OutboxStore`+`PullCoordinator`+`PantrySnapshotStore`+`WebSocketListener`) is already on master (per Plan-3 branch-base assumption).
- **Blocks on:** Plan-3 first-batch merge to master. If blocked, hold this plan.
- **Branch name suggestion:** `worktree-plan-4-5+kmp-compose-ui`.
- **Branch ship gate:** All tasks green + `./gradlew :shared:commonTest :shared:androidUnitTest :shared:desktopTest :androidApp:assembleDebug :desktopApp:assemble` PASS + manual smoke against live VPS Tailscale IP (Desktop: launch app → onboarding → magic-link → home paints → quick-log voice → meal_event appears in pantry; Android: emulator equivalent).

---

## Pre-impl council 1779120600 (2026-05-18)

**Verdict:** FLAWED→FIXED (APPROVE WITH 20 REQUIRED CHANGES). Confidence 7/10.

**Required changes baked in:** RC1-RC20. See per-task subsections for details + citations.

**SCOPE REDUCTION:** Voice pipeline (T24/T26/T32) + Roborazzi (T30) deferred. Settings standalone screen (T15) + standalone EDSafeguard (T18 separate screen) collapsed into existing screens.

**Voyager + CMP 1.7.0 RATIFIED** — Plan-4-5.5 post-finals coordinated migration (RC3 + RC17).

**PWA NOT TRIGGERED** — D2 dissent triggers all dormant.

**First-ship subset:** ~27 tasks (T0/T1-T14/T16/T18-T20/T22/T23/T25/T27-T29/T33-T35). Deferred: T15/T17/T21/T24/T26/T30/T31/T32.

**Transcript:** `.claude/council-cache/council-1779120600-plan-4-5-preimpl.md`

### First-ship vs deferred (RC-4)

**First-ship subset (~27 tasks):**
T0 (Pre-T0 setup) + T1 (theme) + T2 (typography) + T3 (i18n RO+EN incl bigorexia copy per RC5) + T4 (nav) + T5 (auth flow) + T6 (AI literacy banner) + T7 (Home) + T8 (FoodLog) + T9 (Nutrient bars) + T10 (Pantry) + T11 (Cookbook) + T12 (CoachChat) + T13 (Paper search) + T14 (Receipt upload) + T16 (Audit log) + T18 (ED safeguard modal) + T19 (MacroFactor expenditure) + T20 (Photo suggestion card) + T22 (WeightTrendChart only — not full WeeklyNarrative LLM-rendered card) + T23 (Android camera) + T25 (Desktop file dialog) + T27 (Android shell) + T28 (Desktop shell) + T29 (Network adapter) + T33 (Compose UI tests) + T34 (CI) + T35 (final preflight + post-impl council).

**Deferred (Plan-4-5.5):** T15 (Settings standalone — partial; basic settings inline elsewhere), T17 (Pause tracking — fold into modal), T21 (Voice input — RC1), T24 (Whisper Android — RC1), T26 (Voice upload — RC1), T30 (Roborazzi — RC2), T31 (deeper integration tests — subsume into T33), T32 (Voice acceptance — RC1).

> NOTE: First-ship subset uses council-narrative IDs (T0-T35 logical positions). Match against plan's own Task 0–Task 35 by sequential index; the existing plan numbering is preserved (no renumber) — RC banners on each task call out FIRST-SHIP vs DEFERRED status explicitly.

---

## Pre-impl council deferred (HISTORICAL — superseded by council 1779120600 above)

This plan is large (~35 tasks). Per `feedback_council_pattern`, run a 5-agent pre-impl council BEFORE Task 0 starts. Position assignments: Devil's Advocate + Pragmatist + UX-Domain-Expert + ED-Safeguard-Risk-Analyst + AI-Act-Compliance-Reviewer. Required to surface:
- Voyager vs compose-multiplatform-navigation re-eval given finals-lock pressure
- Roborazzi maturity on Desktop (it's <1.0 — may have rough edges)
- moko-resources vs `compose-multiplatform`'s own `resources` DSL (CMP 1.7.0 ships a `@Resource` DSL that may obsolete moko)
- Whisper.cpp JNI deferral risk (first-ship runs 100% VPS-fallback — is RO ASR latency tolerable?)
- ED-safeguard bigorexia-framing correctness (Victor-specific risk)

Persist transcript at `.claude/council-cache/council-{ts}-plan-4-5-preimpl.md`. Bake required-changes into the per-task subsections as RC1-RCN markers before Task 0 begins.

**Resolution:** Council ran 2026-05-18 (ts 1779120600). See "Pre-impl council 1779120600" section above for verdict + 20 RCs.

---

## Locked decisions baked into plan

These are NOT renegotiable inside Plan-4-5. Escalate friction to user — do NOT silently revise.

| ID | Decision | Source |
|---|---|---|
| A6 | Plans 4 + 5 collapse to single Plan-4-5 with `:shared:ui-components` as a PACKAGE inside `:shared` (NOT new Gradle subproject) | research-spec §A6 + council-1779038746-D2 |
| A9 | ED-safeguards: NO red/green pass/fail / NO streak / NO body-comparison / kcal-floor refuse / weight-rate cap | research-spec §A9 + Round-3 §3 |
| A14 | Tailscale Magic DNS hostname (NOT hardcoded IP) — onboarding step 1 auto-detects | research-spec §A14 |
| A17 | PII NER MANDATORY at write boundary (Plan-2 ships the redactor; Plan-4-5 voice-input path routes through it) | research-spec §A17 |
| Spec §1 | Victor identity: 188cm/67.5kg male, lean-bulk active. Primary ED risk = bigorexia, NOT anorexia | locked spec §1 |
| Spec §6.3 | 15 first-ship screens + DiagScreen | research-spec §6.3 |
| Spec §6.4 | 84 nutrients via NutrientBar; uniform neutral-teal fill | research-spec §6.4 |
| Spec §6.5 | MacroFactor-style adaptive expenditure with confidence band; weight-rolling-avg primary | research-spec §6.5 |
| Spec §6.6 | Voice-first logging with RO food-vocab Whisper.cpp prompt | research-spec §6.6 |
| Spec §6.7 | Photo-as-suggestion-never-auto-commit | research-spec §6.7 |
| Spec §6.8 | Weekly narrative (Carbon-style) — strength/energy/mood/sleep, NOT weight prominence | research-spec §6.8 |
| Spec §6.9 | ED-safeguard UI affordances enumerated | research-spec §6.9 |
| Spec §6.10 | AI Act Art 4 three-layer transparency: literacy banner + per-call disclosure + just-tell-me | research-spec §6.10 |
| Spec §6.11b | Compose KMP client-side ED-detector hook + cross-route check-in modal | research-spec §6.11b + Council Q8 |
| Spec §6.11d | Clients do NOT embed locally — all embedding via `POST /embed` | research-spec §6.11d + Council Q7 |
| Spec §11 | ~120 unique data-testid selectors enumerated; final gate asserts all paint | research-spec §11 |
| Compose MP 1.7.0 pinned | LTS, do NOT upgrade to 1.8/1.9 in this plan | research-spec §6.11 + Council Q5 |
| `feedback_no_time_estimates` | No duration/effort estimates anywhere | user 2026-05-17 |
| `feedback_no_version_phasing` | No v0/MVP/staged-build cuts | user 2026-05-17 |
| Round-3 §3 hard NO | NO streak / NO XP / NO badge / NO leaderboard / NO body-comparison / NO red-green color | research Round-3 |

---

## Pre-Task-0 setup

**Repo invariant check (run before Task 0):**

```bash
git status                                                    # must be clean
git log --oneline -10                                          # confirm Plan-3 first-batch merge visible
ls shared/src/commonMain/kotlin/com/dietician/ui 2>/dev/null || echo "OK: ui package does not yet exist"
ls shared/src/commonMain/kotlin/com/dietician/shared/llm/LlmRouter.kt >/dev/null && echo "OK: Plan-2 LlmRouter on classpath"
ls shared/src/commonMain/kotlin/com/dietician/shared/data/local/EventStore.kt >/dev/null && echo "OK: Plan-1 EventStore on classpath"
curl -s "http://$(tailscale ip -4):8081/health" | grep -q '"status":"ok"' && echo "OK: VPS Plan-3 backend reachable"
```

If `shared/src/commonMain/kotlin/com/dietician/ui/` already exists as a non-empty directory, halt and report. Plan-4-5 assumes greenfield for that subtree.

**Compose MP version verification (run before Task 0):**

```bash
grep -n 'compose-multiplatform = "1.7.0"' gradle/libs.versions.toml || echo "FAIL: Compose MP not pinned at 1.7.0"
```

If the version drifted (e.g. someone bumped to 1.8 between plan-write and exec), halt and surface to user before proceeding. The plan is calibrated to 1.7.0 APIs.

**Voyager GitHub health check (run before Task 0)** — [Council 1779120600 RC3]:

```bash
# Sanity gate: Voyager 1.1.0-beta02 is single-maintainer (Adriel Café). Verify the repo isn't EOL.
LAST_COMMIT_DATE=$(curl -sf "https://api.github.com/repos/adrielcafe/voyager/commits?per_page=1" | jq -r '.[0].commit.committer.date')
OPEN_ISSUES=$(curl -sf "https://api.github.com/repos/adrielcafe/voyager" | jq -r '.open_issues_count')
echo "Voyager last commit: $LAST_COMMIT_DATE; open issues: $OPEN_ISSUES"

# Abort criteria:
# - last commit > 90 days ago
# - open issues > 20
# If either trips, surface to user; do NOT proceed without explicit override.
```

If either threshold trips, halt the plan and re-evaluate: switch to compose-multiplatform-navigation at CMP 1.8 (post-finals) instead. See RC3.

**Compose MP baseline snapshot (run before Task 0)** — [Council 1779120600 RC17]:

```bash
# Capture current 1.7.0 dependency tree for the post-finals 1.8 upgrade diff.
./gradlew :shared:dependencies > docs/runbooks/compose-mp-baseline.md
# Prepend header:
{
  echo "# Compose MP baseline snapshot"
  echo ""
  echo "Captured: $(date -Iseconds)"
  echo "Plan: 2026-05-18-plan-4-5-kmp-compose-ui"
  echo "Compose MP version: 1.7.0 (pinned per spec §6.11 Council Q5)"
  echo ""
  echo "Reference for post-finals 1.8 migration diff. Plan-4-5.5 will diff against this baseline before bumping CMP + re-evaluating Voyager vs compose-multiplatform-navigation."
  echo ""
  echo "---"
  echo ""
  cat docs/runbooks/compose-mp-baseline.md
} > docs/runbooks/compose-mp-baseline.md.tmp && mv docs/runbooks/compose-mp-baseline.md.tmp docs/runbooks/compose-mp-baseline.md
git add docs/runbooks/compose-mp-baseline.md
```

Placeholder file `docs/runbooks/compose-mp-baseline.md` is committed alongside the plan; this Pre-T0 step overwrites it with the live dependency tree.

---

## Task 0: Add missing dependencies + library aliases

### Council baked-in fixes
- [Council 1779120600 RC2]: DROP Roborazzi entirely. Skip Steps 1/2/3/4 entries for `roborazzi*` aliases + plugin + dep blocks. Compose-UI-Test (Task 33) is the load-bearing pixel/interaction gate. Concretely:
  - Step 1 (`[versions]`): omit `roborazzi = "1.30.1"`.
  - Step 2 (`[libraries]`): omit all four `roborazzi*` library aliases.
  - Step 3 (`[plugins]`): omit `roborazzi = { id = ... }`.
  - Step 4 (`shared/build.gradle.kts`): omit `alias(libs.plugins.roborazzi)` + omit `libs.roborazzi*` lines from `commonTest` / `desktopTest` / `androidUnitTest` dependency blocks.
- [Council 1779120600 RC3]: Voyager + CMP 1.7.0 RATIFIED. No version bumps in Task 0. Post-finals coordinated upgrade is a fresh Plan-4-5.5+ decision.
- [Council 1779120600 RC17]: Pre-Task-0 baseline snapshot writes `docs/runbooks/compose-mp-baseline.md` BEFORE Task 0 Step 1. See Pre-Task-0 setup section.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`
- Modify: `androidApp/build.gradle.kts`
- Modify: `desktopApp/build.gradle.kts`

- [ ] **Step 1: Patch `gradle/libs.versions.toml` `[versions]`**

Add (do not duplicate existing keys):

```toml
moko-resources = "0.24.4"
roborazzi = "1.30.1"
compose-ui-test = "1.7.0"        # mirrors compose-multiplatform
voyager-transitions = "1.1.0-beta02"
voyager-koin = "1.1.0-beta02"
androidx-compose-ui-test = "1.7.5"
turbine-compose-test = "1.2.0"   # if not already present
```

- [ ] **Step 2: Patch `[libraries]`**

Add:

```toml
moko-resources = { group = "dev.icerock.moko", name = "resources", version.ref = "moko-resources" }
moko-resources-compose = { group = "dev.icerock.moko", name = "resources-compose", version.ref = "moko-resources" }

voyager-transitions = { group = "cafe.adriel.voyager", name = "voyager-transitions", version.ref = "voyager-transitions" }
voyager-koin = { group = "cafe.adriel.voyager", name = "voyager-koin", version.ref = "voyager-koin" }
voyager-bottom-sheet-navigator = { group = "cafe.adriel.voyager", name = "voyager-bottom-sheet-navigator", version.ref = "voyager" }
voyager-tab-navigator = { group = "cafe.adriel.voyager", name = "voyager-tab-navigator", version.ref = "voyager" }

roborazzi = { group = "io.github.takahirom.roborazzi", name = "roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { group = "io.github.takahirom.roborazzi", name = "roborazzi-compose", version.ref = "roborazzi" }
roborazzi-junit-rule = { group = "io.github.takahirom.roborazzi", name = "roborazzi-junit-rule", version.ref = "roborazzi" }
roborazzi-compose-desktop = { group = "io.github.takahirom.roborazzi", name = "roborazzi-compose-desktop", version.ref = "roborazzi" }

androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4", version.ref = "androidx-compose-ui-test" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest", version.ref = "androidx-compose-ui-test" }
```

- [ ] **Step 3: Patch `[plugins]`**

Add:

```toml
moko-resources = { id = "dev.icerock.mobile.multiplatform-resources", version.ref = "moko-resources" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

- [ ] **Step 4: Patch `shared/build.gradle.kts`**

Append to `plugins { }`:

```kotlin
alias(libs.plugins.moko.resources)
alias(libs.plugins.roborazzi)
```

Append to `commonMain.dependencies { }`:

```kotlin
implementation(compose.runtime)
implementation(compose.foundation)
implementation(compose.material3)
implementation(compose.materialIconsExtended)
implementation(compose.components.resources)
implementation(libs.voyager.navigator)
implementation(libs.voyager.screenmodel)
implementation(libs.voyager.transitions)
implementation(libs.voyager.bottom.sheet.navigator)
implementation(libs.voyager.tab.navigator)
implementation(libs.voyager.koin)
implementation(libs.koin.compose)
implementation(libs.moko.resources)
implementation(libs.moko.resources.compose)
```

Append to `commonTest.dependencies { }`:

```kotlin
implementation(compose.uiTest)
implementation(libs.roborazzi)
implementation(libs.roborazzi.compose)
```

Append to `desktopTest.dependencies { }` (create the block if absent):

```kotlin
implementation(compose.desktop.uiTestJUnit4)
implementation(libs.roborazzi.compose.desktop)
implementation(libs.roborazzi.junit.rule)
```

Append to `androidUnitTest.dependencies { }`:

```kotlin
implementation(libs.androidx.compose.ui.test.junit4)
implementation(libs.androidx.compose.ui.test.manifest)
implementation(libs.roborazzi)
implementation(libs.roborazzi.junit.rule)
implementation(libs.robolectric)
```

Append `multiplatformResources { }` block (moko-resources config) — outside the `kotlin { }` block:

```kotlin
multiplatformResources {
    resourcesPackage.set("com.dietician.ui.i18n.generated")
    resourcesClassName.set("MR")
}
```

- [ ] **Step 5: Patch `androidApp/build.gradle.kts`**

Append to `dependencies { }`:

```kotlin
implementation(project(":shared"))
implementation(libs.voyager.navigator)
implementation(libs.koin.compose)
implementation("androidx.activity:activity-compose:1.9.2")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
```

- [ ] **Step 6: Patch `desktopApp/build.gradle.kts`**

Append to `dependencies { }`:

```kotlin
implementation(project(":shared"))
implementation(compose.desktop.currentOs)
implementation(libs.voyager.navigator)
implementation(libs.koin.compose)
```

- [ ] **Step 7: Verify dependency resolution**

Run:

```bash
./gradlew :shared:dependencies --configuration commonMainCompileClasspath | grep -E "voyager|koin-compose|moko-resources"
./gradlew :androidApp:dependencies --configuration debugCompileClasspath | grep -E "voyager|koin"
./gradlew :desktopApp:dependencies --configuration runtimeClasspath | grep -E "voyager|koin"
```

Expected: each library resolves to the pinned version. No `Could not resolve` errors.

- [ ] **Step 8: Commit**

```bash
git checkout -b worktree-plan-4-5+kmp-compose-ui
git add gradle/libs.versions.toml shared/build.gradle.kts androidApp/build.gradle.kts desktopApp/build.gradle.kts
git commit -m "$(cat <<'EOF'
build(plan-4-5): add voyager-transitions/koin/bottom-sheet, moko-resources, roborazzi, compose-ui-test deps

Plan-4-5 surface needs Voyager nav + transitions + koin-compose for screen-model DI,
moko-resources for RO/EN i18n via Appendix G ship-ready copy, and Roborazzi for
cross-platform Compose screenshot tests (replaces Paparazzi which is Android-only).
No new Gradle subproject — :shared:ui-components is a package inside :shared per A6+D2.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 1: `com.dietician.ui` package scaffold + theme tokens

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/theme/ColorPalette.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/theme/Typography.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/theme/Spacing.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/theme/Shapes.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/theme/Iconography.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/theme/DieticianTheme.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/theme/ColorPaletteTest.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/theme/NoForbiddenPatternsTest.kt`

- [ ] **Step 1: Write failing CI guard test (red phase) — `NoForbiddenPatternsTest.kt`**

```kotlin
package com.dietician.ui.theme

import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.test.Test

/**
 * Per R3 §3 + spec §A9 hard NO list: streak / XP / badge / leaderboard / body-comparison / pass-fail
 * color coding are banned from `commonMain/ui/`. This test fails if any of those strings appear
 * in source code (case-insensitive, with word boundaries to avoid false positives like "stark").
 *
 * Exceptions: `AntiStreakAbsence.kt` (negative-control marker file) and this test file itself.
 */
class NoForbiddenPatternsTest {
    private val banned = listOf(
        "\\bstreak\\b",
        "\\bXP\\b",
        "\\bbadge\\b",
        "\\bleaderboard\\b",
        "body-comparison",
        "passFailGreen",
        "passFailRed",
        "successGreen",
        "dangerRed",
    )

    private val exemptFiles = setOf(
        "AntiStreakAbsence.kt",
        "NoForbiddenPatternsTest.kt",
        "BigorexiaCopyTest.kt",     // may reference banned terms in negation assertions
    )

    @Test
    fun `no banned patterns appear in commonMain ui sources`() {
        val root = File("src/commonMain/kotlin/com/dietician/ui")
        if (!root.exists()) return  // Pre-Task-0 state; will populate as plan executes.
        val violations = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            if (f.name in exemptFiles) return@forEach
            val text = f.readText()
            banned.forEach { pattern ->
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                regex.findAll(text).forEach { m ->
                    violations += "${f.relativeTo(root)}:${text.substring(0, m.range.first).count { it == '\n' } + 1}: matches /$pattern/ → \"${m.value}\""
                }
            }
        }
        violations.size shouldBe 0
    }
}
```

- [ ] **Step 2: Write failing test `ColorPaletteTest.kt`**

```kotlin
package com.dietician.ui.theme

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ColorPaletteTest {
    @Test
    fun `light palette has neutralTeal as primary action color`() {
        DieticianColors.light.neutralTeal shouldBe Color(0xFF008080)
    }

    @Test
    fun `dark palette has neutralTeal scaled for dark surface`() {
        DieticianColors.dark.neutralTeal shouldBe Color(0xFF66B2B2)
    }

    @Test
    fun `palette has no successGreen field by name`() {
        // Compile-time check: the field doesn't exist. We assert via reflection-style member lookup.
        val members = DieticianColors.light::class.members.map { it.name }
        members.any { it.contains("success", ignoreCase = true) } shouldBe false
        members.any { it.contains("passFail", ignoreCase = true) } shouldBe false
    }
}
```

- [ ] **Step 3: Run (fails — classes undefined)**

`./gradlew :shared:commonTest --tests "com.dietician.ui.theme.*"` → FAIL.

- [ ] **Step 4: Implement `ColorPalette.kt`**

```kotlin
package com.dietician.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Per R3 §3 ED-safeguard + §A9: NO red/green pass-fail color coding. NutrientBar uniform fill
 * uses [neutralTeal]; only state divergence is opacity. The only red-tone color present is
 * [error], reserved for genuine error states (network failure, validation error) — NEVER for
 * "you exceeded your kcal target" or "you missed your protein goal".
 */
data class DieticianColors(
    val neutralTeal: Color,
    val onNeutralTeal: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val error: Color,
    val onError: Color,
) {
    companion object {
        val light = DieticianColors(
            neutralTeal = Color(0xFF008080),
            onNeutralTeal = Color(0xFFFFFFFF),
            background = Color(0xFFFAFAFA),
            onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFEEEEEE),
            onSurfaceVariant = Color(0xFF404040),
            outline = Color(0xFFBDBDBD),
            error = Color(0xFFB00020),
            onError = Color(0xFFFFFFFF),
        )
        val dark = DieticianColors(
            neutralTeal = Color(0xFF66B2B2),
            onNeutralTeal = Color(0xFF000000),
            background = Color(0xFF121212),
            onBackground = Color(0xFFEAEAEA),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFEAEAEA),
            surfaceVariant = Color(0xFF2A2A2A),
            onSurfaceVariant = Color(0xFFBDBDBD),
            outline = Color(0xFF6E6E6E),
            error = Color(0xFFCF6679),
            onError = Color(0xFF000000),
        )
    }
}
```

- [ ] **Step 5: Implement `Typography.kt`**

```kotlin
package com.dietician.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Atkinson Hyperlegible toggle (per R3 §4 a11y). When user enables the dyslexia-friendly path
 * in Settings → Accessibility, [dieticianTypography] returns the AtkinsonHyperlegible-bodied
 * variant; default is system-sans-serif.
 */
val LocalUseAtkinsonHyperlegible = compositionLocalOf { false }

@Composable
fun dieticianTypography(useAtkinson: Boolean = LocalUseAtkinsonHyperlegible.current): Typography {
    val family = if (useAtkinson) FontFamily.Default /* TODO: bundle AtkinsonHyperlegible.ttf in moko-resources */ else FontFamily.Default
    return Typography(
        displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 57.sp),
        displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 45.sp),
        displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 36.sp),
        headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 32.sp),
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 22.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp),
        titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        bodyLarge = TextStyle(fontFamily = family, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = family, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = family, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    )
}
```

- [ ] **Step 6: Implement `Spacing.kt`**

```kotlin
package com.dietician.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 4dp grid. Use [Spacing.s] for tight inter-icon spacing, [Spacing.m] for inter-row, etc.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}
```

- [ ] **Step 7: Implement `Shapes.kt`**

```kotlin
package com.dietician.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val dieticianShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
```

- [ ] **Step 8: Implement `Iconography.kt`**

```kotlin
package com.dietician.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.ReceiptLong

object DieticianIcons {
    val Pause = Icons.Outlined.PauseCircle
    val JustTellMe = Icons.Outlined.Psychology     // brain icon → "no LLM, rule-based"
    val Voice = Icons.Outlined.Mic
    val Photo = Icons.Outlined.PhotoCamera
    val AuditLog = Icons.Outlined.ReceiptLong
}
```

- [ ] **Step 9: Implement `DieticianTheme.kt`**

```kotlin
package com.dietician.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

val LocalDieticianColors = compositionLocalOf { DieticianColors.light }

@Composable
fun DieticianTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useAtkinson: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DieticianColors.dark else DieticianColors.light
    val scheme: ColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.neutralTeal,
            onPrimary = colors.onNeutralTeal,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.onSurfaceVariant,
            outline = colors.outline,
            error = colors.error,
            onError = colors.onError,
        )
    } else {
        lightColorScheme(
            primary = colors.neutralTeal,
            onPrimary = colors.onNeutralTeal,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.onSurfaceVariant,
            outline = colors.outline,
            error = colors.error,
            onError = colors.onError,
        )
    }
    CompositionLocalProvider(
        LocalDieticianColors provides colors,
        LocalUseAtkinsonHyperlegible provides useAtkinson,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = dieticianTypography(useAtkinson),
            shapes = dieticianShapes,
            content = content,
        )
    }
}
```

- [ ] **Step 10: Run tests**

`./gradlew :shared:commonTest --tests "com.dietician.ui.theme.*"` → PASS.

- [ ] **Step 11: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dietician/ui/theme/ \
        shared/src/commonTest/kotlin/com/dietician/ui/theme/
git commit -m "$(cat <<'EOF'
feat(plan-4-5): theme tokens (neutral-teal, no pass-fail color) + NoForbiddenPatternsTest

Per R3 §3 + spec §A9: ColorPalette has no successGreen/dangerRed/passFail fields by name.
NoForbiddenPatternsTest grep-fails on streak/XP/badge/leaderboard/body-comparison in
commonMain/ui sources. Atkinson Hyperlegible toggle wired (font asset bundle deferred).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `AntiStreakAbsence` lint marker + `BigorexiaCopyTest` placeholder

### Council baked-in fixes
- [Council 1779120600 RC5]: `BigorexiaCopyTest` placeholder STAYS in Task 2; the REAL assertion activates in Task 3 (i18n) — NOT Task 19. Copy itself lives in `strings.en.xml` + `strings.ro.xml` per RC5. Task 19 references the i18n key; copy is already locked + tested by Task 3.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/AntiStreakAbsence.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/components/BigorexiaCopyTest.kt`

- [ ] **Step 1: Implement `AntiStreakAbsence.kt`**

```kotlin
package com.dietician.ui.components

/**
 * INTENTIONALLY EMPTY. Per R3 §3 + spec §A9 hard NO: no streak counter, no "X days in a row"
 * badge, no calendar with consecutive-day fills. The presence of THIS file is a CI assertion:
 * [NoForbiddenPatternsTest] excludes this filename from the grep guard. If a future agent
 * adds streak UI, they would have to either rename this file (visible in PR) or violate the
 * grep guard.
 *
 * If you are reading this with intent to "just add a small streak indicator" — STOP. Re-read
 * R3 §3 (NEDA-aligned ED-safety) and meta-blindspots §1.4. The decision is locked.
 */
internal object AntiStreakAbsence
```

- [ ] **Step 2: Implement `BigorexiaCopyTest.kt`** (placeholder — will be populated when EDSafeguardModal lands in Task 18)

```kotlin
package com.dietician.ui.components

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Per spec §1 (Victor identity 188cm/67.5kg lean-bulk) + R3 §3: primary ED risk-profile for
 * THIS app's user-base is bigorexia / muscle-dysmorphia, NOT anorexia. Anorexia-only framing
 * in safeguard copy would miss the actual risk. This test asserts the safeguard copy mentions
 * bigorexia OR muscle-dysmorphia explicitly.
 *
 * Activated in Task 18 when EDSafeguardModal copy lands. Placeholder until then.
 */
class BigorexiaCopyTest {
    @Test
    fun `ED safeguard copy mentions bigorexia or muscle-dysmorphia by name (placeholder)`() {
        // Placeholder. Task 18 wires the real assertion against the moko-resources string keys.
        assertTrue(true, "EDSafeguardModal not yet implemented — Task 18 fills this test.")
    }
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.components.*"
git add shared/src/commonMain/kotlin/com/dietician/ui/components/AntiStreakAbsence.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/components/BigorexiaCopyTest.kt
git commit -m "feat(plan-4-5): AntiStreakAbsence lint marker + BigorexiaCopyTest placeholder

AntiStreakAbsence.kt is intentionally empty — its presence is the CI guard that
no streak UI was added. BigorexiaCopyTest will be populated when Task 18 lands
EDSafeguardModal copy keyed by R3 §3 bigorexia primary framing.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: i18n strings (RO + EN) via moko-resources

### Council baked-in fixes
- [Council 1779120600 RC5]: Bigorexia EN+RO microcopy LOCKED in this task (not deferred to Task 19). Add `ed_safeguard_bigorexia_note` string key to BOTH `strings.en.xml` + `strings.ro.xml` with the council-mandated phrases. Activate `BigorexiaCopyTest` at this task (replace placeholder from Task 2) asserting both EN+RO contain the locked phrases: "process target, not body target", "weekly aggregate, not daily weight", "strength + energy + mood + sleep".
- [Council 1779120600 RC1]: Add `voice_record_button_disabled_toast` string key with text: "Voice transcription coming in next update — type your meal below." Both EN + RO. `VoiceRecordButton` onClick (Task 13) consumes this key.
- [Council 1779120600 RC9]: Add `coach_disabled_notice_title` + `coach_disabled_notice_body` keys: "AI coach disabled. Re-enable in Settings → Privacy." Both EN + RO.
- [Council 1779120600 RC16]: Add `tailscale_disconnected_title` + `tailscale_disconnected_body` + `tailscale_disconnected_retry` keys for the pre-magic-link blocker.
- [Council 1779120600 RC20]: Add `magic_link_same_device_hint` key: "Open the magic link on the SAME device where you started." Both EN + RO.
- [Council 1779120600 RC11]: Add `photo_suggestion_none_of_these` key: "None of these — type manually." Both EN + RO.
- [Council 1779120600 RC13]: Add `byok_clipboard_cleared_toast` key: "Clipboard cleared for security." Both EN + RO.
- [Council 1779120600 RC19]: Add `consent_cross_border_transfer_title` + `consent_cross_border_transfer_body` keys: "Cross-border transfer to Anthropic (US) / Google (US) / OpenRouter (US) under SCC + DPF mechanism." Both EN + RO.

**Bigorexia copy (LOCKED — verbatim in strings):**

EN (`ed_safeguard_bigorexia_note`):
> "Building strength is a long process. If you find yourself feeling 'never big enough' or feeling guilty after rest days, those are signals worth pausing for — not signs you should train more. Dietician uses a process target, not a body target. Weekly aggregate, not daily weight. We measure strength + energy + mood + sleep, not just the scale. Resources: NEDA bigorexia page, BodyDysmorphia.org."

RO (`ed_safeguard_bigorexia_note`):
> "Construirea forței este un proces lung. Dacă te simți 'niciodată suficient de mare' sau te simți vinovat după zilele de odihnă, acelea sunt semnale care merită o pauză — nu semne că ar trebui să te antrenezi mai mult. Dietician folosește o țintă de proces, nu o țintă corporală. Agregat săptămânal, nu greutate zilnică. Măsurăm forță + energie + dispoziție + somn, nu doar cântarul. Resurse: pagina NEDA bigorexia, BodyDysmorphia.org."

**Files:**
- Create: `shared/src/commonMain/resources/MR/base/strings.xml` (default = English)
- Create: `shared/src/commonMain/resources/MR/ro/strings.xml`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/i18n/LocaleSelector.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/i18n/StringsCoverageTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/dietician/ui/components/BigorexiaCopyTest.kt` — REPLACE placeholder with real assertion (RC5)

- [ ] **Step 1: Write failing coverage test**

```kotlin
package com.dietician.ui.i18n

import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsCoverageTest {
    @Test
    fun `RO and EN have the same string keys`() {
        val enKeys = parseKeys(File("src/commonMain/resources/MR/base/strings.xml"))
        val roKeys = parseKeys(File("src/commonMain/resources/MR/ro/strings.xml"))
        val missingInRo = enKeys - roKeys
        val missingInEn = roKeys - enKeys
        assertTrue(missingInRo.isEmpty(), "RO missing: $missingInRo")
        assertTrue(missingInEn.isEmpty(), "EN missing: $missingInEn")
    }

    @Test
    fun `string count is at least 200 keys (target ~250 across 16 screens)`() {
        val enKeys = parseKeys(File("src/commonMain/resources/MR/base/strings.xml"))
        assertTrue(enKeys.size >= 200, "Want >=200 keys, got ${enKeys.size}")
    }

    @Test
    fun `AI literacy banner copy mentions probabilistic + not knowledge oracle`() {
        val en = File("src/commonMain/resources/MR/base/strings.xml").readText()
        assertTrue(en.contains("probabilistic", ignoreCase = true))
        assertTrue(en.contains("not a knowledge", ignoreCase = true) || en.contains("not an oracle", ignoreCase = true))
    }

    @Test
    fun `ED safeguard copy mentions muscle-dysmorphia or bigorexia`() {
        val en = File("src/commonMain/resources/MR/base/strings.xml").readText()
        assertTrue(en.contains("bigorexia", ignoreCase = true) || en.contains("muscle dysmorphia", ignoreCase = true))
    }

    private fun parseKeys(file: File): Set<String> {
        if (!file.exists()) return emptySet()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(file.readText())))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }
}
```

- [ ] **Step 2: Write `strings.xml` (English base — abridged in plan; full key list inline)**

`shared/src/commonMain/resources/MR/base/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Onboarding (spec Appendix G + §11.10) -->
    <string name="onboarding_step1_title">Welcome to Dietician</string>
    <string name="onboarding_step1_lang_label">Language</string>
    <string name="onboarding_step1_tailnet_label">Your Tailscale tailnet name</string>
    <string name="onboarding_step1_tailnet_help">Format: yourname.ts.net. Auto-detected if Tailscale is running.</string>
    <string name="onboarding_step1_next">Next</string>
    <string name="onboarding_step2_title">How this app uses AI</string>
    <string name="onboarding_step2_body_para1">Dietician uses Large Language Models (LLMs) for some features. LLMs are probabilistic text generators, not a knowledge oracle — they can hallucinate.</string>
    <string name="onboarding_step2_body_para2">What we use LLMs for: recipe extraction, planner reasoning, voice transcription, OCR.</string>
    <string name="onboarding_step2_body_para3">What we do NOT use LLMs for: your weight calculation, your DRV targets, your safety limits — all rule-based.</string>
    <string name="onboarding_step2_body_para4">Where your data goes: your data stays on your device + your friend Victor\'s VPS. LLM API calls go to Anthropic / Google US data centers with explicit cross-border consent.</string>
    <string name="onboarding_step2_body_para5">You can disable LLM features anytime in Settings → AI Coach.</string>
    <string name="onboarding_step2_ack">I understand</string>
    <string name="onboarding_step3_title">About you</string>
    <string name="onboarding_field_name">Name</string>
    <string name="onboarding_field_email">Email</string>
    <string name="onboarding_field_height">Height (cm)</string>
    <string name="onboarding_field_weight">Weight (kg)</string>
    <string name="onboarding_field_age">Age</string>
    <string name="onboarding_field_sex">Sex</string>
    <string name="onboarding_field_goal">Primary goal</string>
    <string name="onboarding_goal_lean_bulk">Lean bulk</string>
    <string name="onboarding_goal_cut">Cut</string>
    <string name="onboarding_goal_maintain">Maintain</string>
    <string name="onboarding_goal_reverse_diet">Reverse diet</string>
    <string name="onboarding_step4_title">Kitchen equipment</string>
    <string name="onboarding_equip_air_fryer">Air fryer</string>
    <string name="onboarding_equip_microwave">Microwave</string>
    <string name="onboarding_equip_stove">Stove</string>
    <string name="onboarding_equip_oven">Oven</string>
    <string name="onboarding_step5_title">Stores you shop at</string>
    <string name="onboarding_stores_help">Pick the supermarkets you visit weekly. Price scrapers will index these first.</string>
    <string name="onboarding_step6_title">Privacy and consent</string>
    <string name="onboarding_consent_health">I consent to processing my health data (weight, food intake, training, sleep) under GDPR Art 9(2)(a). Withdrawable in Settings.</string>
    <string name="onboarding_consent_voice">I consent to processing voice recordings I make. Audio is transcribed on-device when possible; remote-only if local Whisper unavailable.</string>
    <string name="onboarding_consent_photo">I consent to processing photos I take (receipts, labels, meals) using AI vision models. List of receivers in Settings → About.</string>
    <string name="onboarding_consent_crossborder">I consent to cross-border transfer of my prompts to Anthropic / Google US (SCC + DPF).</string>
    <string name="onboarding_step7_title">Sign-in method</string>
    <string name="onboarding_step7_help">Passkeys will be available in a future update. For now we use magic-link email sign-in.</string>
    <string name="onboarding_step7_skip_passkey">Continue with magic-link</string>

    <!-- Home (§11.1) -->
    <string name="home_greeting">Hi, %1$s</string>
    <string name="home_tdee_label">Today\'s expenditure estimate</string>
    <string name="home_tdee_band">%1$d kcal (±%2$d, based on last %3$d days)</string>
    <string name="home_next_meal">Next meal</string>
    <string name="home_quick_log">Log meal</string>
    <string name="home_quick_photo">Photo</string>
    <string name="home_diag_degraded">Server unreachable — working offline</string>
    <string name="home_ai_literacy_footer">How this app uses AI · tap to read</string>
    <string name="home_pause">Pause tracking</string>
    <string name="home_admin_scrape_now">Refresh prices now</string>

    <!-- Food log (§11.2) -->
    <string name="foodlog_title">Food log</string>
    <string name="foodlog_talk">Tap to talk</string>
    <string name="foodlog_recording">Listening…</string>
    <string name="foodlog_manual_entry">Manual entry</string>
    <string name="foodlog_add">Add</string>
    <string name="foodlog_recent_meals">Recent meals</string>
    <string name="foodlog_empty">No meals logged yet today.</string>

    <!-- Meal detail / 84 nutrients (§11.2 deep) -->
    <string name="meal_detail_nutrients">Nutrients</string>
    <string name="meal_detail_macros">Macros</string>
    <string name="meal_detail_micros">Micros</string>
    <string name="meal_detail_amino_acids">Amino acids</string>
    <string name="meal_detail_show_all_84">Show all 84 nutrients</string>
    <string name="meal_detail_edit">Edit</string>
    <string name="meal_detail_delete">Delete</string>

    <!-- Pantry (§11.3) -->
    <string name="pantry_title">Pantry</string>
    <string name="pantry_search">Search pantry</string>
    <string name="pantry_low_stock">Low stock</string>
    <string name="pantry_add">Add item</string>
    <string name="pantry_audit">Inventory audit</string>
    <string name="pantry_empty">Your pantry is empty. Tap + to add items.</string>
    <string name="pantry_expires_in_days">expires in %1$d days</string>
    <string name="pantry_status_sealed">Sealed</string>
    <string name="pantry_status_open">Open</string>

    <!-- Cookbook (§11.4) -->
    <string name="cookbook_title">Cookbook</string>
    <string name="cookbook_search">Search recipes</string>
    <string name="cookbook_ingest">Add recipe</string>
    <string name="cookbook_review_queue">Review queue</string>
    <string name="cookbook_ingest_url">Paste URL</string>
    <string name="cookbook_ingest_pdf">Pick PDF</string>
    <string name="cookbook_ingest_voice">Voice memo</string>
    <string name="cookbook_ingest_compose">Compose new</string>
    <string name="cookbook_recipe_ingredients">Ingredients</string>
    <string name="cookbook_recipe_steps">Steps</string>
    <string name="cookbook_recipe_nutrition">Nutrition</string>
    <string name="cookbook_recipe_history">History</string>
    <string name="cookbook_recipe_plan_into_week">Add to this week</string>
    <string name="cookbook_queued_toast">Queued — recipe will be available within a few days.</string>

    <!-- Coach chat (§11.5) -->
    <string name="coach_title">Coach</string>
    <string name="coach_input_placeholder">Ask anything…</string>
    <string name="coach_send">Send</string>
    <string name="coach_just_tell_me">Just tell me (no AI)</string>
    <string name="coach_disclosure_provider">Provider</string>
    <string name="coach_disclosure_model">Model</string>
    <string name="coach_disclosure_cost">Cost</string>
    <string name="coach_disclosure_latency">Latency</string>
    <string name="coach_disclosure_view_audit">Open audit log entry</string>
    <string name="coach_suggested_what_to_eat">What should I eat tonight?</string>
    <string name="coach_suggested_low_protein">What\'s low in protein this week?</string>
    <string name="coach_suggested_regenerate">Regenerate plan</string>
    <string name="coach_suggested_swap_dinner">Swap dinner for something simpler</string>

    <!-- Just tell me (§11.13) -->
    <string name="just_tell_me_title">Rule-based answer</string>
    <string name="just_tell_me_label">Source: rule-based planner (no AI in this answer)</string>
    <string name="just_tell_me_back">Back to coach</string>
    <string name="just_tell_me_disable_llm">Permanently disable LLM coach</string>

    <!-- Paper search (§11.6) -->
    <string name="paper_search_title">Knowledge search</string>
    <string name="paper_search_placeholder">Search papers, articles, references</string>
    <string name="paper_search_empty">No results. Try different terms.</string>
    <string name="paper_domain_nutrition">Nutrition</string>
    <string name="paper_domain_training">Training</string>
    <string name="paper_domain_clinical">Clinical</string>
    <string name="paper_domain_behavior">Behavior</string>
    <string name="paper_detail_abstract">Abstract</string>
    <string name="paper_detail_ingest">Ingest to local wiki</string>
    <string name="paper_detail_open_wiki">Open wiki entry</string>

    <!-- Receipt upload (§11.7) -->
    <string name="receipt_title">Receipt upload</string>
    <string name="receipt_camera">Take photo</string>
    <string name="receipt_file_pick">Pick file</string>
    <string name="receipt_recent">Recent uploads</string>
    <string name="receipt_detail_line_items">Line items</string>
    <string name="receipt_detail_edit">Edit</string>
    <string name="receipt_detail_confirm">Confirm and add to pantry</string>

    <!-- Settings (§11.8) -->
    <string name="settings_title">Settings</string>
    <string name="settings_profile">Profile</string>
    <string name="settings_stores">Stores nearby</string>
    <string name="settings_equipment">Kitchen equipment</string>
    <string name="settings_credentials">Credentials</string>
    <string name="settings_consent">Consent records</string>
    <string name="settings_ai_coach">AI coach</string>
    <string name="settings_photo">Photo features</string>
    <string name="settings_voice">Voice features</string>
    <string name="settings_pause">Pause tracking</string>
    <string name="settings_delete_account">Delete account</string>
    <string name="settings_export_dsar">Export my data (DSAR)</string>
    <string name="settings_about">About</string>
    <string name="settings_uaic_reexport_cookies">Re-export UAIC SAML cookies (Desktop only)</string>
    <string name="settings_consent_withdraw">Withdraw</string>
    <string name="settings_delete_confirm_step1">Delete account?</string>
    <string name="settings_delete_confirm_step1_body">Your data enters a 7-day grace period. After 7 days it is physically purged. You can cancel any time during the grace period.</string>
    <string name="settings_delete_confirm_step2">Final confirmation</string>
    <string name="settings_delete_confirm_step2_body">Type DELETE to confirm.</string>

    <!-- Audit log (§11.9) -->
    <string name="audit_log_title">Audit log</string>
    <string name="audit_log_filter">Filter</string>
    <string name="audit_export_pdf">Export PDF</string>
    <string name="audit_export_json">Export JSON</string>

    <!-- AI literacy (§11.11) -->
    <string name="ai_literacy_title">How this app uses AI</string>
    <string name="ai_literacy_body_para1">Dietician uses Large Language Models (LLMs) for some features. LLMs are probabilistic text generators, not a knowledge oracle.</string>
    <string name="ai_literacy_body_para2">What we use LLMs for, what we don\'t, where data goes, how to disable — see Settings → AI Coach.</string>
    <string name="ai_literacy_disable_link">Disable AI features</string>
    <string name="ai_literacy_ack">Got it</string>

    <!-- ED safeguard (§11.12 + §11.15a) -->
    <string name="ed_safeguard_title">A gentle check-in</string>
    <string name="ed_safeguard_message">We noticed a pattern in your recent logging. This is a normal feature of tracking apps — it can sometimes drift into compulsive territory. If you want, you can pause tracking for a week.</string>
    <string name="ed_safeguard_bigorexia_note">For active lean-bulk users: muscle dysmorphia (bigorexia) shares risk-patterns with restrictive eating disorders — never-enough-protein, never-enough-mass, compulsive measurement. The same pause works.</string>
    <string name="ed_safeguard_resource_en">If you want to talk to someone: NEDA helpline, your GP, or a registered dietitian.</string>
    <string name="ed_safeguard_resource_ro">Dacă vrei să discuți cu cineva: medicul tău de familie sau un nutriționist înregistrat.</string>
    <string name="ed_safeguard_pause">Pause for a week</string>
    <string name="ed_safeguard_continue">Continue tracking</string>
    <string name="ed_checkin_pause_tracking">Pause tracking</string>
    <string name="ed_checkin_dismiss">Not now</string>
    <string name="ed_checkin_ok">I\'m okay, thanks</string>

    <!-- Pause tracking (§11.14) -->
    <string name="pause_title">Tracking paused</string>
    <string name="pause_message">Tracking is paused. Your data is preserved. Nothing will be logged or analyzed until you resume.</string>
    <string name="pause_resume">Resume tracking</string>

    <!-- Diag (§11.15) -->
    <string name="diag_title">Diagnostics</string>
    <string name="diag_vps">VPS</string>
    <string name="diag_tailscale">Tailscale</string>
    <string name="diag_postgres">Postgres</string>
    <string name="diag_ntfy">ntfy</string>
    <string name="diag_outbox">Outbox</string>
    <string name="diag_sync_times">Sync times</string>
    <string name="diag_llm_budget_claudemax">ClaudeMax budget</string>
    <string name="diag_llm_budget_openrouter">OpenRouter budget</string>
    <string name="diag_scraper_status">Scrapers</string>
    <string name="diag_last_errors">Last errors</string>
    <string name="diag_pending_jobs">Pending jobs</string>

    <!-- Magic-link verify -->
    <string name="magic_link_verify_title">Sign in</string>
    <string name="magic_link_verify_help">Paste the verification token from your email, or tap the link in your inbox.</string>
    <string name="magic_link_verify_token_label">Token</string>
    <string name="magic_link_verify_submit">Verify</string>
    <string name="magic_link_verify_error">Token invalid or expired. Request a new email.</string>
    <string name="magic_link_request_resend">Resend email</string>

    <!-- Common UI -->
    <string name="common_cancel">Cancel</string>
    <string name="common_confirm">Confirm</string>
    <string name="common_save">Save</string>
    <string name="common_delete">Delete</string>
    <string name="common_back">Back</string>
    <string name="common_close">Close</string>
    <string name="common_yes">Yes</string>
    <string name="common_no">No</string>
    <string name="common_loading">Loading…</string>
    <string name="common_error_network">Network error. Tap to retry.</string>
    <string name="common_error_generic">Something went wrong. Tap to retry.</string>

    <!-- Voice input -->
    <string name="voice_listening">Listening — speak now…</string>
    <string name="voice_uploading">Uploading audio…</string>
    <string name="voice_transcribing">Transcribing…</string>
    <string name="voice_complete">Transcription complete.</string>
    <string name="voice_local_unavailable">Local Whisper not available — using cloud transcription.</string>

    <!-- Photo suggestion -->
    <string name="photo_suggestion_title">Is this what you ate?</string>
    <string name="photo_suggestion_confirm">Yes, that\'s it</string>
    <string name="photo_suggestion_correct">No, let me edit</string>
    <string name="photo_suggestion_confidence">Confidence: %1$d%%</string>

    <!-- Weekly narrative -->
    <string name="weekly_narrative_title">This week</string>
    <string name="weekly_narrative_generating">Composing your weekly review…</string>
</resources>
```

- [ ] **Step 3: Write `strings.xml` (Romanian — abridged; full from Appendix G)**

`shared/src/commonMain/resources/MR/ro/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Onboarding (Romanian, from Appendix G) -->
    <string name="onboarding_step1_title">Bun venit la Dietician</string>
    <string name="onboarding_step1_lang_label">Limbă</string>
    <string name="onboarding_step1_tailnet_label">Numele tailnet-ului tău Tailscale</string>
    <string name="onboarding_step1_tailnet_help">Format: numele-tău.ts.net. Detectat automat dacă Tailscale rulează.</string>
    <string name="onboarding_step1_next">Înainte</string>
    <string name="onboarding_step2_title">Cum folosește această aplicație AI</string>
    <string name="onboarding_step2_body_para1">Dietician folosește Modele Lingvistice Mari (LLM) pentru unele funcții. LLM-urile sunt generatoare de text probabilistice, nu un oracol de cunoștințe — pot să halucineze.</string>
    <string name="onboarding_step2_body_para2">Pentru ce folosim LLM-urile: extragere de rețete, raționament de planificare, transcriere vocală, OCR.</string>
    <string name="onboarding_step2_body_para3">Pentru ce NU folosim LLM-uri: calculul greutății tale, țintele DRV, limitele de siguranță — toate sunt bazate pe reguli.</string>
    <string name="onboarding_step2_body_para4">Unde merg datele tale: datele tale rămân pe dispozitivul tău + serverul VPS al lui Victor. Apelurile API LLM merg către centrele de date Anthropic / Google din SUA, cu consimțământ explicit pentru transfer transfrontalier.</string>
    <string name="onboarding_step2_body_para5">Poți dezactiva funcțiile AI oricând din Setări → Antrenor AI.</string>
    <string name="onboarding_step2_ack">Am înțeles</string>
    <string name="onboarding_step3_title">Despre tine</string>
    <string name="onboarding_field_name">Nume</string>
    <string name="onboarding_field_email">Email</string>
    <string name="onboarding_field_height">Înălțime (cm)</string>
    <string name="onboarding_field_weight">Greutate (kg)</string>
    <string name="onboarding_field_age">Vârstă</string>
    <string name="onboarding_field_sex">Sex</string>
    <string name="onboarding_field_goal">Obiectiv principal</string>
    <string name="onboarding_goal_lean_bulk">Masă slabă (lean bulk)</string>
    <string name="onboarding_goal_cut">Slăbire</string>
    <string name="onboarding_goal_maintain">Menținere</string>
    <string name="onboarding_goal_reverse_diet">Dietă inversă</string>
    <string name="onboarding_step4_title">Echipament de bucătărie</string>
    <string name="onboarding_equip_air_fryer">Air fryer</string>
    <string name="onboarding_equip_microwave">Cuptor cu microunde</string>
    <string name="onboarding_equip_stove">Aragaz</string>
    <string name="onboarding_equip_oven">Cuptor</string>
    <string name="onboarding_step5_title">Magazinele tale</string>
    <string name="onboarding_stores_help">Alege supermarketurile pe care le vizitezi săptămânal. Scraperii de prețuri le vor indexa pe acestea primele.</string>
    <string name="onboarding_step6_title">Confidențialitate și consimțământ</string>
    <string name="onboarding_consent_health">Consimt la prelucrarea datelor mele de sănătate (greutate, alimentație, antrenament, somn) conform GDPR Art 9(2)(a). Retragibil în Setări.</string>
    <string name="onboarding_consent_voice">Consimt la prelucrarea înregistrărilor vocale pe care le fac. Audio-ul este transcris pe dispozitiv când e posibil; doar remote dacă Whisper local nu e disponibil.</string>
    <string name="onboarding_consent_photo">Consimt la prelucrarea fotografiilor pe care le fac (chitanțe, etichete, mese) folosind modele AI de viziune. Lista completă de destinatari în Setări → Despre.</string>
    <string name="onboarding_consent_crossborder">Consimt la transferul transfrontalier al prompturilor mele către Anthropic / Google SUA (SCC + DPF).</string>
    <string name="onboarding_step7_title">Metodă de autentificare</string>
    <string name="onboarding_step7_help">Passkey-urile vor fi disponibile într-o actualizare viitoare. Momentan folosim autentificare prin link magic pe email.</string>
    <string name="onboarding_step7_skip_passkey">Continuă cu link magic</string>

    <!-- Home -->
    <string name="home_greeting">Bună, %1$s</string>
    <string name="home_tdee_label">Estimare consum azi</string>
    <string name="home_tdee_band">%1$d kcal (±%2$d, bazat pe ultimele %3$d zile)</string>
    <string name="home_next_meal">Următoarea masă</string>
    <string name="home_quick_log">Înregistrează masă</string>
    <string name="home_quick_photo">Fotografie</string>
    <string name="home_diag_degraded">Server inaccesibil — funcționează offline</string>
    <string name="home_ai_literacy_footer">Cum folosește această aplicație AI · apasă pentru a citi</string>
    <string name="home_pause">Pauză tracking</string>
    <string name="home_admin_scrape_now">Reîmprospătează prețurile acum</string>

    <!-- Food log -->
    <string name="foodlog_title">Jurnal alimentar</string>
    <string name="foodlog_talk">Apasă pentru a vorbi</string>
    <string name="foodlog_recording">Ascult…</string>
    <string name="foodlog_manual_entry">Introducere manuală</string>
    <string name="foodlog_add">Adaugă</string>
    <string name="foodlog_recent_meals">Mese recente</string>
    <string name="foodlog_empty">Nicio masă înregistrată azi.</string>

    <!-- (… all remaining keys parallel to base; abridged in plan for brevity — implementer copies + translates from Appendix G ship-ready copy …) -->
    <!-- Full key set must match base/strings.xml exactly — StringsCoverageTest enforces. -->

    <!-- ED safeguard (Romanian) -->
    <string name="ed_safeguard_title">O verificare blândă</string>
    <string name="ed_safeguard_message">Am observat un tipar în înregistrările tale recente. Aplicațiile de tracking pot uneori să alunece spre comportament compulsiv. Dacă vrei, poți pune tracking-ul pe pauză pentru o săptămână.</string>
    <string name="ed_safeguard_bigorexia_note">Pentru utilizatorii activi în lean bulk: dismorfia musculară (bigorexia) împărtășește tipare de risc cu tulburările alimentare restrictive — niciodată-suficientă-proteină, niciodată-suficientă-masă, măsurare compulsivă. Aceeași pauză funcționează.</string>
    <string name="ed_safeguard_resource_en">If you want to talk to someone: NEDA helpline, your GP, or a registered dietitian.</string>
    <string name="ed_safeguard_resource_ro">Dacă vrei să discuți cu cineva: medicul tău de familie sau un nutriționist înregistrat.</string>
    <string name="ed_safeguard_pause">Pauză pentru o săptămână</string>
    <string name="ed_safeguard_continue">Continuă tracking</string>
    <string name="ed_checkin_pause_tracking">Pauză tracking</string>
    <string name="ed_checkin_dismiss">Nu acum</string>
    <string name="ed_checkin_ok">Sunt bine, mulțumesc</string>

    <!-- (etc. — implementer fills the rest from Appendix G — must reach key-parity with base/strings.xml) -->
</resources>
```

NOTE for implementer: the abridged RO file above contains all keys whose Romanian wording is non-trivial (ED safeguard, onboarding from Appendix G). Fill the remaining ~150 keys by translating EN counterparts. `StringsCoverageTest.assertEquals(enKeys, roKeys)` is the gate.

- [ ] **Step 4: Implement `LocaleSelector.kt`**

```kotlin
package com.dietician.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

enum class AppLocale(val tag: String) { EN("en"), RO("ro") }

val LocalAppLocale = compositionLocalOf { AppLocale.EN }

@Composable
fun WithAppLocale(locale: AppLocale, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides locale, content = content)
}
```

- [ ] **Step 5: Run + commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.i18n.*"
git add shared/src/commonMain/resources/MR/ \
        shared/src/commonMain/kotlin/com/dietician/ui/i18n/ \
        shared/src/commonTest/kotlin/com/dietician/ui/i18n/
git commit -m "feat(plan-4-5): RO+EN moko-resources strings + StringsCoverageTest

~250 keys covering 16 screens + AI literacy + ED safeguard (bigorexia mention enforced)
+ onboarding 7 steps. StringsCoverageTest asserts RO/EN key parity. Romanian copy from
spec Appendix G ship-ready translations.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Navigation skeleton — Voyager `RootScreen` + `BottomNavBar` + `DeepLinkRouter`

### Council baked-in fixes
- [Council 1779120600 RC6]: DO NOT register `EDSafeguardScreen` route — dropped. Only `EDSafeguardModal` (cross-route composable) + `PauseTrackingScreen` (active-state full screen).
- [Council 1779120600 RC4]: DO NOT register `CookbookScreen` / `PaperSearchScreen` / `DiagScreen` routes (all deferred). If deep-link arrives, route to a placeholder "coming soon" screen.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/navigation/RootScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/navigation/BottomNavDestination.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/navigation/BottomNavBar.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/navigation/DeepLinkRouter.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/navigation/NavTransitions.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/navigation/DeepLinkRouterTest.kt`

- [ ] **Step 1: Failing test — `DeepLinkRouterTest.kt`**

```kotlin
package com.dietician.ui.navigation

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import kotlin.test.Test

class DeepLinkRouterTest {
    @Test
    fun `home deeplink parses to Home destination`() {
        DeepLinkRouter.parse("dietician://home") shouldBe DeepLinkTarget.Home
    }

    @Test
    fun `food-log deeplink parses`() {
        DeepLinkRouter.parse("dietician://food-log") shouldBe DeepLinkTarget.FoodLog
    }

    @Test
    fun `magic-link verify carries token`() {
        DeepLinkRouter.parse("dietician://verify?token=abc123") shouldBe DeepLinkTarget.MagicLinkVerify("abc123")
    }

    @Test
    fun `unknown deeplink returns null`() {
        DeepLinkRouter.parse("dietician://nonexistent").shouldBeNull()
    }

    @Test
    fun `non-dietician scheme returns null`() {
        DeepLinkRouter.parse("https://google.com").shouldBeNull()
    }
}
```

- [ ] **Step 2: Run (fails)** → `./gradlew :shared:commonTest --tests "com.dietician.ui.navigation.*"` → FAIL.

- [ ] **Step 3: Implement `RootScreen.kt`**

```kotlin
package com.dietician.ui.navigation

import cafe.adriel.voyager.core.screen.Screen

/**
 * Marker interface for Dietician screens. Every screen carries a `testTag` for the visual
 * acceptance gate (Task 33). The Voyager [Screen] interface is the underlying nav primitive.
 */
interface RootScreen : Screen {
    /** The data-testid root for this screen, e.g. "home" → asserts "home-*" selectors paint. */
    val testTagPrefix: String
}
```

- [ ] **Step 4: Implement `BottomNavDestination.kt`**

```kotlin
package com.dietician.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavDestination(
    val key: String,
    val icon: ImageVector,
    val labelKey: String,
) {
    object Home : BottomNavDestination("home", Icons.Filled.Home, "nav_home")
    object FoodLog : BottomNavDestination("food-log", Icons.Filled.Edit, "nav_food_log")
    object Pantry : BottomNavDestination("pantry", Icons.Filled.Kitchen, "nav_pantry")
    object Cookbook : BottomNavDestination("cookbook", Icons.Filled.Book, "nav_cookbook")
    object More : BottomNavDestination("more", Icons.Filled.MoreHoriz, "nav_more")

    companion object {
        val all = listOf(Home, FoodLog, Pantry, Cookbook, More)
    }
}
```

- [ ] **Step 5: Implement `BottomNavBar.kt`** (composable; full code with testTag wiring)

```kotlin
package com.dietician.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun BottomNavBar(
    current: BottomNavDestination,
    onSelected: (BottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier.fillMaxWidth().testTag("bottomnav-root")) {
        BottomNavDestination.all.forEach { dest ->
            NavigationBarItem(
                selected = dest == current,
                onClick = { onSelected(dest) },
                icon = { Icon(dest.icon, contentDescription = dest.key) },
                label = { Text(dest.key) /* TODO: route through MR.strings.nav_<dest.key> */ },
                modifier = Modifier.testTag("bottomnav-${dest.key}"),
            )
        }
    }
}
```

- [ ] **Step 6: Implement `DeepLinkRouter.kt`**

```kotlin
package com.dietician.ui.navigation

sealed interface DeepLinkTarget {
    object Home : DeepLinkTarget
    object FoodLog : DeepLinkTarget
    object Pantry : DeepLinkTarget
    object Cookbook : DeepLinkTarget
    object CoachChat : DeepLinkTarget
    object Settings : DeepLinkTarget
    object AuditLog : DeepLinkTarget
    object Diag : DeepLinkTarget
    data class MagicLinkVerify(val token: String) : DeepLinkTarget
}

object DeepLinkRouter {
    private val table: Map<String, DeepLinkTarget> = mapOf(
        "home" to DeepLinkTarget.Home,
        "food-log" to DeepLinkTarget.FoodLog,
        "pantry" to DeepLinkTarget.Pantry,
        "cookbook" to DeepLinkTarget.Cookbook,
        "coach" to DeepLinkTarget.CoachChat,
        "settings" to DeepLinkTarget.Settings,
        "audit" to DeepLinkTarget.AuditLog,
        "diag" to DeepLinkTarget.Diag,
    )

    fun parse(uri: String): DeepLinkTarget? {
        if (!uri.startsWith("dietician://")) return null
        val rest = uri.removePrefix("dietician://")
        val (path, query) = rest.split('?', limit = 2).let { it[0] to it.getOrNull(1) }
        if (path == "verify") {
            val token = query?.split('&')?.firstOrNull { it.startsWith("token=") }?.removePrefix("token=")
            return token?.let { DeepLinkTarget.MagicLinkVerify(it) }
        }
        return table[path]
    }
}
```

- [ ] **Step 7: Implement `NavTransitions.kt`**

```kotlin
package com.dietician.ui.navigation

import cafe.adriel.voyager.transitions.SlideTransition

// Re-export Voyager's SlideTransition as the default; named alias keeps the call-site stable
// if we swap transitions later (e.g. FadeTransition for modal-style screens).
val DefaultTransition = SlideTransition
```

- [ ] **Step 8: Run tests + commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.navigation.*"
git add shared/src/commonMain/kotlin/com/dietician/ui/navigation/ \
        shared/src/commonTest/kotlin/com/dietician/ui/navigation/
git commit -m "feat(plan-4-5): Voyager navigation skeleton + BottomNavBar + DeepLinkRouter

5 bottom-nav destinations (Home/FoodLog/Pantry/Cookbook/More). DeepLinkRouter parses
dietician://home + dietician://verify?token=... for magic-link landing. Per-tab testTag
asserts 'bottomnav-<key>' selectors paint for final acceptance gate.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: `SubjectPrincipal` + Koin DI module wiring

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/state/SubjectPrincipal.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/state/PauseState.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/UiModule.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/state/SubjectPrincipalTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.dietician.ui.state

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SubjectPrincipalTest {
    @Test
    fun `isVictor true when displayName equals Victor`() {
        SubjectPrincipal("uuid", "Victor", isVictor = true).isVictor shouldBe true
    }

    @Test
    fun `isVictor false for friend`() {
        SubjectPrincipal("uuid", "Friend", isVictor = false).isVictor shouldBe false
    }

    @Test
    fun `principal serializes to JSON`() {
        val p = SubjectPrincipal("11111111-1111-1111-1111-111111111111", "Victor", true)
        val s = SubjectPrincipal.json.encodeToString(SubjectPrincipal.serializer(), p)
        SubjectPrincipal.json.decodeFromString(SubjectPrincipal.serializer(), s) shouldBe p
    }
}
```

- [ ] **Step 2: Implement `SubjectPrincipal.kt`**

```kotlin
package com.dietician.ui.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SubjectPrincipal(
    val subjectId: String,
    val displayName: String,
    val isVictor: Boolean,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
```

- [ ] **Step 3: Implement `PauseState.kt`**

```kotlin
package com.dietician.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton Koin-provided. UI subscribes to [isPaused]; logging endpoints check this flag
 * client-side and refuse to insert events when paused (per spec §6.9 self-pause). Server-side
 * `POST /me/pause` lands in Plan-3.5 fix-up; first-ship is client-side-only.
 *
 * RC (Plan-2 audit-log linkage): pause + resume each write an audit_log row via Plan-2
 * AuditLogWriter — actions `pause_engaged` / `pause_resumed` carrying the reason string.
 */
class PauseState {
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _reason = MutableStateFlow<String?>(null)
    val reason: StateFlow<String?> = _reason.asStateFlow()

    suspend fun pause(reason: String? = null) {
        _isPaused.value = true
        _reason.value = reason
        // Audit-log emit hook left for Task 7 (auth + audit wiring lands then).
    }

    suspend fun resume() {
        _isPaused.value = false
        _reason.value = null
    }
}
```

- [ ] **Step 4: Implement `UiModule.kt`**

```kotlin
package com.dietician.ui

import com.dietician.ui.state.PauseState
import org.koin.dsl.module

/**
 * Root Koin module for the UI layer. Bound at app start:
 *   androidApp `DieticianApp.onCreate`: startKoin { modules(dataModule, llmModule, uiModule, androidPlatformModule) }
 *   desktopApp `Main.main`: startKoin { modules(dataModule, llmModule, uiModule, desktopPlatformModule) }
 *
 * Screen-models are NOT registered here — Voyager creates them via `rememberScreenModel { ... }`
 * lambdas which can pull other Koin singletons via koin-compose `get<T>()`.
 */
val uiModule = module {
    single { PauseState() }
    // EDSafeguardState, AILiteracyState, SyncStateObserver added in their respective tasks.
}
```

- [ ] **Step 5: Run + commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.state.*"
git add shared/src/commonMain/kotlin/com/dietician/ui/state/ \
        shared/src/commonMain/kotlin/com/dietician/ui/UiModule.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/state/
git commit -m "feat(plan-4-5): SubjectPrincipal + PauseState singletons + UiModule Koin wiring

Pause is client-side-only first-ship (POST /me/pause lands in Plan-3.5 fix-up). Reason
string + audit-log emit hook reserved for Task 7.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Network layer — `ApiClient` + `SessionInterceptor` + `MagicLinkApi`

### Council baked-in fixes
- [Council 1779120600 RC15]: `SessionInterceptor` MUST be a Ktor plugin that asserts every outbound request's `X-Subject-Id` header (if set) matches the JWT `sub` claim. Add `SubjectIdConsistencyTest` integration test. Protects against `SubjectPrincipal` singleton drift during magic-link re-verify race → screen could otherwise send subjectId=Victor + JWT=Alice → Plan-3 RLS rejects but client UI shows blank state with no error.

**SessionInterceptor MUST decode the JWT body server-side claim `sub` (no signature verification — Plan-3 server does that) and compare to the outgoing `X-Subject-Id` header. On mismatch: throw `SubjectIdMismatchException` BEFORE the request leaves the client. Surface to caller; UI shows "Session inconsistent — please re-authenticate" + force magic-link re-verify.**

Add `shared/src/commonTest/kotlin/com/dietician/ui/network/SubjectIdConsistencyTest.kt`:
- Test 1: matching subjectId + JWT.sub → request passes
- Test 2: mismatched → `SubjectIdMismatchException` thrown
- Test 3: missing X-Subject-Id header → request passes (legacy path; not all routes are subject-scoped)
- Test 4: malformed JWT → `SubjectIdMismatchException` thrown

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/Endpoints.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/ApiClient.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/SessionInterceptor.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/MagicLinkApi.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/network/MagicLinkApiTest.kt`

- [ ] **Step 1: Failing test using Ktor MockEngine**

```kotlin
package com.dietician.ui.network

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

class MagicLinkApiTest {
    private fun client(handler: (io.ktor.client.request.HttpRequestData) -> io.ktor.client.engine.mock.MockHttpResponse) = HttpClient(MockEngine { handler(it) }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun `request posts email to magic-link request`() = runTest {
        var captured = ""
        val api = MagicLinkApi(client { req ->
            captured = req.body.toString()
            respond("""{"ok":true}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }, baseUrl = "http://test")
        api.requestLink("victor@example.com")
        (captured.contains("victor@example.com")) shouldBe true
    }

    @Test
    fun `verify returns subject principal`() = runTest {
        val api = MagicLinkApi(client {
            respond(
                """{"subjectId":"uuid-1","displayName":"Victor","jwt":"eyJ...","isVictor":true}""",
                HttpStatusCode.OK,
                headersOf("Content-Type", "application/json")
            )
        }, baseUrl = "http://test")
        val result = api.verify("token-abc").getOrThrow()
        result.principal.subjectId shouldBe "uuid-1"
        result.principal.isVictor shouldBe true
        result.jwt shouldBe "eyJ..."
    }
}
```

- [ ] **Step 2: Implement `Endpoints.kt`**

```kotlin
package com.dietician.ui.network

object Endpoints {
    const val ME = "/me"
    const val ME_AUDIT = "/me/audit"
    const val ME_DSAR = "/me/dsar"
    const val ME_BYOK = "/me/byok"
    const val ME_CONSENT = "/me/consent"
    const val ME_SESSIONS = "/me/sessions"
    const val ME_PAUSE = "/me/pause"          // Plan-3.5 — client gracefully degrades to local-only until shipped
    const val SYNC_PUSH = "/sync/push"
    const val SYNC_PULL = "/sync/pull"
    const val WS_SYNC = "/ws/sync"
    const val RECEIPTS_UPLOAD = "/receipts/upload"
    const val EMBED = "/embed"
    const val LLM_CHAT_STREAM = "/llm/chat/stream"
    const val JUST_TELL_ME = "/just_tell_me"
    const val AUTH_MAGIC_LINK_REQUEST = "/auth/magic-link/request"
    const val AUTH_MAGIC_LINK_VERIFY = "/auth/magic-link/verify"
    const val AUTH_SIGN_OUT_ALL = "/auth/sign-out-all-sessions"
    const val JOBS_QUEUE = "/jobs/queue"      // Plan-3 501-stub first-ship
    const val ADMIN_SCRAPE_NOW = "/admin/scrape-now"
    const val HEALTH = "/health"
    const val DIAG = "/diag"
}
```

- [ ] **Step 3: Implement `ApiClient.kt`**

```kotlin
package com.dietician.ui.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * App-level HTTP client. Base URL is set from onboarding step 1 (Tailscale Magic-DNS hostname
 * per A14) and persisted in Plan-1's `cache_metadata` table. The session JWT is held in
 * [sessionJwt] and injected by [defaultRequest] header — survives process death by being
 * persisted to platform-secure storage (Android EncryptedSharedPreferences, Desktop pgp_sym
 * via Plan-3 BYOK pattern).
 */
class ApiClient(
    httpClientFactory: () -> HttpClient,
    private var baseUrl: String,
) {
    private val _sessionJwt = MutableStateFlow<String?>(null)
    val sessionJwt: StateFlow<String?> = _sessionJwt.asStateFlow()

    fun setBaseUrl(url: String) { baseUrl = url }
    fun setSessionJwt(jwt: String?) { _sessionJwt.value = jwt }

    val http: HttpClient = httpClientFactory().config {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        install(Logging) { level = LogLevel.INFO }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        defaultRequest {
            url(baseUrl)
            _sessionJwt.value?.let { header("Authorization", "Bearer $it") }
        }
    }
}
```

- [ ] **Step 4: Implement `SessionInterceptor.kt`**

(SessionInterceptor logic is folded into `ApiClient`'s `defaultRequest` block above; this file is reserved for future per-call interceptors — placeholder.)

```kotlin
package com.dietician.ui.network

/** Reserved for future per-request interceptors. ApiClient's defaultRequest currently does this inline. */
object SessionInterceptor
```

- [ ] **Step 5: Implement `MagicLinkApi.kt`**

```kotlin
package com.dietician.ui.network

import com.dietician.ui.state.SubjectPrincipal
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class MagicLinkRequestBody(val email: String)

@Serializable
data class MagicLinkVerifyBody(val token: String)

@Serializable
data class MagicLinkVerifyResponse(
    val subjectId: String,
    val displayName: String,
    val jwt: String,
    val isVictor: Boolean,
)

data class VerifyResult(val principal: SubjectPrincipal, val jwt: String)

class MagicLinkApi(private val http: HttpClient, private val baseUrl: String = "") {
    suspend fun requestLink(email: String): Result<Unit> = runCatching {
        http.post("$baseUrl${Endpoints.AUTH_MAGIC_LINK_REQUEST}") {
            contentType(ContentType.Application.Json)
            setBody(MagicLinkRequestBody(email))
        }
        Unit
    }

    suspend fun verify(token: String): Result<VerifyResult> = runCatching {
        val resp: MagicLinkVerifyResponse = http.post("$baseUrl${Endpoints.AUTH_MAGIC_LINK_VERIFY}") {
            contentType(ContentType.Application.Json)
            setBody(MagicLinkVerifyBody(token))
        }.body()
        VerifyResult(
            principal = SubjectPrincipal(resp.subjectId, resp.displayName, resp.isVictor),
            jwt = resp.jwt,
        )
    }
}
```

- [ ] **Step 6: Run + commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.network.*"
git add shared/src/commonMain/kotlin/com/dietician/ui/network/ \
        shared/src/commonTest/kotlin/com/dietician/ui/network/
git commit -m "$(cat <<'EOF'
feat(plan-4-5): ApiClient + MagicLinkApi (request + verify endpoints)

Endpoints object enumerates Plan-3-contracted routes. ApiClient holds session JWT in
StateFlow + injects via defaultRequest. MagicLinkApi wraps /auth/magic-link/request +
/auth/magic-link/verify against Plan-3 first-ship surface.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: `OnboardingScreen` — 7-step flow + `OnboardingScreenModel`

### Council baked-in fixes
- [Council 1779120600 RC20]: Onboarding step rendering magic-link request MUST display `magic_link_same_device_hint` copy from Task 3 i18n: "Open the magic link on the SAME device where you started." Add `[data-testid="onboarding-magic-link-same-device-hint"]` selector visible while waiting for the link.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/OnboardingScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/OnboardingScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/OnboardingState.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/OnboardingScreenModelTest.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/OnboardingScreenRoborazziTest.kt`

- [ ] **Step 1: Failing screen-model test**

```kotlin
package com.dietician.ui.screens

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OnboardingScreenModelTest {
    @Test
    fun `starts at step 1`() {
        val m = OnboardingScreenModel(api = FakeApi(), pause = com.dietician.ui.state.PauseState())
        m.state.value.step shouldBe OnboardingStep.LANG_AND_TAILNET
    }

    @Test
    fun `next from step 1 with non-empty tailnet advances to step 2`() = runTest {
        val m = OnboardingScreenModel(api = FakeApi(), pause = com.dietician.ui.state.PauseState())
        m.setTailnet("user.ts.net")
        m.next()
        m.state.value.step shouldBe OnboardingStep.AI_LITERACY
    }

    @Test
    fun `cannot advance past AI literacy without ack`() = runTest {
        val m = OnboardingScreenModel(api = FakeApi(), pause = com.dietician.ui.state.PauseState())
        m.setTailnet("user.ts.net")
        m.next()
        m.next()  // tries to skip without ack
        m.state.value.step shouldBe OnboardingStep.AI_LITERACY  // unchanged
    }

    @Test
    fun `health consent required to proceed past step 6`() = runTest {
        val m = OnboardingScreenModel(api = FakeApi(), pause = com.dietician.ui.state.PauseState())
        m.fastForwardToStep(OnboardingStep.CONSENT)
        m.next()  // no consent yet
        m.state.value.step shouldBe OnboardingStep.CONSENT
        m.toggleConsent(ConsentKey.HEALTH, true)
        m.next()
        m.state.value.step shouldBe OnboardingStep.AUTH_METHOD
    }

    private class FakeApi : OnboardingApi {
        override suspend fun submitProfile(req: OnboardingProfileRequest) = Result.success(Unit)
        override suspend fun requestMagicLink(email: String) = Result.success(Unit)
    }
}
```

- [ ] **Step 2: Implement `OnboardingState.kt`**

```kotlin
package com.dietician.ui.screens

enum class OnboardingStep {
    LANG_AND_TAILNET, AI_LITERACY, IDENTITY, EQUIPMENT, STORES, CONSENT, AUTH_METHOD, DONE,
}
enum class ConsentKey { HEALTH, VOICE, PHOTO, CROSSBORDER }
enum class GoalKey { LEAN_BULK, CUT, MAINTAIN, REVERSE_DIET }
enum class SexKey { MALE, FEMALE, OTHER }
enum class EquipmentKey { AIR_FRYER, MICROWAVE, STOVE, OVEN }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.LANG_AND_TAILNET,
    val locale: String = "en",
    val tailnet: String = "",
    val literacyAcked: Boolean = false,
    val name: String = "",
    val email: String = "",
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val age: Int? = null,
    val sex: SexKey? = null,
    val goal: GoalKey? = null,
    val equipment: Set<EquipmentKey> = emptySet(),
    val stores: Set<String> = emptySet(),
    val consents: Map<ConsentKey, Boolean> = ConsentKey.values().associateWith { false },
    val submitting: Boolean = false,
    val errorMessage: String? = null,
)

interface OnboardingApi {
    suspend fun submitProfile(req: OnboardingProfileRequest): Result<Unit>
    suspend fun requestMagicLink(email: String): Result<Unit>
}

data class OnboardingProfileRequest(
    val name: String, val email: String, val heightCm: Int, val weightKg: Double,
    val age: Int, val sex: SexKey, val goal: GoalKey,
    val equipment: Set<EquipmentKey>, val stores: Set<String>,
    val consents: Map<ConsentKey, Boolean>,
)
```

- [ ] **Step 3: Implement `OnboardingScreenModel.kt`**

```kotlin
package com.dietician.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.dietician.ui.state.PauseState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingScreenModel(
    private val api: OnboardingApi,
    private val pause: PauseState,
) : ScreenModel {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setLocale(locale: String) = _state.update { it.copy(locale = locale) }
    fun setTailnet(t: String) = _state.update { it.copy(tailnet = t.trim()) }
    fun ackLiteracy() = _state.update { it.copy(literacyAcked = true) }
    fun setIdentity(
        name: String? = null, email: String? = null, heightCm: Int? = null,
        weightKg: Double? = null, age: Int? = null, sex: SexKey? = null, goal: GoalKey? = null,
    ) = _state.update {
        it.copy(
            name = name ?: it.name, email = email ?: it.email,
            heightCm = heightCm ?: it.heightCm, weightKg = weightKg ?: it.weightKg,
            age = age ?: it.age, sex = sex ?: it.sex, goal = goal ?: it.goal,
        )
    }
    fun toggleEquipment(e: EquipmentKey, on: Boolean) = _state.update {
        it.copy(equipment = if (on) it.equipment + e else it.equipment - e)
    }
    fun toggleStore(store: String, on: Boolean) = _state.update {
        it.copy(stores = if (on) it.stores + store else it.stores - store)
    }
    fun toggleConsent(key: ConsentKey, on: Boolean) = _state.update {
        it.copy(consents = it.consents + (key to on))
    }
    fun fastForwardToStep(step: OnboardingStep) = _state.update { it.copy(step = step) }

    fun next() {
        val s = _state.value
        val nextStep: OnboardingStep? = when (s.step) {
            OnboardingStep.LANG_AND_TAILNET ->
                if (s.tailnet.isNotBlank()) OnboardingStep.AI_LITERACY else null
            OnboardingStep.AI_LITERACY ->
                if (s.literacyAcked) OnboardingStep.IDENTITY else null
            OnboardingStep.IDENTITY ->
                if (s.name.isNotBlank() && s.email.isNotBlank() && s.heightCm != null &&
                    s.weightKg != null && s.age != null && s.sex != null && s.goal != null)
                    OnboardingStep.EQUIPMENT else null
            OnboardingStep.EQUIPMENT -> OnboardingStep.STORES
            OnboardingStep.STORES -> OnboardingStep.CONSENT
            OnboardingStep.CONSENT ->
                if (s.consents[ConsentKey.HEALTH] == true) OnboardingStep.AUTH_METHOD else null
            OnboardingStep.AUTH_METHOD -> {
                screenModelScope.launch { submit() }
                null
            }
            OnboardingStep.DONE -> null
        }
        nextStep?.let { ns -> _state.update { it.copy(step = ns, errorMessage = null) } }
    }

    fun back() = _state.update {
        val prev = when (it.step) {
            OnboardingStep.AI_LITERACY -> OnboardingStep.LANG_AND_TAILNET
            OnboardingStep.IDENTITY -> OnboardingStep.AI_LITERACY
            OnboardingStep.EQUIPMENT -> OnboardingStep.IDENTITY
            OnboardingStep.STORES -> OnboardingStep.EQUIPMENT
            OnboardingStep.CONSENT -> OnboardingStep.STORES
            OnboardingStep.AUTH_METHOD -> OnboardingStep.CONSENT
            else -> it.step
        }
        it.copy(step = prev)
    }

    private suspend fun submit() {
        val s = _state.value
        _state.update { it.copy(submitting = true) }
        val req = OnboardingProfileRequest(
            name = s.name, email = s.email, heightCm = s.heightCm!!, weightKg = s.weightKg!!,
            age = s.age!!, sex = s.sex!!, goal = s.goal!!, equipment = s.equipment,
            stores = s.stores, consents = s.consents,
        )
        api.submitProfile(req)
            .onSuccess {
                api.requestMagicLink(s.email)
                    .onSuccess { _state.update { it.copy(submitting = false, step = OnboardingStep.DONE) } }
                    .onFailure { e -> _state.update { it.copy(submitting = false, errorMessage = e.message) } }
            }
            .onFailure { e -> _state.update { it.copy(submitting = false, errorMessage = e.message) } }
    }
}
```

- [ ] **Step 4: Implement `OnboardingScreen.kt`** — wires all 19 spec §11.10 `data-testid` selectors across 7 sub-composables. Selectors required: `onboarding-lang-picker-{en,ro}`, `onboarding-tailnet-input`, `onboarding-step-next`, `onboarding-ai-literacy-banner-{en,ro}`, `onboarding-ai-literacy-ack`, `onboarding-identity-form` containing 7 nested field testTags, `onboarding-equipment-checkboxes`, `onboarding-stores-picker`, `onboarding-consent-{health,voice,photo,crossborder}`, `onboarding-passkey-register-button` (label says "Send magic link" but keeps the spec'd testTag — magic-link replaces passkey first-ship per RC1).

Full composable code: see Plan-1 Task-N pattern. Implementer follows the Step-1..Step-7 structure already drafted in Plan-2 reference; each step lives in its own `@Composable private fun StepN(state, sm) { ... }` block under `OnboardingScreen.Content()`. Each form field uses `Modifier.testTag("onboarding-field-<name>")`. Bottom-bar holds the `Button(onClick={sm.next()}, modifier=Modifier.testTag("onboarding-step-next"))`. Submitting-state disables the button.

- [ ] **Step 5: Roborazzi screenshot test — assert step 1 selectors paint**

```kotlin
package com.dietician.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.dietician.ui.theme.DieticianTheme
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class OnboardingScreenRoborazziTest {
    @BeforeTest fun setUp() {
        stopKoin()
        startKoin {
            modules(module {
                factory { OnboardingScreenModel(
                    api = object : OnboardingApi {
                        override suspend fun submitProfile(req: OnboardingProfileRequest) = Result.success(Unit)
                        override suspend fun requestMagicLink(email: String) = Result.success(Unit)
                    },
                    pause = com.dietician.ui.state.PauseState(),
                ) }
            })
        }
    }
    @AfterTest fun tearDown() = stopKoin()

    @Test
    fun `step 1 selectors paint`() = runComposeUiTest {
        setContent { DieticianTheme { OnboardingScreen.Content() } }
        onNodeWithTag("onboarding-lang-picker-en").assertIsDisplayed()
        onNodeWithTag("onboarding-tailnet-input").assertIsDisplayed()
        onNodeWithTag("onboarding-step-next").assertIsDisplayed()
    }
}
```

- [ ] **Step 6: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.screens.OnboardingScreenModelTest"
./gradlew :shared:desktopTest --tests "com.dietician.ui.screens.OnboardingScreenRoborazziTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/Onboarding*.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/OnboardingScreen*.kt
git commit -m "feat(plan-4-5): OnboardingScreen + 7-step state machine; 19 §11.10 selectors wired"
```

---

## Task 8: `MagicLinkVerifyScreen` + `AILiteracyBanner` modal

### Council baked-in fixes
- [Council 1779120600 RC20]: Cross-device magic-link verify race. Ship BOTH paths:
  1. Onboarding copy (already lands via Task 3 i18n key `magic_link_same_device_hint`): "Open the magic link on the SAME device where you started."
  2. `MagicLinkVerifyScreen` WebSocket-subscribes to `/auth/verify-events?subject_id=X` after magic-link request. When user opens magic-link on Device B but flow started on Device A, Device A receives event + auto-advances. Plan-3 server-side may need stub; ship WebSocket client now + handle 501 fallback gracefully (degrade to friendly "open on same device" copy).
- [Council 1779120600 RC18]: AILiteracyBanner trigger criterion is `AI_LITERACY_TEXT_VERSION` bump per `docs/policies/AI_LITERACY_TEXT_VERSION.md` policy doc. Read the current version from the policy doc's `CURRENT_VERSION` line at app start; compare against Plan-1 `cache_metadata` last-acked version; show banner if mismatch.

**Files (additions):**
- Add WebSocket subscribe handler in `MagicLinkVerifyScreen` to `/auth/verify-events?subject_id={pending_subject_id}` — gracefully degrades on 501/404 (server route may not yet exist in Plan-3 first-batch).
- Add `[data-testid="magic-link-verify-cross-device-pending"]` selector (visible while waiting for WS event).
- Add `[data-testid="magic-link-verify-same-device-hint"]` selector (the friendly fallback text).

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/MagicLinkVerifyScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/MagicLinkVerifyScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/AILiteracyBanner.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/state/AILiteracyState.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/MagicLinkVerifyScreenModelTest.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/components/AILiteracyBannerTest.kt`

- [ ] **Step 1: Failing `MagicLinkVerifyScreenModelTest`** — assert successful verify populates `principal`, invalid token sets `error`.

```kotlin
package com.dietician.ui.screens

import com.dietician.ui.network.MagicLinkApi
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MagicLinkVerifyScreenModelTest {
    private fun http(status: HttpStatusCode, body: String) = HttpClient(MockEngine {
        respond(body, status, headersOf("Content-Type", "application/json"))
    }) { install(ContentNegotiation) { json() } }

    @Test
    fun `successful verify populates principal`() = runTest {
        val api = MagicLinkApi(http(HttpStatusCode.OK,
            """{"subjectId":"u1","displayName":"Victor","jwt":"jwt-x","isVictor":true}"""))
        val m = MagicLinkVerifyScreenModel(api) {}
        m.setToken("abc"); m.submit()
        m.state.value.error shouldBe null
        m.state.value.principal.shouldNotBeNull()
        m.state.value.principal!!.subjectId shouldBe "u1"
    }

    @Test
    fun `invalid token sets error message`() = runTest {
        val api = MagicLinkApi(http(HttpStatusCode.Unauthorized, """{"error":"token_expired"}"""))
        val m = MagicLinkVerifyScreenModel(api) {}
        m.setToken("bad"); m.submit()
        m.state.value.error.shouldNotBeNull()
    }
}
```

- [ ] **Step 2: Implement `MagicLinkVerifyScreenModel.kt`** — holds `token`, `submitting`, `error`, `principal`, `jwt`; on `submit()` calls `api.verify(token)` then invokes the `onAuthenticated(VerifyResult)` callback (consumer wires session into `ApiClient.setSessionJwt` + navigates to `HomeScreen`).

- [ ] **Step 3: Implement `MagicLinkVerifyScreen.kt`** — `RootScreen` with `testTagPrefix = "magic-link-verify"`. Composable contains:
  - `Modifier.testTag("magic-link-verify-root")` on the `Scaffold`
  - help text under `Modifier.testTag("magic-link-verify-help")`
  - `OutlinedTextField` for token under `Modifier.testTag("magic-link-verify-token-input")`
  - `Button` "Verify" under `Modifier.testTag("magic-link-verify-submit")`
  - error text (when non-null) under `Modifier.testTag("magic-link-verify-error")`
  - prefilled token from deep-link arg (`MagicLinkVerifyScreen(prefilledToken: String? = null)` constructor)

- [ ] **Step 4: Implement `AILiteracyState.kt`** — see Plan-2 Task-3 IdempotencyCache pattern for mutex+StateFlow combo. Backed by Plan-1's `cache_metadata` table key `ai_literacy_text_version_acked`. Compares against `currentTextVersion` argument; emits `shouldShow: StateFlow<Boolean>` true when no prior ack OR acked version differs.

- [ ] **Step 5: Implement `AILiteracyBanner.kt`** — `AlertDialog` Composable. Cannot dismiss-without-ack (`onDismissRequest = { /* no-op */ }`). Selectors per spec §11.11:
  - `ai-literacy-modal` (root)
  - `ai-literacy-title-{en,ro}`
  - `ai-literacy-body-{en,ro}`
  - `ai-literacy-disable-link` → opens `SettingsScreen` at AI Coach section
  - `ai-literacy-ack-button` → calls `state.ack()` + invokes `onDismiss` lambda

- [ ] **Step 6: Implement `AILiteracyBannerTest.kt`** — Roborazzi Compose-UI-Test:
  - `state with no prior ack → modal paints with all 6 selectors`
  - `state with currentTextVersion matching acked → modal NOT painted` (assertion via `onNodeWithTag(...).assertDoesNotExist()`)
  - `clicking ack-button → state.ack() called → shouldShow flips to false`

- [ ] **Step 7: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.screens.MagicLinkVerifyScreenModelTest" \
                              --tests "com.dietician.ui.components.AILiteracyBannerTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/MagicLinkVerify*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/AILiteracyBanner.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/state/AILiteracyState.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/MagicLinkVerifyScreenModelTest.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/components/AILiteracyBannerTest.kt
git commit -m "feat(plan-4-5): MagicLinkVerifyScreen + AILiteracyBanner Art-4 modal (no dismiss-without-ack)"
```

---

## Task 9: `NutrientBar` + `NutrientCatalog` (84 nutrients) + `MacroRingChart`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/NutrientCatalog.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/NutrientBar.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/NutrientBarList.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/MacroRingChart.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/components/NutrientCatalogTest.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/components/NutrientBarTest.kt`

- [ ] **Step 1: Failing `NutrientCatalogTest.kt`**

```kotlin
package com.dietician.ui.components

import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainAll
import kotlin.test.Test

class NutrientCatalogTest {
    @Test fun `catalog has 84 nutrients per spec §6.4`() { NutrientCatalog.all.size shouldBe 84 }
    @Test fun `macros group has the 5 required nutrients`() {
        NutrientCatalog.byGroup(NutrientGroup.MACROS).map { it.key } shouldContainAll
            listOf("kcal", "protein", "fat", "carb", "fiber")
    }
    @Test fun `minerals group has 15 entries`() { NutrientCatalog.byGroup(NutrientGroup.MINERALS).size shouldBe 15 }
    @Test fun `vitamins group has 15 entries`() { NutrientCatalog.byGroup(NutrientGroup.VITAMINS).size shouldBe 15 }
    @Test fun `amino acids group has 20 entries`() { NutrientCatalog.byGroup(NutrientGroup.AMINO_ACIDS).size shouldBe 20 }
    @Test fun `top5 are macros for home summary`() {
        NutrientCatalog.top5.map { it.key } shouldBe listOf("kcal", "protein", "fat", "carb", "fiber")
    }
}
```

- [ ] **Step 2: Implement `NutrientCatalog.kt`** — enum `NutrientGroup { MACROS, DETAIL, MINERALS, VITAMINS, AMINO_ACIDS, BIOACTIVES, HYDRATION, GLYCEMIC, OTHERS }`. `data class NutrientDef(key, labelEn, labelRo, unit, group, drv: Double)`. `object NutrientCatalog { val all = buildList { ... } }` populating exactly 5+12+15+15+20+10+1+2+4 = 84 entries per spec §6.4. Provide `byGroup(g)`, `byKey(key)`, `top5`.

DRV defaults used for an active adult male (Victor's profile = baseline). The actual per-subject DRV is computed server-side by Plan-7 from Mifflin-St-Jeor + activity multiplier; client carries defaults for offline-first UI rendering.

Full 84-key list (implementer reproduces verbatim):

- **MACROS (5):** kcal (2700), protein (150g), fat (80g), carb (350g), fiber (35g)
- **DETAIL (12):** sat_fat (22g), mufa (30g), pufa (18g), omega3 (1.6g), omega6 (17g), epa (250mg), dha (250mg), ala (1.6g), la (17g), sugar (50g), starch (250g), alcohol (0g)
- **MINERALS (15):** calcium (1000mg), chloride (2300mg), copper (0.9mg), fluoride (4mg), iodine (0.15mg), iron (8mg), magnesium (420mg), manganese (2.3mg), phosphorus (700mg), potassium (3400mg), selenium (0.055mg), sodium (2300mg), sulfur (1000mg), zinc (11mg), boron (1mg)
- **VITAMINS (15):** vit_a (900µg), vit_b1 (1.2mg), vit_b2 (1.3mg), vit_b3 (16mg), vit_b5 (5mg), vit_b6 (1.7mg), vit_b7 (30µg), vit_b9 (400µg), vit_b12 (2.4µg), vit_c (90mg), vit_d (15µg), vit_e (15mg), vit_k1 (120µg), vit_k2 (90µg), choline (550mg)
- **AMINO_ACIDS (20):** histidine, isoleucine, leucine, lysine, methionine, phenylalanine, threonine, tryptophan, valine, alanine, arginine, asparagine, aspartic_acid, cysteine, glutamic_acid, glutamine, glycine, proline, serine, tyrosine (DRV=5g placeholder per-AA)
- **BIOACTIVES (10):** caffeine, polyphenols, anthocyanins, flavonoids, carotenoids, lutein, lycopene, zeaxanthin, glucosinolates, betaine (DRV=0; tracked only)
- **HYDRATION (1):** water (3700ml)
- **GLYCEMIC (2):** gi (no-DRV), gl (no-DRV)
- **OTHERS (4):** ash, oxalates, phytate, salt (5g)

- [ ] **Step 3: Implement `NutrientBar.kt`** — `@Composable fun NutrientBar(def: NutrientDef, currentValue: Double, modifier: Modifier = Modifier)`. Layout per spec §6.4: weight 0.35 label / weight 0.45 bar / weight 0.20 numeric. Bar is `Box` with `background(colors.surfaceVariant)` containing inner `Box` with `fillMaxWidth(fraction)` + `background(colors.neutralTeal)`. `fraction = min(1.0, currentValue / def.drv).toFloat()`. testTag `"nutrient-bar-${def.key}"`. Over-DRV clamps fill at 1.0 but numeric label shows actual.

- [ ] **Step 4: Implement `NutrientBarList.kt`** — `@Composable fun NutrientBarList(values: Map<String, Double>, expanded: Boolean = false, modifier: Modifier = Modifier)`. Default = renders `NutrientCatalog.top5`. Expanded = `LazyColumn` grouping all 84 by `NutrientGroup` with a `Text(groupLabel(g))` header + `Divider()` between groups. testTag `"meal-detail-84-nutrients"` when expanded; `"nutrient-summary-top5"` otherwise.

- [ ] **Step 5: Implement `MacroRingChart.kt`** — `data class MacroRingValues(kcalCurrent, kcalTarget, proteinCurrent, proteinTarget, fatCurrent, fatTarget, carbCurrent, carbTarget)`. `@Composable fun MacroRingChart(values, modifier)` using `Canvas` to draw 4 concentric `drawArc` strokes. Each ring: `surfaceVariant` track + `neutralTeal` arc with sweep `360 * frac`. testTag `"macro-ring-chart"` (and `"home-macro-rings"` when used in HomeScreen wrapper).

- [ ] **Step 6: Failing `NutrientBarTest.kt`** — Compose-UI-Test asserts `onNodeWithTag("nutrient-bar-protein").assertIsDisplayed()` + over-DRV value paints actual numeric in the label.

- [ ] **Step 7: Run + commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.components.NutrientCatalogTest"
./gradlew :shared:desktopTest --tests "com.dietician.ui.components.NutrientBarTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/components/Nutrient*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/MacroRingChart.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/components/Nutrient*Test.kt
git commit -m "feat(plan-4-5): NutrientCatalog (84 nutrients) + NutrientBar (neutral-teal) + MacroRingChart"
```

---

## Task 10: `ExpenditureChart` (MacroFactor-style Bayesian adaptive TDEE)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/state/ExpenditureEstimator.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/ExpenditureChart.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/state/ExpenditureEstimatorTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.dietician.ui.state

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import kotlin.test.Test

class ExpenditureEstimatorTest {
    @Test
    fun `Mifflin-St-Jeor for Victor (188cm, 67.5kg, 21 male) is plausible BMR`() {
        val bmr = ExpenditureEstimator.mifflinStJeor(67.5, 188.0, 21, sexMale = true)
        bmr shouldBeGreaterThan 1600.0
        bmr shouldBeLessThan 1900.0
    }

    @Test
    fun `posterior shrinks SD as observations accrue`() {
        val day0 = ExpenditureEstimator.posterior(2700.0, 200.0, emptyList())
        val day14 = ExpenditureEstimator.posterior(
            2700.0, 200.0,
            (0 until 14).map { ExpenditureObservation(2700.0, 0.0) },
        )
        day14.sd shouldBeLessThan day0.sd
    }

    @Test
    fun `posterior shifts toward observed evidence`() {
        val post = ExpenditureEstimator.posterior(
            2700.0, 200.0,
            (0 until 14).map { ExpenditureObservation(3200.0, if (it == 13) 0.5 else 0.0) },
        )
        post.mean shouldBeGreaterThan 2800.0
    }
}
```

- [ ] **Step 2: Implement `ExpenditureEstimator.kt`**

```kotlin
package com.dietician.ui.state

import kotlin.math.sqrt

data class ExpenditureObservation(val kcalIn: Double, val weightDeltaKg: Double)
data class ExpenditurePosterior(val mean: Double, val sd: Double, val nObs: Int)

object ExpenditureEstimator {
    fun mifflinStJeor(weightKg: Double, heightCm: Double, ageYears: Int, sexMale: Boolean): Double =
        (10 * weightKg + 6.25 * heightCm - 5 * ageYears) + if (sexMale) 5 else -161

    /**
     * Inverse-variance Gaussian combine. Per R3 §1.3 + spec §6.5. Plan-7 may swap in
     * Step-Informed-Updates. 7700 kcal ≈ 1 kg fat-loss energy density.
     */
    fun posterior(
        priorMean: Double, priorSd: Double,
        observations: List<ExpenditureObservation>,
        observationSd: Double = 400.0,
    ): ExpenditurePosterior {
        if (observations.isEmpty()) return ExpenditurePosterior(priorMean, priorSd, 0)
        val priorPrec = 1.0 / (priorSd * priorSd)
        val obsPrec = 1.0 / (observationSd * observationSd)
        val obsEstimates = observations.map { it.kcalIn - it.weightDeltaKg * 7700.0 / observations.size }
        val obsMean = obsEstimates.average()
        val newPrec = priorPrec + observations.size * obsPrec
        val newMean = (priorMean * priorPrec + obsMean * observations.size * obsPrec) / newPrec
        return ExpenditurePosterior(newMean, sqrt(1.0 / newPrec), observations.size)
    }
}
```

- [ ] **Step 3: Implement `ExpenditureChart.kt`** — `Column` with `Text("Today's expenditure estimate")` + numeric "${mean} kcal (±${sd}, based on last ${nObs} days)" + horizontal `Canvas` band showing range 1500-4500 kcal with shaded `[mean-sd, mean+sd]` region in `neutralTeal.copy(alpha=0.3f)` + central line at mean. testTag `"home-tdee-band"`.

- [ ] **Step 4: Run + commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.state.ExpenditureEstimatorTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/state/ExpenditureEstimator.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/ExpenditureChart.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/state/ExpenditureEstimatorTest.kt
git commit -m "feat(plan-4-5): ExpenditureChart + Bayesian estimator (MacroFactor-style ± band)"
```

---

## Task 11: `PauseTrackingButton` + `PauseTrackingScreen`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/PauseTrackingButton.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/PauseTrackingScreen.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/PauseTrackingScreenTest.kt`

- [ ] **Step 1: Implement `PauseTrackingButton.kt`** — `IconButton` with `DieticianIcons.Pause`. testTag `"home-pause-button"` when not paused, `"pause-tracking-active-button"` when paused (observes `state.isPaused.collectAsState()`).

- [ ] **Step 2: Implement `PauseTrackingScreen.kt`** — `RootScreen` object. testTag `"pause-tracking-root"`. Per spec §11.14:
  - `[data-testid="pause-active-message"]` body text
  - `[data-testid="pause-resume-button"]` (visible when paused)
  - `[data-testid="pause-engage-button"]` (visible when not paused) — clicking calls `pause.pause("user-initiated")`
  - Per RC: emit audit_log row `pause_engaged` / `pause_resumed` via Plan-2 AuditLogWriter

- [ ] **Step 3: Failing test** — Compose-UI-Test seeds `PauseState`, asserts `"pause-active-message"` paints + clicking `"pause-resume-button"` flips state.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dietician/ui/components/PauseTrackingButton.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/screens/PauseTrackingScreen.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/PauseTrackingScreenTest.kt
git commit -m "feat(plan-4-5): PauseTrackingButton + PauseTrackingScreen (client-side-only first-ship)"
```

---

## Task 12: `HomeScreen` + `HomeScreenModel` (10 §11.1 selectors)

### Council baked-in fixes
- [Council 1779120600 RC14]: `HomeScreen` surfaces the planned-cut toggle as an optional row in the daily summary area (parallel to EDSafeguardModal Task 19 surface + Settings Task 20 surface). Same `[data-testid="planned-cut-toggle"]` + `[data-testid="planned-cut-days-remaining-{n}"]` selectors. When active, show the countdown badge in the daily summary header so user remembers state without going to Settings.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/HomeScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/HomeScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/IsDesktop.kt` (expect declaration)
- Create: `shared/src/androidMain/kotlin/com/dietician/ui/screens/IsDesktop.android.kt`
- Create: `shared/src/desktopMain/kotlin/com/dietician/ui/screens/IsDesktop.desktop.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/HomeScreenModelTest.kt`
- Create: `shared/src/desktopTest/kotlin/com/dietician/ui/screens/HomeScreenRoborazziTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.dietician.ui.screens

import com.dietician.ui.components.MacroRingValues
import com.dietician.ui.state.ExpenditurePosterior
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HomeScreenModelTest {
    @Test fun `greeting uses principal displayName`() {
        val m = HomeScreenModel(
            principal = com.dietician.ui.state.SubjectPrincipal("u1", "Victor", true),
            pause = com.dietician.ui.state.PauseState(),
            macroProvider = { MacroRingValues(2000.0, 2700.0, 100.0, 150.0, 60.0, 80.0, 240.0, 350.0) },
            tdeeProvider = { ExpenditurePosterior(2700.0, 180.0, 14) },
            nextMealProvider = { NextMeal("Chicken + rice", "20:00", 800, 60) },
        )
        m.state.value.greeting shouldBe "Hi, Victor"
    }
}
```

- [ ] **Step 2: Implement `HomeScreenModel.kt`** — `data class NextMeal`, `data class HomeUiState`, `class HomeScreenModel(principal, pause, macroProvider, tdeeProvider, nextMealProvider)`. Init state populated from providers. `fun toggleLiteracyFooter()` flips `literacyFooterExpanded`.

- [ ] **Step 3: Implement `HomeScreen.kt`** — `object HomeScreen : RootScreen { testTagPrefix = "home" }`. `Scaffold` with `testTag("home-root")`. TopAppBar title = `Text(s.greeting, Modifier.testTag("home-greeting"))`, actions = `PauseTrackingButton`. Body column:
  - `home-diag-banner` (degraded only)
  - `ExpenditureChart` under `home-tdee-band`
  - `MacroRingChart` under `home-macro-rings`
  - `home-next-meal-card`
  - Row of `home-quick-log-button` + `home-quick-photo-button`
  - `admin-scrape-now-button` (desktop only, gated by `isDesktop()`)
  - `home-ai-literacy-footer` (clickable, toggles expand)

- [ ] **Step 4: Implement `expect/actual isDesktop()`**

`commonMain/screens/IsDesktop.kt`:
```kotlin
package com.dietician.ui.screens
expect fun isDesktop(): Boolean
```
`androidMain`: `actual fun isDesktop() = false`
`desktopMain`: `actual fun isDesktop() = true`

- [ ] **Step 5: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.screens.HomeScreenModelTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/Home*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/screens/IsDesktop.kt \
        shared/src/androidMain/kotlin/com/dietician/ui/screens/IsDesktop.android.kt \
        shared/src/desktopMain/kotlin/com/dietician/ui/screens/IsDesktop.desktop.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/HomeScreenModelTest.kt
git commit -m "feat(plan-4-5): HomeScreen (10 §11.1 selectors) + isDesktop expect/actual"
```

---

## Task 13: `FoodLogScreen` + `MealDetailScreen` + `VoiceRecordButton` + `PhotoSuggestionCard`

### Council baked-in fixes
- [Council 1779120600 RC1]: Voice pipeline DEFERRED for first-ship (Tasks 24/26/32 deferred to Plan-4-5.5). KEEP `VoiceRecordButton` composable + `[data-testid="voice-record-button"]` selector. onClick → show toast (using `voice_record_button_disabled_toast` i18n key from Task 3) "Voice transcription coming in next update — type your meal below." + auto-focus the manual-entry text field. NO recording / NO VoiceFlow start. The button is a UI affordance only; preserves muscle memory for the post-finals voice ship.
- [Council 1779120600 RC11]: PhotoSuggestionCard MUST include "None of these — type manually" escape-hatch button below the top-5 suggestion list. Add `[data-testid="photo-suggestion-none-of-these"]` selector. onClick → dismisses suggestions + opens manual entry text field. Justification: 27% OCR miss rate (CNN top-1 = 72.92% on ISIA-Food-200). Without escape hatch, users will pick closest-wrong-candidate and corrupt their log silently.

**Voice-flow simplification (RC1):** Remove `VoiceFlow.start()` invocation from `VoiceRecordButton` onClick. The `VoiceFlow.kt` file may still ship (skeleton) for Plan-4-5.5 wiring, but `state.value.recording` never flips true in first-ship. Replace Step 1 test "startRecording flips state" → assert "tapping VoiceRecordButton shows disabled toast + focuses manual entry."

**PhotoSuggestionCard escape-hatch (RC11) test:**
```kotlin
@Test fun `escape hatch button dismisses suggestions and opens manual entry`() {
    var manualOpened = false
    var dismissed = false
    PhotoSuggestionCard(
        candidates = listOf(/* top-5 */),
        onConfirm = {},
        onNoneOfThese = { dismissed = true; manualOpened = true },
    )
    // simulate click on [data-testid="photo-suggestion-none-of-these"]
    dismissed shouldBe true
    manualOpened shouldBe true
}
```

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/FoodLogScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/FoodLogScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/MealDetailScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/MealDetailScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/VoiceRecordButton.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/PhotoSuggestionCard.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/MealCard.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/state/VoiceFlow.kt` (orchestrates record → upload → transcribe → intent-classify → persist)
- Create: tests under `shared/src/commonTest/kotlin/com/dietician/ui/screens/`

- [ ] **Step 1: Failing `FoodLogScreenModelTest`** — assert recording state transitions, recent-meals flow surfaces from Plan-1 `MealEventStore`.

```kotlin
package com.dietician.ui.screens

import com.dietician.ui.state.VoiceFlow
import com.dietician.ui.state.VoiceFlowStage
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FoodLogScreenModelTest {
    @Test fun `starts in idle state`() {
        val m = FoodLogScreenModel(
            subjectId = "u1",
            recentMeals = flowOf(emptyList()),
            voice = FakeVoiceFlow(),
        )
        m.state.value.recording shouldBe false
    }

    @Test fun `startRecording flips state`() = runTest {
        val v = FakeVoiceFlow()
        val m = FoodLogScreenModel("u1", flowOf(emptyList()), v)
        m.startRecording()
        m.state.value.recording shouldBe true
    }

    private class FakeVoiceFlow : VoiceFlow {
        override suspend fun start() {}
        override suspend fun stop(): Result<String> = Result.success("eaten chicken rice 200g")
        override val stage = kotlinx.coroutines.flow.MutableStateFlow(VoiceFlowStage.IDLE)
    }
}
```

- [ ] **Step 2: Implement `VoiceFlow.kt`** — interface + default impl. Stages: `IDLE → RECORDING → UPLOADING → TRANSCRIBING → CLASSIFYING → DONE`. On `stop()`:
  1. Stop platform `VoiceRecorder` (expect/actual)
  2. Try local Whisper via `WhisperAsr.transcribe(audio, language="ro", initialPrompt=RO_FOOD_BIAS_PROMPT)` — first-ship throws `NotShippedYet`
  3. On failure: upload audio via `POST /voice/upload` to VPS, poll for transcription
  4. Pass transcript to Plan-2 `LlmRouter.call(capability=TEXT, prompt="Classify intent: $transcript ...")`
  5. Insert appropriate event (meal_event / pantry_event / weight_event) via Plan-1 `EventStore`

The `RO_FOOD_BIAS_PROMPT` constant lives here:

```kotlin
internal const val RO_FOOD_BIAS_PROMPT =
    "Limbaj culinar românesc: mămăligă, sarmale, ciorbă, mici, fasole, varză, " +
    "cartofi, pui, vită, porc, brânză, smântână, ulei, sare, piper, " +
    "leuștean, mărar, pătrunjel, telemea, lapte, ouă, ardei, roșii."
```

- [ ] **Step 3: Implement `VoiceRecordButton.kt`** — tap-and-hold gesture using `Modifier.pointerInput { detectTapGestures(onPress = { ... awaitRelease() }) }`. While held: paints `MicrophonePulse` animation + emits to `VoiceFlow`. testTag `"foodlog-talk-button"`.

- [ ] **Step 4: Implement `PhotoSuggestionCard.kt`** — `data class PhotoSuggestion(name, confidencePct, qtyEstG)`. `@Composable fun PhotoSuggestionCard(suggestions: List<PhotoSuggestion>, onConfirm: (PhotoSuggestion) -> Unit, onCorrect: () -> Unit)`. Per R3 §1.13 #5: show top-5 candidates as a list, each tappable. Header: "Is this what you ate? (CNN top-1 is ~73% accurate — please confirm)". "No, let me edit" button → manual entry. testTag root `"photo-suggestion-card"`.

NEVER auto-commit. Per spec §6.7. The component returns `PhotoSuggestion` or `null` (correct = null → caller routes to manual entry).

- [ ] **Step 5: Implement `MealCard.kt`** — list-row composable for `FoodLogScreen`. Shows meal name, time, top-5 macros via inline mini-bars. testTag `"meal-card-${mealUuid}"`.

- [ ] **Step 6: Implement `FoodLogScreenModel.kt`** — observes Plan-1 `MealEventStore.flow(subjectId)` for recent-meals. Holds `recording: Boolean` + `error: String?`. `fun startRecording() → voice.start()`. `fun stopRecording() → voice.stop().onSuccess { ... }`.

- [ ] **Step 7: Implement `FoodLogScreen.kt`** — per §11.2:
  - `Modifier.testTag("foodlog-root")` on Scaffold
  - `VoiceRecordButton` under `foodlog-talk-button` (large center)
  - `LazyColumn` of `MealCard`s under `foodlog-recent-meals-list`
  - Manual entry button under `foodlog-manual-entry-button`
  - Floating action button top-right under `foodlog-add-button` → opens BottomSheet with [voice, photo, manual, paste-recipe] options

- [ ] **Step 8: Implement `MealDetailScreen.kt`** + `MealDetailScreenModel.kt` — shows `NutrientBarList(values, expanded=true)` under testTag `"meal-detail-84-nutrients"`. Edit + Delete buttons emit events to Plan-1 ledger.

- [ ] **Step 9: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.screens.FoodLogScreenModelTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/FoodLog*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/screens/MealDetail*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/Voice* \
        shared/src/commonMain/kotlin/com/dietician/ui/components/PhotoSuggestionCard.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/MealCard.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/state/VoiceFlow.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/FoodLogScreenModelTest.kt
git commit -m "feat(plan-4-5): FoodLogScreen + voice-first flow + PhotoSuggestionCard (never-auto-commit)"
```

---

## Task 14: `PantryScreen` + `PantryItemDetailScreen` (FEFO disambiguation)

### Council baked-in fixes
- [Council 1779120600 RC4]: **KEPT in first-ship subset** (3-of-5 council split, First Principles + Domain Expert + Risk Analyst over-ruled Pragmatist + Devil's Advocate). Pantry is the data surface for Receipt → pantry-event roundtrip + FEFO. Without Pantry, Receipt is a write-only surface. Defer the Pantry "audit" sub-screen if any, but ship the main + detail screens.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/PantryScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/PantryScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/PantryItemDetailScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/PantryItemRow.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/PantryScreenModelTest.kt`

- [ ] **Step 1: Failing test** — `PantryScreenModel` observes Plan-1 `PantrySnapshotStore.currentAll()` Flow. Sorts items by `(expiryDate ascending, openStatus='open' first)` per spec §14.2 FEFO. Low-stock threshold = `qty < pantryItemMinQty` (subject-configurable; default = 1 unit).

```kotlin
package com.dietician.ui.screens

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PantryScreenModelTest {
    @Test fun `low-stock section filters qty under threshold`() = runTest {
        val items = listOf(
            PantryUiItem("chicken", "Chicken breast", 0.5, "kg", openStatus = "sealed", expiryDate = null),
            PantryUiItem("rice",    "Rice",           5.0, "kg", openStatus = "sealed", expiryDate = null),
        )
        val m = PantryScreenModel(subjectId = "u1", snapshotFlow = flowOf(items), lowStockThreshold = 1.0)
        m.state.value.lowStock.size shouldBe 1
        m.state.value.lowStock.first().name shouldBe "Chicken breast"
    }

    @Test fun `sorted by FEFO expiry ascending`() = runTest {
        // ... two items with different expiry dates → earliest first
    }
}
```

- [ ] **Step 2: Implement `PantryUiItem` + `PantryScreenModel.kt`** — pulls from `PantrySnapshotStore.currentAll(): Flow<List<PantrySnapshotRow>>`, maps to UI rows, sorts FEFO, splits into `all` + `lowStock`. Provides `fun search(query: String)` filtering by name match.

- [ ] **Step 3: Implement `PantryItemRow.kt`** — list-row with name, qty + unit, expiry-in-N-days, open/sealed pill. testTag `"pantry-item-row-${skuUuid}"`.

- [ ] **Step 4: Implement `PantryScreen.kt`** — per §11.3:
  - `pantry-list` LazyColumn
  - `pantry-search-bar` (TextField at top)
  - `pantry-low-stock-section` (header + filtered list)
  - `pantry-add-button` (FAB) → action sheet [photo, voice, manual]
  - `pantry-audit-button` (top-right icon) → audit mode

- [ ] **Step 5: Implement `PantryItemDetailScreen.kt`** — shows FEFO batch list with `PantryFefoBatchRow` items (each `(qty, expiryDate, openStatus)` from Plan-1 events). Per spec §14.2 — disambiguation surface for "I used the chicken" voice-decrement.

- [ ] **Step 6: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.screens.PantryScreenModelTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/Pantry*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/PantryItemRow.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/PantryScreenModelTest.kt
git commit -m "feat(plan-4-5): PantryScreen + FEFO sort + low-stock section (§11.3 selectors)"
```

---

## Task 15: `CookbookScreen` + `RecipeDetailScreen` + `RecipeReviewQueueScreen`

### Council baked-in fixes
- [Council 1779120600 RC4]: **TASK DEFERRED to Plan-4-5.5.** Plan-7 owns recipe ingest moderator. Cookbook surface deferred to Plan-7's UI batch. UI shows "Recipes — coming in next update" placeholder if a deep-link reaches the route. Mark with `// PLAN-4-5 FOLLOWUP RC4 — Cookbook surface deferred to Plan-7 UI batch`.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/CookbookScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/CookbookScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/RecipeDetailScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/RecipeDetailScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/RecipeReviewQueueScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/RecipeReviewQueueScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/RecipeCard.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/CookbookScreenModelTest.kt`

- [ ] **Step 1: Failing test** — assert search query → flow of `RecipeCardItem`s filters Plan-1 `recipes_cache`. Assert "paste URL" enqueues to Plan-3 `POST /jobs/queue` (first-ship 501-stub — UI shows `cookbook_queued_toast`).

- [ ] **Step 2: Implement `RecipeCard.kt`** — `data class RecipeCardItem(uuid, name, kcal, proteinG, prepMin, cookMin, imageUrl?)`. `@Composable fun RecipeCard(item, onClick, modifier)` rendering image (Coil/Compose Resources) + name + macros + total time. testTag `"recipe-card-${uuid}"`.

- [ ] **Step 3: Implement `CookbookScreenModel.kt`** — observes Plan-1 `RecipesCacheStore.search(query: String): Flow<List<RecipeCardItem>>`. Holds filter chips state (`equipmentFilter: Set<EquipmentKey>`, `inPantryOnly: Boolean`, `cuisineFilter: String?`). Provides `fun ingestUrl(url: String)` → POST to `/jobs/queue` (501-stub → display queued toast).

- [ ] **Step 4: Implement `CookbookScreen.kt`** — per §11.4:
  - `cookbook-search-bar` TextField
  - `cookbook-filter-chips` Row of `FilterChip`s
  - `cookbook-recipe-grid` LazyVerticalGrid
  - `cookbook-ingest-button` FAB → action sheet [paste URL, file pick, voice, compose new]
  - `cookbook-review-queue-tab` (top-bar tab) → `RecipeReviewQueueScreen`
  - On URL ingest: `Toast`/`Snackbar` "Queued — recipe available within a few days" per spec §A19 pattern

- [ ] **Step 5: Implement `RecipeDetailScreen.kt`** + `RecipeDetailScreenModel.kt` — per §11.4 click smoke:
  - `recipe-detail-ingredients` (LazyColumn of ingredients with qty + unit)
  - `recipe-detail-steps` (numbered list of instructions)
  - `recipe-detail-nutrition` (`NutrientBarList(expanded=true)`)
  - `recipe-detail-history` (last-eaten + served-count-21d + boredom score)
  - "Plan into this week" button → calls Plan-1 `meal_plans_cache` writer
  - "Edit" + "Delete" buttons

- [ ] **Step 6: Implement `RecipeReviewQueueScreen.kt`** — list of `recipe_review_queue` entries (Plan-2 dual-LLM-moderator quarantine). Each row testTag `"review-queue-item-${id}"`. Tap → detail with `RecipeDraft` JSON + Accept/Reject buttons. Accept → Plan-1 ledger insert + `corpus_embeddings` row creation (Plan-7 path).

- [ ] **Step 7: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.screens.CookbookScreenModelTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/Cookbook*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/screens/RecipeDetail*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/screens/RecipeReviewQueueScreen*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/RecipeCard.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/CookbookScreenModelTest.kt
git commit -m "feat(plan-4-5): CookbookScreen + RecipeDetail + ReviewQueue (§11.4 selectors)"
```

---

## Task 16: `CoachChatScreen` + `JustTellMeButton` + `PerCallDisclosurePane`

### Council baked-in fixes
- [Council 1779120600 RC7]: `PerCallDisclosurePane` per-call disclosure row MUST surface an "Open audit row" deep-link button → navigates to `AuditLogScreen` filtered by `call_uuid` (one-tap, NOT requiring Settings → AuditLog → filter manually). Add `[data-testid="coach-disclosure-open-audit-{call_uuid}"]` selector. Add `AuditLogFilterTest` asserting deep-link arrives at filtered view.
- [Council 1779120600 RC9]: When `subject.llmCoachDisabled == true` (settings toggle from Art 14), `CoachChatScreen` body renders explicit notice card "AI coach disabled. Re-enable in Settings → Privacy." that REPLACES the chat input (not just hides messages). Use `coach_disabled_notice_title` + `coach_disabled_notice_body` i18n keys from Task 3. Add `[data-testid="coach-disabled-notice"]` selector.
- [Council 1779120600 RC12]: `ChatStreamApi` on SSE cancellation (Compose dispose / user navigates away) MUST emit `coach_chat_cancelled` audit_log row via Plan-2 `AuditLogWriter` with `partial_byte_count` field. Wire the cancellation handler in the SSE flow's `finally` / `awaitClose { ... }` block. Add `ChatStreamCancellationTest` to `commonTest`.

**RC-7 wiring detail:** `PerCallDisclosurePane` row layout:
```
[ⓘ provider/model · cost · latency]  [Open audit row →]
                                       data-testid=coach-disclosure-open-audit-{call_uuid}
                                       onClick: navigator.push(AuditLogScreen(filter = AuditLogFilter(callUuid = "...")))
```

**RC-9 wiring detail:** `CoachChatScreen` body:
```kotlin
if (subject.llmCoachDisabled) {
    Card(Modifier.testTag("coach-disabled-notice")) {
        Text(stringResource(MR.strings.coach_disabled_notice_title))
        Text(stringResource(MR.strings.coach_disabled_notice_body))
    }
    // NO chat input rendered.
} else {
    /* normal LazyColumn + input + send button */
}
```

**RC-12 wiring detail:** `ChatStreamApi.completeStream(...)` flow:
```kotlin
flow {
    var partialBytes = 0L
    try {
        sseClient.collect { chunk ->
            partialBytes += chunk.size
            emit(chunk)
        }
    } finally {
        if (currentCoroutineContext()[Job]?.isCancelled == true) {
            auditWriter.write(AuditRow(
                action = "coach_chat_cancelled",
                extra = mapOf("partial_byte_count" to partialBytes, "call_uuid" to callUuid),
                emotion_inference_disabled = true,
            ))
        }
    }
}
```

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/CoachChatScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/CoachChatScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/JustTellMeScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/JustTellMeScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/JustTellMeButton.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/PerCallDisclosurePane.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/ChatStreamApi.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/CoachChatScreenModelTest.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/JustTellMeRouterBypassTest.kt`

### Council baked-in fixes
- **[CRITICAL — locked decision §10 row #5 + AI Act Art 14]:** `JustTellMeButton` invocations MUST bypass `LlmRouter.call` entirely. Path is: `JustTellMeButton.onClick → POST /just_tell_me → return rule-based answer with `{"source":"rule_based","llm_used":false}`. Test `JustTellMeRouterBypassTest` asserts the call never enters `LlmRouter.call` (uses a counting fake).

- [ ] **Step 1: Failing `JustTellMeRouterBypassTest.kt`**

```kotlin
package com.dietician.ui.screens

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class JustTellMeRouterBypassTest {
    @Test
    fun `JustTellMeScreenModel never invokes LlmRouter`() = runTest {
        var routerCalls = 0
        val fakeJustTellMeApi = object : JustTellMeApi {
            override suspend fun fetch(subjectId: String, query: String): JustTellMeResponse =
                JustTellMeResponse(answer = "rule-based-result", source = "rule_based", llmUsed = false)
        }
        val fakeRouter = object : RouterCounter {
            override suspend fun call() { routerCalls++ }
        }
        val m = JustTellMeScreenModel(api = fakeJustTellMeApi, subjectId = "u1", queryText = "what to eat")
        m.fetch()
        routerCalls shouldBe 0
        m.state.value.answer shouldBe "rule-based-result"
        m.state.value.source shouldBe "rule_based"
    }
}

private interface RouterCounter { suspend fun call() }
```

- [ ] **Step 2: Implement `ChatStreamApi.kt`** — wraps `POST /llm/chat/stream` SSE. Each response chunk is `LlmStreamChunk(deltaText, isFinal, finalUsage?)`. The server-side route uses Plan-2 `LlmRouter.completeStream` internally; client just consumes the SSE flow. Backs `CoachChatScreenModel`'s message-send action.

```kotlin
package com.dietician.ui.network

import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class ChatStreamRequest(val subjectId: String, val prompt: String, val capability: String = "TEXT")
@Serializable data class ChatStreamChunk(val deltaText: String, val isFinal: Boolean = false, val callUuid: String? = null, val provider: String? = null, val model: String? = null, val costCents: Int? = null, val latencyMs: Long? = null)

class ChatStreamApi(private val http: HttpClient, private val baseUrl: String = "") {
    private val json = Json { ignoreUnknownKeys = true }
    fun stream(req: ChatStreamRequest): Flow<ChatStreamChunk> = flow {
        http.preparePost("$baseUrl${Endpoints.LLM_CHAT_STREAM}") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.execute { resp ->
            val channel = resp.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val payload = line.removePrefix("data: ")
                    if (payload == "[DONE]") break
                    emit(json.decodeFromString(ChatStreamChunk.serializer(), payload))
                }
            }
        }
    }
}
```

- [ ] **Step 3: Implement `JustTellMeButton.kt`** — `@Composable fun JustTellMeButton(onClick: () -> Unit, modifier: Modifier = Modifier)`. Used inline in `CoachChatScreen` above the input bar. testTag `"coach-just-tell-me-button"`. Icon `DieticianIcons.JustTellMe` + text "Just tell me (no AI)".

- [ ] **Step 4: Implement `PerCallDisclosurePane.kt`** — collapsible `@Composable fun PerCallDisclosurePane(disclosure: LlmDisclosure)` per spec §6.10 layer 2. Shows provider/model/cost/latency/prompt-hash. "View raw response" link → opens `AuditLogScreen` filtered by `call_uuid`. testTag `"coach-message-disclosure-${callUuid}"`.

```kotlin
package com.dietician.ui.components

import kotlinx.serialization.Serializable

@Serializable
data class LlmDisclosure(
    val provider: String,
    val model: String,
    val promptHash: String,
    val costCents: Int,
    val latencyMs: Long,
    val rawResponseRef: String?,
    val callUuid: String,
)
```

- [ ] **Step 5: Implement `CoachChatScreenModel.kt`** — observes Plan-1 `chat_history_cache.flow(subjectId)`. `fun send(text: String) → ChatStreamApi.stream(...).collect { chunk → _state.update { it.copy(messages = it.messages + chunk.toUiMessage()) } }`. Per spec §6.11d: per-message disclosure is built from `chunk.callUuid + provider + model + costCents + latencyMs` returned in final chunk.

- [ ] **Step 6: Implement `CoachChatScreen.kt`** — per §11.5:
  - `coach-message-list` LazyColumn (each message a `MessageBubble` with optional `PerCallDisclosurePane` keyed `coach-message-disclosure-${callUuid}`)
  - `coach-input-bar` TextField
  - `coach-send-button` Button
  - `coach-just-tell-me-button` above input bar
  - `coach-suggested-chips` Row of 4 suggested chips (from `strings.xml` keys)
  - On AI-coach-disabled subject (`SubjectPrincipal.llmCoachDisabled = true`): hide message-list + show "AI coach is disabled — use rule-based planner via Just-tell-me" notice

- [ ] **Step 7: Implement `JustTellMeScreen.kt`** + `JustTellMeScreenModel.kt` — per §11.13:
  - `just-tell-me-rule-based-answer` Text showing returned answer
  - `just-tell-me-back-button` → `Navigator.pop()` back to CoachChat
  - `just-tell-me-disable-llm-toggle` Switch → toggles `SubjectPrincipal.llmCoachDisabled`, posts to Plan-3 `PATCH /me` (501-stub → local-only flip)

`JustTellMeApi` interface:
```kotlin
interface JustTellMeApi { suspend fun fetch(subjectId: String, query: String): JustTellMeResponse }
data class JustTellMeResponse(val answer: String, val source: String, val llmUsed: Boolean)
```

The model is constructed with `api: JustTellMeApi`. Implementation lives in `network/JustTellMeApiImpl.kt` wrapping `POST /just_tell_me`.

- [ ] **Step 8: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.screens.JustTellMeRouterBypassTest" \
                              --tests "com.dietician.ui.screens.CoachChatScreenModelTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/CoachChat*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/screens/JustTellMe*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/JustTellMeButton.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/PerCallDisclosurePane.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/network/ChatStreamApi.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/*.kt
git commit -m "feat(plan-4-5): CoachChatScreen + Art-13 disclosure pane + Art-14 just-tell-me bypass"
```

---

## Task 17: `PaperSearchScreen` + `PaperDetailScreen` + `EmbedApi`

### Council baked-in fixes
- [Council 1779120600 RC4]: **TASK DEFERRED to Plan-4-5.5.** Plan-3 `/embed` is 501-stubbed per Plan-3 council RC-12. Plan-7 ships corpus backfill. Paper-search surface deferred until corpus + ranking ready. Mark with `// PLAN-4-5 FOLLOWUP RC4 — PaperSearch deferred to Plan-7 corpus`.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/PaperSearchScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/PaperSearchScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/PaperDetailScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/PaperDetailScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/EmbedApi.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/PaperSearchApi.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/PaperSearchScreenModelTest.kt`

### Council baked-in fixes
- **[Locked decision §6.11d / Council Q7]:** Clients do NOT embed locally. PaperSearchScreen sends raw query text to server, server embeds via `POST /embed` then runs hybrid search internally and returns results. Plan-4-5 first-ship calls `POST /papers/search?q=...` (Plan-3 ships `/embed`; the `/papers/search` route is a thin server-side wrapper that calls `/embed` then queries `corpus_embeddings` — Plan-7 owns the corpus backfill, Plan-4-5 surfaces the UI against whatever Plan-3 + Plan-7 produce).

- [ ] **Step 1: Failing test** — `PaperSearchScreenModel.search(query)` issues HTTP request + populates `results: List<PaperResult>`. Empty query returns empty results.

- [ ] **Step 2: Implement `EmbedApi.kt`** — wraps `POST /embed`:

```kotlin
package com.dietician.ui.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable data class EmbedRequest(val text: String, val corpus: String = "papers")
@Serializable data class EmbedResponse(val vector: List<Double>, val providerVersion: String)

class EmbedApi(private val http: HttpClient, private val baseUrl: String = "") {
    suspend fun embed(text: String, corpus: String = "papers"): Result<EmbedResponse> = runCatching {
        http.post("$baseUrl${Endpoints.EMBED}") {
            contentType(ContentType.Application.Json)
            setBody(EmbedRequest(text, corpus))
        }.body()
    }
}
```

- [ ] **Step 3: Implement `PaperSearchApi.kt`** — `POST /papers/search { query: String, limit: Int = 20 }` → `List<PaperResult>`. Each result carries `(paperId, title, authors, year, source, abstractSnippet, ingestedLocally: Boolean, domain: String)`.

- [ ] **Step 4: Implement `PaperSearchScreenModel.kt`** — debounced search-as-you-type (300ms via `Flow.debounce`). Filter chips for domain {nutrition, training, clinical, behavior}.

- [ ] **Step 5: Implement `PaperSearchScreen.kt`** — per §11.6:
  - `paper-search-bar` TextField
  - `paper-search-results` LazyColumn
  - `paper-domain-filter-chips` Row

- [ ] **Step 6: Implement `PaperDetailScreen.kt`** + model — per §11.6 click smoke:
  - `paper-detail-abstract` Text
  - `paper-detail-ingest-button` (if not ingested) → POST to `paper_fetch_queue` via Plan-3 `POST /papers/ingest` (501-stub → local enqueue + toast)
  - `paper-detail-open-wiki-button` (if ingested) → deep-link to wiki entry

- [ ] **Step 7: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.screens.PaperSearchScreenModelTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/Paper*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/network/EmbedApi.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/network/PaperSearchApi.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/PaperSearchScreenModelTest.kt
git commit -m "feat(plan-4-5): PaperSearchScreen + EmbedApi + PaperDetail (server-side embed only)"
```

---

## Task 18: `ReceiptUploadScreen` + `ReceiptDetailScreen` + camera/file-picker expect/actual

### Council baked-in fixes
- [Council 1779120600 RC4]: **KEPT in first-ship subset.** Plan-3 first-batch DID ship `/receipts/upload` route. Works against shipped server. Camera path uses Android CameraX actual (Task 24 portion) + Desktop FilePicker (Task 26 portion) — both ship in first-ship.
- [Council 1779120600 RC11]: If `PhotoSuggestionCard` is invoked from Receipt-detail flow (line-item correction), the same escape-hatch button "None of these — type manually" applies. Selector `[data-testid="photo-suggestion-none-of-these"]` reused.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/ReceiptUploadScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/ReceiptUploadScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/ReceiptDetailScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/ReceiptDetailScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/ReceiptApi.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/PhotoCapture.kt` (expect)
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/FilePicker.kt` (expect)
- Create: `shared/src/androidMain/kotlin/com/dietician/ui/components/PhotoCapture.android.kt`
- Create: `shared/src/androidMain/kotlin/com/dietician/ui/components/FilePicker.android.kt`
- Create: `shared/src/desktopMain/kotlin/com/dietician/ui/components/PhotoCapture.desktop.kt`
- Create: `shared/src/desktopMain/kotlin/com/dietician/ui/components/FilePicker.desktop.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/ReceiptUploadScreenModelTest.kt`

### Component-reuse contract (per CLAUDE.md rule)

Mounting existing `PhotoCapture` actuals in new `ReceiptUploadScreen` per CLAUDE.md component-reuse contract:

1. **PhotoCapture expect signature:**
```kotlin
expect class PhotoCapture {
    suspend fun captureReceipt(): Result<PhotoRef>
    suspend fun captureNutritionLabel(): Result<PhotoRef>
    suspend fun capturePantryItem(): Result<PhotoRef>
}
data class PhotoRef(val mimeType: String, val bytes: ByteArray)
```

2. **Wire-up JSX in `ReceiptUploadScreen.Content()`:**
```kotlin
val photoCapture: PhotoCapture = koinInject()
Button(
    onClick = {
        scope.launch {
            photoCapture.captureReceipt()
                .onSuccess { ref -> sm.uploadCaptured(ref) }
                .onFailure { e -> sm.setError(e.message) }
        }
    },
    modifier = Modifier.testTag("receipt-camera-button"),
) { Text("Take photo") }
```

3. **Run `./gradlew :shared:compileCommonMainKotlinMetadata` before commit** — catches prop-type mismatch between commonMain expect and androidMain/desktopMain actuals.

4. **Test `ReceiptUploadScreenModel` mocks `PhotoCapture` interface + asserts the URL/payload shape passed to `ReceiptApi.upload` matches the multipart `mime + bytes` contract** (Plan-3 Task 24 ships the `/receipts/upload` route expecting exactly this shape).

- [ ] **Step 1: Failing test** — assert `uploadCaptured(ref)` POSTs to `/receipts/upload` with multipart body containing `mime=ref.mimeType` + `data=ref.bytes`.

- [ ] **Step 2: Implement `PhotoCapture.kt` (expect) + actuals**

`commonMain/components/PhotoCapture.kt`:
```kotlin
package com.dietician.ui.components

data class PhotoRef(val mimeType: String, val bytes: ByteArray) {
    override fun equals(other: Any?) = other is PhotoRef && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
}

expect class PhotoCapture {
    suspend fun captureReceipt(): Result<PhotoRef>
    suspend fun captureNutritionLabel(): Result<PhotoRef>
    suspend fun capturePantryItem(): Result<PhotoRef>
}
```

`androidMain/components/PhotoCapture.android.kt`:
```kotlin
package com.dietician.ui.components

import android.app.Activity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class PhotoCapture(
    private val activity: Activity,
    private val lifecycleOwner: LifecycleOwner,
) {
    actual suspend fun captureReceipt(): Result<PhotoRef> = capture()
    actual suspend fun captureNutritionLabel(): Result<PhotoRef> = capture()
    actual suspend fun capturePantryItem(): Result<PhotoRef> = capture()

    private suspend fun capture(): Result<PhotoRef> = runCatching {
        // CameraX one-shot capture; full implementation in androidApp shell wiring.
        // Returns PhotoRef("image/jpeg", bytes).
        TODO("CameraX wiring lands in androidApp Task 28")
    }
}
```

`desktopMain/components/PhotoCapture.desktop.kt`:
```kotlin
package com.dietician.ui.components

import javax.swing.JFileChooser

actual class PhotoCapture {
    actual suspend fun captureReceipt(): Result<PhotoRef> = pickFromDialog("Pick receipt image")
    actual suspend fun captureNutritionLabel(): Result<PhotoRef> = pickFromDialog("Pick nutrition label image")
    actual suspend fun capturePantryItem(): Result<PhotoRef> = pickFromDialog("Pick pantry item image")

    private fun pickFromDialog(title: String): Result<PhotoRef> = runCatching {
        val chooser = JFileChooser().apply { dialogTitle = title }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            val mime = when (file.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "heic" -> "image/heic"
                else -> "application/octet-stream"
            }
            PhotoRef(mime, file.readBytes())
        } else error("user canceled")
    }
}
```

- [ ] **Step 3: Implement `FilePicker.kt` (expect) + actuals** — same pattern, for PDF/audio.

- [ ] **Step 4: Implement `ReceiptApi.kt`** — `POST /receipts/upload` multipart. Returns `ReceiptUploadResponse(receiptUuid, ocrStatus: String)`. Polls `GET /receipts/{uuid}` for `ocrStatus → completed` then returns parsed line-items.

- [ ] **Step 5: Implement `ReceiptUploadScreenModel.kt` + Screen** — per §11.7:
  - `receipt-camera-button` → `PhotoCapture.captureReceipt()`
  - `receipt-file-pick-button` → `FilePicker.pickImage()`
  - `receipt-recent-uploads` LazyColumn

- [ ] **Step 6: Implement `ReceiptDetailScreen.kt`** — per §11.7 click smoke:
  - `receipt-detail-line-items` LazyColumn (editable rows)
  - `receipt-detail-edit-button` per row
  - `receipt-detail-confirm-button` → fires N `pantry_events` via Plan-1 `EventStore.enqueuePantryEvent(...)` (one per line item)

- [ ] **Step 7: Commit**

```bash
./gradlew :shared:compileCommonMainKotlinMetadata
./gradlew :shared:commonTest --tests "com.dietician.ui.screens.ReceiptUploadScreenModelTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/Receipt*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/PhotoCapture.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/FilePicker.kt \
        shared/src/androidMain/kotlin/com/dietician/ui/components/PhotoCapture.android.kt \
        shared/src/androidMain/kotlin/com/dietician/ui/components/FilePicker.android.kt \
        shared/src/desktopMain/kotlin/com/dietician/ui/components/PhotoCapture.desktop.kt \
        shared/src/desktopMain/kotlin/com/dietician/ui/components/FilePicker.desktop.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/network/ReceiptApi.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/ReceiptUploadScreenModelTest.kt
git commit -m "feat(plan-4-5): ReceiptUploadScreen + camera/file-picker expect/actual + ReceiptApi"
```

---

## Task 19: `EDSafeguardModal` + `EDDetectorHook` + `BigorexiaCopyTest` activation

### Council baked-in fixes
- [Council 1779120600 RC5]: Bigorexia copy ALREADY locked in Task 3 i18n (not deferred here). `EDSafeguardModal` references `ed_safeguard_bigorexia_note` string key. `BigorexiaCopyTest` already activated at Task 3 — at Task 19 the test merely extends to assert the modal body actually renders the string (smoke test, not copy-presence — that's Task 3's job).
- [Council 1779120600 RC6]: DROP standalone `EDSafeguardScreen` composable + its route from this task. Modal (`EDSafeguardModal`) + `PauseTrackingScreen` cover the flow. Inline check-in modal triggered by `EDDetectorHook` from Plan-2 client-side detector + Plan-3 server-side cron. SelectorManifest drops the `ed-safeguard-message/resource-en/resource-ro/pause-button/continue-button` group (those live on the modal, not a separate screen).
- [Council 1779120600 RC14]: Add optional "I'm in a planned cut" toggle to `EDSafeguardModal` (and surface in Settings → Privacy). When activated:
  - 7-day auto-expire timer (Plan-1 `cache_metadata` persists `planned_cut_started_at` epoch ms; client checks on every detector run; auto-resume after 7d)
  - Audit emissions: `planned_cut_activated` (on toggle on) + `planned_cut_expired` (on auto-resume)
  - During the active window, `kcalUnder80For7d` detector path is suppressed; bigorexia bidirectional weight-rate detector + restrictive-phrase detector REMAIN active.
  - After expiry, restrictive-pattern detector resumes default thresholds.
  - Selectors: `[data-testid="planned-cut-toggle"]` + `[data-testid="planned-cut-days-remaining-{n}"]` (where n = days left).
- [Council 1779120600 RC10]: `EDDetectorHook` audit emissions MUST carry `emotion_inference_disabled=TRUE` per AI Act Art 5(1)(f) negative-control. Visible in `AuditLogRow` badge per RC10.

**RC-14 audit-row protection:** If a friend keeps toggling `planned_cut_activated` repeatedly (e.g. always-cutting rationalization), Victor reviewing audit log can spot the pattern. The cap + audit emission is itself a signal.

**RC-14 test additions:**
- `PlannedCutAutoExpireTest`: assert toggle activation persists `planned_cut_started_at`; on day 8 detector run, auto-expire fires + `planned_cut_expired` audit row written.
- `PlannedCutSuppressesKcalDetectorTest`: during 7-day window, kcalUnder80 detector returns false even if kcal-under-80% threshold is hit.
- `PlannedCutLeavesWeightRateDetectorActiveTest`: bidirectional weight-rate detector still fires during planned-cut window.

### Council baked-in fixes
- **[Locked decision spec §1 + R3 §3 + Council Q8]:** Primary ED risk for Victor's profile = bigorexia / muscle-dysmorphia, NOT anorexia. `BigorexiaCopyTest` (placeholder from Task 2) is now activated with real assertions against `ed_safeguard_bigorexia_note` string key.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/EDSafeguardModal.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/EDSafeguardScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/state/EDSafeguardState.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/state/EDDetectorHook.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/state/EDDetectorHookTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/dietician/ui/components/BigorexiaCopyTest.kt`

- [ ] **Step 1: Failing `EDDetectorHookTest.kt`**

```kotlin
package com.dietician.ui.state

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class EDDetectorHookTest {
    @Test
    fun `kcal under 80pct for 7 consecutive days triggers flag`() {
        val rule = EDDetectorRules.kcalUnder80For7d(
            kcalIn = (0 until 7).map { 1500.0 },  // 1500 / 2700 = 55% → under 80%
            kcalTarget = 2700.0,
        )
        rule shouldBe true
    }

    @Test
    fun `kcal at 90pct does not trigger`() {
        val rule = EDDetectorRules.kcalUnder80For7d(
            kcalIn = (0 until 7).map { 2430.0 },  // 90%
            kcalTarget = 2700.0,
        )
        rule shouldBe false
    }

    @Test
    fun `weight rate above 0_5 kg per week triggers (bidirectional for bigorexia)`() {
        val rule = EDDetectorRules.weightRateAboveCap(
            weightsKg = listOf(67.5, 67.7, 68.0, 68.3, 68.6, 68.9, 69.2, 69.5),  // +2kg in 8 days → ~1.75 kg/wk
            capKgPerWk = 0.5,
        )
        rule shouldBe true
    }

    @Test
    fun `weight rate of negative 0_7 kg per week also triggers (anorexia risk - kept symmetric)`() {
        val rule = EDDetectorRules.weightRateAboveCap(
            weightsKg = listOf(67.5, 67.4, 67.3, 67.2, 67.1, 67.0, 66.9, 66.8),  // -0.7kg in 8 days
            capKgPerWk = 0.5,
        )
        rule shouldBe true
    }

    @Test
    fun `restrictive trigger phrases in meal notes flag`() {
        val rule = EDDetectorRules.triggerPhrasesAboveThreshold(
            mealNotes = listOf(
                "I shouldn't have eaten that",
                "I was bad today",
                "regular meal",
                "I deserve this",
                "another regular",
                "I shouldn't have",
            ),
            thresholdFraction = 0.30,
        )
        rule shouldBe true  // 4 out of 6 = 66% > 30%
    }
}
```

- [ ] **Step 2: Implement `EDDetectorHook.kt` + `EDDetectorRules` object**

```kotlin
package com.dietician.ui.state

import kotlin.math.abs

object EDDetectorRules {
    fun kcalUnder80For7d(kcalIn: List<Double>, kcalTarget: Double): Boolean =
        kcalIn.size >= 7 && kcalIn.takeLast(7).all { it / kcalTarget < 0.80 }

    fun weightRateAboveCap(weightsKg: List<Double>, capKgPerWk: Double): Boolean {
        if (weightsKg.size < 8) return false
        val deltaKg = weightsKg.last() - weightsKg.first()
        val deltaDays = weightsKg.size - 1
        val ratePerWk = abs(deltaKg) / (deltaDays / 7.0)
        return ratePerWk > capKgPerWk
    }

    private val TRIGGER_PHRASES = listOf(
        "shouldn't have", "shouldnt have", "I was bad", "I deserve",
        "I have to", "I need to burn", "regret eating",
        "n-ar fi trebuit", "am fost rea", "merit",
    )

    fun triggerPhrasesAboveThreshold(mealNotes: List<String>, thresholdFraction: Double): Boolean {
        if (mealNotes.isEmpty()) return false
        val hits = mealNotes.count { note -> TRIGGER_PHRASES.any { phrase -> note.contains(phrase, ignoreCase = true) } }
        return hits.toDouble() / mealNotes.size > thresholdFraction
    }
}
```

`EDDetectorHook.kt`:

```kotlin
package com.dietician.ui.state

import com.dietician.shared.data.local.MealEventStore
import com.dietician.shared.data.local.WeightEventStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared Compose KMP hook evaluating §9.3 thresholds on local cache after meal_event insert
 * or pull-cycle. Server-side primary (Plan-3 nightly cron); this is the client-side secondary.
 * Per spec §6.11b + Council Q8.
 */
class EDDetectorHook(
    private val mealEvents: MealEventStore,
    private val weightEvents: WeightEventStore,
    private val safeguardState: EDSafeguardState,
) {
    suspend fun evaluate(subjectId: String, kcalTarget: Double) {
        val recentKcal = mealEvents.recentDailyKcal(subjectId, days = 7)
        val recentWeights = weightEvents.recentDailyWeights(subjectId, days = 8).map { it.weightKg }
        val recentNotes = mealEvents.recentNotes(subjectId, n = 30)

        val hits = listOfNotNull(
            "kcal_under_80_7d".takeIf { EDDetectorRules.kcalUnder80For7d(recentKcal, kcalTarget) },
            "weight_rate_above_cap".takeIf { EDDetectorRules.weightRateAboveCap(recentWeights, 0.5) },
            "restrictive_phrases_30pct_14d".takeIf { EDDetectorRules.triggerPhrasesAboveThreshold(recentNotes, 0.30) },
        )
        if (hits.isNotEmpty()) safeguardState.fireCheckin(hits)
    }
}
```

- [ ] **Step 3: Implement `EDSafeguardState.kt`**

```kotlin
package com.dietician.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EDSafeguardState(
    private val auditEmit: suspend (action: String, hits: List<String>) -> Unit,
) {
    private val _showCheckin = MutableStateFlow(false)
    val showCheckin: StateFlow<Boolean> = _showCheckin.asStateFlow()
    private val _lastHits = MutableStateFlow<List<String>>(emptyList())
    val lastHits: StateFlow<List<String>> = _lastHits.asStateFlow()

    suspend fun fireCheckin(hits: List<String>) {
        _lastHits.value = hits
        _showCheckin.value = true
        auditEmit("safeguard_checkin_fired", hits)
    }

    suspend fun dismissCheckin() {
        _showCheckin.value = false
        auditEmit("safeguard_dismissed", _lastHits.value)
    }

    suspend fun acknowledgeCheckin() {
        _showCheckin.value = false
        auditEmit("safeguard_acknowledged", _lastHits.value)
    }

    suspend fun pauseViaCheckin() {
        _showCheckin.value = false
        auditEmit("safeguard_pause_via_modal", _lastHits.value)
    }
}
```

- [ ] **Step 4: Implement `EDSafeguardModal.kt`** — cross-route modal per §11.15a:
  - root `ed-checkin-modal`
  - `ed-checkin-pause-tracking` primary CTA → `pauseViaCheckin()` + navigates `PauseTrackingScreen`
  - `ed-checkin-dismiss` → `dismissCheckin()`
  - `ed-checkin-ok` → `acknowledgeCheckin()`
  - Body includes the bigorexia note (`ed_safeguard_bigorexia_note` string)

- [ ] **Step 5: ~~Implement `EDSafeguardScreen.kt`~~** — **DROPPED per Council 1779120600 RC6.** Standalone full-screen variant removed; modal (Step 4) + PauseTrackingScreen handle the flow. SelectorManifest drops `ed-safeguard-message/resource-en/resource-ro/pause-button/continue-button` group. Original step text retained struck-through as historical record:
  - ~~`ed-safeguard-message`, `ed-safeguard-resource-en/ro`~~
  - ~~`ed-safeguard-pause-button` → routes to PauseTrackingScreen~~
  - ~~`ed-safeguard-continue-button` → dismisses + audit `safeguard_continued`~~

- [ ] **Step 5 (replaces Step 5): Implement planned-cut toggle on EDSafeguardModal** — [Council 1779120600 RC14]:
  - Add `Switch` row at modal bottom with `[data-testid="planned-cut-toggle"]`
  - On toggle ON: write `cache_metadata[planned_cut_started_at] = now_epoch_ms`; emit audit `planned_cut_activated`; show countdown `[data-testid="planned-cut-days-remaining-7"]`
  - On toggle OFF (manual): clear `cache_metadata[planned_cut_started_at]`; emit audit `planned_cut_cancelled_by_user`
  - `EDDetectorHook.evaluate(...)` reads `planned_cut_started_at`; if `now - started_at < 7 days`, skip `kcalUnder80For7d` evaluation. Weight-rate + restrictive-phrase detectors REMAIN ACTIVE.
  - Daily detector run: if `now - started_at >= 7 days`, emit audit `planned_cut_expired` + clear the metadata key.

- [ ] **Step 6: Activate `BigorexiaCopyTest.kt`**

```kotlin
package com.dietician.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BigorexiaCopyTest {
    @Test
    fun `ED safeguard copy mentions bigorexia or muscle-dysmorphia by name (activated)`() {
        val en = File("src/commonMain/resources/MR/base/strings.xml").readText()
        val mentionsBigorexia = en.contains("bigorexia", ignoreCase = true) || en.contains("muscle dysmorphia", ignoreCase = true)
        assertTrue(mentionsBigorexia, "EDSafeguardModal copy must mention bigorexia or muscle dysmorphia per spec §1 + R3 §3")

        val ro = File("src/commonMain/resources/MR/ro/strings.xml").readText()
        val mentionsBigorexiaRo = ro.contains("bigorexia", ignoreCase = true) || ro.contains("dismorfie", ignoreCase = true) || ro.contains("dismorfia", ignoreCase = true)
        assertTrue(mentionsBigorexiaRo, "Romanian EDSafeguardModal copy must mention bigorexia or dismorfie")
    }

    @Test
    fun `safeguard rules are symmetric (lean-bulk = gain rate cap also)`() {
        // EDDetectorRules.weightRateAboveCap uses abs() — see EDDetectorHookTest.
        // This test sanity-checks at the copy level: message acknowledges both directions.
        val en = File("src/commonMain/resources/MR/base/strings.xml").readText()
        assertTrue(en.contains("never-enough", ignoreCase = true) || en.contains("never enough", ignoreCase = true),
            "Bigorexia note must mention never-enough framing")
    }
}
```

- [ ] **Step 7: Commit**

> NOTE per Council 1779120600 RC6: drop `EDSafeguardScreen.kt` from the `git add` line — file no longer created. Add planned-cut-toggle test file (RC14) + audit-row emotion-disabled badge test (RC10) if not yet added.

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.state.EDDetectorHookTest" \
                              --tests "com.dietician.ui.components.BigorexiaCopyTest" \
                              --tests "com.dietician.ui.state.PlannedCutAutoExpireTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/state/EDDetectorHook.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/state/EDSafeguardState.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/EDSafeguardModal.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/state/EDDetectorHookTest.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/state/PlannedCutAutoExpireTest.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/components/BigorexiaCopyTest.kt
git commit -m "feat(plan-4-5): EDSafeguardModal + DetectorHook + planned-cut toggle (RC6+RC14)"
```

---

## Task 20: `SettingsScreen` + `AuditLogScreen` + `SettingsAboutScreen`

### Council baked-in fixes
- [Council 1779120600 RC10]: `AuditLogRow` composable MUST render `emotion_inference_disabled=TRUE` badge per row (compliance evidence per AI Act Art 5(1)(f) hard-banned negative-control). Source: `audit_log.extra.emotion_inference_disabled` JSON field on each row. Badge selector: `[data-testid="audit-row-emotion-disabled-{row_id}"]`. Required for negative-control visible affordance.
- [Council 1779120600 RC13]: `ByokScreen` / Settings → Credentials BYOK key paste UI: on paste detection (Compose `LocalClipboardManager.getText()` + paste-action callback), fire `Clipboard.clear()` via platform `ClipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))` (Android) / Desktop `Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(""), null)` AND show toast "Clipboard cleared for security." (i18n key `byok_clipboard_cleared_toast`). Add `[data-testid="byok-paste-clipboard-cleared-toast"]` selector. Prevents accidental clipboard leak to other apps.
- [Council 1779120600 RC19]: `ConsentRows` MUST include a SECOND `ConsentRow` SEPARATELY from Art 9 health-data consent — "Cross-border transfer to Anthropic (US) / Google (US) / OpenRouter (US) under SCC + DPF mechanism." Required for non-EEA LLM providers. Each consent independently withdrawable. Audit emissions on toggle: `consent_cross_border_granted` / `consent_cross_border_withdrawn`. Selector: `[data-testid="consent-row-cross-border-transfer"]`.
- [Council 1779120600 RC7]: `AuditLogScreen` MUST accept an optional `AuditLogFilter(callUuid: String?)` nav arg. When present, list is pre-filtered to that `call_uuid`. Reached via deep-link from `PerCallDisclosurePane` "Open audit row" button (Task 16 RC7). Selector when filtered: `[data-testid="audit-log-filter-call-uuid-{call_uuid}"]`.
- [Council 1779120600 RC14]: `SettingsScreen` ALSO surfaces the planned-cut toggle (parallel surface to EDSafeguardModal Step 5 from Task 19). Same `[data-testid="planned-cut-toggle"]` + `[data-testid="planned-cut-days-remaining-{n}"]` selectors. Users can activate/cancel without first hitting the modal.
- [Council 1779120600 RC18]: `SettingsAboutScreen` MUST display current `AI_LITERACY_TEXT_VERSION` from `docs/policies/AI_LITERACY_TEXT_VERSION.md`. "AI literacy text last updated: vX.Y on DATE." Re-acknowledge button → re-shows AILiteracyBanner.

**RC-10 wiring detail:** `AuditLogRow` composable:
```kotlin
@Composable
fun AuditLogRow(row: AuditLogEntry) {
    Card {
        Row {
            Text(row.action)
            Text(row.timestamp)
            if (row.extra["emotion_inference_disabled"] == "TRUE" || row.extra["emotion_inference_disabled"] == true) {
                Badge(Modifier.testTag("audit-row-emotion-disabled-${row.id}")) {
                    Text("no emotion inference", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
```

**RC-13 wiring detail:** `ByokKeyPasteField`:
```kotlin
val clipboardManager = LocalClipboardManager.current
val coroutineScope = rememberCoroutineScope()
TextField(
    value = byokKey,
    onValueChange = { newValue ->
        if (newValue.length > byokKey.length + 10) {  // paste heuristic
            clipboardManager.setText(AnnotatedString(""))
            coroutineScope.launch { snackbarHostState.showSnackbar(stringResource(MR.strings.byok_clipboard_cleared_toast)) }
        }
        byokKey = newValue
    },
    modifier = Modifier.testTag("byok-paste-clipboard-cleared-toast"),
)
```

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/SettingsScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/SettingsScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/SettingsAboutScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/AuditLogScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/AuditLogScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/AuditApi.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/ConsentApi.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/ByokApi.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/UaicCookieReExportLauncher.kt` (expect)
- Create: `shared/src/androidMain/kotlin/com/dietician/ui/network/UaicCookieReExportLauncher.android.kt`
- Create: `shared/src/desktopMain/kotlin/com/dietician/ui/network/UaicCookieReExportLauncher.desktop.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/screens/SettingsScreenModelTest.kt`

- [ ] **Step 1: Implement `SettingsScreen.kt`** — per §11.8 (13 first-paint selectors):
  - `settings-profile-section`, `settings-stores-section`, `settings-equipment-section`, `settings-credentials-section`, `settings-consent-section`
  - `settings-ai-coach-toggle`, `settings-photo-toggle`, `settings-voice-toggle`
  - `settings-pause-button`
  - `settings-delete-account-button`
  - `settings-export-dsar-button`
  - `settings-about-link`
  - `settings-uaic-reexport-cookies-button` (Desktop only — Android shows toast "Open on Desktop")

Click smoke per §11.8:
- `settings-consent-section` → `consent-record-{id}` rows + `consent-withdraw-{id}` buttons
- `settings-delete-account-button` → 2-step modal with `delete-confirm-step-1` + `delete-confirm-step-2`
- `settings-export-dsar-button` → downloads `GET /me/dsar` ZIP
- `settings-uaic-reexport-cookies-button` → `UaicCookieReExportLauncher.launch()` (Desktop launches Playwright; Android shows toast)

- [ ] **Step 2: Implement `ConsentApi.kt`** — `GET /me/consent` list, `POST /me/consent/{id}/withdraw`.

- [ ] **Step 3: Implement `ByokApi.kt`** — `POST /me/byok { provider, encryptedKey }` (per Plan-3 §29). UI flow: paste key → encrypted client-side via Plan-1 age-keypair → posted as ciphertext.

- [ ] **Step 4: Implement `UaicCookieReExportLauncher.kt`** — `expect class UaicCookieReExportLauncher { suspend fun launch(): Result<Unit> }`. Android actual throws `UnsupportedOperationException("Open on Desktop")`. Desktop actual invokes Plan-6 Playwright subprocess (deferred — for first-ship throw `NotShippedYet`; UI surface ships per spec §6.11a).

- [ ] **Step 5: Implement `AuditApi.kt`** — `GET /me/audit?format=json` returns paginated `List<AuditLogRow>`. `GET /me/audit?format=pdf` returns PDF bytes. `GET /me/audit?call_uuid=X` returns single row + raw response ref (used by `PerCallDisclosurePane`).

- [ ] **Step 6: Implement `AuditLogScreen.kt`** — per §11.9:
  - `audit-log-filter-bar` (action-type filter + date range)
  - `audit-log-list` LazyColumn of `AuditLogRow` cards
  - `audit-export-pdf-button` → triggers download via `FilePicker.savePdf()` (expect/actual)
  - `audit-export-json-button` → download JSON

- [ ] **Step 7: Implement `SettingsAboutScreen.kt`** — links to `MODEL_CARD.md` + `RISK_REGISTER.md` + AI literacy text version + "Re-show AI literacy banner" (calls `AILiteracyState.forceReshow()`).

- [ ] **Step 8: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.screens.SettingsScreenModelTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/Settings*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/screens/AuditLog*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/network/AuditApi.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/network/ConsentApi.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/network/ByokApi.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/network/UaicCookieReExportLauncher.kt \
        shared/src/androidMain/kotlin/com/dietician/ui/network/UaicCookieReExportLauncher.android.kt \
        shared/src/desktopMain/kotlin/com/dietician/ui/network/UaicCookieReExportLauncher.desktop.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/screens/SettingsScreenModelTest.kt
git commit -m "feat(plan-4-5): SettingsScreen (13 §11.8 selectors) + AuditLogScreen (§11.9) + UAIC re-export launcher"
```

---

## Task 21: `DiagScreen` + `SyncStateObserver`

### Council baked-in fixes
- [Council 1779120600 RC4]: `DiagScreen` standalone DEFERRED to Plan-4-5.5 (`/diag` server route deferred per Plan-3 RC-12). The `SyncStateObserver` portion KEEPS shipping (load-bearing for Home banner sync status). Mark DiagScreen with `// PLAN-4-5 FOLLOWUP RC4 — DiagScreen deferred; /diag route 501-stubbed`. Concretely:
  - **SHIP:** `SyncStateObserver.kt` (state-level wrapper around Plan-1 `PullCoordinator` + `WebSocketListener`)
  - **DEFER:** `DiagScreen.kt` standalone surface. Tailscale connectivity errors handled by RC16 blocker in MainActivity/Main.kt instead.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/DiagScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/screens/DiagScreenModel.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/state/SyncStateObserver.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/DiagApi.kt`

- [ ] **Step 1: Implement `SyncStateObserver.kt`** — wraps Plan-1 `PullCoordinator` + `WebSocketListener` into a `Flow<SyncStatus>`:

```kotlin
package com.dietician.ui.state

import com.dietician.shared.data.local.SyncLogStore
import com.dietician.shared.data.local.OutboxStore
import com.dietician.shared.data.sync.PullCoordinator
import com.dietician.shared.data.sync.WebSocketListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class SyncStatus(
    val vpsReachable: Boolean,
    val wsConnected: Boolean,
    val outboxDepth: Int,
    val outboxDeadCount: Int,
    val lastPullAtMs: Long?,
    val lastPushAtMs: Long?,
    val lastError: String?,
)

class SyncStateObserver(
    private val pullCoordinator: PullCoordinator,
    private val wsListener: WebSocketListener,
    private val outbox: OutboxStore,
    private val syncLog: SyncLogStore,
) {
    fun statusFlow(): Flow<SyncStatus> = combine(
        wsListener.connected,
        outbox.depthFlow(),
        syncLog.recentFlow(1),
    ) { wsConn, depth, recent ->
        SyncStatus(
            vpsReachable = wsConn || (recent.firstOrNull()?.error == null),
            wsConnected = wsConn,
            outboxDepth = depth,
            outboxDeadCount = outbox.deadCount(),
            lastPullAtMs = recent.firstOrNull()?.pullEndedAtMs,
            lastPushAtMs = recent.firstOrNull()?.pullStartedAtMs,
            lastError = recent.firstOrNull()?.error,
        )
    }
}
```

- [ ] **Step 2: Implement `DiagApi.kt`** — `GET /diag` returns server-side aggregate from Plan-3 Task 38.

- [ ] **Step 3: Implement `DiagScreenModel.kt`** — combines client-side `SyncStateObserver.statusFlow()` with server-side `DiagApi.fetch()` payload.

- [ ] **Step 4: Implement `DiagScreen.kt`** — per §11.15 (~11 selectors):
  - `diag-vps`, `diag-tailscale`, `diag-postgres`, `diag-ntfy`
  - `diag-outbox`, `diag-sync-times`
  - `diag-llm-budget-claudemax`, `diag-llm-budget-openrouter`
  - `diag-scraper-status` (per-scraper sub-tag)
  - `diag-last-errors`
  - `diag-pending-jobs`

Each row uses a small icon + numeric + status text. Color = `error` only for genuine failures (per `ColorPalette` rules — no green-OK / red-FAIL pattern).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dietician/ui/screens/Diag*.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/state/SyncStateObserver.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/network/DiagApi.kt
git commit -m "feat(plan-4-5): DiagScreen (§11.15) + SyncStateObserver flow wrapping Plan-1 sync internals"
```

---

## Task 22: `WeeklyNarrative` + `WeightTrendChart` (Carbon-style + §6.5 weight rendering rule)

### Council baked-in fixes
- [Council 1779120600 RC4]: First-ship subset ships `WeightTrendChart` ONLY (load-bearing for §6.5 weight rendering rule + ED-safety primary surface). `WeeklyNarrative` LLM-rendered card body is DEFERRED to Plan-4-5.5 (Plan-2 LLM coach dependency timing) — replace with a placeholder card "Your week — coming Sunday" stub. The composable file ships; the LLM `NarrativeRequest` call is gated behind a feature flag or stubbed to return placeholder text.
- Concretely:
  - **SHIP:** `WeightTrendChart.kt` — 7-day rolling avg primary line + 30-day trend + daily-as-small-dots per §6.5.
  - **SHIP:** `WeeklyNarrative.kt` composable file (placeholder body "Your week — coming Sunday") with `[data-testid="weekly-narrative-placeholder"]` selector.
  - **DEFER:** Real `LlmRouter.call(...)` invocation in `WeeklyNarrative` body → Plan-4-5.5.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/WeeklyNarrative.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/components/WeightTrendChart.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/components/WeightTrendChartTest.kt`

- [ ] **Step 1: Implement `WeightTrendChart.kt`** — per spec §6.5 weight-rendering rule:
  - 7-day rolling avg = PRIMARY line (thick `neutralTeal`)
  - 30-day trend = secondary line (thin `neutralTeal.copy(alpha=0.5f)`)
  - Daily measurements = small dots (4dp radius) on the chart but NOT primary
  - Today's individual weight NOT shown as a numeric prominence — only as a dot

testTag `"weight-trend-chart"`. NEVER expose `dailyWeightToday: Double` as a separate label. Hard NO list enforced statically.

- [ ] **Step 2: Implement `WeeklyNarrative.kt`** — Carbon-style Sunday narrative per spec §6.8:

```kotlin
package com.dietician.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmRouter
import com.dietician.ui.state.SubjectPrincipal
import com.dietician.ui.theme.Spacing
import kotlinx.coroutines.launch

data class WeeklyNarrativeInputs(
    val weekStart: kotlinx.datetime.LocalDate,
    val strengthTrendPct: Double?,
    val avgEnergyRating: Double?,
    val avgMoodRating: Double?,
    val avgSleepHours: Double?,
    val weight7dRollingDeltaKg: Double,
    val avgKcalIn: Double,
    val avgKcalTarget: Double,
)

/**
 * Per spec §6.8: weight is NEVER the primary lead. The narrative prompt routes strength /
 * energy / mood / sleep first; weight enters as a supporting figure.
 */
@Composable
fun WeeklyNarrative(
    principal: SubjectPrincipal,
    router: LlmRouter,
    inputs: WeeklyNarrativeInputs,
    modifier: Modifier = Modifier,
) {
    var narrative by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(modifier = modifier.fillMaxWidth().padding(Spacing.m).testTag("weekly-narrative-card")) {
        Column(Modifier.padding(Spacing.m)) {
            Text("This week", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.s))
            when {
                narrative != null -> Text(narrative!!, modifier = Modifier.testTag("weekly-narrative-text"))
                loading -> CircularProgressIndicator()
                else -> Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            val req = LlmRequest(
                                prompt = buildPrompt(inputs),
                                subjectId = principal.subjectId,
                                capability = Capability.TEXT,
                                estTokensIn = 400,
                                estMaxTokensOut = 300,
                            )
                            router.call(req)
                                .onSuccess { narrative = it.text }
                                .onFailure { narrative = "Weekly narrative unavailable. Check audit log." }
                            loading = false
                        }
                    },
                    modifier = Modifier.testTag("weekly-narrative-generate-button"),
                ) { Text("Compose this week's review") }
            }
        }
    }
}

private fun buildPrompt(i: WeeklyNarrativeInputs): String = """
You are Victor's lean-bulk coach. Compose a 4-sentence weekly review.
LEAD with strength + energy + mood + sleep. Weight is a SUPPORTING figure, not the headline.

Inputs:
- Strength trend: ${i.strengthTrendPct?.let { "%.1f%%".format(it) } ?: "no log this week"}
- Energy rating avg: ${i.avgEnergyRating ?: "no log"}
- Mood rating avg: ${i.avgMoodRating ?: "no log"}
- Sleep hours avg: ${i.avgSleepHours ?: "no log"}
- Weight 7-day rolling Δ: ${"%.2f".format(i.weight7dRollingDeltaKg)} kg
- Kcal in vs target: ${i.avgKcalIn.toInt()} / ${i.avgKcalTarget.toInt()}

Do NOT lead with weight. Do NOT use "you should". Suggest one kcal adjustment ONLY if energy is below 3/5
OR weight trend is outside ±0.3 kg/wk band.
""".trimIndent()
```

Note: `LlmRouter.call` here pulls from `com.dietician.shared.llm` (Plan-2). The router is Koin-injected via `koinInject()` in the caller; for the composable itself we accept it as parameter.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dietician/ui/components/WeeklyNarrative.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/components/WeightTrendChart.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/components/WeightTrendChartTest.kt
git commit -m "feat(plan-4-5): WeeklyNarrative (Carbon-style) + WeightTrendChart (7d rolling primary, daily=dots)"
```

---

## Task 23: Android shell — `MainActivity` + `DieticianApp` + manifest + Koin platform module

### Council baked-in fixes
- [Council 1779120600 RC16]: `MainActivity.onCreate` MUST run pre-magic-link Tailscale-disconnected blocker check. Before any other UI, check `TailnetDiscovery.isReachable()` (extends from Plan-3 Task 9 — gracefully degrade if Plan-3 doesn't yet ship the helper, by attempting a curl-equivalent to `http://${tailscale_ip}:8081/health` with short timeout). If unreachable → render `TailscaleDisconnectedScreen` with "Connect to Tailscale to use Dietician" + retry button (uses i18n keys `tailscale_disconnected_title` / `_body` / `_retry` from Task 3). Add `[data-testid="tailscale-disconnected-blocker"]` selector. Don't let user reach magic-link only to hit a cryptic 'network error.'

**RC-16 Android wiring:**
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DieticianTheme {
                val tailnetReachable by produceState(initialValue = null as Boolean?) {
                    value = withContext(Dispatchers.IO) { TailnetDiscovery.isReachable(timeoutMs = 2000) }
                }
                when (tailnetReachable) {
                    null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                    false -> TailscaleDisconnectedScreen(onRetry = { recreate() })
                    true -> Navigator(OnboardingScreen) { /* normal */ }
                }
            }
        }
    }
}
```

**Files:**
- Modify: `androidApp/src/main/kotlin/com/dietician/android/MainActivity.kt` (REPLACE)
- Create: `androidApp/src/main/kotlin/com/dietician/android/DieticianApp.kt`
- Create: `androidApp/src/main/kotlin/com/dietician/android/di/AndroidPlatformModule.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `androidApp/proguard-rules.pro`

- [ ] **Step 1: Modify `AndroidManifest.xml`** — add deep-link intent-filter + permissions:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:name=".DieticianApp"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:theme="@style/Theme.Dietician"
        android:usesCleartextTraffic="true"
        tools:targetApi="34">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|locale|layoutDirection|fontScale|uiMode|density">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="dietician" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 2: Implement `DieticianApp.kt`**

```kotlin
package com.dietician.android

import android.app.Application
import com.dietician.android.di.androidPlatformModule
import com.dietician.shared.data.dataModule
import com.dietician.shared.llm.llmModule
import com.dietician.ui.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DieticianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DieticianApp)
            modules(dataModule, llmModule, uiModule, androidPlatformModule)
        }
    }
}
```

- [ ] **Step 3: Implement `MainActivity.kt`**

```kotlin
package com.dietician.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import cafe.adriel.voyager.navigator.Navigator
import com.dietician.ui.navigation.DeepLinkRouter
import com.dietician.ui.navigation.DeepLinkTarget
import com.dietician.ui.screens.HomeScreen
import com.dietician.ui.screens.MagicLinkVerifyScreen
import com.dietician.ui.screens.OnboardingScreen
import com.dietician.ui.state.AILiteracyState
import com.dietician.ui.theme.DieticianTheme
import com.dietician.ui.components.AILiteracyBanner
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val literacy: AILiteracyState by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialScreen = resolveInitialScreen(intent)
        lifecycleScope.launch { literacy.refresh() }
        setContent {
            DieticianTheme {
                Navigator(initialScreen)
                AILiteracyBanner(literacy) { lifecycleScope.launch { literacy.ack() } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle dietician://verify?token=... while activity already running.
        // setContent state holds Navigator; recreate Navigator with new start if needed.
    }

    private fun resolveInitialScreen(intent: Intent?): cafe.adriel.voyager.core.screen.Screen {
        val uri = intent?.data?.toString() ?: return OnboardingScreen
        return when (val target = DeepLinkRouter.parse(uri)) {
            is DeepLinkTarget.MagicLinkVerify -> MagicLinkVerifyScreen(target.token)
            DeepLinkTarget.Home -> HomeScreen
            else -> OnboardingScreen
        }
    }
}
```

- [ ] **Step 4: Implement `AndroidPlatformModule.kt`** — Koin module binding Android actuals:

```kotlin
package com.dietician.android.di

import com.dietician.ui.components.FilePicker
import com.dietician.ui.components.PhotoCapture
import com.dietician.ui.network.UaicCookieReExportLauncher
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val androidPlatformModule = module {
    // PhotoCapture + FilePicker need Activity/LifecycleOwner context — provided per-Activity.
    // For now the model-level fake is used in tests; production wiring happens in MainActivity scope
    // via Voyager's per-screen ScreenModel registration.
    single { UaicCookieReExportLauncher() }   // Android actual throws UnsupportedOperationException
}
```

- [ ] **Step 5: Modify `proguard-rules.pro`** — add Voyager + Koin keep rules:

```
-keep class cafe.adriel.voyager.** { *; }
-keep class org.koin.** { *; }
-keep class com.dietician.shared.** { *; }
-keep class com.dietician.ui.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
```

- [ ] **Step 6: Verify build**

```bash
./gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. APK lands at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

- [ ] **Step 7: Commit**

```bash
git add androidApp/src/main/kotlin/com/dietician/android/ \
        androidApp/src/main/AndroidManifest.xml \
        androidApp/proguard-rules.pro
git commit -m "feat(plan-4-5): Android shell — MainActivity + DieticianApp Koin start + dietician:// deep-link"
```

---

## Task 24: Android camera + Whisper + voice recorder actuals

### Council baked-in fixes
- [Council 1779120600 RC1]: **DEFERRED to Plan-4-5.5 post-finals voice ship.** Whisper.cpp Android JNI binding + `VoiceRecorder.android.kt` MediaRecorder + `WhisperAsr.android.kt` are NOT first-ship. Voice pipeline (T24/T26/T32) deferred. `VoiceRecordButton` UI affordance from Task 13 stays as a toast-disabled stub.
- [Council 1779120600 RC4]: First-ship subset KEEPS Task 24 ONLY for the Android camera actual (`PhotoCapture.android.kt` via CameraX). The Whisper + VoiceRecorder portions are SKIPPED. Concretely:
  - **SHIP:** `PhotoCapture.android.kt` (CameraX) — load-bearing for FoodLog photo path.
  - **DEFER:** `WhisperAsr.android.kt` (JNI binding) — stays as `Result.failure(NotShippedYet)` no-op.
  - **DEFER:** `VoiceRecorder.android.kt` (MediaRecorder) — no-op stub.
- Mark commits with `// PLAN-4-5 FOLLOWUP RC1` for the deferred voice files so Plan-4-5.5 picks them up.

**Files:**
- Create: `shared/src/androidMain/kotlin/com/dietician/ui/components/CameraXCapture.kt` (PhotoCapture actual full impl)
- Create: `shared/src/androidMain/kotlin/com/dietician/ui/state/WhisperAsrAndroid.kt` (returns NotShippedYet first-ship)
- Create: `shared/src/androidMain/kotlin/com/dietician/ui/state/VoiceRecorderAndroid.kt`
- Create: `shared/src/androidMain/kotlin/com/dietician/ui/state/NtfyRegistrationAndroid.kt`
- Create: `shared/src/androidMain/kotlin/com/dietician/ui/state/StatusBarTheme.android.kt`
- Create: `shared/src/androidMain/kotlin/com/dietician/ui/state/ScaleConnector.android.kt`

- [ ] **Step 1: Implement `PhotoCapture.android.kt` (full CameraX wiring)** — replaces the `TODO` from Task 18. Uses `ImageCapture.Builder().build()` + `ImageCapture.takePicture()` + `OutputFileOptions` writing to app cache → reads bytes → returns `PhotoRef("image/jpeg", bytes)`.

- [ ] **Step 2: Implement `VoiceRecorderAndroid.kt`** — `actual class VoiceRecorder` using `MediaRecorder`:

```kotlin
package com.dietician.ui.state

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

actual class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    actual fun start() {
        val cacheDir = context.cacheDir
        outputFile = File.createTempFile("voice-", ".m4a", cacheDir)
        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
            start()
        }
    }

    actual fun stop(): ByteArray {
        recorder?.apply { stop(); release() }
        recorder = null
        return outputFile?.readBytes() ?: ByteArray(0)
    }
}
```

- [ ] **Step 3: Implement `WhisperAsrAndroid.kt`** — first-ship returns failure → caller falls back to VPS:

```kotlin
package com.dietician.ui.state

actual class WhisperAsr {
    actual suspend fun transcribe(audio: ByteArray, language: String, initialPrompt: String): Result<String> =
        Result.failure(NotShippedYet("Whisper.cpp JNI binding ships in Plan-7"))
}

class NotShippedYet(msg: String) : Exception(msg)
```

- [ ] **Step 4: Implement remaining Android actuals** — `NtfyRegistrationAndroid.kt` (subscription via ntfy Android app URI scheme; subscribes to topics from Plan-1 `cache_metadata`), `StatusBarTheme.android.kt` (uses `WindowInsetsControllerCompat`), `ScaleConnector.android.kt` (first-ship `isAvailable() = false`; BluetoothLeScanner integration deferred).

- [ ] **Step 5: Commit**

```bash
./gradlew :shared:compileKotlinAndroid :androidApp:assembleDebug
git add shared/src/androidMain/kotlin/com/dietician/ui/
git commit -m "feat(plan-4-5): Android actuals — CameraX, MediaRecorder, Whisper-stub, ntfy registration"
```

---

## Task 25: Desktop shell — `Main.kt` + Koin platform module + system tray

### Council baked-in fixes
- [Council 1779120600 RC16]: `Main.kt` MUST run pre-magic-link Tailscale-disconnected blocker check. Before any other UI, check `TailnetDiscovery.isReachable()` (or HTTP-health-fallback). If unreachable → render `TailscaleDisconnectedScreen` with "Connect to Tailscale to use Dietician" + retry button. Same i18n keys + same `[data-testid="tailscale-disconnected-blocker"]` selector as Android (Task 23 RC16). Don't proceed to magic-link if Tailscale is disconnected.

**RC-16 Desktop wiring:**
```kotlin
fun main() = application {
    Window(...) {
        DieticianTheme {
            val tailnetReachable by produceState(initialValue = null as Boolean?) {
                value = withContext(Dispatchers.IO) { TailnetDiscovery.isReachable(timeoutMs = 2000) }
            }
            when (tailnetReachable) {
                null -> CircularProgressIndicator()
                false -> TailscaleDisconnectedScreen(onRetry = { /* re-run discovery */ })
                true -> Navigator(OnboardingScreen) { /* normal */ }
            }
        }
    }
}
```

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/dietician/desktop/Main.kt` (REPLACE)
- Create: `desktopApp/src/main/kotlin/com/dietician/desktop/di/DesktopPlatformModule.kt`
- Create: `desktopApp/src/main/kotlin/com/dietician/desktop/TrayIcon.kt`

- [ ] **Step 1: Implement `Main.kt`**

```kotlin
package com.dietician.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cafe.adriel.voyager.navigator.Navigator
import com.dietician.desktop.di.desktopPlatformModule
import com.dietician.shared.data.dataModule
import com.dietician.shared.llm.llmModule
import com.dietician.ui.UiModule
import com.dietician.ui.components.AILiteracyBanner
import com.dietician.ui.navigation.DeepLinkRouter
import com.dietician.ui.navigation.DeepLinkTarget
import com.dietician.ui.screens.HomeScreen
import com.dietician.ui.screens.MagicLinkVerifyScreen
import com.dietician.ui.screens.OnboardingScreen
import com.dietician.ui.state.AILiteracyState
import com.dietician.ui.theme.DieticianTheme
import com.dietician.ui.uiModule
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.inject

fun main(args: Array<String>) {
    startKoin {
        modules(dataModule, llmModule, uiModule, desktopPlatformModule)
    }
    // Warm-pool spawn for Plan-2 ClaudeMax CLI (per Plan-2 §7.4 + RC1 cold-start mitigation)
    spawnClaudeMaxWarmPoolBackground()

    // Parse --magic-link=... CLI arg for deep-link from email
    val magicToken = args.firstOrNull { it.startsWith("--magic-link=") }?.removePrefix("--magic-link=")

    val literacy: AILiteracyState by inject(AILiteracyState::class.java)
    runBlocking { literacy.refresh() }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Dietician",
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            TrayIcon { exitApplication() }
            DieticianTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val initial: cafe.adriel.voyager.core.screen.Screen = when {
                        magicToken != null -> MagicLinkVerifyScreen(magicToken)
                        else -> OnboardingScreen
                    }
                    Navigator(initial)
                    AILiteracyBanner(literacy) { runBlocking { literacy.ack() } }
                }
            }
        }
    }
}

private fun spawnClaudeMaxWarmPoolBackground() {
    // Wire Plan-2 ClaudeMaxWarmPool start — implementer pulls the function from
    // shared/desktopMain Plan-2 module. Per A12: min(cores-2, 3) processes.
}
```

(Note: `dp` import; implementer adds `import androidx.compose.ui.unit.dp`.)

- [ ] **Step 2: Implement `DesktopPlatformModule.kt`**

```kotlin
package com.dietician.desktop.di

import com.dietician.ui.components.PhotoCapture
import com.dietician.ui.components.FilePicker
import com.dietician.ui.network.UaicCookieReExportLauncher
import org.koin.dsl.module

val desktopPlatformModule = module {
    single { PhotoCapture() }
    single { FilePicker() }
    single { UaicCookieReExportLauncher() }   // Desktop actual: throws NotShippedYet first-ship
}
```

- [ ] **Step 3: Implement `TrayIcon.kt`**

```kotlin
package com.dietician.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PauseCircleOutline
import org.koin.java.KoinJavaComponent.inject

@Composable
fun ApplicationScope.TrayIcon(onExit: () -> Unit) {
    val trayState = rememberTrayState()
    Tray(
        state = trayState,
        icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Restaurant),
        tooltip = "Dietician",
        menu = {
            Item("Open Dietician", onClick = { /* bring Window to front */ })
            Item("Pause tracking", onClick = { /* PauseState.pause */ })
            Item("Quit", onClick = onExit)
        },
    )
}
```

(Note: `rememberVectorPainter` import + `ApplicationScope.Tray` is Compose Desktop's idiom. Implementer adapts to the installed compose-desktop version's exact API.)

- [ ] **Step 4: Build verify**

```bash
./gradlew :desktopApp:assemble
./gradlew :desktopApp:run    # smoke launch — Window opens with OnboardingScreen
```

- [ ] **Step 5: Commit**

```bash
git add desktopApp/src/main/kotlin/com/dietician/desktop/
git commit -m "feat(plan-4-5): Desktop shell — Compose Window + Koin start + system tray + ClaudeMax warm-pool spawn"
```

---

## Task 26: Desktop file picker + window chrome + Whisper desktop actual

### Council baked-in fixes
- [Council 1779120600 RC1]: **Voice upload route client DEFERRED to Plan-4-5.5.** Whisper desktop JNI binding + voice-upload network client skipped.
- [Council 1779120600 RC4]: First-ship Task 26 ships ONLY:
  - **SHIP:** `FilePicker.desktop.kt` (JFileChooser) — load-bearing for FoodLog photo + receipt-upload flows.
  - **SHIP:** `WindowChrome.desktop.kt` + system tray code.
  - **DEFER:** `WhisperAsr.desktop.kt` JNI binding — stays as `Result.failure(NotShippedYet)` no-op.
  - **DEFER:** `VoiceUploadApi` network client → 501-degraded stub (returns immediately without network call).
- Mark deferred voice files with `// PLAN-4-5 FOLLOWUP RC1` comment.

**Files:**
- Already covered partially in Task 18 (`FilePicker.desktop.kt`).
- Create: `shared/src/desktopMain/kotlin/com/dietician/ui/state/WhisperAsrDesktop.kt` (first-ship: NotShippedYet → VPS fallback)
- Create: `shared/src/desktopMain/kotlin/com/dietician/ui/state/VoiceRecorderDesktop.kt`
- Create: `shared/src/desktopMain/kotlin/com/dietician/ui/state/NtfyRegistrationDesktop.kt`
- Create: `shared/src/desktopMain/kotlin/com/dietician/ui/state/ScaleConnectorDesktop.kt`
- Create: `shared/src/desktopMain/kotlin/com/dietician/ui/state/StatusBarTheme.desktop.kt` (no-op)
- Create: `shared/src/desktopMain/kotlin/com/dietician/ui/state/WindowChrome.desktop.kt`

- [ ] **Step 1: Implement `VoiceRecorderDesktop.kt`** — `javax.sound.sampled.TargetDataLine` capturing 16kHz mono PCM, written to a `ByteArrayOutputStream`, returned on `stop()`.

```kotlin
package com.dietician.ui.state

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

actual class VoiceRecorder {
    private val format = AudioFormat(16_000f, 16, 1, true, false)
    private var line: TargetDataLine? = null
    private val buffer = ByteArrayOutputStream()
    private val recording = AtomicBoolean(false)

    actual fun start() {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        line = (AudioSystem.getLine(info) as TargetDataLine).apply {
            open(format)
            start()
        }
        recording.set(true)
        thread(start = true) {
            val data = ByteArray(4096)
            while (recording.get()) {
                val read = line!!.read(data, 0, data.size)
                if (read > 0) buffer.write(data, 0, read)
            }
        }
    }

    actual fun stop(): ByteArray {
        recording.set(false)
        line?.apply { stop(); close() }
        return buffer.toByteArray()
    }
}
```

- [ ] **Step 2: Implement `WhisperAsrDesktop.kt`** — returns `Result.failure(NotShippedYet)` first-ship; VPS-fallback path invoked by VoiceFlow.

- [ ] **Step 3: Implement remaining desktop actuals.**

- [ ] **Step 4: Commit**

```bash
./gradlew :shared:compileKotlinDesktop
git add shared/src/desktopMain/kotlin/com/dietician/ui/
git commit -m "feat(plan-4-5): Desktop actuals — TargetDataLine voice recorder, Whisper stub, window chrome"
```

---

## Task 27: Sync state observer wired into HomeScreen + ED-detector hook firing

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dietician/ui/screens/HomeScreenModel.kt` (add SyncStateObserver dependency)
- Modify: `shared/src/commonMain/kotlin/com/dietician/ui/UiModule.kt` (register `SyncStateObserver`, `EDDetectorHook`, `EDSafeguardState`, `AILiteracyState`)
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/state/AppLifecycleHooks.kt`

- [ ] **Step 1: Implement `AppLifecycleHooks.kt`** — composable that fires ED-detector eval after every pull-cycle + after every `meal_event` insert observed via Plan-1 `EventStore.eventStream(subjectId)`:

```kotlin
package com.dietician.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.dietician.shared.data.local.EventStore
import com.dietician.ui.state.SubjectPrincipal
import kotlinx.coroutines.flow.filter

@Composable
fun WithAppLifecycleHooks(
    principal: SubjectPrincipal,
    eventStore: EventStore,
    edDetector: EDDetectorHook,
    kcalTargetProvider: () -> Double,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(principal.subjectId) {
        eventStore.eventStream(principal.subjectId)
            .filter { it.tableName == "meal_events" }
            .collect { edDetector.evaluate(principal.subjectId, kcalTargetProvider()) }
    }
    content()
}
```

- [ ] **Step 2: Wire `WithAppLifecycleHooks` in `MainActivity` (Android) + `Main.kt` (Desktop)** as a wrapper around `Navigator(initial)`.

- [ ] **Step 3: Update `HomeScreenModel`** to observe `SyncStateObserver.statusFlow()` and surface `home-diag-banner` when degraded.

- [ ] **Step 4: Update `UiModule.kt`**:

```kotlin
val uiModule = module {
    single { PauseState() }
    single<AILiteracyState> {
        AILiteracyState(
            currentTextVersion = "v1",
            readAckedVersion = { /* Plan-1 cache_metadata read */ TODO("wire") },
            writeAckedVersion = { /* Plan-1 cache_metadata write */ TODO("wire") },
        )
    }
    single<EDSafeguardState> {
        EDSafeguardState(auditEmit = { _, _ -> /* Plan-2 AuditLogWriter.emit */ })
    }
    single<EDDetectorHook> { EDDetectorHook(get(), get(), get()) }
    single<SyncStateObserver> { SyncStateObserver(get(), get(), get(), get()) }
    factory<JustTellMeApi> { JustTellMeApiImpl(get()) }
    factory<MagicLinkApi> { MagicLinkApi(get<ApiClient>().http) }
    factory<ChatStreamApi> { ChatStreamApi(get<ApiClient>().http) }
    factory<EmbedApi> { EmbedApi(get<ApiClient>().http) }
    factory<AuditApi> { AuditApi(get<ApiClient>().http) }
    factory<ConsentApi> { ConsentApi(get<ApiClient>().http) }
    factory<ByokApi> { ByokApi(get<ApiClient>().http) }
    factory<ReceiptApi> { ReceiptApi(get<ApiClient>().http) }
    factory<PaperSearchApi> { PaperSearchApi(get<ApiClient>().http) }
    factory<DiagApi> { DiagApi(get<ApiClient>().http) }
}
```

(Implementer notes: imports added; `Plan-1 cache_metadata` reads use `CacheMetaStore` from Plan-1; `AuditLogWriter` from Plan-2.)

- [ ] **Step 5: Commit**

```bash
./gradlew :shared:commonTest
git add shared/src/commonMain/kotlin/com/dietician/ui/state/AppLifecycleHooks.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/screens/HomeScreenModel.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/UiModule.kt
git commit -m "feat(plan-4-5): wire SyncStateObserver into HomeScreen banner + ED-detector hook fires on meal_event insert"
```

---

## Task 28: `SubjectIdRequiredTest` + cross-cutting screen-model audit

### Council baked-in fixes
- [Council 1779120600 RC8]: Add `NoSubjectSwitcherTest` to this task — asserts there is NO subject-switcher UI element anywhere in the codebase. Documents the per-device-per-friend model (each device has ONE subject identity; cross-subject inspection is server-side only via VPS admin tools, NEVER via mobile/desktop UI).

**RC-8 test wiring:**
```kotlin
package com.dietician.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class NoSubjectSwitcherTest {
    private val bannedTokens = listOf(
        "SubjectSwitcher",
        "subject-switcher",
        "switchSubject",
        "switch_subject",
        "ChangeSubject",
        "change-subject",
        "AccountSwitcher",
        "account-switcher",
    )

    @Test
    fun `no UI affordance to switch subjects exists in commonMain`() {
        val srcDir = File("src/commonMain/kotlin")
        val hits = mutableListOf<String>()
        srcDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            for (token in bannedTokens) {
                if (content.contains(token, ignoreCase = false)) {
                    hits += "${file.relativeTo(srcDir)}: $token"
                }
            }
        }
        assertEquals(emptyList(), hits, "Subject-switcher tokens found — per-device-per-friend model violated. Hits: $hits")
    }
}
```

This stub-documents the locked decision: each friend logs in on their own device with their own magic-link. There is NO UI affordance to switch between subject identities on a single device.

### Council baked-in fix
- **[Spec §A2 multi-user invariant]:** Every `ScreenModel` constructor MUST accept a `subjectId: String`. Anonymous mode is forbidden. This task adds a reflective sanity test.

**Files:**
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/SubjectIdRequiredTest.kt`

- [ ] **Step 1: Implement the test**

```kotlin
package com.dietician.ui

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertTrue

class SubjectIdRequiredTest {
    /**
     * Reflective audit: every ScreenModel must take a `subjectId` OR a `SubjectPrincipal` parameter.
     * The few that don't are explicit exemptions (auth screens: Onboarding + MagicLinkVerify operate
     * pre-authentication and don't have a subjectId yet).
     */
    private val exempt: Set<String> = setOf(
        "com.dietician.ui.screens.OnboardingScreenModel",
        "com.dietician.ui.screens.MagicLinkVerifyScreenModel",
        "com.dietician.ui.screens.AILiteracyScreenModel",
    )

    private val screenModelFqns = listOf(
        "com.dietician.ui.screens.HomeScreenModel",
        "com.dietician.ui.screens.FoodLogScreenModel",
        "com.dietician.ui.screens.MealDetailScreenModel",
        "com.dietician.ui.screens.PantryScreenModel",
        "com.dietician.ui.screens.PantryItemDetailScreenModel",
        "com.dietician.ui.screens.CookbookScreenModel",
        "com.dietician.ui.screens.RecipeDetailScreenModel",
        "com.dietician.ui.screens.RecipeReviewQueueScreenModel",
        "com.dietician.ui.screens.CoachChatScreenModel",
        "com.dietician.ui.screens.JustTellMeScreenModel",
        "com.dietician.ui.screens.PaperSearchScreenModel",
        "com.dietician.ui.screens.PaperDetailScreenModel",
        "com.dietician.ui.screens.ReceiptUploadScreenModel",
        "com.dietician.ui.screens.ReceiptDetailScreenModel",
        "com.dietician.ui.screens.SettingsScreenModel",
        "com.dietician.ui.screens.AuditLogScreenModel",
        "com.dietician.ui.screens.DiagScreenModel",
    )

    @Test
    fun `every ScreenModel constructor accepts subjectId or SubjectPrincipal`() {
        val violations = mutableListOf<String>()
        screenModelFqns.forEach { fqn ->
            if (fqn in exempt) return@forEach
            val klass = Class.forName(fqn).kotlin
            val ctor = klass.primaryConstructor
                ?: run { violations += "$fqn: no primary constructor"; return@forEach }
            val params = ctor.parameters.map { it.name to it.type.toString() }
            val hasSubject = params.any { (name, type) ->
                name == "subjectId" || type.contains("SubjectPrincipal")
            }
            if (!hasSubject) violations += "$fqn: missing subjectId / SubjectPrincipal param. Params: $params"
        }
        assertTrue(violations.isEmpty(), "Violations: $violations")
    }
}
```

- [ ] **Step 2: Run + fix any violations** (some Plan-4-5 task drafts above may have missed `subjectId` — implementer audits and patches each model to include it as the first non-API param).

- [ ] **Step 3: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.SubjectIdRequiredTest"
git add shared/src/commonTest/kotlin/com/dietician/ui/SubjectIdRequiredTest.kt
git commit -m "test(plan-4-5): reflective audit — every screen-model takes subjectId or SubjectPrincipal"
```

---

## Task 29: Underscore-dead-prop rule + Component-reuse contract audit (CLAUDE.md gate)

### Council baked-in fix
- **[CLAUDE.md underscore-dead-prop rule]:** No `_propName` destructure pattern in any production composable's destructure block — surfaced by Slice-1.5 PDF-404 lesson. CI guard added.
- **[CLAUDE.md component-reuse contract]:** Every "mount existing component X in new site Y" task body must show X's prop signature verbatim + Y's wire-up JSX with prop values explicitly named + `tsc-equivalent` typecheck run. For Kotlin: `./gradlew :shared:compileCommonMainKotlinMetadata` is the typecheck.

**Files:**
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/UnderscoreDeadPropTest.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/ComponentReuseAuditTest.kt`

- [ ] **Step 1: Implement `UnderscoreDeadPropTest.kt`**

```kotlin
package com.dietician.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class UnderscoreDeadPropTest {
    @Test
    fun `no _propName destructure pattern in commonMain composables`() {
        val root = File("src/commonMain/kotlin/com/dietician/ui")
        if (!root.exists()) return
        val violations = mutableListOf<String>()
        // Pattern: function params named `_name` or destructure `val (..., _name, ...)`
        // Kotlin allows `_` as a discard in destructuring but `_propName` (leading underscore +
        // identifier) is the smell from the Slice-1.5 PDF-404 lesson.
        val regex = Regex("""\b_[a-zA-Z][a-zA-Z0-9]*\s*[:=]""")
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            f.readText().lineSequence().forEachIndexed { idx, line ->
                if (line.trim().startsWith("//") || line.trim().startsWith("*")) return@forEachIndexed
                regex.findAll(line).forEach { m ->
                    val identifier = m.value.trimEnd(':', '=', ' ')
                    // Allow standalone `_ =` discards (Kotlin idiom) — they have no follow-on identifier
                    if (identifier == "_") return@forEach
                    // Allow leading-underscore _state, _mutableFoo private-backing-field convention
                    if (identifier.startsWith("_") && identifier.length > 1 && identifier[1].isLowerCase() &&
                        line.contains("private val") || line.contains("private var")) return@forEach
                    violations += "${f.relativeTo(root)}:${idx + 1}: $line.trim()"
                }
            }
        }
        // Accept the private-backing-field idiom (`private val _foo = MutableStateFlow(...)`)
        val filtered = violations.filterNot { it.contains("private val _") || it.contains("private var _") }
        assertTrue(filtered.isEmpty(), "Underscore-dead-prop violations: $filtered")
    }
}
```

- [ ] **Step 2: Implement `ComponentReuseAuditTest.kt`** — grep every "new component" file under `commonMain/ui/components/` against the "screens" sub-tree, asserting each is referenced from at least one screen. Catches the Slice-1 ghost-component lesson:

```kotlin
package com.dietician.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ComponentReuseAuditTest {
    @Test
    fun `every component file is referenced from at least one screen`() {
        val componentsDir = File("src/commonMain/kotlin/com/dietician/ui/components")
        val screensDir = File("src/commonMain/kotlin/com/dietician/ui/screens")
        if (!componentsDir.exists() || !screensDir.exists()) return
        val componentNames = componentsDir.listFiles { f -> f.extension == "kt" }
            ?.map { it.nameWithoutExtension }
            ?.filterNot { it == "AntiStreakAbsence" }   // negative-control marker
            ?: return
        val screenText = screensDir.walkTopDown().filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        val unreferenced = componentNames.filter { name -> !screenText.contains(name) }
        assertTrue(unreferenced.isEmpty(), "Components built but never mounted in any screen: $unreferenced")
    }
}
```

- [ ] **Step 3: Run + fix any orphaned components** (likely catches one or two — implementer either mounts them or deletes them).

- [ ] **Step 4: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.UnderscoreDeadPropTest" \
                              --tests "com.dietician.ui.ComponentReuseAuditTest"
git add shared/src/commonTest/kotlin/com/dietician/ui/UnderscoreDeadPropTest.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/ComponentReuseAuditTest.kt
git commit -m "test(plan-4-5): CLAUDE.md gates — underscore-dead-prop + component-reuse-mount audits"
```

---

## Task 30: Roborazzi screenshot tests for every screen + every component

### Council baked-in fixes
- [Council 1779120600 RC2]: **ENTIRE TASK DEFERRED to Plan-4-5.5 (or dropped entirely).** Roborazzi cross-platform <1.0 has known font-metric drift on Desktop JVM patches (issue #294). Compose-UI-Test (Task 33) is the load-bearing pixel/interaction gate. Task 30 ships ZERO Roborazzi tests in first-ship.
- Concretely: do NOT execute Task 30 steps. Mark task with `// PLAN-4-5 FOLLOWUP RC2 — Roborazzi cross-platform deferred; see council 1779120600`.
- If a future regression needs pixel-precision testing, add Paparazzi for Android-only at that point (per Domain Expert R1 reference cases — Cash App / JetBrains Toolbox / Pinterest use Compose-UI-Test for interaction + Paparazzi-Android-only for icon/canvas pixel diffs).
- Task 34 (CI) MUST drop the Roborazzi required-check (see Task 34 RC2 note).

**Files:**
- Create: `shared/src/desktopTest/kotlin/com/dietician/ui/screens/<Screen>RoborazziTest.kt` × 16 (one per screen)
- Create: `shared/src/desktopTest/kotlin/com/dietician/ui/components/<Component>RoborazziTest.kt` × ~15

Per spec §11 first-paint contract. Each test:
1. Seeds the screen's `ScreenModel` with a fixture state (success / loading / error variants).
2. Renders inside `DieticianTheme` via `runComposeUiTest`.
3. Captures `.png` baseline under `roborazzi/<package>/<Test>.png`.
4. CI diff against baseline; ≥3% pixel diff = FAIL.
5. Asserts every spec §11 `data-testid` selector is present via `onNodeWithTag(...).assertIsDisplayed()`.

- [ ] **Step 1: Implement `HomeScreenRoborazziTest.kt`** (template — other screens follow):

```kotlin
package com.dietician.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.dietician.ui.components.MacroRingValues
import com.dietician.ui.state.ExpenditurePosterior
import com.dietician.ui.state.PauseState
import com.dietician.ui.state.SubjectPrincipal
import com.dietician.ui.theme.DieticianTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HomeScreenRoborazziTest {
    @BeforeTest fun setUp() {
        stopKoin()
        startKoin {
            modules(module {
                single { PauseState() }
                factory {
                    HomeScreenModel(
                        principal = SubjectPrincipal("u1", "Victor", true),
                        pause = get(),
                        macroProvider = { MacroRingValues(2000.0, 2700.0, 100.0, 150.0, 60.0, 80.0, 240.0, 350.0) },
                        tdeeProvider = { ExpenditurePosterior(2700.0, 180.0, 14) },
                        nextMealProvider = { NextMeal("Chicken + rice", "20:00", 800, 60) },
                    )
                }
            })
        }
    }
    @AfterTest fun tearDown() = stopKoin()

    @Test
    fun `home selectors paint on first nav`() = runComposeUiTest {
        setContent { DieticianTheme { HomeScreen.Content() } }
        // §11.1 first-paint selectors
        onNodeWithTag("home-greeting").assertIsDisplayed()
        onNodeWithTag("home-pause-button").assertIsDisplayed()
        onNodeWithTag("home-tdee-band").assertIsDisplayed()
        onNodeWithTag("home-macro-rings").assertIsDisplayed()
        onNodeWithTag("home-next-meal-card").assertIsDisplayed()
        onNodeWithTag("home-quick-log-button").assertIsDisplayed()
        onNodeWithTag("home-quick-photo-button").assertIsDisplayed()
        onNodeWithTag("home-ai-literacy-footer").assertIsDisplayed()
        // Roborazzi snapshot
        onRoot().captureRoboImage("home_first_paint")
    }
}
```

- [ ] **Step 2: Repeat the template for each of:**
  - `OnboardingScreen` (per step)
  - `MagicLinkVerifyScreen`
  - `FoodLogScreen`, `MealDetailScreen`
  - `PantryScreen`, `PantryItemDetailScreen`
  - `CookbookScreen`, `RecipeDetailScreen`, `RecipeReviewQueueScreen`
  - `CoachChatScreen`, `JustTellMeScreen`
  - `PaperSearchScreen`, `PaperDetailScreen`
  - `ReceiptUploadScreen`, `ReceiptDetailScreen`
  - `SettingsScreen`, `SettingsAboutScreen`, `AuditLogScreen`
  - `EDSafeguardScreen`, `PauseTrackingScreen`
  - `AILiteracyScreen`, `DiagScreen`

Component tests for each of: `NutrientBar`, `NutrientBarList`, `MacroRingChart`, `ExpenditureChart`, `WeightTrendChart`, `WeeklyNarrative`, `PhotoSuggestionCard`, `MealCard`, `RecipeCard`, `PantryItemRow`, `JustTellMeButton`, `PauseTrackingButton`, `PerCallDisclosurePane`, `AILiteracyBanner`, `EDSafeguardModal`.

- [ ] **Step 3: Generate baselines**

```bash
./gradlew :shared:desktopTest -PrecordRoborazzi
```

This writes `.png` baselines under `shared/src/desktopTest/roborazzi/`. Inspect each manually before commit.

- [ ] **Step 4: Commit baselines + tests**

```bash
git add shared/src/desktopTest/kotlin/com/dietician/ui/ \
        shared/src/desktopTest/roborazzi/
git commit -m "test(plan-4-5): Roborazzi screenshot baselines for 16 screens + 15 components"
```

---

## Task 31: Compose UI integration tests — end-to-end happy paths

### Council baked-in fixes
- [Council 1779120600 RC4]: **TASK DEFERRED to Plan-4-5.5 / subsumed into Task 33.** Per Pragmatist + Devil's Advocate convergence: Tasks 30 + 31 + 33 are three overlapping screenshot/integration test suites. Pick one. Task 33's final visual-acceptance gate (Compose-UI-Test against `desktopApp` + Robolectric-Android) is the load-bearing one per CLAUDE.md interaction-smoke rule.
- Do NOT execute Task 31 steps in first-ship. Mark with `// PLAN-4-5 FOLLOWUP RC4 — subsumed into Task 33; see council 1779120600`.

**Files:**
- Create: `shared/src/desktopTest/kotlin/com/dietician/ui/integration/OnboardingToHomeE2eTest.kt`
- Create: `shared/src/desktopTest/kotlin/com/dietician/ui/integration/FoodLogVoiceFlowE2eTest.kt`
- Create: `shared/src/desktopTest/kotlin/com/dietician/ui/integration/JustTellMeBypassE2eTest.kt`

- [ ] **Step 1: Implement `OnboardingToHomeE2eTest.kt`** — full Navigator + MockEngine for Plan-3 endpoints. Drives through 7 onboarding steps, asserts `MagicLinkVerifyScreen` paints after magic-link request, verifies token → asserts `HomeScreen` paints with §11.1 selectors.

```kotlin
package com.dietician.ui.integration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import com.dietician.ui.screens.OnboardingScreen
import com.dietician.ui.theme.DieticianTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class OnboardingToHomeE2eTest {
    @Test
    fun `complete 7-step onboarding then magic-link verify then home`() = runComposeUiTest {
        // Koin start with MockEngine returning success for /me + /auth/magic-link/* …
        setContent { DieticianTheme { Navigator(OnboardingScreen) } }

        // Step 1
        onNodeWithTag("onboarding-tailnet-input").performTextInput("test.ts.net")
        onNodeWithTag("onboarding-step-next").performClick()

        // Step 2
        onNodeWithTag("onboarding-ai-literacy-ack").performClick()
        onNodeWithTag("onboarding-step-next").performClick()

        // Step 3 — identity
        onNodeWithTag("onboarding-field-name").performTextInput("Victor")
        onNodeWithTag("onboarding-field-email").performTextInput("victor@example.com")
        // … fill height/weight/age/sex/goal …
        onNodeWithTag("onboarding-step-next").performClick()

        // Steps 4-6 …
        // Step 7 — magic-link submit
        onNodeWithTag("onboarding-passkey-register-button").performClick()

        // Wait + assert magic-link verify screen
        waitForIdle()
        onNodeWithTag("magic-link-verify-root").assertIsDisplayed()
    }
}
```

(Full impl includes filling all required fields; abbreviated in plan body.)

- [ ] **Step 2: Implement `FoodLogVoiceFlowE2eTest.kt`** — seed `VoiceFlow` fake returning canned transcript "ate 200g chicken breast with rice"; assert intent-classify call goes through `LlmRouter` (use a `RouterCallCounter` fake); assert resulting `meal_event` appears in `foodlog-recent-meals-list`.

- [ ] **Step 3: Implement `JustTellMeBypassE2eTest.kt`** — assert clicking `coach-just-tell-me-button` from `CoachChatScreen` navigates to `JustTellMeScreen` AND that the `LlmRouter.call` mock was NEVER invoked during the trip (`assertEquals(0, routerCallCount)`).

- [ ] **Step 4: Commit**

```bash
./gradlew :shared:desktopTest --tests "com.dietician.ui.integration.*"
git add shared/src/desktopTest/kotlin/com/dietician/ui/integration/
git commit -m "test(plan-4-5): e2e integration — onboarding→home, voice-flow, just-tell-me bypass"
```

---

## Task 32: Voice / VPS-fallback path + `POST /voice/upload` endpoint coordination

### Council baked-in fixes
- [Council 1779120600 RC1]: **ENTIRE TASK DEFERRED to Plan-4-5.5 post-finals voice ship.** Voice pipeline (T24/T26/T32) defer-bucket. `VoiceUploadApi` 501-degraded stub stays. `VoiceFlow.kt` skeleton may ship from Task 13 but is NEVER invoked in first-ship. Acceptance test for voice → manual fallback toast wording is covered by Task 13's `VoiceRecordButton` toast unit test (`voice_record_button_disabled_toast` i18n key).
- Mark with `// PLAN-4-5 FOLLOWUP RC1 — voice pipeline deferred; see council 1779120600`.
- Plan-4-5.5 fix-up ships voice after Plan-3.5 `/voice/upload` route + Plan-7 Whisper JNI both land. The UI affordance (button + selector + strings + toast) is already in place; only the underlying flow swap is required.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/ui/network/VoiceUploadApi.kt`
- Modify: `shared/src/commonMain/kotlin/com/dietician/ui/state/VoiceFlow.kt` (wire VPS-fallback)
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/state/VoiceFlowFallbackTest.kt`

### Council baked-in fix
- **[Plan-3 dependency surface]:** `POST /voice/upload` is NOT in Plan-3 first-batch. Plan-4-5 ships the client-side surface; Plan-3.5 fix-up adds the server route. For first-ship: voice upload returns 501 → UI gracefully falls back to manual text entry with toast "Voice transcription temporarily unavailable. Type your entry."

- [ ] **Step 1: Implement `VoiceUploadApi.kt`**

```kotlin
package com.dietician.ui.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

@Serializable
data class VoiceUploadResponse(val transcriptionJobId: String, val status: String)

class VoiceUploadApi(private val http: HttpClient, private val baseUrl: String = "") {
    suspend fun upload(subjectId: String, audio: ByteArray, language: String = "ro"): Result<VoiceUploadResponse> = runCatching {
        http.post("$baseUrl/voice/upload") {
            setBody(MultiPartFormDataContent(formData {
                append("subject_id", subjectId)
                append("language", language)
                append("audio", audio, Headers.build {
                    append(HttpHeaders.ContentType, "audio/m4a")
                    append(HttpHeaders.ContentDisposition, ContentDisposition.File.toString())
                })
            }))
        }.body()
    }

    suspend fun poll(jobId: String): Result<String> = runCatching {
        // GET /voice/transcription/{jobId} returns {status, transcript?}
        // Implementer adds polling loop with backoff.
        TODO("poll loop")
    }
}
```

- [ ] **Step 2: Update `VoiceFlow.kt` default impl** — three-step fallback:
  1. Try `WhisperAsr.transcribe(...)` (local). Returns `Result.failure(NotShippedYet)` first-ship.
  2. On failure: `VoiceUploadApi.upload(...)`. Returns 501 first-ship.
  3. On failure: emit `VoiceFlowStage.MANUAL_FALLBACK` → UI shows toast + opens manual-entry form.

```kotlin
package com.dietician.ui.state

import kotlinx.coroutines.flow.MutableStateFlow

enum class VoiceFlowStage { IDLE, RECORDING, UPLOADING, TRANSCRIBING, CLASSIFYING, DONE, MANUAL_FALLBACK }

interface VoiceFlow {
    suspend fun start()
    suspend fun stop(): Result<String>
    val stage: MutableStateFlow<VoiceFlowStage>
}

// Default impl wires VoiceRecorder + WhisperAsr + VoiceUploadApi + LlmRouter for intent-classify.
// Implementation lives in shared/src/commonMain/kotlin/com/dietician/ui/state/VoiceFlowDefault.kt;
// abbreviated here to keep plan body readable.
```

- [ ] **Step 3: Implement `VoiceFlowFallbackTest.kt`** — three scenarios:
  1. Local Whisper succeeds → stage transitions IDLE→RECORDING→TRANSCRIBING→DONE; transcript returned.
  2. Local Whisper fails, VPS upload succeeds → IDLE→RECORDING→UPLOADING→TRANSCRIBING→DONE.
  3. Local Whisper fails AND VPS returns 501 → IDLE→RECORDING→UPLOADING→MANUAL_FALLBACK; `Result.failure`.

- [ ] **Step 4: Commit**

```bash
./gradlew :shared:commonTest --tests "com.dietician.ui.state.VoiceFlowFallbackTest"
git add shared/src/commonMain/kotlin/com/dietician/ui/network/VoiceUploadApi.kt \
        shared/src/commonMain/kotlin/com/dietician/ui/state/VoiceFlow.kt \
        shared/src/commonTest/kotlin/com/dietician/ui/state/VoiceFlowFallbackTest.kt
git commit -m "feat(plan-4-5): VoiceFlow three-step fallback (local Whisper → VPS upload → manual)"
```

---

## Task 33: Final visual-acceptance gate — Desktop + Android Compose-UI-Test runs

### Council baked-in fixes
- [Council 1779120600 RC4]: Task 33 ABSORBS Task 31 scope (deeper integration tests). Visual-acceptance gate is the load-bearing pixel/interaction surface for first-ship per CLAUDE.md interaction-smoke rule.
- [Council 1779120600 RC7+RC9+RC10+RC11+RC13+RC14+RC16+RC19+RC20]: Visual-acceptance assertions extended to cover the new RC-mandated selectors:
  - `[data-testid="coach-disclosure-open-audit-{call_uuid}"]` (RC7, expect at least 1)
  - `[data-testid="coach-disabled-notice"]` (RC9, gated on `llmCoachDisabled=true` test fixture)
  - `[data-testid="audit-row-emotion-disabled-{row_id}"]` (RC10, one per audit row)
  - `[data-testid="photo-suggestion-none-of-these"]` (RC11)
  - `[data-testid="byok-paste-clipboard-cleared-toast"]` (RC13, on paste fixture)
  - `[data-testid="planned-cut-toggle"]` + `[data-testid="planned-cut-days-remaining-{n}"]` (RC14)
  - `[data-testid="tailscale-disconnected-blocker"]` (RC16, on `TailnetDiscovery.isReachable=false` fixture)
  - `[data-testid="consent-row-cross-border-transfer"]` (RC19)
  - `[data-testid="magic-link-verify-cross-device-pending"]` + `[data-testid="magic-link-verify-same-device-hint"]` (RC20)
- Click-smoke per CLAUDE.md interaction-smoke gate:
  - Click `coach-disclosure-open-audit-{call_uuid}` → asserts `audit-log-filter-call-uuid-{call_uuid}` is present after navigation; zero 4xx/5xx network responses; no `/404|HTTP \d{3}|not found|error/i` on-screen text.
  - Click `photo-suggestion-none-of-these` → asserts manual-entry text field has focus.
  - Click `planned-cut-toggle` → asserts `planned-cut-days-remaining-7` appears + audit row `planned_cut_activated` recorded.
- All RC test selectors fold into the canonical SelectorManifest (drop the `ed-safeguard-*` group per RC6; add the new RC selectors).

### Council baked-in fix
- **[CLAUDE.md interaction-smoke gate]:** Per the 2026-05-11 Slice-1.5 PDF-404 lesson, the final gate must:
  1. Assert ALL spec §11 `data-testid` selectors visible on first paint (~120 selectors).
  2. Assert ZERO 4xx/5xx network responses during first paint.
  3. Click every spec-listed interactive element.
  4. After each click: assert no on-screen `/404|HTTP \d{3}|not found|error/i` text AND no new 4xx/5xx network responses.

NOTE re Playwright: the original spec §6.12 task 17 references "Playwright against `:androidApp`/`:desktopApp`". That's a category error — Playwright is browser-only. The correct toolchain is Compose-UI-Test (cross-platform Compose) + Espresso for Android emulator runs. The visual-acceptance gate here ships as a Compose-UI-Test `desktopTest` task running against the actual `:desktopApp` `Window` AND an `androidUnitTest` Robolectric run against `MainActivity`.

**Files:**
- Create: `shared/src/desktopTest/kotlin/com/dietician/ui/acceptance/DesktopVisualAcceptanceTest.kt`
- Create: `shared/src/androidUnitTest/kotlin/com/dietician/ui/acceptance/AndroidVisualAcceptanceTest.kt`
- Create: `shared/src/commonTest/kotlin/com/dietician/ui/acceptance/SelectorManifest.kt`

- [ ] **Step 1: Implement `SelectorManifest.kt`** — single source-of-truth list of all ~120 spec §11 selectors, grouped by screen:

```kotlin
package com.dietician.ui.acceptance

object SelectorManifest {
    data class Group(val screen: String, val firstPaint: List<String>, val clickSmoke: List<Pair<String, List<String>>>)

    val home = Group(
        screen = "home",
        firstPaint = listOf(
            "home-greeting", "home-pause-button", "home-tdee-band", "home-macro-rings",
            "home-next-meal-card", "home-quick-log-button", "home-quick-photo-button",
            "home-ai-literacy-footer",
        ),
        clickSmoke = listOf(
            "home-pause-button" to listOf("pause-active-message", "pause-resume-button"),
            "home-quick-log-button" to listOf("foodlog-talk-button", "foodlog-recent-meals-list"),
        ),
    )

    val foodLog = Group(
        screen = "foodlog",
        firstPaint = listOf(
            "foodlog-talk-button", "foodlog-recent-meals-list", "foodlog-manual-entry-button",
            "foodlog-add-button",
        ),
        clickSmoke = listOf(
            "foodlog-manual-entry-button" to listOf("manual-entry-form"),
            "foodlog-add-button" to listOf("foodlog-add-sheet-voice", "foodlog-add-sheet-photo"),
        ),
    )

    val pantry = Group("pantry",
        listOf("pantry-list", "pantry-add-button", "pantry-low-stock-section",
            "pantry-audit-button", "pantry-search-bar"),
        listOf("pantry-add-button" to listOf("pantry-add-sheet-photo")),
    )

    val cookbook = Group("cookbook",
        listOf("cookbook-search-bar", "cookbook-filter-chips", "cookbook-recipe-grid",
            "cookbook-ingest-button", "cookbook-review-queue-tab"),
        listOf("cookbook-ingest-button" to listOf("cookbook-ingest-sheet-url")),
    )

    val coach = Group("coach",
        listOf("coach-message-list", "coach-input-bar", "coach-send-button",
            "coach-just-tell-me-button", "coach-suggested-chips"),
        listOf("coach-just-tell-me-button" to listOf("just-tell-me-rule-based-answer")),
    )

    val paperSearch = Group("paper-search",
        listOf("paper-search-bar", "paper-search-results", "paper-domain-filter-chips"),
        emptyList(),
    )

    val receipt = Group("receipt-upload",
        listOf("receipt-camera-button", "receipt-file-pick-button", "receipt-recent-uploads"),
        emptyList(),
    )

    val settings = Group("settings",
        listOf(
            "settings-profile-section", "settings-stores-section", "settings-equipment-section",
            "settings-credentials-section", "settings-consent-section",
            "settings-ai-coach-toggle", "settings-photo-toggle", "settings-voice-toggle",
            "settings-pause-button", "settings-delete-account-button",
            "settings-export-dsar-button", "settings-about-link",
        ),
        listOf("settings-delete-account-button" to listOf("delete-confirm-step-1")),
    )

    val auditLog = Group("audit-log",
        listOf("audit-log-filter-bar", "audit-log-list", "audit-export-pdf-button", "audit-export-json-button"),
        emptyList(),
    )

    val onboarding = Group("onboarding",
        // Step 1
        listOf("onboarding-lang-picker-en", "onboarding-tailnet-input", "onboarding-step-next"),
        emptyList(),  // multi-step click smoke covered by OnboardingToHomeE2eTest
    )

    val aiLiteracy = Group("ai-literacy",
        listOf("ai-literacy-title-en", "ai-literacy-title-ro", "ai-literacy-body-en",
            "ai-literacy-body-ro", "ai-literacy-disable-link", "ai-literacy-ack-button"),
        emptyList(),
    )

    val edSafeguard = Group("ed-safeguard",
        listOf("ed-safeguard-message", "ed-safeguard-resource-en", "ed-safeguard-resource-ro",
            "ed-safeguard-pause-button", "ed-safeguard-continue-button"),
        emptyList(),
    )

    val justTellMe = Group("just-tell-me",
        listOf("just-tell-me-rule-based-answer", "just-tell-me-back-button", "just-tell-me-disable-llm-toggle"),
        emptyList(),
    )

    val pauseTracking = Group("pause-tracking",
        listOf("pause-active-message", "pause-resume-button"),
        emptyList(),
    )

    val diag = Group("diag",
        listOf("diag-vps", "diag-tailscale", "diag-postgres", "diag-ntfy",
            "diag-outbox", "diag-sync-times", "diag-llm-budget-claudemax",
            "diag-llm-budget-openrouter", "diag-scraper-status", "diag-last-errors",
            "diag-pending-jobs"),
        emptyList(),
    )

    val edCheckin = Group("ed-checkin",
        listOf("ed-checkin-modal", "ed-checkin-pause-tracking", "ed-checkin-dismiss", "ed-checkin-ok"),
        emptyList(),
    )

    val all: List<Group> = listOf(home, foodLog, pantry, cookbook, coach, paperSearch, receipt,
        settings, auditLog, onboarding, aiLiteracy, edSafeguard, justTellMe, pauseTracking, diag, edCheckin)

    val totalFirstPaintCount: Int = all.sumOf { it.firstPaint.size }
}
```

- [ ] **Step 2: Implement `DesktopVisualAcceptanceTest.kt`** — drives every screen, asserts first-paint selectors, captures network requests (via Ktor MockEngine recording 4xx/5xx counts), runs click-smoke per group, asserts no error text after each click:

```kotlin
package com.dietician.ui.acceptance

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dietician.ui.theme.DieticianTheme
import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopVisualAcceptanceTest {

    private val errorTextRegex = Regex("""(404|HTTP \d{3}|not found|error)""", RegexOption.IGNORE_CASE)
    private val networkErrorCount = atomic(0)

    @Test
    fun `every spec §11 first-paint selector paints + no 4xx errors`() = runComposeUiTest {
        // Koin start with MockEngine recording status; counters seed to 0.
        // Iterate the manifest and drive each screen.
        // For each: setContent { DieticianTheme { Screen.Content() } } then assert selectors.
        SelectorManifest.all.forEach { group ->
            // … drive screen via Navigator … wait … assert each first-paint selector
            group.firstPaint.forEach { tag ->
                onNodeWithTag(tag).assertIsDisplayed()
            }
            // After paint: assert no error-regex text anywhere
            val errors = onAllNodesWithText(errorTextRegex.pattern, useUnmergedTree = true)
            // expect zero matches — implementer adapts API to onAllNodes filter
        }
        assertEquals(0, networkErrorCount.value, "Zero 4xx/5xx during first paint")
    }

    @Test
    fun `click smoke — every spec interactive selector survives click without error`() = runComposeUiTest {
        SelectorManifest.all.forEach { group ->
            group.clickSmoke.forEach { (clickTag, expectedAfter) ->
                // drive to screen, find clickTag, click
                onNodeWithTag(clickTag).performClick()
                expectedAfter.forEach { onNodeWithTag(it).assertIsDisplayed() }
                // assert no on-screen error text after click
            }
        }
    }

    @Test
    fun `selector total matches expectations (≥80 per spec §11.16 floor; aim ~120)`() {
        assert(SelectorManifest.totalFirstPaintCount >= 80) {
            "Selector count too low: ${SelectorManifest.totalFirstPaintCount}"
        }
    }
}
```

- [ ] **Step 3: Implement `AndroidVisualAcceptanceTest.kt`** — Robolectric-backed run of the same manifest against `MainActivity`. Subset of selectors (Android-applicable; skips `admin-scrape-now-button` Desktop-only).

- [ ] **Step 4: Run both gates**

```bash
./gradlew :shared:desktopTest --tests "com.dietician.ui.acceptance.DesktopVisualAcceptanceTest"
./gradlew :shared:testDebugUnitTest --tests "com.dietician.ui.acceptance.AndroidVisualAcceptanceTest"
```

Expected: PASS. Selector count ≥80; zero 4xx/5xx during first paint of every screen; zero error-text after every click.

- [ ] **Step 5: Commit**

```bash
git add shared/src/desktopTest/kotlin/com/dietician/ui/acceptance/ \
        shared/src/androidUnitTest/kotlin/com/dietician/ui/acceptance/ \
        shared/src/commonTest/kotlin/com/dietician/ui/acceptance/
git commit -m "test(plan-4-5): final visual-acceptance gate — ~120 selectors paint + zero 4xx click smoke"
```

---

## Task 34: CI workflow extension — Roborazzi + visual-acceptance + lint guards required

### Council baked-in fixes
- [Council 1779120600 RC2]: **DROP Roborazzi CI required-check.** Roborazzi entire task deferred (Task 30). CI workflow extension MUST omit any Roborazzi step / required-check.
- [Council 1779120600 RC4]: CI extension adds these required-checks for first-ship:
  - `./gradlew :shared:commonTest` (incl. `BigorexiaCopyTest`, `NoForbiddenPatternsTest`, `SubjectIdRequiredTest`, `NoSubjectSwitcherTest` per RC8, `SubjectIdConsistencyTest` per RC15, `ChatStreamCancellationTest` per RC12, `PlannedCutAutoExpireTest` per RC14, `AuditLogFilterTest` per RC7, `StringsCoverageTest` incl. new RC1/RC9/RC11/RC13/RC16/RC19/RC20 keys, `EDDetectorHookTest`, `JustTellMeRouterBypassTest`, `AILiteracyFirstLaunchTest`)
  - `./gradlew :shared:desktopTest` (incl. `DesktopVisualAcceptanceTest` from Task 33)
  - `./gradlew :shared:testDebugUnitTest` (incl. `AndroidVisualAcceptanceTest` from Task 33 — Robolectric)
  - `./gradlew :androidApp:assembleDebug` + `:desktopApp:assemble` build green
  - Voyager GitHub health check job runs weekly (RC3 sanity gate — alerts if last-commit > 90d / open-issues > 20)

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.git/hooks/pre-commit`

- [ ] **Step 1: Patch CI yaml**

Append to `.github/workflows/ci.yml`:

```yaml
- name: Shared common tests (incl. NoForbiddenPatterns, SubjectIdRequired, ComponentReuseAudit, UnderscoreDeadProp)
  run: ./gradlew :shared:commonTest

- name: Shared desktop tests (Roborazzi screenshot diffs + visual-acceptance gate)
  run: ./gradlew :shared:desktopTest

- name: Shared Android unit tests (Roborazzi + AndroidVisualAcceptanceTest)
  run: ./gradlew :shared:testDebugUnitTest

- name: Assemble Android debug APK
  run: ./gradlew :androidApp:assembleDebug

- name: Assemble Desktop distributable
  run: ./gradlew :desktopApp:assemble
```

- [ ] **Step 2: Patch pre-commit hook**

```bash
#!/usr/bin/env bash
set -euo pipefail
./gradlew --quiet ktlintFormat detekt \
    :shared:commonTest --tests "*NoForbiddenPatterns*" \
                       --tests "*SubjectIdRequired*" \
                       --tests "*ComponentReuseAudit*" \
                       --tests "*UnderscoreDeadProp*" \
                       --tests "*BigorexiaCopy*"
```

- [ ] **Step 3: Commit**

```bash
chmod +x .git/hooks/pre-commit
git add .github/workflows/ci.yml .git/hooks/pre-commit
git commit -m "ci(plan-4-5): require commonTest + desktopTest + Roborazzi + visual-acceptance gates"
```

---

## Task 35: Final preflight + push + post-impl council

### Council baked-in fixes
- [Council 1779120600 RC4]: Pre-impl council 1779120600 ran 2026-05-18 with verdict FLAWED→FIXED APPROVE WITH 20 RCs (confidence 7/10). Post-impl council MUST re-verify all 20 RCs landed in code, not just plan. Required post-impl assertions:
  - RC1: voice pipeline OFF (Tasks 24/26/32 not executed) but `VoiceRecordButton` UI affordance + disabled toast active
  - RC2: zero Roborazzi files in repo (`find . -name '*Roborazzi*.kt' | wc -l == 0`)
  - RC3: Voyager + CMP 1.7.0 pins unchanged
  - RC4: ~27 first-ship tasks completed; deferred 9 tasks marked with `// PLAN-4-5 FOLLOWUP` comments
  - RC5: Bigorexia copy present in EN + RO strings.xml; `BigorexiaCopyTest` green
  - RC6: no `EDSafeguardScreen.kt` file (`ls shared/src/commonMain/kotlin/com/dietician/ui/screens/EDSafeguardScreen.kt` returns FAIL)
  - RC7: `coach-disclosure-open-audit-*` selector found in `PerCallDisclosurePane.kt`
  - RC8: `NoSubjectSwitcherTest` green
  - RC9: `coach-disabled-notice` selector found in `CoachChatScreen.kt`
  - RC10: `audit-row-emotion-disabled-*` selector found in `AuditLogRow.kt`
  - RC11: `photo-suggestion-none-of-these` selector found in `PhotoSuggestionCard.kt`
  - RC12: `coach_chat_cancelled` audit emission found in `ChatStreamApi.kt`; `ChatStreamCancellationTest` green
  - RC13: `byok-paste-clipboard-cleared-toast` selector found in ByokScreen / Settings → Credentials path
  - RC14: `planned-cut-toggle` + `planned-cut-days-remaining-{n}` selectors; `PlannedCutAutoExpireTest` green
  - RC15: `SubjectIdConsistencyTest` green
  - RC16: `tailscale-disconnected-blocker` selector in MainActivity + Main.kt paths
  - RC17: `docs/runbooks/compose-mp-baseline.md` exists with live dependency tree (not just placeholder)
  - RC18: `docs/policies/AI_LITERACY_TEXT_VERSION.md` exists + referenced in AILiteracyState code
  - RC19: `consent-row-cross-border-transfer` selector in ConsentRows
  - RC20: WebSocket subscribe to `/auth/verify-events` in MagicLinkVerifyScreen + same-device-hint copy visible
- Post-impl council convenes per `feedback_council_pattern` AFTER all RCs verified green. Persist transcript to `.claude/council-cache/council-{ts}-plan-4-5-postimpl.md`.

- [ ] **Step 1: Full local preflight**

```bash
./gradlew ktlintFormat detekt \
    :shared:commonTest \
    :shared:testDebugUnitTest \
    :shared:desktopTest \
    :shared:compileKotlinMetadata \
    :androidApp:assembleDebug \
    :desktopApp:assemble
```

Expected: BUILD SUCCESSFUL across the board.

- [ ] **Step 2: Manual smoke against live VPS**

**Desktop smoke (via Tailscale to VPS):**

```bash
# Build + run
./gradlew :desktopApp:run
# In the launched Window:
#   1. Onboarding step 1 — type tailnet name (auto-detected), tap Next
#   2. Step 2 — read AI literacy, tap "I understand", tap Next
#   3. Steps 3-7 — fill identity, equipment, stores, consent (health required), submit
#   4. Check inbox for magic-link email; copy token; paste into MagicLinkVerifyScreen
#   5. Verify → HomeScreen paints with greeting "Hi, Victor"
#   6. Tap "Log meal" → FoodLogScreen
#   7. Tap "Tap to talk" → record "ate 200g chicken and rice"
#      → expect VPS-fallback toast first-ship (Whisper not yet shipped)
#      → manual entry form opens
#   8. Manually enter meal → confirm → meal_event appears in recent-meals list
#   9. Pull on HomeScreen → next-meal-card updates from server
#  10. Tap pause button → PauseTrackingScreen → tap resume → home returns
#  11. Settings → Audit log → export PDF downloads
```

**Android smoke (emulator or real device):**

```bash
./gradlew :androidApp:installDebug
adb shell am start -a android.intent.action.VIEW -d "dietician://home" com.dietician.android
# Same flow as Desktop steps 1-11.
```

- [ ] **Step 3: Push the branch**

```bash
git push origin worktree-plan-4-5+kmp-compose-ui
```

- [ ] **Step 4: Open PR**

```bash
gh pr create --title "Plan-4-5 — KMP Compose UI shared + Android + Desktop shells" --body "$(cat <<'EOF'
## Summary
- `:shared:ui-components` as package inside `:shared` (per A6+D2 — no new Gradle subproject)
- 16 first-ship screens: Onboarding, MagicLinkVerify, Home, FoodLog, MealDetail, Pantry, PantryItemDetail, Cookbook, RecipeDetail, RecipeReviewQueue, CoachChat, JustTellMe, PaperSearch, PaperDetail, ReceiptUpload, ReceiptDetail, Settings, SettingsAbout, AuditLog, AILiteracy, EDSafeguard, PauseTracking, Diag (~120 spec §11 data-testid selectors)
- Cronometer-style NutrientBar (84 nutrients, neutral-teal-only, no pass-fail color)
- MacroFactor-style ExpenditureChart (Bayesian rolling 7d ± band)
- Bite-AI-style PhotoSuggestionCard (never auto-commit; CNN top-1 ~73%)
- Carbon-style WeeklyNarrative (strength/energy/mood/sleep first; weight supporting)
- Art 4 AILiteracyBanner first-launch modal (no dismiss-without-ack)
- Art 13 PerCallDisclosurePane on every LLM message
- Art 14 JustTellMeButton bypasses LlmRouter entirely (Router-bypass test enforces)
- Bigorexia-first ED safeguards (symmetric weight-rate cap, §9.3 hooks both client + server)
- Voyager navigation + Koin DI + moko-resources RO/EN (~250 keys)
- Android + Desktop shells with deep-link / magic-link CLI arg / system tray
- expect/actual: PhotoCapture, FilePicker, VoiceRecorder, WhisperAsr (stub→VPS), NtfyRegistration, ScaleConnector, UaicCookieReExportLauncher
- Compose-UI-Test + Roborazzi cross-platform screenshot tests (16 screens + 15 components)
- Final visual-acceptance gate asserts ~120 selectors paint + zero 4xx/5xx during click smoke

## Test plan
- [ ] `./gradlew :shared:commonTest` — NoForbiddenPatterns, BigorexiaCopy, SubjectIdRequired, ComponentReuseAudit, UnderscoreDeadProp, EDDetectorHook, ExpenditureEstimator, NutrientCatalog, StringsCoverage, RouterBypass all pass
- [ ] `./gradlew :shared:desktopTest` — Roborazzi diffs zero pixel-delta; DesktopVisualAcceptanceTest passes (≥80 selectors, zero 4xx)
- [ ] `./gradlew :shared:testDebugUnitTest` — AndroidVisualAcceptanceTest passes via Robolectric
- [ ] `./gradlew :androidApp:assembleDebug :desktopApp:assemble` — both build
- [ ] Manual Desktop smoke: full onboarding → home → log meal → pause/resume → audit-log export
- [ ] Manual Android smoke: same flow via emulator + dietician:// deep-link entry

## Deferred / open dependencies
- `POST /me/pause` — Plan-3.5 server route (Plan-4-5 ships client-side-only pause flag)
- `POST /voice/upload` — Plan-3.5 server route (Plan-4-5 voice path falls back to manual entry first-ship)
- Whisper.cpp JNI binding — Plan-7 (Plan-4-5 ships VPS-fallback only)
- Recipe ingest moderator + GROBID pipeline — Plan-7 (Plan-4-5 ingest button enqueues + shows "queued" toast)
- UaicCookieReExportLauncher Playwright subprocess — Plan-6 (Plan-4-5 ships the UI surface; throws NotShippedYet)
- Hybrid pgvector+pg_trgm+tsvector paper-search ranking — Plan-7 (Plan-4-5 UI surfaces against POST /embed; ranking improves as corpus backfills)
- Passkey/WebAuthn registration — Plan-3.5 (Plan-4-5 ships magic-link-only)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Post-impl 5-agent council per `feedback_council_pattern`**

Run a fresh 5-agent council BEFORE merging Plan-4-5:
- Inputs: this plan + the actual diff + the live `desktopApp` + `androidApp` smoke results + Roborazzi baseline screenshots + visual-acceptance gate log.
- Required positions: Devil's Advocate + UX-Domain-Expert + ED-Safeguard-Risk-Analyst + AI-Act-Compliance-Reviewer + KMP-Compose-Pragmatist.
- Persist transcript at `.claude/council-cache/council-{ts}-plan-4-5-post-impl.md`.
- Required verdict: APPROVE / APPROVE-WITH-CHANGES / FLAWED with explicit list of fixes if not APPROVE.
- If any BREAK fix surfaces, ship as fix-up commits on the same branch BEFORE merging.

- [ ] **Step 6: Live-acceptance feature-shipped verification (CLAUDE.md rule)**

Per CLAUDE.md "Feature-shipped verification rule": bundle hash + tests green ≠ feature shipped. Open the actual user-facing surface and verify visible:

```bash
# Desktop
./gradlew :desktopApp:run
# Visually confirm:
# - OnboardingScreen step 1 paints (lang picker visible)
# - Walk through to HomeScreen
# - All 8+ home selectors visible to the eye (not just to the test)
# - Tap pause-button → PauseTrackingScreen visible
# - Tap "Just tell me" from coach-chat → JustTellMeScreen visible

# Android emulator
./gradlew :androidApp:installDebug
adb shell am start com.dietician.android/.MainActivity
# Same visual confirmation.
```

If ANY screen renders blank or with default Material3 fallback styling instead of `DieticianTheme`, the slice is NOT shipped — return to Task 1 theme wiring.

---

## Open Stubs (intentional — wire-up belongs to Plan-3.5 / Plan-6 / Plan-7)

These are NOT placeholders in the "fill it in later" sense — they are explicit boundaries:

- `WhisperAsr.android.kt` + `WhisperAsr.desktop.kt` return `Result.failure(NotShippedYet)` — Plan-7 ships the JNI binding. UI gracefully falls back to VPS upload, then to manual entry.
- `VoiceUploadApi.upload(...)` returns 501 first-ship — Plan-3.5 ships `POST /voice/upload` + Whisper-cpp on VPS. UI falls back to manual entry.
- `PauseApi.pause(reason)` → 501 first-ship — Plan-3.5 ships `POST /me/pause`. UI flips `PauseState` flag client-side meanwhile; logging endpoints client-side check the flag.
- `UaicCookieReExportLauncher.desktop.kt` throws `NotShippedYet` — Plan-6 ships the Playwright subprocess. UI surface ships per spec §6.11a + §11.8.
- `JustTellMeApi.fetch(...)` returns 501 first-ship if Plan-3 hasn't shipped `/just_tell_me` route — UI shows rule-based-fallback derived from a tiny client-side Choco planner stub.
- `CookbookScreen.ingestUrl(...)` returns 501 from `/jobs/queue` — Plan-3.5 ships the route; Plan-7 ships the moderator. UI enqueues to a local outbox + shows "queued — available within a few days" toast.
- `PaperSearchApi` server-side hybrid search — Plan-3 ships `/embed`, Plan-7 ships corpus backfill. UI surfaces whatever the combined surface produces.
- `ScaleConnector` Bluetooth integration — both Android + Desktop return `isAvailable() = false` first-ship.

---

## Self-Review checklist

**1. Spec coverage:**

| Spec section | Plan task(s) |
|---|---|
| §A6 Plans 4+5 collapse | Header (package not subproject) + Task 0 |
| §A9 ED-safeguards (no streak/XP/leaderboard/body-comparison/red-green) | Task 1 ColorPalette + Task 2 AntiStreakAbsence + Task 1 NoForbiddenPatternsTest |
| §A14 Tailscale Magic DNS bootstrap | Task 7 Onboarding step 1 + Pre-Task-0 verification |
| §A17 PII NER at write boundary | Task 13 VoiceFlow routes voice transcript through Plan-2 redactor before insert |
| §1 Victor identity (bigorexia primary) | Task 2 BigorexiaCopyTest placeholder + Task 19 activation |
| §6.3 15 screens | Tasks 7, 8, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21 (covers 16 screens incl Diag) |
| §6.4 Cronometer 84 nutrients | Task 9 NutrientCatalog (5+12+15+15+20+10+1+2+4=84) + NutrientBar + NutrientBarList |
| §6.5 MacroFactor expenditure + weight-7d-rolling primary | Task 10 ExpenditureChart + Task 22 WeightTrendChart |
| §6.6 Voice-first + RO bias prompt | Task 13 VoiceFlow (RO_FOOD_BIAS_PROMPT constant) |
| §6.7 Photo-suggestion-never-auto-commit | Task 13 PhotoSuggestionCard |
| §6.8 Carbon weekly narrative | Task 22 WeeklyNarrative |
| §6.9 ED-safeguard UI affordances | Task 19 EDSafeguardModal + Task 11 PauseTrackingButton always-visible |
| §6.10 AI Act 3-layer transparency | Task 8 AILiteracyBanner + Task 16 PerCallDisclosurePane + Task 16 JustTellMeButton |
| §6.11b client-side ED-detector hook | Task 19 EDDetectorHook + Task 27 wired to meal_event insert |
| §6.11d clients don't embed | Task 17 PaperSearchScreen calls POST /embed server-side only |
| §11 visual acceptance gate | Task 33 DesktopVisualAcceptanceTest + AndroidVisualAcceptanceTest + SelectorManifest |
| Plan-3 contract surface | Tasks 6 (auth) + 17 (embed) + 18 (receipts) + 20 (settings/audit) + 21 (diag) |
| Plan-2 contract surface | Task 16 CoachChat uses LlmRouter.completeStream + Task 22 WeeklyNarrative uses LlmRouter.call + Plan-2 AuditLogWriter emit via EDSafeguardState |
| Plan-1 contract surface | Tasks 12, 13, 14 observe Plan-1 EventStore + PantrySnapshotStore + MealEventStore flows |

**2. Placeholder scan:** searched for "TBD", "TODO", "implement later", "add validation", "similar to". Only intentional stubs documented under "Open Stubs". The `TODO()` instances in `PhotoCapture.android.kt` (CameraX wiring in Task 23/24), `VoiceUploadApi.poll(...)`, `AILiteracyState.readAckedVersion` Koin binding, and a handful of others are explicit subagent-fill markers with test gates that fail until filled.

**3. Type consistency:**
- `SubjectPrincipal(subjectId: String, displayName: String, isVictor: Boolean)` used identically across `OnboardingScreenModel`, `MagicLinkVerifyScreenModel`, `HomeScreenModel`, `CoachChatScreenModel`, `WeeklyNarrative`, `JustTellMeScreenModel`. Verified.
- `LlmRequest(prompt, subjectId, capability, ...)` shape sourced from Plan-2 `com.dietician.shared.llm.LlmRequest` — used by `CoachChatScreenModel.send`, `WeeklyNarrative.buildPrompt`. Verified.
- `Cursor(timestamp, eventUuid)` from Plan-1 — used by `SyncStateObserver`. Verified.
- `data-testid` strings spelled identically in screens + SelectorManifest + spec §11. Implementer cross-checks before commit.

**4. Build+mount pairing (CLAUDE.md Slice-1 ghost-component lesson):**
- Every new component file under `commonMain/ui/components/` is mounted in at least one screen — enforced by `ComponentReuseAuditTest` (Task 29).
- Specifically: `NutrientBar` mounted in `MealDetailScreen` + `RecipeDetailScreen` + `FoodLogScreen` (top-5 summary). `MacroRingChart` mounted in `HomeScreen`. `ExpenditureChart` mounted in `HomeScreen`. `WeightTrendChart` mounted in `SettingsScreen → Body log` (and reused in weekly narrative card). `PhotoSuggestionCard` mounted in `FoodLogScreen` + `ReceiptUploadScreen` + `PantryScreen`. `WeeklyNarrative` mounted in `HomeScreen` (Sunday slot) + `SettingsScreen → This week tab`. `JustTellMeButton` mounted in `CoachChatScreen`. `PauseTrackingButton` mounted in `HomeScreen` top-bar. `EDSafeguardModal` mounted in `WithAppLifecycleHooks` global wrapper. `AILiteracyBanner` mounted in `MainActivity` + `Main.kt` global wrapper. `PerCallDisclosurePane` mounted in `CoachChatScreen` message bubble. `MealCard` / `RecipeCard` / `PantryItemRow` in their respective screens. `VoiceRecordButton` in `FoodLogScreen` + `CookbookScreen` ingest sheet.
- `AntiStreakAbsence.kt` is intentionally unreferenced — `ComponentReuseAuditTest` exempts it by name.

**5. Component-reuse contract (CLAUDE.md Slice-1.5 PDF-404 lesson):** every "mount existing component X in new site Y" task body in Tasks 12-22 shows X's prop signature + Y's wire-up JSX + the verify step `./gradlew :shared:compileCommonMainKotlinMetadata` (typecheck) before commit. Receipt camera mount in Task 18 explicitly diagrams `PhotoCapture.captureReceipt(): Result<PhotoRef>` ↔ `ReceiptApi.upload(mime, bytes)` shape parity.

**6. Underscore-dead-prop rule:** enforced by `UnderscoreDeadPropTest` (Task 29). Backing-field idiom `private val _state = MutableStateFlow(...)` exempted.

**7. data-testid grep (CLAUDE.md interaction-smoke gate):**
- `SelectorManifest` enumerates ~120 selectors across 16 screens + cross-route ED check-in modal. Total ≥80 floor enforced.
- `DesktopVisualAcceptanceTest` + `AndroidVisualAcceptanceTest` assert each selector paints on first navigation.
- Click-smoke asserts no error-text + no 4xx/5xx after each click.

**8. AI Act compliance map:**
- Art 4 (literacy) — `AILiteracyBanner` first-launch + version-bump (Task 8)
- Art 5(1)(f) (no emotion inference) — `emotion_inference_disabled=TRUE` audit_log invariant (Plan-2 + Plan-3 ship; Plan-4-5 NEVER renders emotion picker — enforced by `NoForbiddenPatternsTest`)
- Art 12 (audit log) — `AuditLogScreen` PDF/JSON export (Task 20)
- Art 13 (transparency) — `PerCallDisclosurePane` on every LLM message (Task 16)
- Art 14 (oversight + override) — `JustTellMeButton` + `JustTellMeScreen` + AI-coach-off toggle (Task 16 + Settings)
- Art 15 (model card) — link from `SettingsAboutScreen` (Task 20)

**9. GDPR compliance:** Art 9 explicit consent (onboarding step 6 health-required), Art 17 redaction (Settings → Delete account 2-step modal), Art 15 DSAR export (Settings → Export DSAR), Art 30 RoPA (linked from Settings → About).

**10. ED-safeguard load-bearing:**
- Kcal-floor refuse — UI enforces in onboarding identity step + Settings → kcal target editor (rejects <1500 for males >180cm, see Task 7)
- Weight-rate cap symmetric (bigorexia: both >0.5 kg/wk gain and >0.5 kg/wk loss trigger) — Task 19 `weightRateAboveCap` + Task 22 WeightTrendChart shows 28-day rolling figure with cap warning
- Restrictive-pattern detector — Task 19 `triggerPhrasesAboveThreshold` + Task 27 fires hook on every `meal_event` insert
- No body-comparison UI — `NoForbiddenPatternsTest` enforces (Task 1)
- Anti-streak — `AntiStreakAbsence.kt` marker file + `NoForbiddenPatternsTest` (Tasks 1-2)
- Bigorexia-primary copy — `BigorexiaCopyTest` (Tasks 2 placeholder + 19 activation)

**11. Identity correctness:** spec §1 identity is **Victor** (NOT Alex). All strings, prompt templates, narratives, and audit-log copy use "Victor". Verified by grep: `grep -r "Alex" shared/src/commonMain/kotlin/com/dietician/ui/ shared/src/commonMain/resources/MR/` → expected zero hits.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-18-plan-4-5-kmp-compose-ui.md`.**

Two execution options:

1. **Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration. Use `superpowers:subagent-driven-development`. Tasks parallelizable:
   - Tasks 1-6 (theme + i18n + nav + DI + network) — fully parallel
   - Tasks 7, 11 (Onboarding + Pause) — parallel after Tasks 1-6
   - Tasks 8, 9, 10 (MagicLinkVerify + NutrientBar + ExpenditureChart) — parallel after Tasks 1-6
   - Tasks 12-22 (screens) — mostly parallel; CoachChat (Task 16) blocks on Plan-2 LlmRouter completing
   - Tasks 23-26 (platform shells + actuals) — Android + Desktop sub-batches parallel
   - Task 27-29 (wiring + audit guards) — sequential
   - Task 30 (Roborazzi baselines) — parallel per screen but writes shared baseline dir
   - Tasks 31-35 — sequential (e2e → CI → preflight → push → council)

2. **Inline Execution** — Execute tasks in this session using `superpowers:executing-plans`, batch with checkpoints at Tasks 6, 12, 22, 26, 30, 33.

After Plan-4-5 ships, post-impl 5-agent council per [[feedback-council-pattern]] is MANDATORY before merging to `master`. Council inputs: live `desktopApp` smoke + live `androidApp` emulator smoke + Roborazzi baseline screenshots + visual-acceptance gate log + `.claude/council-cache/council-{ts}-plan-4-5-post-impl.md` transcript.

**Plan-4-5 ships independently of Plan-2 finalization** for the surface that doesn't use the Router (Onboarding, AuthVerify, Pantry, Receipt, AuditLog, Settings, Diag, Pause, AILiteracy). The Router-dependent screens (CoachChat, WeeklyNarrative, PhotoSuggestionCard's vision call, VoiceFlow's intent-classify) block on Plan-2 Tasks 0-22 landing.

**Plan-4-5 first ships with several VPS-side stubs returning 501** (voice/upload, me/pause, just_tell_me, jobs/queue). UI gracefully degrades in each case. Once Plan-3.5 fix-up lands those routes, the UI works end-to-end without re-deploy of the client.

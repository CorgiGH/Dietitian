---
title: "Implementation — Consolidated Build Plan"
slug: design-implementation
domain: design
applies_to: []
sources:
  - name: "Compose Multiplatform 1.7.0 release notes"
    url: "https://blog.jetbrains.com/kotlin/2024/10/compose-multiplatform-1-7-0-released/"
    citation: "JetBrains 2024-10"
    accessed: 2026-05-19
  - name: "Material 3 Compose API"
    url: "https://developer.android.com/develop/ui/compose/designsystems/material3"
    citation: "Android Developers 2026"
    accessed: 2026-05-19
  - name: "Voyager 1.1 docs"
    url: "https://voyager.adriel.cafe/"
    citation: "adrielcafe Voyager 1.1"
    accessed: 2026-05-19
authority: derived
confidence: high
last_verified: 2026-05-19
review_cadence_days: 60
instantiated_for_user: false
user_numbers: {}
related: [design-overview, design-visual-language, design-ux-patterns, design-components, design-references, design-index]
contradicts: []
supersedes: []
tags: [design, implementation, build, libraries, code, drifts]
---

# Implementation — Consolidated Build Plan

How to actually build everything the other five pages describe. Each section maps the design constraint to the concrete Compose Multiplatform 1.7.0 library, API, or code pattern. Cross-references back into [[design-visual-language]], [[design-ux-patterns]], [[design-components]] for the *what* and *why*; this page is the *how*.

Sourced from the 2026-05-19 implementation research pass — 10 parallel agents covered theming, typography, motion, charts, navigation, M3 components + bento, capture pipeline, accessibility, AI Act + GDPR surfaces, desktop windowing + i18n + icons + markdown. All findings against the current locked stack (Compose MP 1.7.0, Voyager 1.1.0-beta02, Material 3, Koin 4.0.0, SQLDelight 2.0.2, Ktor 3.0.1, CameraX 1.3.4, onnxruntime 1.20.0 desktop).

## Stack snapshot

### Already wired (from `gradle/libs.versions.toml` + module `build.gradle.kts`)

| Layer | Library | Version | Where |
|-------|---------|---------|-------|
| UI | Compose Multiplatform | 1.7.0 | shared, androidApp, desktopApp |
| UI | Material 3 (`compose.material3`) | 1.7.0 bundle | shared, androidApp, desktopApp |
| Nav | Voyager (navigator + screenmodel) | 1.1.0-beta02 | shared commonMain |
| DI | Koin (core + compose) | 4.0.0 | shared commonMain |
| Storage | SQLDelight | 2.0.2 | shared, drivers per target |
| Net | Ktor client (core + cio + okhttp + ws + auth + logging) | 3.0.1 | shared per target |
| Cam | AndroidX CameraX (camera2 + lifecycle + view) | 1.3.4 | androidApp |
| Crypto | androidx.security:security-crypto | 1.1.0-alpha06 | androidApp |
| ML | onnxruntime (Microsoft) | 1.20.0 | shared desktopMain |
| Solver | Choco-solver | 4.10.14 | commented; re-enable platform sets |
| Resilience | resilience4j | 2.2.0 | shared commonMain |
| Files (desktop) | pdfbox + twelvemonkeys + imgscalr | 3.0.3 / 3.12.0 / 4.2 | desktopApp |
| Wiki I/O (desktop) | flexmark + kaml + directory-watcher | 0.64.8 / 0.65.0 / 0.18.0 | desktopApp |
| Test | kotest + mockk + turbine + robolectric + junit5 + compose UI test | various | per target |

### To add (per the 10 research domains)

| Library | Coord | Use | Module | Notes |
|---------|-------|-----|--------|-------|
| Shimmer skeleton | `com.valentinilk.shimmer:compose-shimmer:1.4.0` | `SkeletonRow` placeholder, KMP-native | shared commonMain | Accompanist shimmer dead — this is the canonical 2026 replacement |
| Image loading | `io.coil-kt.coil3:coil-compose:3.4.0` + `io.coil-kt.coil3:coil-network-ktor3:3.4.0` | `RecipeCard` photo, `ReceiptPreviewCard` thumb, `AsyncImage` everywhere | shared commonMain | Full KMP; use `LocalPlatformContext` not `LocalContext` |
| Barcode (Android) | `com.google.mlkit:barcode-scanning:17.3.0` | `BarcodeScanButton` → CameraX `MlKitAnalyzer` | androidApp | Bundled, no Play Services dep required |
| Barcode (Desktop fallback) | `com.google.zxing:javase:3.5.3` + `com.google.zxing:core:3.5.3` | Decode picked image file via `HybridBinarizer` | desktopApp | Only needed if desktop ever scans (webcam not in spec — file-picker path) |
| Iconography | `dev.seyfarth:tabler-icons-kmp:1.0.0` | 20-glyph `DieticianIcons` replacement (mic, jar, sparkles, etc.) | shared commonMain | Per-icon tree-shake — only imported icons land in APK; KMP |
| Markdown | `com.mikepenz:multiplatform-markdown-renderer-m3:0.33.0+` | `WikiArticleScreen`, `MealDetail` recipe narrative | shared commonMain | M3 variant matches `DieticianTheme` |
| Fuzzy search (optional, deferred) | `ca.solo-studios:kt-fuzzy` or [sublime-fuzzy KMP port](https://github.com/android-password-store/sublime-fuzzy) | Command palette ranking when index >500 | shared commonMain | Hand-roll scorer first |
| Window-size class | `org.jetbrains.compose.material3:material3-window-size-class:1.7.0` | Responsive bento phone vs desktop | shared commonMain | Replaces deprecated chrisbanes lib |
| Atkinson Hyperlegible bold/italic/bold-italic | TTF assets from [googlefonts/atkinson-hyperlegible](https://github.com/googlefonts/atkinson-hyperlegible) | Three missing weight files | `shared/src/commonMain/composeResources/font/` | SIL OFL 1.1; lowercase + underscore naming required |
| whisper.cpp JNI | NDK build, no Maven coord | Voice transcription, large-v3-turbo q5_0 (547 MiB) or medium q5_0 (187 MiB) | androidApp NDK + desktopApp DLL | First-launch download from HuggingFace; bundle DLL on desktop |

Total bundle delta: shimmer ~50 KB + coil ~250 KB + tabler-icons ~20 KB (only used glyphs) + markdown-renderer ~150 KB + window-size-class ~5 KB ≈ **~475 KB** added to shared. ML Kit barcode adds ~2 MB to Android only. Tabler-icons replaces the planned 11 MB material-icons-extended — **net Android APK is smaller** than the alternative.

## Build order (priority)

Surfaces blocked on data are NOT scheduled here — this is design-system + scaffold order only.

1. **Theming hardening** — fill missing M3 ColorScheme roles (surfaceTint, surfaceContainer*, inverseSurface, outlineVariant, errorContainer, scrim) to kill purple-drift on elevated surfaces; add `DieticianDimens` `CompositionLocal`; add pure-Kotlin WCAG contrast test in `commonTest`.
2. **Typography hardening** — ship Atkinson Hyperlegible bold/italic/bold-italic TTFs; add `Typography.numericBodyMedium` extension with `fontFeatureSettings = "\"tnum\""`; empirically verify tabular figures on `:desktopApp:run` (Skia historically dropped the feature); CI grep ban U+015F/U+0163 cedilla codepoints.
3. **Iconography swap** — adopt `dev.seyfarth:tabler-icons-kmp:1.0.0`; map 20 glyphs into a `DieticianIcons` object; replace `DieticianNav.kt` bottom-nav icons + capture button icons in same diff.
4. **Motion + reduce-motion plumbing** — expose `MotionScheme` via `MaterialTheme.motionScheme`; add `expect/actual areAnimatorsEnabled()` (Android `Settings.Global.ANIMATOR_DURATION_SCALE`, Desktop `Toolkit.getDesktopProperty("win.uxtheme.animationsEnabled")`); gate `MetricRingNeutral` sweep on `hasUserInteracted`.
5. **Shimmer + EmptyStateCard** — wire `com.valentinilk.shimmer:compose-shimmer:1.4.0`; introduce `SkeletonRow` + `EmptyStateCard` Composables; replace each screen's bespoke empty card.
6. **AI Act + GDPR drift fix** — add `DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)` to `AILiteracyBanner` + `EDSafeguardModal`; switch Snackbar UNDO to Indefinite + `delay(60_000)` (Long is 10s — wrong); add `BackHandler(enabled = !acknowledged) {}` to `AILiteracyBanner`; route every `EDSafeguardModal.onDismiss` through audit sink; introduce `AuditedMutator` wrapper for regulated kinds.
7. **Voyager nav drift fix** — add `MagicLinkVerify(token)` + `Byok` to `DieticianScreen` sealed hierarchy; wire `ExternalUriHandler` (commonMain singleton) + platform shells (`Activity.onNewIntent` Android, `args[0]` Desktop); add `BackHandler` to `PushedScreenScaffold` (defence-in-depth) and to `AILiteracyVersionGate` block.
8. **Coil 3 KMP** — wire `coil-compose:3.4.0` + `coil-network-ktor3:3.4.0`; replace `ReceiptPreviewCard` raw bytes pattern with `AsyncImage`; `RecipeCard` gains optional photo slot.
9. **Capture pipeline build-out** — `expect/actual captureImage()`; CameraX preview Composable for `PhotoCaptureButton`; `MlKitAnalyzer` for `BarcodeScanButton`; Open Food Facts client (`Ktor` + 30-day SQLite cache).
10. **Charts hardening + accessibility overlay** — wrap each `Canvas` chart in `Modifier.semantics(mergeDescendants = true) { contentDescription = ... }`; resolve `MaterialTheme.colorScheme.primary` in parent `@Composable` and pass into `DrawScope`; add tabular-figures `tnum` to axis labels via `rememberTextMeasurer`.
11. **Charts mount** — finally drop `AdaptiveExpenditureChart` placeholder card on `HomeScreen`, mount the real chart with the data flow; same for `MetricRingNeutral` as kcal donut hero.
12. **Bento layout** — `LazyVerticalGrid` (NOT `LazyVerticalStaggeredGrid` — Staggered only supports `FullLine` spans, not the 2-of-4 hero); `BoxWithConstraints` breakpoint at 600 dp; mount when ≥4 tiles exist.
13. **Markdown wiki transclusion** — `multiplatform-markdown-renderer-m3`; `WikilinkPreprocessor` for `[[slug]]` → `dietician://wiki/slug`; `TransclusionResolver` for `![[slug.data]]` with file-watch invalidate.
14. **Desktop windowing polish** — `WindowState` persistence (extend `SettingsState`); `window.minimumSize = Dimension(1024, 720)`; `Tray { Item }` for minimise-to-tray; `onPreviewKeyEvent` for `Ctrl+K` palette + `Ctrl+,` settings + `Ctrl+1..5` tabs.
15. **Command palette** — `AlertDialog` (NOT a separate `Window`); hand-rolled fuzzy scorer; seed index with screens + recipes + pantry + audit + `/diag`; persist recents in SQLite; defer kt-fuzzy/sublime-fuzzy until index >500.
16. **Voice (whisper.cpp)** — bottom-priority because the constraint solver + Plan-6 scrapers gate the user value; build NDK bridge when LLM-router lands.

## Per-domain implementation

### Theming + color

See [[design-visual-language]] §Color for the *what*. Implementation:

- **Disable Material You dynamic color** — just don't call `dynamicLightColorScheme(LocalContext.current)`. Desktop has no equivalent API; Android opt-out is one-line absence.
- **30+ M3 ColorScheme roles to fill** — current `DieticianLightColorScheme` / `DieticianDarkColorScheme` cover primary/secondary/tertiary/background/surface/surfaceVariant/outline/error tonal pairs. **MISSING**: `surfaceTint` (defaults to M3 brand purple — visible on every elevated Card/FAB), `surfaceContainerLowest`/`Low`/`Container`/`High`/`Highest`, `inverseSurface`/`inverseOnSurface`/`inversePrimary`, `outlineVariant`, `errorContainer`/`onErrorContainer`, `scrim`. Drift documented in §Known drifts; fix targets `DieticianColors.kt:60-103`.
- **WCAG contrast verification** — pure-Kotlin formula in `commonTest`, no Android dep. Pattern:

```kotlin
private fun Float.lin(): Double =
    if (this <= 0.03928f) this / 12.92 else ((this + 0.055) / 1.055).pow(2.4)
fun Color.relativeLuminance(): Double =
    0.2126 * red.lin() + 0.7152 * green.lin() + 0.0722 * blue.lin()
fun contrastRatio(fg: Color, bg: Color): Double {
    val l1 = fg.relativeLuminance(); val l2 = bg.relativeLuminance()
    val (lighter, darker) = if (l1 > l2) l1 to l2 else l2 to l1
    return (lighter + 0.05) / (darker + 0.05)
}
```

WCAG 2.1 SC 1.4.3 thresholds (4.5:1 body / 3:1 large) remain the legal bar in 2026 — APCA was removed from WCAG3 working draft in July 2023; use APCA Silver as a heuristic only.

- **Light↔dark toggle (user-driven)** — `enum class ThemeMode { System, Light, Dark }` persisted in `SettingsStore`; resolve `effectiveDarkTheme = when (mode) { Light -> false; Dark -> true; System -> isSystemInDarkTheme() }` in `DieticianTheme`. `isSystemInDarkTheme()` consults `LocalSystemTheme` on Desktop. Recomposition is automatic — never read the toggle via `LaunchedEffect { settings.read() }`; always `collectAsState`.

- **CompositionLocal theming extensions** — `staticCompositionLocalOf` for `DieticianDimens` + `DieticianExtraColors(chipBackground, chipForeground)`. Pattern:

```kotlin
@Immutable data class DieticianDimens(
    val xs: Dp = 4.dp, val sm: Dp = 8.dp, val md: Dp = 16.dp,
    val lg: Dp = 24.dp, val xl: Dp = 32.dp, val xxl: Dp = 48.dp,
)
val LocalDieticianDimens = staticCompositionLocalOf { DieticianDimens() }
val MaterialTheme.dimens: DieticianDimens
    @Composable @ReadOnlyComposable get() = LocalDieticianDimens.current
```

**Known Voyager pitfall** — JetBrains/compose-multiplatform#4685 reports `staticCompositionLocalOf` values not propagating correctly across Voyager screen transitions in some versions. If observed on 1.1.0-beta02, switch to `compositionLocalOf` (small perf cost, correctness wins) OR re-provide at each Voyager screen root.

### Typography

See [[design-visual-language]] §Typography. Implementation:

- **`compose.components.resources` Font loading** — current pattern at `DieticianTypography.kt:55-62` is correct (`Font(Res.font.atkinson_hyperlegible_regular, FontWeight.Normal, FontStyle.Normal)`). `Font(...)` is `@Composable` in 1.7.0 (issue #4471 still open through 1.8.0; only `preloadFont` for web in 1.8). The composable wrapper convention stays.

- **Atkinson Hyperlegible weights** — ship three missing TTFs to `shared/src/commonMain/composeResources/font/`: `atkinson_hyperlegible_bold.ttf`, `atkinson_hyperlegible_italic.ttf`, `atkinson_hyperlegible_bolditalic.ttf`. Source: [googlefonts/atkinson-hyperlegible](https://github.com/googlefonts/atkinson-hyperlegible) (archived but stable, SIL OFL 1.1). File names must be lowercase + underscore-only (Compose MP resource constraint). Include `OFL.txt` in distribution. Without bold/italic shipped, Compose synthesises fake-bold by stroke widening — defeats Atkinson's whole pitch.

- **Tabular figures** — `TextStyle(fontFeatureSettings = "\"tnum\"")`. Add extension on M3 `Typography`:

```kotlin
val Typography.numericBodyMedium: TextStyle
    get() = bodyMedium.copy(fontFeatureSettings = "\"tnum\"")
val Typography.numericTitleLarge: TextStyle
    get() = titleLarge.copy(fontFeatureSettings = "\"tnum\"")
```

**Skia desktop caveat** — `fontFeatureSettings` historically dropped on the Skia text path. Verify empirically: render `Text("111\n888\n", style = numericTitleLarge)` on `:desktopApp:run`; if digit columns don't align on Windows, fall back to a monospaced numeric `FontFamily` for charts/numerics only. Negligible perf cost when it works.

- **`FontFamily.Default` cross-platform** — resolves to Roboto (Android) / Segoe UI (Windows) automatically since Compose MP 1.5 (PR #557). No `expect/actual` needed.

- **Romanian diacritics** — Roboto historically broken on `ț` rendering as cedilla (`google/fonts#167`); fixed in Android 4.3+. Modern Segoe UI Variable (Windows 11) renders comma-below `ș ț` correctly. Atkinson Hyperlegible covers them since launch. Source-code safety: CI grep ban U+015F/U+0163 in `**/Strings*.kt`. Custom Detekt rule per `CLAUDE.md` precedent (`UnusedUnderscoreDestructuring`).

- **Weight ladder synthesis** — disable fake-bold with `TextStyle(fontSynthesis = FontSynthesis.None)` if you want hard-fail rather than degrade.

- **Runtime swap** — `LocalUseAccessibleTypography` is `staticCompositionLocalOf` (correct — toggle is rare; flipping invalidates the entire subtree, which is what you want). ~1 frame jank during the swap.

### Motion

See [[design-visual-language]] §Motion. Implementation:

- **`MotionScheme` (NOT `MotionTokens`)** — `MotionTokens` is internal (`androidx.compose.material3.tokens`); don't import. **`MotionScheme`** is public since M3 1.4 (Sep 2025): `MaterialTheme.motionScheme.defaultSpatialSpec<Float>()` / `fastEffectsSpec()`. Spatial = springs with overshoot, Effects = critically damped. Map your 150/250/400 ms budget onto `fastEffects` / `defaultSpatial` / `slowSpatial` to stay theme-consistent.

- **Easing** — `FastOutSlowInEasing` (default `tween`), `LinearOutSlowInEasing` (exits), `FastOutLinearInEasing`, `EaseInOutCubic`, `EaseOutQuint`, `CubicBezierEasing`. Choose `spring()` for interruption-safe gesture/toggle animations, `tween(durationMillis, easing=)` when the duration cap is contractual.

- **Reduced motion** — `LocalAccessibilityManager.current?.isReduceMotionEnabled == true`. Compose ≥1.2 auto-honors `ANIMATOR_DURATION_SCALE=0` on Android. **Desktop returns null** — use expect/actual reading `Toolkit.getDefaultToolkit().getDesktopProperty("win.uxtheme.animationsEnabled")` on Windows.

- **`AnimatedContent` chart toggle**:

```kotlin
AnimatedContent(
    targetState = window, // 4wk | 12wk | 26wk
    transitionSpec = {
        (fadeIn(tween(250)) togetherWith fadeOut(tween(150)))
            .using(SizeTransform(clip = false))
    },
    label = "expenditureWindow",
) { w -> ExpenditureChart(data = data[w]) }
```

Use `Crossfade` if there's no size change; `AnimatedContent` when bar count differs. Missing `label =` breaks Layout Inspector traces.

- **Blinking caret** — `rememberInfiniteTransition` + `animateFloat` with `infiniteRepeatable(tween(400, LinearEasing), RepeatMode.Reverse)`. Wrap in `if (isStreaming && messageVisible)` so the animation tears down on cancel.

- **Token-stream auto-scroll** — `derivedStateOf { /* nearBottom check */ }`; only scroll if user hasn't scrolled up. Coalesce rapid tokens via `snapshotFlow { lastMessage.text }.conflate().sample(33.ms)` (~30 fps).

- **Cancel during streaming** — store the streaming `Job?` in ViewModel; `Flow.collect` checks cancellation between emissions; add `ensureActive()` inside heavy sync work; `cancelAndJoin()` when next send must wait.

- **Shimmer** — `com.valentinilk.shimmer:compose-shimmer:1.4.0` (KMP: Android, iOS, JVM-desktop, JS, Wasm). Accompanist shimmer is dead. Pattern:

```kotlin
@Composable fun SkeletonRow() = Row(
    Modifier.shimmer().fillMaxWidth().padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE0E0E0)))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(0.7f).height(14.dp).background(Color(0xFFE0E0E0)))
        Box(Modifier.fillMaxWidth(0.4f).height(12.dp).background(Color(0xFFE0E0E0)))
    }
}
```

Configure once with `LocalShimmerTheme`. Skip entirely when `reduceMotion`.

- **EmptyStateCard** — centred `Card` with `surfaceContainerLow` background, icon (24 dp), titleMedium headline, bodyMedium body, single primary Button CTA. Replaces bespoke empty cards per screen.

- **Determinate progress** — any op >2 s shows `LinearProgressIndicator(progress = { downloaded / total.toFloat() })`. Indeterminate only when total truly unknown AND <2 s expected.

- **Snackbar 60 s UNDO** — **`SnackbarDuration.Long = 10_000 ms` is NOT 60 s.** Use `SnackbarDuration.Indefinite` + manual timeout:

```kotlin
scope.launch {
    val job = launch {
        delay(60_000)
        snackbarHostState.currentSnackbarData?.dismiss()
    }
    val result = snackbarHostState.showSnackbar(
        message = "Logged 350 kcal", actionLabel = "UNDO",
        duration = SnackbarDuration.Indefinite,
    )
    job.cancel()
    if (result == SnackbarResult.ActionPerformed) auditRepo.emit(compensating(originalEventId))
}
```

- **No-autoplay charts** — `var primed by rememberSaveable { mutableStateOf(false) }` + `animateFloatAsState(targetValue = if (primed) 1f else 0f, ...)`. Flip only on user gesture or `LaunchedEffect(reduceMotion) { if (reduceMotion) primed = true }` to skip the animation entirely. Static at `progress = 1f` on first paint by NOT triggering primed from `LaunchedEffect(Unit)`.

### Charts

See [[design-components]] §Charts. Implementation:

**Decision: keep hand-rolled `Canvas`.** Library comparison:

| Library | KMP | Latest | License | Size | Red/green override | A11y | Anim control |
|---|---|---|---|---|---|---|---|
| Hand-rolled `Canvas` | n/a | n/a | n/a | ~0 KB | Total | DIY via semantics | Total |
| Vico | Android, JVM, iOS, wasm, Native | 3.1.0 (Apr 2026) | Apache-2.0 | ~150-250 KB | High | Same Canvas trick | `animateIn = false` |
| KoalaPlot | Android, Desktop, iOS, Web | 0.11.2 (May 2026) | MIT | ~200-300 KB | High (Composable-per-element) | Same Canvas trick | Most plots static |
| ComposeCharts (ehsannarmani) | Android, Desktop, iOS, wasm | 0.2.5 (Feb 2026) | Apache-2.0 | ~120-180 KB | Medium (built-in red/green delta semantics) | Minimal | Per-chart param hunt |

Reasoning: already shipping Canvas, ~0 KB, total control over no-red/green + no-autoplay constraints, multiplatform parity free (Skia-Android ≈ Skiko desktop). Vico is excellent but its strength is real-time scrubbing — we don't need that. Owning the Canvas keeps invariants local and grep-able.

**Per-chart**:

- **AdaptiveExpenditureChart** — three layers in single `DrawScope`: posterior `drawPath` solid; target `drawLine(pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))`; observed `drawCircle` r=3.5dp. Resolve `MaterialTheme.colorScheme.primary` in PARENT `@Composable` and pass into `DrawScope` helper (DrawScope isn't `@Composable`).

- **WeightTrendChart** — line + horizontal grid; for 4-wk window with <2 points show "not enough data — log 4+ weeks to see trend" in parent Composable. Never auto-extrapolate inside Canvas (bigorexia safeguard).

- **NutrientBar** — already `Box + fillMaxWidth(fraction)`. Add `fontFeatureSettings = "\"tnum\""` to numeric label + `Modifier.semantics { contentDescription = "Iron 12 milligrams of 8 target, above by 50 percent" }`.

- **MetricRingNeutral** — two `drawArc(useCenter = false, style = Stroke(12.dp, cap = StrokeCap.Round))` calls (background full 360°, foreground 0..sweep). `var hasUserInteracted by remember { mutableStateOf(false) }`; `animationSpec = if (hasUserInteracted) tween(250) else snap()`. Animate only on tap or `reduceMotion → snap` fallback.

**Accessibility overlay (load-bearing per Eevis Panula)** — Canvas content is invisible to TalkBack/Narrator by default. Wrap in `Modifier.semantics(mergeDescendants = true) { contentDescription = "Adaptive expenditure: 2750 kcal posterior, target 2750 kcal, 5 observations over 7 days" }`. For per-point exploration, overlay invisible focusable `Box`es at each data point.

**Performance** — `remember(data) { computeBucketedPoints(data) }` so heavy transforms don't re-run every frame. `rememberTextMeasurer()` for axis labels has internal cache. Skiko ≈ Skia-Android within ~10% for <200 draws/chart.

### Voyager nav

See [[design-ux-patterns]] §Navigation. Implementation:

- **`Navigator` + `Scaffold(bottomBar)`** — current `DieticianNav.kt:52` impl correct. Use `CurrentScreen()` (NOT `navigator.lastItem.Content()` — skips `SaveableStateHolder.SaveableStateProvider(key = screen.key)`).

- **`replaceAll` discards sub-stacks on tab swap** — Pantry → Cookbook → MealDetail then Home tap then Pantry tap = Pantry re-roots. Matches Council 4 lock + spec. **Document as explicit non-goal in `ux-patterns.md` §Navigation §IA-drift** so future contributors don't "fix" it. `TabNavigator` would preserve sub-stacks but doesn't handle back press (tabs are siblings).

- **`BackHandler` in commonMain** — `androidx.compose.ui.backhandler.BackHandler` from `org.jetbrains.compose.ui:ui-backhandler` is available in commonMain in CMP 1.7; only Android has a real impl, desktop is no-op. Wire `PushedScreenScaffold` with `BackHandler { navigator.pop() }` for Android system-back. `AILiteracyBanner` needs `BackHandler(enabled = !acknowledged) { /* swallow */ }` — currently missing.

- **Deep-link routing** — `ExternalUriHandler` singleton in commonMain with a `listener: ((String) -> Unit)?`. Android `MainActivity.onNewIntent { intent.data?.let { ExternalUriHandler.onNewUri(it.toString()) } }`; Desktop `main(args) { args.getOrNull(0)?.let { ExternalUriHandler.onNewUri(it) } }`. Composable scope wires the listener via `DisposableEffect`. Android needs `singleTask` launchMode in manifest or `onNewIntent` won't fire on second deep-link tap. Desktop second-instance handling = file lock (CMP doesn't ship one).

- **Modal layered over Navigator** — render `AILiteracyBanner` AS SIBLING of `Navigator` inside theme scope, NOT inside `Scaffold` content. Dialogs use a separate `Popup`/`Dialog` window so z-order is fine; inside `Scaffold` content the dialog re-mounts on every screen swap.

- **testTag conventions** — `testTag("nav-{key}")` on `NavigationBarItem`; `testTag("pushed-screen-back")` on back IconButton. Material 3 `NavigationBarItem` merges child semantics — use `useUnmergedTree = true` in tests targeting nested children.

- **Anti-patterns** — don't `navigator.lastItem.Content()` (skips SaveableStateHolder); don't add `BackHandler { nav.pop() }` at root inside Navigator scope (Voyager installs one — double-pop); don't `rememberSaveable` for cross-tab state when using `replaceAll` (gets disposed); don't underscore-dead-prop destructure (project Detekt rule).

### Material 3 components

See [[design-components]] for the catalog. Implementation specifics:

- **`Card(onClick = …)`** — full-card tap target; child `AssistChip(onClick = …)` consumes the press in `PointerInputChange` (parent doesn't fire). DO NOT pass `onClick = onTap` to both card AND chip. Current `PantryItemCard.kt:63, 72` does this — harmless today but wrong contract once Card.onClick is added.

- **`AssistChip` colour override** — `AssistChipDefaults.assistChipColors(containerColor = NeutralChip.backgroundLight, labelColor = NeutralChip.foregroundLight)` for the neutral chip surface spec'd by §Color rule. Current `PantryItemCard.kt:65, 73` ships `AssistChipDefaults.assistChipColors()` no-arg — that defaults to `colorScheme.surface`/`onSurface`, NOT the spec'd NeutralChip. **Drift** — fix in §Known drifts.

- **`NavigationBar`** — override indicator colour:

```kotlin
NavigationBarItem(
    colors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.primaryContainer, // Amber200
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
    modifier = Modifier.testTag("nav-${screen.key}"),
)
```

Five tabs max — adding a 6th forces a `NavigationRail` or scrollable variant.

- **`ModalBottomSheet`** — `@ExperimentalMaterial3Api` in 1.7.0 (opt-in required). `skipPartiallyExpanded = true` REQUIRED on desktop or sheet sticks mid-screen on first show. `onDismissRequest` fires AFTER swipe-down animation; if you also call `sheetState.hide()` manually, wrap to avoid double-dispatch.

- **`AlertDialog` non-dismissable** — empty `onDismissRequest = {}` alone is INSUFFICIENT — back press still triggers `onDismissRequest` (which no-ops, sheet stays — but on Android 14+ predictive back animates the dialog away briefly before snapping back, looks broken). Use `properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)`. Apply to `AILiteracyBanner` + `EDSafeguardModal.HardRefuse` only; `AddPantryItemDialog` stays dismissable.

- **`FilterChip` row** — for short labels (4wk/12wk/26wk) `Row` is fine; for wider labels (AuditLog kinds, RO copy) use `FlowRow` from `androidx.compose.foundation.layout`. Single-select: `selected == range; onClick = { selected = range }`. Multi-select: `mutableStateListOf<String>()` + `if (range in picked) picked.remove(range) else picked.add(range)`.

- **`ExtendedFloatingActionButton`** — current `PantryScreen.kt:127` passes `icon = {}` which reserves 12 dp start-padding and pushes text off-centre. Either pass a real icon or use the no-icon overload `ExtendedFloatingActionButton(onClick, modifier) { Text(label) }`. Fix in §Known drifts.

- **`Snackbar UNDO` 60 s** — `SnackbarDuration.Long = 10_000 ms` ≠ spec 60 s. See Motion §Snackbar above.

- **`TopAppBar` scroll** — `enterAlwaysScrollBehavior` does NOTHING without `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on `Scaffold`. Current `PushedScreenScaffold.kt:34` has no scrollBehavior → pinned-by-default; fine for short screens.

- **`OutlinedTextField` decimal** — `KeyboardOptions(keyboardType = KeyboardType.Decimal)` is a hint; OS keyboard CAN emit letters (paste, hardware kb). Always re-validate in `onValueChange` + at ViewModel.

```kotlin
onValueChange = { raw ->
    val cleaned = raw.filter { it.isDigit() || it == '.' }
        .let { if (it.count { ch -> ch == '.' } > 1) qty else it }
    qty = cleaned
}
```

- **`Switch` disabled** — `enabled = false` makes the switch un-toggleable but doesn't show a tooltip explaining why. Add a `Text` underneath ("Locked while plan is active") or it looks broken.

- **BYOK masked password** — `PasswordVisualTransformation()`. Do NOT also set `KeyboardType.Password` — that disables autocorrect AND triggers password-manager save prompts, leaking the API key to Google Password Manager. Use `KeyboardType.Text` + `imeAction = ImeAction.Done`.

- **`Icons.Filled.Visibility`** requires `material-icons-extended` (11 MB Android APK). Current project uses `material-icons-core` only (`DieticianNav.kt:99` comment). Use a Tabler-icons-kmp glyph or import only the vector path data.

- **Bento layout — DO NOT use `LazyVerticalStaggeredGrid`** for hero=2-of-4 desktop pattern. `StaggeredGridItemSpan.FullLine` is the ONLY span option (all-or-nothing); there's no `Span(2)`. Use `LazyVerticalGrid` + `GridItemSpan(2)` instead (fixed row heights — acceptable trade-off). Defer until ≥4 dashboard tiles exist.

- **Responsive breakpoint** — `BoxWithConstraints { val cols = if (maxWidth < 600.dp) 2 else 4 }` for one breakpoint is lightest. For more (tablet) use `WindowSizeClass` from `org.jetbrains.compose.material3:material3-window-size-class:1.7.0` (now in common-set; `calculateWindowSizeClass()` is platform-specific `actual`). `BoxWithConstraints` recomposes its WHOLE content tree on every desktop window-resize during drag — `WindowSizeClass` only recomposes when crossing a boundary.

### Capture pipeline

See [[design-ux-patterns]] §Input. Implementation per modality:

- **CameraX preview + photo** — `LifecycleCameraController(ctx).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE); bindToLifecycle(lifecycle) }`. `AndroidView(factory = { PreviewView(it).apply { this.controller = controller } })`. `controller.takePicture(executor, OnImageCapturedCallback)` → `ImageProxy` → COPY bytes BEFORE `img.close()` (`buffer` is direct ByteBuffer = use-after-free).

- **AWT FileDialog desktop** — `java.awt.FileDialog(parent, "Pick food photo", FileDialog.LOAD).apply { setFilenameFilter { _, name -> name.lowercase().endsWith(".jpg") || ... }; isVisible = true }`. `Files.readAllBytes(Path.of(dir, file))`. **Pitfall**: `FilenameFilter` is IGNORED on Windows native dialogs (Win32 OPENFILENAME only honours it on Linux/macOS). Validate extension AFTER pick.

- **`expect/actual captureImage()`** — `expect suspend fun captureImage(): ByteArray?` in commonMain. Android: `CaptureHost` `CompositionLocal` at app root owns a registered `ActivityResultLauncher`; bridge via `suspendCancellableCoroutine`. Desktop: `withContext(Dispatchers.IO) { pickImageBytesAwt() }`. Null on cancel consistent both platforms.

- **ML Kit barcode (Android)** — `com.google.mlkit:barcode-scanning:17.3.0` (bundled). `BarcodeScannerOptions().setBarcodeFormats(FORMAT_EAN_13, FORMAT_UPC_A, FORMAT_UPC_E, FORMAT_EAN_8)`. `controller.setImageAnalysisAnalyzer(MlKitAnalyzer(listOf(scanner), COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result -> ... })`. **Debounce** — fires continuously, unbind analyzer after first valid hit. Requires CameraX 1.3.0+ (we have 1.3.4).

- **Open Food Facts API** — `GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json`. Anonymous, no key. **Required**: `User-Agent: Dietician/0.1 (victor.vasiloi@gmail.com)`. **Rate limit**: 15 req/min/IP product, 10 req/min/IP search. Use `fields=...` query param to shrink payload. Romanian product coverage thin — fall back to `product_name` if `product_name_ro` missing. **Cache aggressively** — SQLite barcode→product, 30-day TTL.

- **Coil 3 KMP** — `io.coil-kt.coil3:coil-compose:3.4.0` + `io.coil-kt.coil3:coil-network-ktor3:3.4.0`. Use `LocalPlatformContext.current` (NOT `LocalContext.current` — cross-platform). `AsyncImage(model = ImageRequest.Builder(...).data(url).crossfade(true).build(), placeholder = painterResource(Res.drawable.placeholder), error = painterResource(...))`. Desktop disk cache defaults to `%LOCALAPPDATA%\coil` on Windows.

- **whisper.cpp on-device** — Best path: **whisper.cpp via JNI** on Android + Windows desktop. `onnxruntime:1.20.0` CANNOT run whisper.cpp GGML format (would need ONNX-exported weights, larger, slower). Model: `ggml-large-v3-turbo-q5_0.bin` = **547 MiB disk**, **~1.5 GB runtime RAM**. For phone consider `ggml-medium-q5_0.bin` (~187 MiB). Multi-language including Romanian (quality medium per arXiv 2511.03361). First-launch download from HuggingFace `ggerganov/whisper.cpp` (NOT bundled — APK ballooning). Audio MUST be 16 kHz mono PCM-16; `AudioRecord` Android gives this directly with `MediaRecorder.AudioSource.MIC`. NDK adds 5-10 min to clean Android builds (`ndkVersion = "26.x"`).

- **Press-and-hold mic UI** — `Modifier.pointerInput { detectTapGestures(onPress = { ... }) }`. `onPress` fires on touch-down, returns a `PressGestureScope` where you call `tryAwaitRelease()` (returns `false` on cancel/drag-out). Pair with `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`. `tryAwaitRelease() == false` = "user dragged away → discard recording" (matches WhatsApp/Telegram).

- **Permission flow (Android)** — Use `androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` directly. Don't add Accompanist Permissions (still `@ExperimentalPermissionsApi` in 2026). Permanent-deny detection: `!granted && !shouldShowRequestPermissionRationale(activity, CAMERA)` AFTER first ask (track "have I asked at least once" in SharedPreferences — `shouldShowRationale` returns `false` BOTH before first ask AND after permanent deny).

- **Autocomplete dropdown** — `ExposedDropdownMenuBox` + `Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)` REQUIRED or dropdown floats at (0,0). Debounce 250 ms via `snapshotFlow { query }.debounce(250L).distinctUntilChanged().mapLatest { ... }` in ViewModel. M3 `ExposedDropdownMenu` doesn't focus-trap properly on desktop — keyboard down-arrow may not navigate items; fall back to manual `DropdownMenu` + `Popup` if desktop usability matters.

### Accessibility (WCAG 2.2 mobile)

See [[design-ux-patterns]] §Refusals + safeguards, [[design-overview]] §6 wiki. Implementation:

- **`Modifier.semantics`** — `mergeDescendants = true` for full-row card a11y (TalkBack reads `PantryItemCard` as one node). `clearAndSetSemantics` for icon-only buttons. `liveRegion = LiveRegionMode.Polite` for chat streaming tokens (NEVER `Assertive` — interrupts whatever TalkBack is reading).

- **Custom accessibility actions** — pair with `clearAndSetSemantics` or TalkBack double-announces children:

```kotlin
.clearAndSetSemantics {
    contentDescription = "${suggestion.label}, confidence ${(c*100).toInt()} percent"
    customActions = listOf(
        CustomAccessibilityAction("Confirm meal") { onConfirm(s); true },
        CustomAccessibilityAction("Edit suggestion") { onEdit(s); true },
        CustomAccessibilityAction("Mark as wrong") { onWrong(s); true },
    )
}
```

- **Focus** — `FocusRequester` + `LaunchedEffect(Unit) { requester.requestFocus() }` for autofocus. `Modifier.focusGroup()` makes `PerCallDisclosurePane` one stop. **Desktop traversal follows PLACEMENT ORDER, not visual** — always override with `focusProperties { next = nextRef }` when columns are involved.

- **Touch targets** — `Modifier.minimumInteractiveComponentSize()` BEFORE `.size(...)` (order matters — `.size(20.dp).minimumInteractiveComponentSize()` is silently broken). Material 3 `Button`/`IconButton`/`Checkbox`/`Switch`/`RadioButton`/`AssistChip`/`FilterChip` auto-apply. Custom clickable `Box`/`Icon`/`Text` are failure modes.

- **WCAG 2.2 new criteria** — **2.4.11 Focus Not Obscured**: focused element must be partially visible; wrap scrollable in `Modifier.bringIntoViewRequester()` + `onFocusEvent { if (it.isFocused) scope.launch { requester.bringIntoView() } }`. **2.5.7 Dragging Movements**: every drag has a tap alternative; for drag-to-reorder pair with `IconButton(KeyboardArrowUp)` + `KeyboardArrowDown`. **2.5.8 Target Size 24 px**: Material 48 dp exceeds.

- **Screen reader testing** — **Windows Compose Desktop a11y goes through Java Access Bridge** (NOT directly to Narrator). Run `%JAVA_HOME%\bin\jabswitch.exe /enable` once; add `modules("jdk.accessibility")` to `compose.desktop.application.nativeDistributions {}` for packaged build. Validated screen readers: JAWS + NVDA. Narrator implicit via JAB. **Linux is NOT supported** (Skia a11y tree not exposed to AT-SPI) — moot for spec (Windows-only desktop).

- **Color contrast** — pure-Kotlin in `commonTest` (see Theming §WCAG contrast above). WCAG 2.1 SC 1.4.3 thresholds (4.5:1 / 3:1) remain the legal bar in 2026. APCA was removed from WCAG3 working draft in July 2023; use APCA Silver as a heuristic only.

- **Chat bubble color-blind safety** — alignment + colour + semantic prefix triplet. Amber-700 (user) vs Sage-700 (assistant) is INVISIBLE under deuteranopia (8% of male users). The fix is REDUNDANCY, not a different colour. Pattern:

```kotlin
Box(modifier = Modifier
    .semantics(mergeDescendants = true) {
        contentDescription = (if (message.fromUser) "You said: " else "Coach said: ") + message.text
        if (!message.fromUser) liveRegion = LiveRegionMode.Polite
    }
) { ... }
```

- **Romanian locale TTS** — `SpanStyle(localeList = LocaleList("ro-RO"))` inside `AnnotatedString` switches TalkBack TTS engine voice. **Windows Narrator via JAB does NOT switch voice mid-string** — known gap. Visual comma-below correctness is independent.

- **Reduced motion** — Compose `animate*AsState` automatically respects `ANIMATOR_DURATION_SCALE = 0` on Android since 1.2. Manual gating matters only for non-Compose-animation motion (parallax scroll, marquee, Lottie). Critical info (ED safeguard modal entry) MUST NOT depend on animation existing — WCAG 2.2.2 + Apple HIG.

- **A11y audit test** — Compose UI test walking the semantics tree with `useUnmergedTree = true`; assert every interactive node has `contentDescription` OR `text` AND touch target ≥ 48 dp. Pattern in `commonTest` under `theme/A11yAuditTest.kt`. See `cvs-health/android-compose-accessibility-techniques` for canonical patterns.

### AI Act + GDPR transparency surfaces

See [[design-ux-patterns]] §Transparency surfaces. Implementation:

- **AILiteracyBanner non-dismissable** — `properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)`. Empty `onDismissRequest = {}` alone is INSUFFICIENT — on Android 14+ predictive back the dialog animates away briefly before snapping back. Current `AILiteracyBanner.kt:40` has empty lambda only — **drift**, fix in §Known drifts.

- **Version gate** — store last-acked version string in `SettingsStore.aiLiteracyAckedVersion`. `AILiteracyVersionGate.shouldShow(acked) = acked != CURRENT_VERSION`. Cap bumps at ≤2/year per Council R2 FM-11 — banner fatigue degrades Art 4 effectiveness. Pair every bump with a one-line diff in `AILiteracyVersionGate.kt` KDoc + the policy doc history table.

- **Per-call disclosure pane** — `Surface(color = surfaceVariant.copy(alpha = 0.5f))` below each assistant `Card`. Truncate `call_uuid.take(3) + "…" + takeLast(2)` for visual density (`a3b…f7`); keep the full UUID as `testTag` suffix. Cost rendering must NOT drop leading zero (`"$0.42"` not `"$.42"`).

- **Audit log filterable** — replace imperative `viewModel.refresh()` per filter change with declarative `combine(kindFilter, callUuidFilter).debounce(150L).mapLatest { ... }.stateIn(...)`. Without debounce the call_uuid `OutlinedTextField` fires a network call per keystroke — current `AuditLogViewModel.kt:76-84` has this bug.

- **Consent rows** — TWO independent toggles, NEVER combined "Agree to all" (GDPR Art 7(2) dark pattern — noyb enforcement cases). Withdrawal MUST be as easy as granting (Art 7(3)) — single `Switch` tap satisfies; do NOT add confirm dialog. Current `ConsentRow.kt:33-86` + `AuditLogScreen.kt:115-130` correct; protect from "improvements" that add confirmation step.

- **DSAR PDF export** — Desktop: `pdfbox:3.0.3` (wired). Android: `android.print.pdf.PrintedPdfDocument` with `PrintAttributes.Builder().setMediaSize(MediaSize.ISO_A4).build()` (in-platform since API 19, zero new dep). `pdfbox-android` is GPL+classpath-exception — audit before Android bundle. Pagination: 50 rows per page max or 6-month export OOMs 4 GB devices.

- **DSAR ZIP export** — `java.util.zip.ZipOutputStream` works identically Android + Desktop JVM 21. Schema: `audit_log.json`, `consents.json`, `meals.csv`, `weights.csv`, `pantry.csv`, `subject.json`, `README.txt` (plain-language file index per ICO guidance), `manifest.sha256` (SHA-256 per file for integrity).

- **EDSafeguardModal severity** — pair colour with Icon (`Icons.Outlined.WarningAmber` for `HardRefuse`, `Icons.Outlined.Info` for `SoftWarn`). NEVER red. **`Dismiss` action MUST emit audit row** (not "dismiss-without-trace"). For `HardRefuse` swap `confirmButton`/`dismissButton` roles — `Pause` becomes primary, `Adjust target` secondary.

- **BYOK paste detection** — Android 13+: `ClipDescription.EXTRA_IS_SENSITIVE = true` on the clip so keyboard preview obfuscates. **NEVER log key value**, only `sha256(key).take(12)` as `key_version_hash`. Length-delta heuristic in `onValueChange` already at `ByokViewModel.kt:47-71` — correct.

- **AI Act Art 50 (Aug 2026)** — Persistent identity disclosure NOT satisfied by "Powered by Claude/GPT-4" alone (European Commission draft guidelines May 2026 explicitly call vendor jargon insufficient). Use "AI assistant" / "automated chatbot". Layered: (a) TopAppBar subtitle "AI assistant", (b) First-message disclosure system bubble, (c) Per-message `AssistChip` "AI" badge on assistant bubbles. Header-only fails because by message 30 user forgets.

- **Withdraw-consent fallback** — withdrawal MUST NOT delete prior data (Art 7(3) "shall not affect lawfulness of processing based on consent before its withdrawal"). Block FUTURE processing only. Common bug = wiring withdrawal to `DELETE` instead of write-block. Fallback flow: `CoachAvailability { ManuallyDisabled | LocalOnly | NoCloudConsent | Cloud }` derived from `combine(coachDisabled, crossBorderConsent, ollamaAvailable)`. SCC/DPF withdrawal with local Ollama available → "Now using on-device model" banner.

- **Audit row schema enforcement** — single chokepoint `AuditedMutator` wrapper. Pattern:

```kotlin
class AuditedMutator(private val sink: AuditLogSink) {
    suspend fun <T> auditedSet(kind: String, extra: Map<String, String> = emptyMap(),
                               block: suspend () -> T): T {
        val result = block()
        sink.write(AuditEntry(kind = kind, extra = extra))
        return result
    }
}
```

Future: custom Detekt rule `MissingAuditOnRegulatedMutation` scanning for `_state.value = _state.value.copy(consent|coachDisabled|aiLiteracyAckedVersion|byokKey` without adjacent `auditSink.write`. Until then: PR-time grep.

### Desktop windowing + i18n + iconography + markdown

See [[design-ux-patterns]] §Desktop windowing, [[design-visual-language]] §Iconography. Implementation:

- **Window** — keep native title bar (undecorated has open resize bug #178 + animation bug #3388 + loses Windows 11 Snap Layouts). `application { Window(state = rememberWindowState(...)) { ... } }`. `window.minimumSize = Dimension(1024, 720)` inside `LaunchedEffect(Unit)` (AWT `Dimension`, after Window realised). Persist via `snapshotFlow { Triple(size, position, placement) }.debounce(300).collect { save(...) }`. `rememberWindowState` is NOT `rememberSaveable` — use file/SQL persistence (extend `PersistedSettingsStore` with `windowState` field).

- **`onPreviewKeyEvent`** — fires top-down (parent first), bypasses focused-component swallow. **FILTER on `KeyEventType.KeyDown` first** or each shortcut fires twice. Only return `true` for handled keys (returning `true` for unhandled swallows user typing).

```kotlin
onPreviewKeyEvent = { e ->
    if (e.type != KeyEventType.KeyDown) return@Window false
    when {
        e.isCtrlPressed && e.key == Key.K -> { paletteOpen = true; true }
        e.isCtrlPressed && e.key == Key.Comma -> { navigator.push(Settings); true }
        e.isCtrlPressed && e.key in tabKeys -> { switchTab(tabKeys.indexOf(e.key)); true }
        else -> false
    }
}
```

- **Command palette** — `AlertDialog` (NOT separate `Window` — palette overlays current screen + child Window adds taskbar entries + focus-loss bugs). Hand-rolled fuzzy scorer (consecutive matches bonus, start-of-word bonus, length penalty); defer `kt-fuzzy` / `sublime-fuzzy` KMP port until index >500. Seed index: screens + recipes + pantry items + audit rows + `/diag` slash commands. Recent-first ranking via `Long` recentTs in small SQLite table.

- **i18n** — **KEEP** sealed-interface pattern (`interface Strings { val foo: String }` + `object Strings_en : Strings` + `object Strings_ro : Strings`). Compile-time exhaustiveness is the win. Adding a key forces both EN+RO impls or build breaks. Refinement: split into per-feature interfaces (`HomeStrings`, `FoodLogStrings`, ...) that the screen pulls; one root `Strings` interface extending all. Reduces 588-line single file to ~50 lines per feature module.

- **`compose.components.resources` strings vs hand-rolled** — KEEP hand-rolled `Strings` interface + `LocalAppLocale`. `stringResource(Res.string.foo)` runtime-switch requires `key(locale)` recomposition trick + XML pain (multiline RO strings escape badly). The interface-impl pattern beats compose-resources for a two-locale solo-dev app.

- **BigorexiaCopyTest extension** — custom Detekt rule `HardcodedComposableString` walking the AST for `KtStringTemplateExpression` inside `@Composable` functions. Pattern matches existing `UnusedUnderscoreDestructuring` rule per `CLAUDE.md` precedent. Allowlist `Strings.kt` override sites.

- **Iconography** — adopt `dev.seyfarth:tabler-icons-kmp:1.0.0`. ~6000 icons, MIT, tree-shakes per icon (only referenced icons land in APK), KMP. 20-glyph mapping (Tabler outline names):

| Glyph | Tabler outline |
|---|---|
| mic | `Microphone` |
| pantry-jar | `Jar` |
| sparkles-AI | `Sparkles` |
| fork-spoon | `ToolsKitchen2` |
| scale | `Scale` |
| droplet | `Droplet` |
| scan-barcode | `Barcode` |
| photo-camera | `Camera` |
| voice-wave | `Waveform` |
| settings | `Settings` |
| audit-log | `History` (or `ClipboardList`) |
| consent-shield | `ShieldCheck` |
| chevron-left / right | `ChevronLeft` / `ChevronRight` |
| close | `X` |
| check | `Check` |
| plus | `Plus` |
| minus | `Minus` |
| search | `Search` |
| palette | `Palette` |

Update `DieticianNav.kt:99-110` `bottomNavMeta` to use Tabler icons in same diff.

- **Markdown rendering** — `com.mikepenz:multiplatform-markdown-renderer-m3:0.33.0+` (M3 variant matches `DieticianTheme`). KMP, Compose-native, parses via JetBrains/markdown. `rememberMarkdownState(text)` for async parsing; `retainState = true` keeps prior render visible during reparse.

- **Obsidian `[[link]]` resolver** — pre-process markdown text BEFORE handing to `Markdown(...)`. Replace `[[slug]]` → `[slug](dietician://wiki/slug)`. Custom `LocalUriHandler` intercepts `dietician://wiki/<slug>` URIs and routes via Voyager `navigator.push(WikiArticle(slug))`. Unresolved slug → `dietician://wiki/_unresolved/<slug>` → toast.

- **`![[slug.data]]` transclusion** — regex pre-process, strip `<!-- AUTOGENERATED -->` header (first line), splice contents. Cache by `(slug, mtime)`. Wire to `directory-watcher` (already in deps): on `MODIFY`/`DELETE` events under `wiki/`, invalidate. **Recursive transclusion guard**: visited-set; depth cap 3; on recursion replace with literal `![[slug]]` and continue.

- **Compose Desktop fonts** — Skia does NOT do per-glyph fallback within a single `FontFamily`. Atkinson Hyperlegible v1 lacked `ș ț`; **Atkinson Hyperlegible v2 (2024) covers Latin Extended-A**. Verify the .ttf you bundle by rendering `s.bigorexia_strength_focus` (RO) in a desktop UI test and asserting no `�`-style boxes.

- **System tray** — `Tray { Item("Open Dietician") { isVisible = true }; Item("Quit") { exitApplication() } }` inside `application { }` scope (NOT inside `Window { }`). 16×16 / 22×22 / 24×24 icons for Win / macOS / GNOME. Linux GNOME requires AppIndicator extension — runbook flag.

## Known drifts to fix

Verified against current code 2026-05-19 — each is a concrete change that aligns implementation with the design wiki. Order: regulatory first, then visual fidelity, then ergonomics.

| # | File:line | Issue | Fix | Severity |
|---|-----------|-------|-----|----------|
| 1 | `AILiteracyBanner.kt:40` | Empty `onDismissRequest` only — Android 14+ predictive back animates dialog away (bypass) | Add `properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)` | Regulatory (AI Act Art 4) |
| 2 | `DieticianNav.kt:78` (AILiteracyVersionGate) | No `BackHandler(enabled = !acknowledged)` override — Android system-back pops Navigator past modal | Add `if (needsAck) BackHandler(enabled = true) { /* swallow */ }` inside the version-gate `if` block | Regulatory (AI Act Art 4) |
| 3 | `AuditLogViewModel.kt:76-84` | Per-keystroke network call without debounce | Replace imperative `refresh()` with `combine(kindFilter, callUuidFilter).debounce(150L).mapLatest { ... }.stateIn(...)` | UX (latency) |
| 4 | `EDSafeguardModal.kt` (Dismiss action) | `Dismiss` does NOT emit audit row | Wrap `onDismiss` to call `auditSink.write(AuditEntry(kind = "ed_modal_action", extra = mapOf("action" to "dismiss", "severity" to severity.name)))` before propagating | Regulatory (audit completeness) |
| 5 | Snackbar UNDO sites (search project) | `SnackbarDuration.Long = 10 s` ≠ spec 60 s | Switch to `Indefinite` + `launch { delay(60_000); snackbarHostState.currentSnackbarData?.dismiss() }` pattern | Spec compliance (undo window) |
| 6 | `DieticianColors.kt:60-103` | Missing M3 ColorScheme roles — `surfaceTint` defaults to M3 purple, visible on elevated Card/FAB/TopAppBar | Override `surfaceTint`, `surfaceContainerLowest`/`Low`/`Container`/`High`/`Highest`, `inverseSurface`, `inverseOnSurface`, `inversePrimary`, `outlineVariant`, `errorContainer`, `onErrorContainer`, `scrim` with warm-amber tinted neutrals | Visual fidelity (brand drift) |
| 7 | `PantryItemCard.kt:65, 73` | `AssistChipDefaults.assistChipColors()` defaults to `colorScheme.surface` — drifts from spec'd `NeutralChip` | Pass `containerColor = NeutralChip.backgroundLight, labelColor = NeutralChip.foregroundLight` (with theme-aware light/dark branching) | Visual fidelity (R3 ruling) |
| 8 | `DieticianTypography.kt:55-62` | Atkinson Hyperlegible ships only `Regular` weight — Compose synthesises fake-bold by stroke widening (defeats accessibility pitch) | Ship `atkinson_hyperlegible_bold.ttf` / `_italic.ttf` / `_bolditalic.ttf` to `composeResources/font/` + register all four faces in `AtkinsonHyperlegibleFamily()` | Accessibility |
| 9 | `PantryScreen.kt:127` (ExtendedFAB) | `icon = {}` reserves 12 dp start-padding, pushes text off-centre | Pass real `Icon(Icons.Filled.Add, ...)` (Tabler `Plus` post-iconography swap) or use no-icon `ExtendedFloatingActionButton(onClick, modifier) { Text(label) }` overload | Visual |
| 10 | `MagicLinkVerifyScreen.kt:32` + `Routes.kt` | Composable exists but route NOT in `DieticianScreen` sealed hierarchy — unreachable from Navigator | Add `data class MagicLinkVerify(val token: String) : DieticianScreen()` + wire `ExternalUriHandler` (commonMain singleton) + platform shells (`Activity.onNewIntent` Android, `args[0]` Desktop) | Spec gap |
| 11 | `ByokScreen.kt:50` + `Routes.kt` | Composable exists but route NOT in nav graph | Add `Byok : DieticianScreen()` to sealed hierarchy + push from `Settings` screen | Spec gap |
| 12 | `PushedScreenScaffold.kt:29` | No `BackHandler { navigator.pop() }` — relies on Voyager's root BackHandler. Defence-in-depth missing | Add explicit `BackHandler { navigator.pop() }` (Voyager's pop is idempotent) | Robustness |

Total: 12 drifts. 4 regulatory/spec-compliance (1, 2, 4, 5, 10), 3 visual-fidelity (6, 7, 9), 1 accessibility (8), rest spec gaps + robustness.

## See also

- [[design-overview]] — north stars + non-goals
- [[design-visual-language]] — color/type/spacing/shape/motion/icon tokens
- [[design-ux-patterns]] — navigation/screens/data display/inputs/transparency/safeguards/bilingual
- [[design-components]] — per-Composable anatomy + usage rules
- [[design-references]] — full bibliography (extended by this pass with library + KMP-tutorial sources)
- [[design-index]] — schema + lint cadence + log

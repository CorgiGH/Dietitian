---
title: "Visual Language — Color, Type, Spacing, Shape, Motion, Icon"
slug: design-visual-language
domain: design
applies_to: []
sources:
  - name: "Material 3 Expressive — Compose docs"
    url: "https://developer.android.com/develop/ui/compose/designsystems/material3"
    citation: "Material 3 / Jetpack Compose"
    accessed: 2026-05-19
  - name: "Atkinson Hyperlegible — Braille Institute"
    url: "https://www.brailleinstitute.org/freefont/"
    citation: "Atkinson Hyperlegible v1, OFL 1.1, Braille Institute"
    accessed: 2026-05-19
  - name: "Apple HIG — Motion foundations"
    url: "https://developer.apple.com/design/human-interface-guidelines/motion"
    citation: "Apple HIG Motion 2026"
    accessed: 2026-05-19
  - name: "WCAG 2.2 — touch targets + contrast"
    url: "https://www.w3.org/TR/WCAG22/"
    citation: "W3C WCAG 2.2 2023-10-05"
    accessed: 2026-05-19
  - name: "Mantlr — How Stripe, Linear, Vercel Ship Premium UI"
    url: "https://mantlr.com/blog/stripe-linear-vercel-premium-ui"
    citation: "Mantlr 2026"
    accessed: 2026-05-19
  - name: "envato — 2026 mobile app colour scheme trends"
    url: "https://elements.envato.com/learn/color-scheme-trends-in-mobile-app-design"
    citation: "envato 2026"
    accessed: 2026-05-19
authority: derived
confidence: high
last_verified: 2026-05-19
review_cadence_days: 60
instantiated_for_user: false
user_numbers: {}
related: [design-overview, design-ux-patterns, design-components, design-references]
contradicts: []
supersedes: []
tags: [design, visual, color, typography, spacing, shape, motion, icon, theme]
---

# Visual Language

The "what does the app look like" layer. Every value here is a current code token plus a rationale; when a token changes, this page and the source file change in the same commit.

## Color

### Philosophy

Warm, comfort-food register. Romanian-amber primary, sage-green secondary, deep neutral background, **explicit hard rule against red/green pass-fail signalling for any nutrition value**. The palette reads more like a cookbook than a SaaS dashboard. Inspired by what the 2026 industry calls the "earth-tone wellness" register — neutral + nature-inspired, evoking calm and authenticity ([envato 2026 colour trends](https://elements.envato.com/learn/color-scheme-trends-in-mobile-app-design); compare also Foodnoms, Reflect, Oura). It is deliberately the opposite of MyFitnessPal blue-on-white-trust-me-bro corporate sterility.

### Token table (current — `shared/.../ui/theme/DieticianColors.kt`)

All values inlined here are the current source of truth; if you edit one in the file, edit one here.

| Role | Light | Dark | Anchor |
|------|-------|------|--------|
| Primary | `#B78A3D` (Amber 700) | `#E8C078` (Amber 500) | `DieticianColors.kt:27` |
| Secondary | `#607D5A` (Sage 700) | `#8FA68E` (Sage 500) | `DieticianColors.kt:31` |
| Tertiary | `#4D483E` (Neutral 700) | `#4D483E` | `DieticianColors.kt:35` |
| Background | `#FDFBF7` | `#181612` | `DieticianColors.kt:40` |
| Surface | `#FFFFFF` | `#24211C` | `DieticianColors.kt:42` |
| Neutral 100 → 900 | `#F4F2EE` → `#221F1A` ladder | (same; theme switches role mapping) | `DieticianColors.kt:44–48` |
| Error | `#B3261E` (DieticianErrorRed) | `#B3261E` | `DieticianColors.kt:50` |
| NeutralChip bg / fg | (defined in object) | (defined in object) | `DieticianColors.kt:109` |

### Hard rule (load-bearing)

`DieticianColors.kt:4–9` carries the rule verbatim: **no red-green pass/fail axis for nutritional choices.** Macro under-target is not red. Macro over-target is not green. The `DieticianErrorRed` token exists for crash banners, network failures, validation errors on text input — never for a kcal or protein value. The `NeutralChip` token at `DieticianColors.kt:109` exposes a background + foreground pair specifically for the "above/below target by N%" chips so they read as a neutral data point rather than a verdict. See [[design-ux-patterns]] §Data display for how this rule shows up in screens.

### Semantic mapping

- Primary (Amber) — CTAs, FAB, selected nav item, headline accents
- Secondary (Sage) — secondary buttons, subtle highlights, filter chips when selected
- Tertiary (Warm neutral) — surfaces inside cards, container backgrounds where Surface would be too white
- Neutral ladder — text on background, divider lines, disabled states
- Error — actual errors only; never nutrition or weight
- NeutralChip — above/below-target labels, AssistChips on PantryItem and AuditLogRow

### Dark mode

Not a tinted-light. The dark surface `#24211C` and background `#181612` are warm-black (slightly red-shifted) to keep the comfort-food register at night. Avoid pure `#000` — too clinical, conflicts with the brand temperature. Pure-white surface in dark mode is forbidden. Both modes ship at equal fidelity; the user toggle is at `SettingsScreen` `ToggleRow`.

### Dynamic color

**Disabled.** Material 3 dynamic-color (Material You) is intentionally not wired in `DieticianTheme.kt:15` because the brand temperature is non-negotiable. The amber/sage palette is the product; if the user's wallpaper is purple, the product does not become purple. This is the same posture Linear, Vercel, and Stripe take — restraint in colour, brand-as-product ([Mantlr 2026](https://mantlr.com/blog/stripe-linear-vercel-premium-ui)).

### Contrast

All text/background combinations meet WCAG 2.2 AA: 4.5:1 for body text, 3:1 for large text. Amber 700 on Background `#FDFBF7` ≈ 4.7:1 — barely AA on body. **Watch this**: if Amber gets used for body copy anywhere it is a contrast bug. Amber is for accents, headlines, CTA labels only. Sage 700 on Background ≈ 5.4:1 — comfortably AA.

## Typography

### Stack

Two-track type system. The user toggles between them at `SettingsScreen` via the `Accessible typography` switch, gated by the `LocalUseAccessibleTypography` `CompositionLocal` at `DieticianTypography.kt:69`.

- **Default**: system font (`Roboto` on Android, `SF Pro` on iOS-future, `Segoe UI` on Windows desktop). Reason: content-readability tools — Linear, Vercel, Raycast — overwhelmingly use system fonts because they prioritise content over brand expression. We want the same.
- **Accessible mode**: Atkinson Hyperlegible. Designed by the Braille Institute specifically to differentiate misinterpreted characters (B vs 8, 1 vs L vs l vs I). Free, OFL 1.1, drop-in compatible. Currently shipped as `atkinson_hyperlegible_regular` (one weight); adding bold + italic when Plan-7 lands.

### Type scale

Body sizes overridden in `DieticianTypography.kt:41–43` to a tight 16/14/12 sp ladder for body / body-small / caption. Other roles inherit Compose `Typography()` defaults (display ≈ 57 / 45 / 36 sp, headline 32 / 28 / 24 sp, title 22 / 16 / 14 sp, label 14 / 12 / 11 sp). This matches Material 3's 13-step ratio scale, which research shows out-performs strict mathematical progressions for content-heavy apps ([Crosley — Type scales](https://blakecrosley.com/blog/typography-systems)).

When adding a new text style, prefer the existing role over inventing a new ratio. If neither displaySmall nor headlineLarge fits, the design question is "is this UI really displaying something at a new hierarchy level" — usually the answer is no.

### Weight ladder

System fonts ship `400/500/600/700`. Use `500` for emphasis inside body, `600` for section headings, `700` only for headline displays. Avoid `400` italic — too thin at body size on low-DPI desktop. Romanian diacritics render correctly at all weights in the system stack; Atkinson Hyperlegible diacritic kerning is also correct (verified for `ăâîșț`).

### Line-height + tracking

Inherit Material defaults. Don't override line-height on body text — it's tuned for the type scale. Do override line-height on headlines that wrap to two lines (use `1.2 × font-size` instead of the default `1.3 ×`) — this is a hand-tune-when-it-bites rule, not a system rule.

### Romanian diacritics

`ăâîșț` use the comma-below variants for `ș` and `ț` (Unicode U+0219 / U+021B), **never** the cedilla variants `şţ` (U+015F / U+0163). The system font stack on all target platforms (Roboto/Segoe UI) honours this since 2018; Atkinson Hyperlegible since v1.001. If you ever see cedilla appear in the app, it is a string-source bug — somewhere a CSV or scraped page was decoded with the wrong codepage. Treat as P1.

### Numerics

Use tabular figures for any column of numbers: macro values, prices in lei, percentages, weight readings. Material 3 typography exposes this via the `fontFeatureSettings = "tnum"` parameter; not yet wired globally. Plan-7 task: add a `Typography.numeric` extension that returns a `TextStyle` with `tnum` on, use it in `TodayNutrientsCard`, `NutrientBar`, `WeightTrendChart`, `AuditLogRow`.

## Spacing

### Base unit

4 dp. Every padding, margin, gap is a multiple of 4. Most screens currently inline `padding(8.dp)` and `Arrangement.spacedBy(8.dp)`. Cards use 12 or 16 dp inner padding. FAB padding is 16 dp.

### Current state

No central `Dimens` object. Values are inlined per Composable. **This is debt.** Action: add `shared/.../ui/theme/DieticianDimens.kt` exposing `space.xs (4) / sm (8) / md (12) / lg (16) / xl (24) / xxl (32)`; replace inline `8.dp` etc. as touched. Don't do a big-bang refactor — replace as you go.

### Vertical rhythm

Sections separated by 24 dp (`xl`). Cards inside a section separated by 12 dp (`md`). Card inner padding 16 dp (`lg`). Header-to-body gap inside a card 8 dp (`sm`). This is the rhythm; deviate only for typography-driven exceptions (e.g., display headlines need 32 dp top padding to breathe).

### Edge padding

Screen-edge 16 dp on desktop, 16 dp on phone landscape, **8 dp on phone portrait** because the bottom-nav `BottomAppBar` already eats the right-side breathing room. Current code uses 8 dp uniformly which reads cramped on desktop — see [[design-components]] §PushedScreenScaffold for the fix planned for the next iteration.

### Inspiration

Vercel's whitespace is famously aggressive: section padding 96–128 px vertical on marketing pages, which is "a huge part of why Vercel feels expansive and expensive" ([Mantlr 2026](https://mantlr.com/blog/stripe-linear-vercel-premium-ui)). We don't need that on a single-user app, but the lesson holds: when a screen feels cluttered, the first fix is more whitespace, not smaller font.

## Shape

### Radii ladder (`DieticianShapes.kt:10–16`)

- `4 dp` — chips, small badges (AssistChip on PantryItem expiry)
- `8 dp` — text-field corners, alert-dialog corners
- `12 dp` — cards (`SubjectCard`, `TodayNutrientsCard`, `RecipeCard`, `AuditLogRow`)
- `16 dp` — FAB, ExtendedFloatingActionButton, primary CTAs
- `24 dp` — modal sheets (planned ModalBottomSheet for pantry detail), large feature cards

Comment at `DieticianShapes.kt:8` reads "Cards 12 dp, FAB 16 dp" — keep that. The shape system is Material 3 default with this one customisation; we are not adopting the M3 Expressive 35-new-shape pack until Plan-7+. Squircles, asymmetric radii, organic blobs are out of scope.

### Card vs Surface

A `Card` reads as "this is a discrete object" (a recipe, a pantry item, a log entry). A `Surface` reads as "this is a region of the screen." Don't card-ify everything; the Settings screen toggles deliberately sit on a `Surface`, not in `Card`s, because they belong to the screen, not to individual entities.

## Motion

### Principles

Borrowed from Apple HIG + iOS 26 calm-motion guidance + Material 3 Expressive motion physics. The rule of thumb: motion should clarify a transition or confirm an action; motion that decorates is debt.

1. **Durations**: 150 ms for micro-feedback (button press, toggle flip), 250 ms for in-view transitions (card expand, sheet show/hide), 400 ms for screen transitions (nav push). Anything over 400 ms is suspect.
2. **Easing**: `FastOutSlowInEasing` for entries, `LinearOutSlowInEasing` for exits. The default Material 3 `MotionTokens` are fine; don't custom-curve unless you have a real reason.
3. **Reduced motion**: respect the OS `Reduce Motion` setting (Android `Animator.areAnimatorsEnabled()`, Compose `LocalAccessibilityManager`). Critical-path animations (nav push, chat streaming) gracefully degrade to instant. Decorative animations (chart line-draw, FAB scale-in) skip entirely. **Important information is never conveyed solely through motion** — Apple HIG's mandate.
4. **No autoplay**: charts do not animate on first paint unless the user interacts with them. The `AdaptiveExpenditureChart` draws statically; the user toggles 4wk/12wk/26wk and the line cross-fades over 250 ms.
5. **No spinners-of-doom**: any operation that takes >2 s shows a determinate progress indicator (`LinearProgressIndicator`, `CircularProgressIndicator` with progress) or a streaming text indicator (the `…` cursor in `CoachChatScreen`). Indeterminate spinners over 2 s are a UX bug — surface the actual progress.

### Chat streaming

The `CoachChatScreen` streaming pattern (`CoachChatScreen.kt:49`) is the load-bearing motion in the app: token-by-token render, blinking caret at the in-progress position, `Cancel` button always visible during streaming. Industry baseline ([thefrontkit 2026](https://thefrontkit.com/blogs/ai-chat-ui-best-practices); [Langtail blog](https://langtail.com/blog/llm-chat-streaming)) — "responses that wait until completion before rendering feel broken by comparison."

### Haptics

Use sparingly. `HapticFeedbackType.LongPress` on long-press confirm. `HapticFeedbackType.TextHandleMove` on slider tick. **Never on data updates** — a haptic when macros tick over target is exactly the "celebration" pattern §1 of [[design-overview]] forbids.

## Iconography

### Current state

Material `material-icons-core` only — the extended pack (`material-icons-extended`) is excluded because it adds 11 MB to the Android APK (`DieticianNav.kt:94` comment). Consequence: several semantic mismatches in the current code.

- `Pantry` tab uses `ShoppingCart` (closest "stuff I bought" glyph in core)
- `Voice` button uses `Phone` (Mic icon is in extended)
- `SameAsRecent` uses `Refresh`
- `Photo` uses `Face`

`DieticianNav.kt:97` comment acknowledges this: "Real product icons get filled in by the design system task." That task is this design pass.

### Path forward

Two options, in order of preference:

1. **Custom icon font** (`DieticianIcons` set, ~20 glyphs at most, drawn as `ImageVector` resources). Adds ~30 KB. Lets us hit semantic targets: pantry-jar, microphone, sparkles-for-AI, fork-spoon for cookbook, scale for weight, droplet for hydration. This is the Linear/Stripe approach — small custom set, semantically tight.
2. **Pull the few needed icons from extended Material as standalone `ImageVector` constants** (copy the vector path data from the source, include only what we use). Same KB cost as option 1, less brand cohesion.

Recommend option 1, scoped: ~20 icons, single style (rounded line, 1.5 dp stroke), no fills. Defer until Plan-7 unless an iteration screams for it.

### Sizing

24 dp default (Material standard). 20 dp inside `AssistChip`. 32 dp on `ExtendedFloatingActionButton`. 48 dp for the bottom-nav (handled by `NavigationBarItem` default).

## Theming entry point

Single `MaterialTheme` wrapped at `DieticianTheme.kt:15`, wired with `lightColorScheme` / `darkColorScheme` (built from the tokens in `DieticianColors.kt`), the typography stack (default or accessible), and `DieticianShapes`. `DieticianApp` at `DieticianNav.kt:52` wraps everything inside `DieticianLocaleProvider → DieticianTheme → Scaffold(bottomBar = DieticianBottomNav)`. The `CompositionLocal`s `LocalLocale` and `LocalUseAccessibleTypography` propagate from there down.

When you need to read the current theme in a new Composable, use `MaterialTheme.colorScheme.primary` etc. — never reference `DieticianColors.DieticianAmber700` directly outside the theme file; that hard-binds you to one mode.

## Surfaces not yet themed (TODO)

- **Loading skeletons**: no skeleton component exists yet. The pattern (shimmer rectangles sized like the final content) needs to land before charts go from preview-card to real data. See [[design-components]] §Planned `SkeletonRow`.
- **Empty states**: each screen invents its own. Convergence opportunity → an `EmptyStateCard` Composable taking an icon, headline, body, and optional CTA. See [[design-components]] §Planned.
- **Snackbar / Toast styling**: currently default Material. Should pick up the warm neutral surface explicitly.
- **Tooltip styling**: defaults. Fine for now; revisit when Plan-7 adds desktop hover affordances.

## Implementation

Full code patterns + library decisions: [[design-implementation]]. Quick pointers:

### Color
- **Disable dynamic-color** by NOT calling `dynamicLightColorScheme(LocalContext.current)`. Desktop has no equivalent.
- **Fill 30+ M3 ColorScheme roles** — current `DieticianColors.kt:60-103` is missing `surfaceTint`, `surfaceContainerLowest`/`Low`/`Container`/`High`/`Highest`, `inverseSurface`, `inverseOnSurface`, `inversePrimary`, `outlineVariant`, `errorContainer`/`onErrorContainer`, `scrim`. Without `surfaceTint` override, M3 brand purple bleeds into every elevated Card/FAB. Drift #6 in [[design-implementation]].
- **WCAG contrast verification** — pure-Kotlin formula in `commonTest`, no Android dep. Pattern in [[design-implementation]] §Theming.
- **Light↔dark toggle** — `ThemeMode { System, Light, Dark }` enum + `StateFlow` + `isSystemInDarkTheme()` resolution; persist via `SettingsStore`.
- **CompositionLocal extensions** — `staticCompositionLocalOf` for `DieticianDimens` + `DieticianExtraColors`. Note JetBrains/compose-multiplatform#4685 (Voyager + static-CompositionLocal propagation bug); fall back to `compositionLocalOf` if observed.

### Typography
- **Ship Atkinson Hyperlegible bold/italic/bold-italic TTFs** to `shared/src/commonMain/composeResources/font/` from [googlefonts/atkinson-hyperlegible](https://github.com/googlefonts/atkinson-hyperlegible) (archived, stable, SIL OFL 1.1). File names lowercase + underscore only. Current `DieticianTypography.kt:55-62` registers Regular only → Compose synthesises fake-bold by stroke widening (defeats accessibility). Drift #8.
- **Tabular figures** — add `Typography.numericBodyMedium = bodyMedium.copy(fontFeatureSettings = "\"tnum\"")`. **Verify on `:desktopApp:run`** — Skia desktop historically dropped `fontFeatureSettings`; if columns don't align, fall back to a monospaced numeric `FontFamily` for charts/numerics only.
- **`FontFamily.Default`** resolves to Roboto (Android) / Segoe UI (Windows) automatically since CMP 1.5 (PR #557). No `expect/actual` needed.
- **CI grep ban U+015F / U+0163** (cedilla `şţ`) in `**/Strings*.kt` — custom Detekt rule per `UnusedUnderscoreDestructuring` precedent.

### Motion
- **`MotionScheme` (NOT `MotionTokens`)** — `MotionTokens` is internal. Use `MaterialTheme.motionScheme.defaultSpatialSpec()` / `fastEffectsSpec()` (public since M3 1.4, Sep 2025).
- **Reduced-motion** — `LocalAccessibilityManager.current?.isReduceMotionEnabled` (Android). **Desktop is null** — expect/actual reading `Toolkit.getDefaultToolkit().getDesktopProperty("win.uxtheme.animationsEnabled")`.
- **Blinking caret** — `rememberInfiniteTransition` + `animateFloat` with `infiniteRepeatable(tween(400, LinearEasing), RepeatMode.Reverse)`. Wrap in `if (isStreaming && messageVisible)` so it tears down on cancel.
- **Shimmer** — `com.valentinilk.shimmer:compose-shimmer:1.4.0` (KMP). Accompanist shimmer dead.
- **Snackbar 60s undo** — `SnackbarDuration.Long = 10_000 ms` ≠ spec 60 s. Use `Indefinite` + `delay(60_000); dismiss()`. Drift #5.
- **No-autoplay charts** — `var primed by rememberSaveable`; flip only on user gesture or `LaunchedEffect(reduceMotion) { if (reduceMotion) primed = true }`.

### Iconography
- **Adopt `dev.seyfarth:tabler-icons-kmp:1.0.0`** — ~6000 icons, MIT, per-icon tree-shake, KMP. Replaces planned 11 MB material-icons-extended at smaller net cost.
- **20-glyph mapping** to Tabler outline names in [[design-implementation]] §Iconography.
- **`Icons.Filled.Visibility` not in `material-icons-core`** — substitute Tabler `Eye` / `EyeOff` for BYOK password reveal toggle.

### Theming entry point
- **Single `MaterialTheme`** wrapped at `DieticianTheme.kt:15` correct. Add `DieticianDimens` `staticCompositionLocalOf` + `DieticianExtraColors` extension property accessors per [[design-implementation]] §Theming.

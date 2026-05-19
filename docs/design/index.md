---
title: "Design Wiki — Index"
slug: design-index
domain: design
applies_to: []
sources: []
authority: derived
confidence: high
last_verified: 2026-05-19
review_cadence_days: 30
instantiated_for_user: false
user_numbers: {}
related: [design-overview, design-visual-language, design-ux-patterns, design-components, design-references, design-implementation]
contradicts: []
supersedes: []
tags: [design, wiki, index]
---

# Design Wiki — Index

LLM-maintained design knowledge base for the Dietician product. Follows the Karpathy LLM-Wiki pattern (three layers: raw sources → wiki → schema). This index is content-oriented; the log at the bottom is chronological.

## Read this first

If you are about to touch any frontend code, navigation, theme, copy, or visual layout, **read [[design-overview]] first**, then the page closest to your task. The pages are short and densely cross-linked; reading the wrong one is cheaper than skipping the right one.

## Pages

- [[design-overview]] — what this product is, who uses it, design north stars, non-goals. Start here.
- [[design-visual-language]] — color, typography, spacing, shape, motion, iconography, theming. The "what does it look like" page.
- [[design-ux-patterns]] — navigation model, screen archetypes, data-display rules, input patterns, AI Act disclosure surfaces, ED safeguards, bilingual copy. The "how does it behave" page.
- [[design-components]] — anatomy + usage rules for every reusable Composable currently in `shared/.../ui/components/`, plus planned components. Each entry cites `file:line`.
- [[design-references]] — bibliography. Internal spec/research links + external web sources (Material 3, Apple HIG, WCAG 2.2, Cronometer, MacroFactor, Foodnoms, Linear, Raycast, Vercel, Reflect, etc.).
- [[design-implementation]] — consolidated build plan. How to implement everything in the other five pages: library choices, code patterns, build order, known drifts to fix. Read alongside the page closest to your task.

## Schema (LLM-maintained)

This wiki follows the same three-layer pattern as the product's domain wiki at `wiki/` (defined in `AGENTS.md` §"Three-layer LLM-Wiki pattern"):

1. **Raw layer** — design research lives in `docs/superpowers/research/2026-05-17-round-3-ux-regulation.md` and the spec `docs/superpowers/specs/2026-05-17-dietician-design.md`. These are immutable inputs. The wiki reads from them, never edits them.
2. **Wiki layer** — the six pages in `docs/design/`. LLM-owned. Narrative, interlinked, citation-bearing.
3. **Schema layer** — this index plus the rules in `CLAUDE.md` and `AGENTS.md`.

### Frontmatter

Every page has YAML frontmatter per the schema in `AGENTS.md:36-60`. Required keys: `title`, `slug`, `domain` (always `design`), `applies_to`, `sources`, `authority`, `confidence`, `last_verified`, `review_cadence_days`, `tags`, `related`, `contradicts`, `supersedes`.

`authority` for design pages is usually `derived` (synthesis of multiple sources) or `practitioner` (Material/Apple HIG primary). `peer-reviewed` reserved for academic citations (e.g., MyFitnessPal-ED clinical study referenced in [[design-ux-patterns]]).

### Links

Use Obsidian-style `[[slug]]` to cross-link. A `[[name]]` that doesn't resolve yet is fine — it marks something worth writing later, not a broken link.

### Numbers + data

Per `AGENTS.md` "Storage decision rule": NEVER put live user numbers in markdown bodies. **Design tokens are different** — they are code constants (in `DieticianColors.kt`, `DieticianTypography.kt`, etc.), stable, and the wiki IS the spec of record for them. Inline token values are encouraged. When a token changes, update both the source file AND this wiki in the same commit.

For component citations, use `file:line` anchors so a reader can jump straight to the source (e.g., `DieticianColors.kt:60`). When you rename a file or move a function, update the anchors.

## Maintenance

| Trigger | Action |
|---------|--------|
| New screen added | Update [[design-ux-patterns]] §Navigation map + [[design-components]] new-component entries |
| New reusable Composable | Add anatomy entry to [[design-components]] with file:line + do/don't |
| Token (color/type/shape/spacing) changed | Update [[design-visual-language]] in same commit as source-file change |
| New design source consumed (article, case study, paper) | Add to [[design-references]] bibliography AND cite from the page that used it |
| Spec section §0 §1 §11 §28 §30 changed | Re-read those sections, refresh [[design-overview]] and [[design-ux-patterns]] |
| New screen tab in nav graph | Reconcile against spec §30 acceptance criteria in [[design-ux-patterns]] §IA-drift |
| User-facing copy changed (especially EN/RO bilingual strings) | Update [[design-ux-patterns]] §Bilingual + cite `Strings.kt` anchor |
| 90 days elapsed without update | Run lint pass per §Lint below |

### Lint pass

Periodic (~quarterly) health-check. Look for:

1. **Stale `file:line` anchors** — grep each anchor; if file moved or line shifted by >20, refresh.
2. **Orphan pages** — every page should have ≥1 inbound `[[link]]` from another design page.
3. **Token drift** — diff `DieticianColors.kt` / `DieticianTypography.kt` / `DieticianShapes.kt` against [[design-visual-language]] inline values.
4. **Contradiction** — any page contradicting [[design-overview]] non-goals is a bug in the page (or a deliberate spec change that needs surfacing in `[design-overview].contradicts:[]`).
5. **Missing component** — grep `@Composable fun` under `shared/.../ui/`; every reusable Composable that ships in two or more screens should have an entry in [[design-components]].
6. **Source rot** — for each external URL in [[design-references]], note `accessed:` date; if >365d, re-fetch and update the citation.

### Ownership

LLM-owned. Victor (the human) curates the source list and asks clarifying questions; the LLM does the bookkeeping. Per the Karpathy pattern: "the tedious part of maintaining a knowledge base is not the reading or the thinking — it's the bookkeeping ... LLMs don't get bored, don't forget to update a cross-reference, and can touch 15 files in one pass."

## Two-file pattern (when it applies)

Most design pages are narrative-only — no live data. They use the single-file format above.

If a page ever needs to embed live tokens (e.g., a future `palette.data.md` auto-generated from `DieticianColors.kt` via a Gradle task), follow the AGENTS.md two-file split: `palette.md` (narrative) + `palette.data.md` (header `<!-- AUTOGENERATED. EDITS OVERWRITTEN. Last refresh: TIMESTAMP -->`). Narrative transcludes via `![[palette.data]]`. Until automation exists, inline is acceptable.

## Reading order for new contributors

1. [[design-overview]] — full read (~10 min)
2. The page closest to your task — full read
3. [[design-references]] — skim only; jump to specific entries when cited

## Log

Append-only. Prefix each entry with `## [YYYY-MM-DD] kind | summary` (parseable via `grep "^## \[" index.md`).

### [2026-05-19] init | design wiki created

Initial six-page wiki built after deep UI/UX research pass. Codebase mapped (14 user-facing screens + 24 reusable components + ~77 Composables across 48 files, current branch `plan-4-5/nav-mount-iter-9`). Web research spanned 22 searches across nutrition-tracker UX, premium AI chat UI, Material 3 Expressive, Apple HIG motion, WCAG 2.2 mobile, color palette systems, AI Act transparency, eating-disorder-safe design, Romanian diacritic handling, bento grid layout, Linear/Raycast/Vercel/Stripe design philosophies. Karpathy LLM-Wiki gist (https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f) consumed as schema template. Internal `docs/superpowers/research/2026-05-17-round-3-ux-regulation.md` integrated as canonical product-specific UX research (12-app review, dark-pattern ban list, ED-safeguard primitives, EU AI Act mapping, Romanian cultural eating context). `CLAUDE.md` at project root created to point future sessions at this wiki + define maintenance triggers.

### [2026-05-19] add | implementation page + per-page implementation notes

Added [[design-implementation]] consolidated build plan + per-page Implementation subsections in `visual-language.md`, `ux-patterns.md`, `components.md`. Sourced from 10-domain parallel-agent research pass covering: theming (M3 30+ ColorScheme roles, dynamic-color opt-out, warm-black dark mode, WCAG contrast verification, CompositionLocal patterns + Voyager #4685 bug); typography (Atkinson Hyperlegible bold/italic/bold-italic TTFs missing, `fontFeatureSettings = "tnum"` with Skia desktop caveat, Romanian comma-below verification); motion (`MotionScheme` graduation from M3 1.4, expect/actual reduce-motion for desktop, blinking-caret pattern, valentinilk shimmer KMP, Snackbar Long=10s not 60s); charts (recommendation = keep hand-rolled Canvas over Vico/KoalaPlot/ComposeCharts, accessibility overlay per Eevis Panula, no-autoplay state gate); Voyager nav (1.1.0-beta02 → -beta03 safe bump, `CurrentScreen()` vs `lastItem.Content()`, BackHandler in commonMain, `ExternalUriHandler` deep-link pattern, MagicLinkVerify + Byok routes missing); M3 components + bento (LazyVerticalStaggeredGrid spans-only-FullLine constraint → use LazyVerticalGrid + GridItemSpan, ModalBottomSheet experimental + skipPartiallyExpanded desktop requirement, FilterChip vs FlowRow); capture pipeline (CameraX `LifecycleCameraController`, AWT FileDialog desktop, ML Kit barcode + ZXing fallback, Open Food Facts UA requirement + rate limit, Coil 3 KMP, whisper.cpp JNI path); accessibility (WCAG 2.2 new criteria 2.4.11/2.5.7/2.5.8, JAB on desktop, color-blind chat-bubble triplet, Romanian TTS via `SpanStyle(localeList)`); AI Act + GDPR (non-dismissable modal `DialogProperties` required, Art 7(3) withdrawal must not delete, Art 50 layered identity disclosure, audit-row chokepoint pattern); desktop + i18n + icons + markdown (Tabler Icons KMP for 20-glyph DieticianIcons, multiplatform-markdown-renderer with custom URI handler for `[[link]]` + `![[name.data]]` transclusion, Compose Desktop Window + WindowState persistence, command palette as AlertDialog).

Identified **12 verified drifts** in current code requiring fixes: AILiteracyBanner missing `DialogProperties(dismissOnBackPress=false,...)` (Android 14+ predictive-back bypass); AILiteracyVersionGate missing BackHandler override; AuditLogViewModel per-keystroke fetch without debounce; EDSafeguardModal `Dismiss` not audited; Snackbar UNDO 10s vs spec 60s; DieticianColors.kt missing `surfaceTint` + `surfaceContainer*` overrides (M3 purple drift on elevated surfaces); PantryItemCard AssistChip not using NeutralChip tokens; DieticianTypography only ships Atkinson Regular (fake-bold synthesis defeats accessibility); ExtendedFAB empty icon reserves padding; MagicLinkVerify + Byok routes unreachable from nav graph; PushedScreenScaffold missing explicit BackHandler defence-in-depth. Full table at end of [[design-implementation]].

Net library additions to land: shimmer + Coil 3 + ML Kit barcode (Android) + ZXing (desktop fallback) + Tabler Icons KMP + multiplatform-markdown-renderer + material3-window-size-class + Atkinson Hyperlegible bold/italic/bold-italic TTFs. ~475 KB shared bundle delta; tabler-icons replaces planned 11 MB material-icons-extended (net Android APK smaller).

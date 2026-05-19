---
title: "References — Bibliography"
slug: design-references
domain: design
applies_to: []
sources: []
authority: derived
confidence: high
last_verified: 2026-05-19
review_cadence_days: 180
instantiated_for_user: false
user_numbers: {}
related: [design-overview, design-visual-language, design-ux-patterns, design-components, design-index]
contradicts: []
supersedes: []
tags: [design, references, bibliography, sources]
---

# References

Every external source cited by another page in this wiki + the internal artefacts that feed design decisions. Maintain accessed dates; re-fetch when older than 365 days.

## Internal

- `docs/superpowers/specs/2026-05-17-dietician-design.md` — Dietician design spec. Sections most-cited by design pages: §0 Quick orientation, §1 Personal context, §11 Knowledge corpus, §17 Preferences + boredom, §22 Notifications, §28 Refusal triggers + macro guardrails, §29 Quality signals, §30 Acceptance criteria (data-testid visible-on-first-paint), §33 Project ergonomics, §34 Anti-patterns. *Source of truth — if this wiki contradicts spec, fix the wiki.*
- `docs/superpowers/specs/2026-05-17-dietician-plans-2-7-research-driven.md` — research-driven spec (3047 lines, 20 binding amendments, 184 data-testid across 21 URLs). Cross-reference for surfaces planned post Plan-4-5.
- `docs/superpowers/research/2026-05-17-round-3-ux-regulation.md` — UX + a11y + AI Act + GDPR + ED-safeguards + Romanian cultural eating research. 12-app review, dark-pattern ban list, EU AI Act mapping, Romanian eating-pattern context. **Canonical product-specific UX research.**
- `docs/superpowers/research/2026-05-17-round-1-behavior-change.md` — behaviour-change research; informs adherence + boredom-decay UX choices.
- `docs/superpowers/research/2026-05-17-round-2-tech-stack.md` — KMP + Compose + LLM-router research; informs Compose Multiplatform + Voyager choice.
- `docs/superpowers/research/2026-05-17-round-4-ro-thin-spots.md` — RO supermarket + Anelis + cultural specifics.
- `docs/superpowers/research/2026-05-17-round-5-roi-gaps.md` — gaps + ROI analysis.
- `docs/superpowers/research/2026-05-17-final-sunk-cost-free.md` — final research synthesis, blind-spot driven.
- `docs/superpowers/research/2026-05-17-meta-blindspots.md` — meta-analysis of research blindspots.
- `docs/superpowers/research/2026-05-17-audit.md` — audit notes.
- `AGENTS.md` — agent conventions: three-layer wiki pattern, two-file pattern, YAML frontmatter schema, refusal triggers, anti-patterns, build/test commands. **Read before touching wiki.**
- `JARVIS_MERGE.md` — adapter contract for folding Dietician into jarvis-kotlin. Affects long-term IA choices (Subsystem interface, side-channel JSON for structured data).
- `docs/policies/AI_LITERACY_TEXT_VERSION.md` — version policy for the AILiteracyBanner text. Bumps require re-firing the banner.
- `docs/runbooks/smoke-checklist-plan-4-5.md` — KMP UI smoke checklist; reflects the acceptance gates this design wiki targets.
- `docs/runbooks/*.md` — 10+ failure-mode runbooks. Useful for designing error-state surfaces.
- `shared/src/commonMain/kotlin/com/dietician/shared/ui/` — KMP Compose source tree. The visual source of truth for tokens, components, screens.
- `~/.claude/projects/C--Users-User-Desktop-Dietician/memory/BRIDGE.md` — append-only session handoff log; useful when a design decision was made in a prior session and needs context.

## Karpathy LLM Wiki pattern (schema source)

- Karpathy A. — *Building an LLM-maintained wiki*. <https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f>. Accessed 2026-05-19. Three-layer model (raw / wiki / schema), ingest / query / lint workflows, index.md content-catalog + log.md chronological-append pattern.

## Design systems + canonical references

- W3C. *Web Content Accessibility Guidelines (WCAG) 2.2*. <https://www.w3.org/TR/WCAG22/>. Accessed 2026-05-19. Touch-target 24 CSS px minimum, contrast 4.5:1 body / 3:1 large, focus indicator 2-px perimeter + 3:1 contrast, new criteria 2.4.11 Focus Not Obscured + 2.5.7 Dragging Movements.
- W3C. *Guidance on Applying WCAG 2.2 to Mobile Applications (WCAG2Mobile)*. <https://www.w3.org/TR/wcag2mobile-22/>. Accessed 2026-05-19.
- corpowid.ai. *Mobile Application Accessibility Guide (2026) – WCAG 2.2, iOS & Android, Real-Device Testing*. <https://corpowid.ai/blog/mobile-application-accessibility-practical-humancentered-guide-android-ios>. Accessed 2026-05-19.
- Apple. *Human Interface Guidelines — Motion*. <https://developer.apple.com/design/human-interface-guidelines/motion>. Accessed 2026-05-19. "Important information isn't conveyed solely through motion." Reduced-motion mandate. iOS 26 calm-motion guidance.
- Google. *Material Design 3 — Compose docs*. <https://developer.android.com/develop/ui/compose/designsystems/material3>. Accessed 2026-05-19.
- Google. *Material 3 Expressive*. <https://supercharge.design/blog/material-3-expressive> and <https://zoewave.medium.com/compose-material-3-expressive-89f4147df5b8>. Accessed 2026-05-19. 35 new shapes + shape morphing, motion physics tokens, accessibility research showing up-to-4× faster element location for participants with varying movement/visual abilities.
- JetBrains. *Compose Multiplatform — keyboard events + window management*. <https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-keyboard.html> + <https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html>. Accessed 2026-05-19. `onPreviewKeyEvent` for desktop shortcuts. Top-level window management API.
- JetBrains. *What's new in Compose Multiplatform 1.9.3*. <https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html>. Accessed 2026-05-19.

## Typography

- Braille Institute. *Atkinson Hyperlegible*. <https://www.brailleinstitute.org/freefont/> + Google Fonts <https://fonts.google.com/specimen/Atkinson+Hyperlegible>. Accessed 2026-05-19. Free OFL 1.1; designed to differentiate misinterpreted characters (B/8, 1/L/l/I).
- Crosley B. *Type Scales: How I Chose 13 Steps and Why the Ratio Matters*. <https://blakecrosley.com/blog/typography-systems>. Accessed 2026-05-19. Content-driven ratios out-perform strict mathematical progressions for content-heavy apps.
- Sirkotsky K. *Inclusive typefaces that we know and love*. <https://sirkotsky.substack.com/p/inclusive-typefaces-that-we-know>. Accessed 2026-05-19. Inter ↔ Atkinson compatibility.
- iubenda. *Most accessible fonts: How to choose typography that supports inclusion*. <https://www.iubenda.com/en/help/182497-most-accessible-fonts/>. Accessed 2026-05-19.
- Fontfabric. *Romanian Fonts — language support*. <https://www.fontfabric.com/language-support/romanian-fonts/>. Accessed 2026-05-19.
- Brandient. *Romanian diacritic marks*. <https://brandient.com/kit-on-romanian-diacritics>. Accessed 2026-05-19. Comma-below `ș ț` vs cedilla `ş ţ`; Windows Vista onward supports correct rendering.

## Colour systems

- envato Elements. *Best 8 Mobile App Color Scheme Trends for 2026*. <https://elements.envato.com/learn/color-scheme-trends-in-mobile-app-design>. Accessed 2026-05-19. Neutral and nature-inspired (sage, terracotta, sand) for wellness brands.
- WebOsmotic. *Modern App Colors: Design Palettes That Work In 2026*. <https://webosmotic.com/blog/modern-app-colors/>. Accessed 2026-05-19. "Color in 2026 is less about loud tricks and more about clear roles."
- Color Hunt. *Food Color Palettes*. <https://colorhunt.co/palettes/food>. Accessed 2026-05-19.
- color-meanings.com. *31 Food Color Palettes for Appetizing Designs*. <https://www.color-meanings.com/food-color-palettes/>. Accessed 2026-05-19. Cream/cinnamon/nutmeg for comfort-food warmth; sandy-beige/terracotta/sage for wellness.

## Premium product design

- Mantlr. *How Stripe, Linear, and Vercel Ship Premium UI*. <https://mantlr.com/blog/stripe-linear-vercel-premium-ui>. Accessed 2026-05-19. "Sparse visually, interaction-dense." Linear → Inter. Vercel → Geist. Stripe → Söhne. Premium decisions invariant: interaction density + responsiveness, typography as brand, restraint in colour, crafted microstates, respect for physical metaphor.
- LogRocket. *Linear design: The SaaS design trend that's boring and bettering UI*. <https://blog.logrocket.com/ux-design/linear-design/>. Accessed 2026-05-19.
- SeedFlip. *Vercel Design System Breakdown*. <https://seedflip.co/blog/vercel-design-system>. Accessed 2026-05-19.
- bitjaru. *styleseed — design.md library*. <https://github.com/bitjaru/styleseed>. Accessed 2026-05-19. Reference for brand-skin extraction patterns.
- Salvucci S. *Awesome design-md*. <https://www.stefanosalvucci.com/en/blog/awesome-design-md-design-system-per-agenti-ai>. Accessed 2026-05-19.

## AI chat + LLM UI

- Smashing Magazine. *Practical Interface Patterns for AI Transparency (Part 2)*. <https://www.smashingmagazine.com/2026/05/practical-interface-patterns-ai-transparency/>. Accessed 2026-05-19. Progressive disclosure pattern, transparency UI components, accountability surfaces.
- Smashing Magazine. *Designing For Agentic AI: Practical UX Patterns For Control, Consent, And Accountability*. <https://www.smashingmagazine.com/2026/02/designing-agentic-ai-practical-ux-patterns/>. Accessed 2026-05-19. Five enterprise-agent patterns: planning visibility, tool-use disclosure, memory surfacing, multi-step workflow tracking, recovery routing.
- thefrontkit. *AI Chat UI Best Practices: Designing Better LLM Interfaces (2026)*. <https://thefrontkit.com/blogs/ai-chat-ui-best-practices>. Accessed 2026-05-19. Streaming = baseline expectation.
- Langtail. *Understanding LLM Chat Streaming*. <https://langtail.com/blog/llm-chat-streaming>. Accessed 2026-05-19.
- Fuselab Creative. *Agent UX: UI Design for AI Agents in 2026*. <https://fuselabcreative.com/ui-design-for-ai-agents/>. Accessed 2026-05-19.
- agentmodeai. *EU AI Act Article 50 disclosure UX 2026*. <https://agentmodeai.com/eu-ai-act-article-50-transparency-disclosure/>. Accessed 2026-05-19. Disclosure obligations effective 2 Aug 2026.

## Regulation + ethics

- European Commission. *AI Act Service Desk — Article 13: Transparency and provision of information to deployers*. <https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-13>. Accessed 2026-05-19.
- EU AI Act. *Key Issue 5: Transparency Obligations*. <https://www.euaiact.com/key-issue/5>. Accessed 2026-05-19.
- GDPR Local. *AI Transparency Requirements: Compliance and Implementation*. <https://gdprlocal.com/ai-transparency-requirements/>. Accessed 2026-05-19.
- arXiv. *Dark Patterns and Consumer Protection Law for App Makers*. <https://arxiv.org/pdf/2603.10020>. Accessed 2026-05-19.

## Nutrition tracker landscape

- Cronometer Blog. *Photo Logging: The Easiest Way to Track Your Meals in Cronometer*. <https://cronometer.com/blog/photo-logging/>. Accessed 2026-05-19.
- Gemma Sampson. *Nutrition tracking: Cronometer vs Myfitnesspal (free versions)*. <https://www.gemmasampson.com/blog/cronometer-vs-myfitnesspal>. Accessed 2026-05-19.
- Nutrola. *Best MyFitnessPal Alternatives in 2026*. <https://nutrola.app/en/blog/best-myfitnesspal-alternatives-2026>. Accessed 2026-05-19.
- Calorie Bliss. *Best Calorie Tracking Apps 2026*. <https://caloriebliss.com/articles/best-calorie-tracking-apps-2026-myfitnesspal-lose-it-cronometer/>. Accessed 2026-05-19.
- The Swaddle. *Health Tracking Apps Provide a Worrying Pipeline to Eating Disorders. Better Tech Design Can Fix That.* <https://www.theswaddle.com/health-tracking-apps-provide-a-worrying-pipeline-to-eating-disorders-better-tech-design-can-fix-that>. Accessed 2026-05-19.
- BJPsych Open. *Effects of diet and fitness apps on eating disorder behaviours: qualitative study* (PMC8485346). <https://pmc.ncbi.nlm.nih.gov/articles/PMC8485346/>. Accessed 2026-05-19.
- Levinson CA et al. *Calorie tracking and ED — clinical sample* (PMC5700836). <https://pmc.ncbi.nlm.nih.gov/articles/PMC5700836/>. Accessed 2026-05-19. ~73% of MyFitnessPal users in clinical sample perceived it as contributing to their ED.
- ResearchGate. *"It's Definitely Been a Journey": A Qualitative Study on How Women with Eating Disorders Use Weight Loss Apps*. <https://www.researchgate.net/publication/313204727>. Accessed 2026-05-19.
- PMC. *Mobile Food Tracking Apps: Do They Provoke Disordered Eating Behavior? Results of a Longitudinal Study*. <https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11556259/>. Accessed 2026-05-19.
- National Center for Health Research. *Fitness Tracking Apps and Eating Disorders*. <https://www.center4research.org/fitness-tracking-apps-eating-disorders/>. Accessed 2026-05-19.

## Health + wearables UX

- Heal Nourish Grow. *Whoop vs Oura: Which Wearable Is Better for Recovery, Sleep and Long Term Health?* <https://healnourishgrow.com/whoop-vs-oura/>. Accessed 2026-05-19. Oura = calm + minimal, Whoop = data-dense + gamified.
- Tom's Guide. *Whoop vs Oura: I tested each sleep tracker for two weeks*. <https://www.tomsguide.com/wellness/sleep-tech/whoop-vs-oura-i-tested-each-sleep-tracker-for-two-weeks-heres-my-winner>. Accessed 2026-05-19.
- ROOK. *Oura vs Whoop: Complete Comparison*. <https://www.tryrook.io/blog/oura-vs-whoop>. Accessed 2026-05-19.

## Food + cooking apps

- top10.com. *Best Cooking & Recipes Apps*. <https://www.top10.com/cooking-apps>. Accessed 2026-05-19.
- WhistleOut. *Paprika App Review*. <https://www.whistleout.com/CellPhones/Reviews/paprika-app>. Accessed 2026-05-19. Personal, curated cookbook + clipping + meal planning + grocery list + pantry management.
- Suri Y. *NYT Cooking app Recipe Page Redesign* case study. <https://www.suriyeseul.design/work/nyt-cooking>. Accessed 2026-05-19.
- Tubik Studio. *Case Study: Perfect Recipes App. UX Design for Cooking and Shopping*. <https://blog.tubikstudio.com/case-study-recipes-app-ux-design/>. Accessed 2026-05-19.

## Personal knowledge / second-brain tools

- Storyflow. *The 12 Best Obsidian Alternatives in 2026*. <https://storyflow.so/blog/best-obsidian-alternatives-2026>. Accessed 2026-05-19. Reflect = "most polished daily-journal tool with a calm interface."
- Buildin. *17 Best Second Brain Apps in 2026*. <https://buildin.ai/blog/best-second-brain-apps-2026>. Accessed 2026-05-19.
- Saner. *Second Brain AI Apps: We Tested the Best 10 in 2026*. <https://blog.saner.ai/10-best-second-brain-ai-apps/>. Accessed 2026-05-19.
- Tana & Obsidian deep dive — Arsan Y. on Medium. <https://medium.com/personal-knowledge-management-deep-dive/tana-obsidian-stand-out-from-the-crowd-04e9f5495e8a>. Accessed 2026-05-19.

## Voice + transcription

- Whisper Memos. <https://whispermemos.com/>. Accessed 2026-05-19. One-tap recording paradigm.
- Whisper Notes — offline transcription. <https://whispernotes.app/>. Accessed 2026-05-19. On-device transcription, zero cloud uploads — model for the Dietician on-device whisper.cpp pipeline.
- AudioNotes. *Best Apps to Summarize Voice Recordings (2026)*. <https://www.audionotes.app/blog/best-mobile-apps-for-summarizing-voice-recording>. Accessed 2026-05-19.

## Food recognition (photo)

- Lifesum. *Can Lifesum Scan Food From Photos? (2026 Review)*. <https://nutrola.app/en/blog/can-lifesum-scan-food-from-photos>. Accessed 2026-05-19.
- LensApp. *Best Food Scanner App in 2026*. <https://lensapp.io/blog/best-food-scanner-app/>. Accessed 2026-05-19.
- PMC. *Comparison of food-recognition platforms* (PMC7752530). <https://pmc.ncbi.nlm.nih.gov/articles/PMC7752530/>. Accessed 2026-05-19. Foodvisor 46% top-1, Bitesnap 49%, Calorie Mama 63% top-1 / 88% top-5.
- MDPI. *Deep-learning review of food image recognition*. <https://www.mdpi.com/2076-3417/15/14/7626>. Accessed 2026-05-19. Single-item 85-95%; mixed dishes 72.92% top-1 on ISIA Food-200.
- kcalm.app. *AI food recognition accuracy review*. <https://www.kcalm.app/blog/ai-food-recognition-accuracy/>. Accessed 2026-05-19. Portion-estimation typical error ±15-30%.
- Nature Communications Medicine. *DietAI24 framework*. <https://www.nature.com/articles/s43856-025-01159-0>. Accessed 2026-05-19.

## UI inspiration / layout patterns

- Mobbin. *Tab Bar UI Design: Best practices, Design variants & Examples*. <https://mobbin.com/glossary/tab-bar>. Accessed 2026-05-19. ≤5 bottom-nav tabs.
- Mobbin. *Tab Bar UI Design Examples for Mobile Apps*. <https://mobbin.com/explore/mobile/ui-elements/tab-bar>. Accessed 2026-05-19.
- Mobbin. *Food App Mobile Design*. <https://mobbin.com/explore/mobile/app-categories/food-drink>. Accessed 2026-05-19. 28k+ curated food/drink app designs.
- orbix studio. *Bento Grid Dashboard Design: Complete Guide 2026*. <https://www.orbix.studio/blogs/bento-grid-dashboard-design-aesthetics>. Accessed 2026-05-19.
- landdding. *Bento Grid Design: How to Create Modern Modular Layouts in 2026*. <https://landdding.com/blog/blog-bento-grid-design-guide>. Accessed 2026-05-19. 47% increase in dwell time, 38% increase in CTR.
- saasframe. *Designing Bento Grids That Actually Work: A 2026 Practical Guide*. <https://www.saasframe.io/blog/designing-bento-grids-that-actually-work-a-2026-practical-guide>. Accessed 2026-05-19.
- muz.li. *50 Best Dashboard Design Examples for 2026*. <https://muz.li/blog/best-dashboard-design-examples-inspirations-for-2026/>. Accessed 2026-05-19.

## Metric + progress visualisation

- Ruixen UI. *Health Stat Card*. <https://www.ruixen.com/docs/components/health-stat-card>. Accessed 2026-05-19.
- Dell Design System. *Metrics Card*. <https://www.delldesignsystem.com/data-visualization/metrics-card>. Accessed 2026-05-19.
- PatternFly. *Dashboard design guidelines*. <https://www.patternfly.org/patterns/dashboard/design-guidelines/>. Accessed 2026-05-19.
- MacroFactor. *Expands Progress Tracking Feature Set with Photos and Body Metrics*. <https://macrofactor.com/body-metrics/>. Accessed 2026-05-19.
- MacroFactor. *Algorithms and core philosophy*. <https://macrofactor.com/macrofactors-algorithms-and-core-philosophy/>. Accessed 2026-05-19.

## How-to + technical patterns

- Compose Multiplatform GitHub. <https://github.com/JetBrains/compose-multiplatform>. Accessed 2026-05-19.
- Compose Material 3 Compose docs. <https://developer.android.com/jetpack/androidx/releases/compose-material3>. Accessed 2026-05-19.

## To-read (queued, not yet integrated)

- *The Linear Method* — public design philosophy by Linear. Cited in [Mantlr 2026]; not yet fetched. Plan: read in full when next design pass begins.
- *Vercel Geist* type-system documentation by Basement Studio. Plan: examine for desktop-density type-scale patterns.
- *Apple HIG — iOS 26 motion guide* (Hui Wang Medium summary). <https://medium.com/@foks.wang/ios-26-motion-design-guide-key-principles-and-practical-tips-for-transition-animations-74def2edbf7c>. Read for the cross-fade pattern when extending CompositionLocal-based theming to per-screen motion overrides.
- *Nielsen Norman Group — recipe / cookbook UX* (TBD search). Plan: targeted research for the next Cookbook iteration.

## Implementation research (added 2026-05-19)

Library + KMP-tutorial + Compose Multiplatform 1.7.0 sources from the 10-domain implementation pass. Consumed by [[design-implementation]].

### Compose Multiplatform + Material 3 platform

- JetBrains. *Compose Multiplatform 1.7.0 release notes.* <https://blog.jetbrains.com/kotlin/2024/10/compose-multiplatform-1-7-0-released/>. Accessed 2026-05-19.
- JetBrains. *What's new in Compose Multiplatform 1.7.* <https://kotlinlang.org/docs/multiplatform/whats-new-compose-170.html>. Accessed 2026-05-19.
- JetBrains. *Top-level windows management — Compose Desktop.* <https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html>. Accessed 2026-05-19.
- JetBrains. *Keyboard events — Compose Desktop.* <https://kotlinlang.org/docs/multiplatform/compose-desktop-keyboard.html>. Accessed 2026-05-19.
- JetBrains. *Compose Multiplatform resources usage.* <https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html>. Accessed 2026-05-19.
- JetBrains. *Manage local resource environment.* <https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html>. Accessed 2026-05-19.
- JetBrains. *Localizing strings.* <https://kotlinlang.org/docs/multiplatform/compose-localize-strings.html>. Accessed 2026-05-19.
- JetBrains. *Compose Desktop accessibility.* <https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html>. Accessed 2026-05-19.
- JetBrains. *Compose Multiplatform deep links.* <https://kotlinlang.org/docs/multiplatform/compose-navigation-deep-links.html>. Accessed 2026-05-19.
- JetBrains/compose-multiplatform-core. *PR #557 — Segoe UI on Windows desktop.* <https://github.com/JetBrains/compose-multiplatform-core/pull/557>. Accessed 2026-05-19.
- JetBrains/compose-multiplatform. *Issue #4471 — `Font(...)` composable restriction.* <https://github.com/JetBrains/compose-multiplatform/issues/4471>. Accessed 2026-05-19.
- JetBrains/compose-multiplatform. *Issue #4685 — Voyager + `staticCompositionLocalOf` propagation bug.* <https://github.com/JetBrains/compose-multiplatform/issues/4685>. Accessed 2026-05-19.
- JetBrains/compose-multiplatform. *Issue #3442 — Skia per-glyph font fallback within FontFamily.* <https://github.com/JetBrains/compose-multiplatform/issues/3442>. Accessed 2026-05-19.
- JetBrains/compose-multiplatform. *Issue #3388 — undecorated window animations on Windows.* <https://github.com/JetBrains/compose-multiplatform/issues/3388>. Accessed 2026-05-19.
- JetBrains/compose-jb. *Issue #178 — undecorated window resize support.* <https://github.com/JetBrains/compose-jb/issues/178>. Accessed 2026-05-19.
- JetBrains/compose-multiplatform. *v1.7.0 release notes.* <https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.7.0>. Accessed 2026-05-19.

### Voyager navigation

- adrielcafe. *Voyager — Navigation.* <https://voyager.adriel.cafe/navigation/>. Accessed 2026-05-19. Navigator + Scaffold pattern; `CurrentScreen()` vs `navigator.lastItem.Content()`.
- adrielcafe. *Voyager — Tab Navigation.* <https://voyager.adriel.cafe/navigation/tab-navigation/>. Accessed 2026-05-19. TabNavigator caveats.
- adrielcafe/voyager. *Issue #383 — state preservation with nested Navigators.* <https://github.com/adrielcafe/voyager/issues/383>. Accessed 2026-05-19.
- adrielcafe/voyager. *Issue #303 — disposeSteps for tab→screen→tab.* <https://github.com/adrielcafe/voyager/issues/303>. Accessed 2026-05-19.
- adrielcafe/voyager. *Issue #421 — bottom nav disappears on push.* <https://github.com/adrielcafe/voyager/issues/421>. Accessed 2026-05-19.
- Erick Medina. *Kotlin Multiplatform navigation libraries 2024 (Voyager / Decompose / Appyx / NavHost-Compose).* <https://eamedina87.medium.com/kotlin-multiplatform-navigation-libraries-in-2024-0979d3fb465b>. Accessed 2026-05-19.
- iifx.dev. *Intercepting the back gesture with Compose Multiplatform BackHandler.* <https://iifx.dev/en/articles/456521042/intercepting-the-back-gesture-using-compose-multiplatform-s-backhandler-for-dialogs-and-exit-confirmation>. Accessed 2026-05-19.

### Material 3 component references

- composables.com. *Material 3 component browser* — Card, AssistChip, FilterChip, ExtendedFloatingActionButton, Switch, ExposedDropdownMenuBox, LazyVerticalStaggeredGrid. <https://composables.com/material3>. Accessed 2026-05-19.
- Android Developers. *NavigationBarItemDefaults.* <https://developer.android.com/reference/kotlin/androidx/compose/material3/NavigationBarItemDefaults>. Accessed 2026-05-19.
- Android Developers. *App bars.* <https://developer.android.com/develop/ui/compose/components/app-bars>. Accessed 2026-05-19.
- Android Developers. *Lazy lists and grids.* <https://developer.android.com/develop/ui/compose/lists>. Accessed 2026-05-19.
- Android Developers. *Partial bottom sheet.* <https://developer.android.com/develop/ui/compose/components/bottom-sheets-partial>. Accessed 2026-05-19.
- Android Developers. *Dialog component guide.* <https://developer.android.com/develop/ui/compose/components/dialog>. Accessed 2026-05-19.
- Android Developers. *Switch component guide.* <https://developer.android.com/develop/ui/compose/components/switch>. Accessed 2026-05-19.
- Android Developers. *FAB component guide.* <https://developer.android.com/develop/ui/compose/components/fab>. Accessed 2026-05-19.
- Android Developers. *Snackbar component guide.* <https://developer.android.com/develop/ui/compose/components/snackbar>. Accessed 2026-05-19.
- Joe Birch. *Exploring Lazy Staggered Grids in Jetpack Compose.* <https://medium.com/google-developer-experts/exploring-lazy-staggered-grids-in-jetpack-compose-5940d5a393be>. Accessed 2026-05-19.
- Ryan W. *calculateWindowSizeClass for KMP.* <https://callmeryan.medium.com/material-3-calculatewindowsizeclass-implementing-responsive-android-and-kotlin-multiplatform-fe03c7f2fdf6>. Accessed 2026-05-19.

### Motion + animation

- Android Developers. *Material 3 release notes — MotionScheme graduation 1.4.0.* <https://developer.android.com/jetpack/androidx/releases/compose-material3>. Accessed 2026-05-19.
- Material 3. *Easing and Duration token specs.* <https://m3.material.io/styles/motion/easing-and-duration/tokens-specs>. Accessed 2026-05-19.
- Material 3. *Expressive motion theming blog.* <https://m3.material.io/blog/m3-expressive-motion-theming>. Accessed 2026-05-19.
- Android Developers. *Compose animation customize guide.* <https://developer.android.com/develop/ui/compose/animation/customize>. Accessed 2026-05-19.
- Android Developers. *Compose animation composables & modifiers.* <https://developer.android.com/develop/ui/compose/animation/composables-modifiers>. Accessed 2026-05-19.
- Rebecca Franks. *Easing in to Easing Curves in Jetpack Compose.* <https://medium.com/androiddevelopers/easing-in-to-easing-curves-in-jetpack-compose-d72893eeeb4d>. Accessed 2026-05-19.
- Eevis Panula. *Android Animations and Reduced Motion.* <https://eevis.codes/blog/2022-12-12/android-animations-and-reduced-motion/>. Accessed 2026-05-19.
- Steven de Tilly. *Smooth content transitions with AnimatedContent.* <https://medium.com/@stevendetilly/smooth-content-transitions-in-jetpack-compose-with-animatedcontent-7ecb380030bc>. Accessed 2026-05-19.
- fornewid. *Material Motion for Compose Multiplatform.* <https://github.com/fornewid/material-motion-compose>. Accessed 2026-05-19.

### Charts

- patrykandpatrick/vico. *Vico KMP chart library v3.1.0 (Apr 2026).* <https://github.com/patrykandpatrick/vico>. Accessed 2026-05-19.
- Vico. *Documentation.* <https://guide.vico.patrykandpatrick.com/>. Accessed 2026-05-19.
- KoalaPlot. *koalaplot-core v0.11.2 (May 2026).* <https://github.com/KoalaPlot/koalaplot-core>. Accessed 2026-05-19.
- ehsannarmani. *ComposeCharts v0.2.5 (Feb 2026).* <https://github.com/ehsannarmani/ComposeCharts>. Accessed 2026-05-19.
- TheChance101. *AAY-chart.* <https://github.com/TheChance101/AAY-chart>. Accessed 2026-05-19.
- netguru. *compose-multiplatform-charts.* <https://github.com/netguru/compose-multiplatform-charts>. Accessed 2026-05-19.
- John O'Reilly. *Compose Multiplatform charts (iOS+Android+Desktop).* <https://johnoreilly.dev/posts/compose-multiplatform-chart/>. Accessed 2026-05-19.
- Eevis Panula. *Accessible Graphs with Compose Pt 1.* <https://eevis.codes/blog/2023-07-24/more-accessible-graphs-with-jetpack-compose-part-1-adding-content/>. Accessed 2026-05-19. Canvas + semantics overlay pattern — load-bearing.
- Eevis Panula. *Text on Compose Canvas.* <https://eevis.codes/blog/2024-11-10/not-a-phase-text-with-compose-and-canvas/>. Accessed 2026-05-19.
- Android Developers. *Compose Graphics overview.* <https://developer.android.com/develop/ui/compose/graphics/draw/overview>. Accessed 2026-05-19.
- Emre Ozsahin. *Interactive Donut Chart with Compose.* <https://medium.com/@eozsahin1993/interactive-donut-chart-using-jetpack-compose-140ca6b0e68c>. Accessed 2026-05-19. `drawArc + useCenter=false` pattern.
- MacroFactor. *Energy Expenditure Calculator + Algorithm Accuracy.* <https://macrofactorapp.com/energy-expenditure-calculator-app/>, <https://macrofactorapp.com/algorithm-accuracy/>. Accessed 2026-05-19.

### Capture pipeline (CameraX, ML Kit, ZXing, Coil, whisper.cpp)

- Android Developers. *CameraX architecture.* <https://developer.android.com/media/camera/camerax/architecture>. Accessed 2026-05-19.
- Android Developers. *ImageCapture.OnImageCapturedCallback.* <https://developer.android.com/reference/androidx/camera/core/ImageCapture.OnImageCapturedCallback>. Accessed 2026-05-19.
- Android Developers Blog. *What's new in CameraX 1.4.0 (Dec 2024).* <https://android-developers.googleblog.com/2024/12/whats-new-in-camerax-140-and-jetpack-compose-support.html>. Accessed 2026-05-19.
- Ramadan Sayed. *CameraX in Jetpack Compose — complete guide.* <https://medium.com/@ramadan123sayed/camerax-in-jetpack-compose-the-complete-guide-preview-photo-capture-video-recording-a737fac5caf6>. Accessed 2026-05-19.
- Google. *ML Kit barcode scanning Android.* <https://developers.google.com/ml-kit/vision/barcode-scanning/android>. Accessed 2026-05-19.
- Android Developers. *CameraX MlKitAnalyzer.* <https://developer.android.com/media/camera/camerax/mlkitanalyzer>. Accessed 2026-05-19.
- Scanbot. *Jetpack Compose barcode scanner CameraX + ML Kit.* <https://scanbot.io/techblog/jetpack-compose-barcode-scanner-app-tutorial/>. Accessed 2026-05-19.
- ZXing GitHub. <https://github.com/zxing/zxing>. Accessed 2026-05-19.
- Open Food Facts. *API intro + tutorial.* <https://openfoodfacts.github.io/openfoodfacts-server/api/>, <https://openfoodfacts.github.io/openfoodfacts-server/api/tutorial-off-api/>. Accessed 2026-05-19.
- coil-kt. *Coil 3 + upgrading guide.* <https://github.com/coil-kt/coil>, <https://coil-kt.github.io/coil/upgrading_to_coil3/>. Accessed 2026-05-19.
- Domen Lanišnik. *Loading images with Coil in CMP.* <https://medium.com/@domen.lanisnik/loading-images-with-coil-in-compose-multiplatform-4c94e16a06d7>. Accessed 2026-05-19.
- ggml-org/whisper.cpp. *Repo + models README.* <https://github.com/ggml-org/whisper.cpp>, <https://github.com/ggml-org/whisper.cpp/blob/master/models/README.md>. Accessed 2026-05-19.
- HuggingFace. *ggerganov/whisper.cpp model hub.* <https://huggingface.co/ggerganov/whisper.cpp/tree/main>. Accessed 2026-05-19.
- HuggingFace. *openai/whisper-large-v3-turbo.* <https://huggingface.co/openai/whisper-large-v3-turbo>. Accessed 2026-05-19.
- Mohammed Chandwala. *Porting Whisper to Android via JNI + Kotlin.* <https://medium.com/@mohammedrazachandwala/building-localmind-how-i-ported-openais-whisper-to-android-using-jni-and-kotlin-575dddd38fdc>. Accessed 2026-05-19.
- arXiv. *Romanian Speech Recognition Whisper benchmark (arXiv 2511.03361).* <https://arxiv.org/html/2511.03361v1>. Accessed 2026-05-19.
- Android Developers. *Tap and press gesture handling in Compose.* <https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press>. Accessed 2026-05-19.
- Naufal Prakoso. *Why your CMP project will break without expect/actual.* <https://medium.com/@naufalprakoso24/why-your-compose-multiplatform-project-will-break-without-expect-actual-9662371e22a0>. Accessed 2026-05-19.
- Ismoy Belizaire. *Unified Camera/Gallery Picker with expect/actual (DEV).* <https://dev.to/ismoy/kotlin-multiplatform-compose-unified-camera-gallery-picker-with-expectactual-and-permission-4573>. Accessed 2026-05-19.
- Kashif-E. *CameraK.* <https://github.com/Kashif-E/CameraK>. Accessed 2026-05-19.
- Composables.com. *Runtime permissions guide.* <https://composables.com/blog/permissions>. Accessed 2026-05-19.
- Rafael Meneghelo. *Requesting permissions in Compose.* <https://medium.com/@rzmeneghelo/how-to-request-permissions-in-jetpack-compose-a-step-by-step-guide-7ce4b7782bd7>. Accessed 2026-05-19.

### Accessibility (WCAG 2.2)

- Android Developers. *Semantics in Compose.* <https://developer.android.com/develop/ui/compose/accessibility/semantics>. Accessed 2026-05-19.
- Android Developers. *Merging and clearing semantics.* <https://developer.android.com/develop/ui/compose/accessibility/merging-clearing>. Accessed 2026-05-19.
- Android Developers. *Change focus traversal order.* <https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-traversal-order>. Accessed 2026-05-19.
- Android Developers. *Compose accessibility testing.* <https://developer.android.com/develop/ui/compose/accessibility/testing>. Accessed 2026-05-19.
- W3C. *WCAG 2.2 SC 2.4.11 Focus Not Obscured (Minimum).* <https://www.w3.org/WAI/WCAG22/Understanding/focus-not-obscured-minimum.html>. Accessed 2026-05-19.
- W3C. *WCAG 2.2 SC 2.5.7 Dragging Movements.* <https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements.html>. Accessed 2026-05-19.
- W3C. *WCAG 2.2 SC 2.5.8 Target Size (Minimum).* <https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html>. Accessed 2026-05-19.
- W3C. *WCAG 2.2 SC 1.4.3 Contrast (Minimum).* <https://www.w3.org/TR/WCAG22/#contrast-minimum>. Accessed 2026-05-19.
- Adrian Roselli. *WCAG3 Contrast as of April 2026.* <https://adrianroselli.com/2026/04/wcag3-contrast-as-of-april-2026.html>. Accessed 2026-05-19.
- APCA. *Why APCA as a New Contrast Method.* <https://git.apcacontrast.com/documentation/WhyAPCA.html>. Accessed 2026-05-19.
- cvs-health. *android-compose-accessibility-techniques* (canonical Compose a11y test patterns). <https://github.com/cvs-health/android-compose-accessibility-techniques>. Accessed 2026-05-19.
- Material 3. *Touch target structure.* <https://m3.material.io/foundations/designing/structure>. Accessed 2026-05-19.

### AI Act + GDPR

- European Commission. *EU AI Act Article 50 (transparency).* <https://artificialintelligenceact.eu/article/50/>, <https://artificialintelligenceact.eu/transparency-rules-article-50/>. Accessed 2026-05-19.
- Inside Global Tech. *10 takeaways from EU Commission draft guidelines on AI transparency under the EU AI Act (May 2026).* <https://www.insideglobaltech.com/2026/05/12/10-takeaways-european-commission-draft-guidelines-on-ai-transparency-under-the-eu-ai-act/>. Accessed 2026-05-19.
- bluearrow. *Article 50 AI Act transparency explained.* <https://bluearrow.ai/article-50-ai-act-transparency/>. Accessed 2026-05-19.
- GDPR-info.eu. *Articles 7, 9, 15.* <https://gdpr-info.eu/art-7-gdpr/>, <https://gdpr-info.eu/art-9-gdpr/>, <https://gdpr-info.eu/art-15-gdpr/>. Accessed 2026-05-19.
- Devisia. *Pragmatic Guide to Article 7.* <https://devisia.pro/en/blog/article-7-gdpr>. Accessed 2026-05-19.
- noyb. *Right to withdraw consent (Art 7(3)).* <https://noyb.eu/en/your-right-withdraw-your-consent-article-73>. Accessed 2026-05-19.
- Secure Privacy. *Dark pattern avoidance 2026 checklist.* <https://secureprivacy.ai/blog/dark-pattern-avoidance-2026-checklist>. Accessed 2026-05-19.
- Legiscope. *Right of Access under GDPR.* <https://www.legiscope.com/blog/right-of-access-gdpr.html>. Accessed 2026-05-19.
- codersee. *PDFBox 3.0.3 + Kotlin tutorial.* <https://blog.codersee.com/sring-boot-kotlin-apache-pdfbox/>. Accessed 2026-05-19.
- Android Developers. *PrintedPdfDocument + custom document printing.* <https://developer.android.com/reference/kotlin/android/print/pdf/PrintedPdfDocument>, <https://developer.android.com/training/printing/custom-docs>. Accessed 2026-05-19.
- Android Developers. *Secure clipboard handling.* <https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling>. Accessed 2026-05-19.
- Bhanit Gaurav. *Android 14 / iOS 17 clipboard transparency.* <https://bhanitgaurav.medium.com/silent-clipboard-reads-are-over-android-14-ios-17-demand-privacy-transparency-d6b00434a60f>. Accessed 2026-05-19.
- PatternFly. *Status and severity.* <https://www.patternfly.org/patterns/status-and-severity/>. Accessed 2026-05-19.
- arXiv 2509.07022. *Tessa safety middleware paper* (lexical-gate + LLM-policy hybrid for health-adjacent assistants). <https://arxiv.org/pdf/2509.07022>. Accessed 2026-05-19.

### State + KMP coroutines

- kmpbits. *StateFlow & SharedFlow in Kotlin Multiplatform.* <https://medium.com/@kmpbits/crossing-the-finish-line-stateflow-sharedflow-in-kotlin-multiplatform-2ccf847b5feb>. Accessed 2026-05-19.
- Kotlin. *Common ViewModel in KMP.* <https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html>. Accessed 2026-05-19.
- Florina Muntenescu. *Cancellation in coroutines.* <https://medium.com/androiddevelopers/cancellation-in-coroutines-aa6b90163629>. Accessed 2026-05-19.
- Kevin Hsu. *Debouncing TextFields in Compose.* <https://xinkev.com/note/androiddev/debouncing-textfields-in-compose/>. Accessed 2026-05-19.

### Theming + tokens

- Kotlin. *ColorScheme — Compose Multiplatform Material3 API.* <https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-color-scheme/>. Accessed 2026-05-19.
- Material 3. *Color roles + advanced color customizations.* <https://m3.material.io/styles/color/roles>, <https://m3.material.io/styles/color/advanced/apply-colors>. Accessed 2026-05-19.
- Notion Avenue. *Notion Color Codes hex palette.* <https://www.notionavenue.co/post/notion-color-code-hex-palette>. Accessed 2026-05-19.
- Muz.li. *Dark Mode Design Systems guide.* <https://muz.li/blog/dark-mode-design-systems-a-complete-guide-to-patterns-tokens-and-hierarchy/>. Accessed 2026-05-19.
- Atmos. *Dark Mode UI Design best practices.* <https://atmos.style/blog/dark-mode-ui-best-practices>. Accessed 2026-05-19.
- Neil Bickford. *Computing WCAG Contrast Ratios.* <https://www.neilbickford.com/blog/2020/10/18/computing-wcag-contrast-ratios/>. Accessed 2026-05-19.
- Yair Morgenstern. *Test your Contrasts! (Kotlin impl).* <https://yairm210.medium.com/test-your-contrasts-33fc071cc599>. Accessed 2026-05-19.
- droidcon. *Static vs Dynamic CompositionLocals — trade-offs (Oct 2025).* <https://www.droidcon.com/2025/10/21/jetpack-compose-static-vs-dynamic-compositionlocals-reads-writes-and-trade-offs/>. Accessed 2026-05-19.
- Kerry Bisset. *Jetpack Compose Theme with CompositionLocal.* <https://medium.com/@kerry.bisset/jetpack-compose-theme-with-composition-local-spacing-shaping-and-status-colors-a00890724f9c>. Accessed 2026-05-19.
- jordond. *MaterialKolor* — seed-driven generation (alternative). <https://github.com/jordond/MaterialKolor>. Accessed 2026-05-19.

### Typography

- google/fonts. *Issue #167 — bad Ț diacritic in Roboto.* <https://github.com/google/fonts/issues/167>. Accessed 2026-05-19.
- googlefonts/atkinson-hyperlegible. *Glyphs source + repo.* <https://github.com/googlefonts/atkinson-hyperlegible>, <https://github.com/googlefonts/atkinson-hyperlegible/blob/main/sources/AtkinsonHyperlegible.glyphs>. Accessed 2026-05-19.
- Google Fonts. *Atkinson Hyperlegible Next (2025, 7 weights).* <https://fonts.google.com/specimen/Atkinson+Hyperlegible+Next>. Accessed 2026-05-19.
- Wikipedia. *Atkinson Hyperlegible — language coverage (27 incl. Romanian).* <https://en.wikipedia.org/wiki/Atkinson_Hyperlegible>. Accessed 2026-05-19.
- Kit Paul. *Romanian diacritic marks.* <https://kitblog.com/romanian-diacritic-marks>. Accessed 2026-05-19.
- composables.com. *TextStyle API reference.* <https://composables.com/docs/androidx.compose.ui/ui-text/classes/TextStyle>. Accessed 2026-05-19.
- alphaomardiallo. *Compose Multiplatform theming guide.* <https://blog.alphaomardiallo.com/compose-multiplatform-theming-a-comprehensive-guide-to-customization>. Accessed 2026-05-19.

### Iconography

- timoseyfarth. *Tabler Icons KMP.* <https://github.com/timoseyfarth/tabler-icons-kmp>. Accessed 2026-05-19. ~6000 icons, MIT, per-icon tree-shake, KMP. **Recommended** for `DieticianIcons`.
- DevSrSouza. *compose-icons (Tabler / Feather / Phosphor for Compose).* <https://github.com/DevSrSouza/compose-icons>. Accessed 2026-05-19.
- DevSrSouza. *svg-to-compose Gradle plugin.* <https://github.com/DevSrSouza/svg-to-compose>. Accessed 2026-05-19.
- dev778g-me. *PhosphorIcon-compose.* <https://github.com/dev778g-me/PhosphorIcon-compose>. Accessed 2026-05-19.

### Shimmer + skeleton

- valentinilk. *compose-shimmer 1.4.0 — KMP shimmer placeholder.* <https://github.com/valentinilk/compose-shimmer>. Accessed 2026-05-19. **Recommended** (Accompanist shimmer dead).
- Touchlab. *Loading shimmer in Compose KMP.* <https://touchlab.co/loading-shimmer-in-compose>. Accessed 2026-05-19.

### Markdown rendering + Obsidian-style transclusion

- mikepenz. *multiplatform-markdown-renderer.* <https://github.com/mikepenz/multiplatform-markdown-renderer>. Accessed 2026-05-19. **Recommended** M3 variant for wiki transclusion.
- Maven Central. *com.mikepenz:multiplatform-markdown-renderer artefact.* <https://central.sonatype.com/artifact/com.mikepenz/multiplatform-markdown-renderer>. Accessed 2026-05-19.
- mikepenz/multiplatform-markdown-renderer. *Issue #553 — link handling routes via LocalUriHandler.* <https://github.com/mikepenz/multiplatform-markdown-renderer/issues/553>. Accessed 2026-05-19.
- Obsidian. *Linking notes and files — embed pattern.* <https://help.obsidian.md/Linking+notes+and+files/Embed+files>. Accessed 2026-05-19. Source of `![[name]]` semantics.

### Command palette + fuzzy matching

- Raycast. *Keyboard shortcuts manual.* <https://manual.raycast.com/keyboard-shortcuts>. Accessed 2026-05-19.
- Destiner. *Designing a Command Palette.* <https://destiner.io/blog/post/designing-a-command-palette/>. Accessed 2026-05-19.
- Philip C. Davis. *Command Palette Interfaces.* <https://philipcdavis.com/writing/command-palette-interfaces>. Accessed 2026-05-19.
- solo-studios. *kt-fuzzy.* <https://github.com/solo-studios/kt-fuzzy>. Accessed 2026-05-19.
- android-password-store. *sublime-fuzzy (KMP port).* <https://github.com/android-password-store/sublime-fuzzy>. Accessed 2026-05-19.

### i18n in Compose MP

- proandroiddev. *Adding localization in CMP — sealed interface pattern.* <https://proandroiddev.com/adding-localization-support-in-compose-multiplatform-1792e4950c19>. Accessed 2026-05-19.
- Dženan Bećirović. *Compose Multiplatform localization (LocalComposeEnvironment + key() trick).* <https://medium.com/@dzenanbecirovicc/compose-multiplatform-localization-ec6745961ddc>. Accessed 2026-05-19.
- JetBrains/compose-multiplatform. *Issue #4197 — `LocalComposeEnvironment` made public.* <https://github.com/JetBrains/compose-multiplatform/issues/4197>. Accessed 2026-05-19.

### Detekt custom rules

- Detekt. *Extending guide.* <https://detekt.dev/docs/introduction/extensions/>. Accessed 2026-05-19.
- Daresay. *Write a custom Detekt rule (Part 2).* <https://medium.com/daresay/write-a-custom-detekt-rule-part-2-b583e043dd11>. Accessed 2026-05-19.

### Desktop windowing + tray + undecorated

- cgruber. *Window minimum size gist.* <https://gist.github.com/cgruber/8a10a54daaab4761fcfd92cf5e5ce1ef>. Accessed 2026-05-19.
- Sasikanth. *Dragging undecorated windows in Compose Desktop.* <https://www.sasikanth.dev/dragging-undecorated-windows-in-compose-desktop/>. Accessed 2026-05-19.
- JetBrains/compose-multiplatform-core. *Tray.desktop.kt source.* <https://github.com/JetBrains/compose-multiplatform-core/blob/44ed9b7c3e4ee66c583196baca38e300e91b84d7/compose/ui/ui/src/desktopMain/kotlin/androidx/compose/ui/window/Tray.desktop.kt>. Accessed 2026-05-19.

### Misc

- BoltUiX. *Material 3 Chips in Jetpack Compose.* <https://www.boltuix.com/2025/08/material-3-chips-in-jetpack-compose.html>. Accessed 2026-05-19.
- Mehedi Hasan. *Mastering TopAppBar scroll behaviors.* <https://mehedi17n.medium.com/mastering-topappbar-scroll-behaviors-in-jetpack-compose-2c4b3419ebc4>. Accessed 2026-05-19.
- Shreyas Patil. *Filtering text input in Compose.* <https://blog.shreyaspatil.dev/filtering-and-modifying-text-input-in-jetpack-compose-way/>. Accessed 2026-05-19.
- tuvakov. *Decimal Input VisualTransformation.* <https://dev.to/tuvakov/decimal-input-formatting-with-jetpack-composes-visualtransformation-110n>. Accessed 2026-05-19.
- Vaddavalli. *Custom Snackbar duration (ProAndroidDev).* <https://proandroiddev.com/how-to-set-custom-duration-for-material3-snackbar-in-jetpack-compose-497fbc491f8b>. Accessed 2026-05-19.
- Niranjan KY. *Haptic Feedback in Compose UI.* <https://medium.com/@niranjanky14/haptic-feedback-in-compose-ui-130b28f902e7>. Accessed 2026-05-19.
- arpitpatel009. *FilterChip Compose single/multi-select.* <https://medium.com/@arpitpatel009/filterchip-compose-single-multi-select-behaviour-d15f09ab7855>. Accessed 2026-05-19.
- Glyphs.app. *Localize your font — Romanian and Moldovan.* <https://glyphsapp.com/learn/localize-your-font-romanian-and-moldovan>. Accessed 2026-05-19.

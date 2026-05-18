# Round 3 — UX + A11y + Regulation + ED-Safeguards + Failure Modes

> Scope: nutrition-app UX patterns, accessibility (WCAG 2.2 / 3.0 draft), GDPR + ANSPDCP for nutrition data, EU AI Act mapping (Art. 4 / 5(1)(f) / 12 / 13 / 14 / 15 / 16), Romanian voice input, receipt-OCR-fail UX, dark patterns + eating-disorder safeguards, failure-mode taxonomy, and Romanian cultural eating context.
> Context: Dietician = Victor's personal lean-bulk system (188cm / 67.5kg, NOT cutting), KMP Compose (Android + Desktop), Ktor server, Postgres + pgvector on VPS via Tailscale, multi-user friends/family (~5), Romania jurisdiction, health-adjacent NOT medical device.

## TL;DR

1. **Ship Cronometer-style micronutrient density**, not MyFitnessPal social-feed sugar. Cronometer's nutrient-summary view + nutrient-target colour bars are the only best-in-class pattern; everyone else under-instruments. Mandate ≥30 tracked nutrients (13 vitamins + 17 minerals + AAs).
2. **MacroFactor-style adaptive expenditure** > fixed-target maths. Bayesian-style smoothing on a rolling weight×intake window beats the static Mifflin-St-Jeor calculator that every consumer app ships.
3. **Voice-first logging for the desktop kitchen, photo-second, barcode-third, text-fourth.** Compose Multiplatform + Whisper.cpp on-device gives Victor RO + EN ASR with zero round-trip; Romanian ASR has matured (WER ~3-5% on benchmarks since 2024).
4. **Hard-ban 8 dark patterns** even at n=5 users — streak-shame, kcal-floor below 1200, weight-loss-rate >0.5 kg/wk, red/green pass/fail colour coding, body-comparison feeds, body-weight-as-primary metric, OCR-blind-trust auto-log, hidden paywall.
5. **EU AI Act applies even at n=5** — Art. 4 (literacy) is in force since Feb 2, 2025; Art. 5(1)(f) emotion inference is *prohibited* (never infer mood from logging gaps); Art. 12/13/14/15/16 only fully fire if classified high-risk, but Dietician's "personal lifestyle tool" classification means most Annex III obligations stay advisory. Still wire MODEL_CARD + RISK_REGISTER + audit-log + transparency-card *now* — cheap, future-proof, sets internal hygiene.
6. **GDPR Art. 9 says nutrition data CAN be special-category** when linked to weight/medication/supplements interacting with medical context. Treat as special-category-health from day 1. ANSPDCP Law 190/2018 Arts. 3-4 + Decision 174/2018 trigger mandatory DPIA on large-scale health-data processing; n=5 friends/family is NOT "large-scale" — but DPIA still recommended as one-page template, plus DPO not required.
7. **ED-safeguard primitives** = soft toggles for kcal-hide / weight-hide / streak-disable; hard guardrails on kcal-floor (≥1500 for males >180 cm) and weight-loss-rate (≤0.5 kg/wk). NEDA-aligned. Lean-bulk is the opposite of ED risk-profile, but Victor is a young muscular male — bigorexia/muscle-dysmorphia is the matched risk, not anorexia.
8. **Romanian cultural eating reality**: 3-meal canonical pattern (heaviest = lunch traditionally, shifting to dinner in urban), Postul Crăciunului Nov 15 – Dec 24 vegan + Great Lent 48-day vegan periods need first-class "post" mode that doesn't fight the user. Treat Lent like a builtin macro-profile, not a "cheat day".

---

## 1. Nutrition app UX patterns (12 apps reviewed)

This section reviews 12 nutrition apps along a fixed rubric: **primary logging UI, search/match heuristic, micros depth, recipe builder, scale integration, social posture, ED-safety affordances**. Synthesis at §1.13.

### 1.1 Cronometer

**Verdict: The gold-standard for nutrient density. Lift heavily.**

- **Primary logging UI**: vertical food-diary list with a top color-coded "nutrient target bar" that fills as you approach daily targets per nutrient; energy balance + macro ratio + micronutrient completion all visible without navigating away ([repreturn.com Cronometer review 2025](https://repreturn.com/cronometer-review/); [calorie-trackers.com](https://calorie-trackers.com/reviews/cronometer/)).
- **Search/match**: NCCDB (Nutrition Coordinating Center) curated foods + USDA + branded barcode. Best signal-to-noise of any consumer app because Cronometer rejected the "anyone-can-add" myFitnessPal-style crowdsource for branded entries — they require receipts ([cronometer.com features](https://cronometer.com/features/index.html)).
- **Micros depth**: tracks 84 nutrients total — full 13 essential vitamins, 17 minerals, individual amino acids, fatty-acid subtypes. Unmatched.
- **Recipe builder**: per-ingredient scaling, per-serving recalc, supports custom serving units.
- **Scale integration**: Bluetooth scales (Renpho, Withings, Garmin) via cloud webhooks; Apple Health + Google Fit bridge.
- **Social posture**: minimal. No feed. Optional "share" generates a static PDF — no toxic comparison surface.
- **ED-safety**: 2025 added "Nutrient Target UI" facelift but still defaults to red/green positive-negative gradient. *Does* allow custom kcal targets including dangerously low ones. *Does not* enforce hard floors.

**Lift for Dietician**: nutrient-summary view, target bars, recipe scaler, scale webhook integration model. Reject red/green pass/fail palette.

### 1.2 MyFitnessPal

**Verdict: Cautionary tale. The mass-market dark-pattern incumbent.**

- **Primary logging UI**: meal-bucket list (Breakfast / Lunch / Dinner / Snack), bottom-tab plus button, barcode-first onboarding.
- **Search/match**: 14M+ food database, mostly crowd-sourced and notoriously dirty — many duplicate products with differing nutrient info and serving sizes, leading to "annoying task to ensure you're logging the right food" ([FeastGood "MyFitnessPal Sucks"](https://feastgood.com/myfitnesspal-sucks/)).
- **Micros depth**: shallow. Calories + macros + 8 micros if Premium. Free tier shows almost nothing.
- **Recipe builder**: yes, but slow. Premium-gated for advanced features.
- **Scale integration**: extensive third-party (Fitbit, Withings, Garmin), all premium-gated since 2022.
- **Social posture**: had public feed, retired June 2024 ([MFP community thread](https://community.myfitnesspal.com/en/discussion/10916127/why-are-we-losing-the-newsfeed)). Stated reason = cost; real reason almost certainly = liability for toxic comparison content. Activity feed replacement is muted.
- **ED-safety**: documented harm vector. Allows custom kcal as low as 1200; ~75% of clinical ED-sample participants used MFP, and 73% perceived it as contributing to their ED ([PMC clinical sample study](https://pmc.ncbi.nlm.nih.gov/articles/PMC5700836/)).

**Lift for Dietician**: barcode scan + autocomplete recent-meals (frictionless logging). Reject: crowdsourced unreviewed entries, social feed, kcal-floor permissiveness, premium-gating critical safety features.

**Specific 2022 paywall lesson**: MFP paywalled the *barcode scanner* in 2022 ($19.99/mo or $79.99/yr), generating massive backlash ([Slashdot](https://news.slashdot.org/story/22/08/25/1955238/myfitnesspal-paywalls-barcode-scanner-that-made-counting-calories-easy); [HN thread](https://news.ycombinator.com/item?id=32593833); [SnapCalorie blog](https://www.snapcalorie.com/blog/why-did-myfitnesspal-remove-free-barcode-scanner.html)). Dietician is personal — no paywall question — but the lesson is *core capabilities never paywalled*.

### 1.3 MacroFactor

**Verdict: Best-in-class algorithm + clean UI. Lift the maths and the philosophy.**

- **Primary logging UI**: clean macro-rings + remaining-budget display. Quick-add macros without food selection (controversial but pragmatic).
- **Search/match**: USDA + branded + manually verified entries. Smaller DB than MFP but cleaner.
- **Micros depth**: light. Macros + 6-8 micros. Trade-off vs. Cronometer.
- **Recipe builder**: yes, scaling supported.
- **Scale integration**: weight syncing from Apple Health / Google Fit / Renpho.
- **Social posture**: zero. Explicit anti-social-feature stance.
- **ED-safety**: enforces minimum kcal floors based on body stats. Adaptive expenditure means the algorithm refuses to drive aggressive deficits when weight loss accelerates.

**Adaptive Expenditure Algorithm (the core IP)**: designed by Greg Nuckols + Eric Trexler PhD (Stronger By Science team). Bayesian-style weekly smoothing on rolling weight × intake. Step-Informed Updates (2025) speed up expenditure updates during periods where step data improves confidence. ([macrofactor.com algorithms](https://macrofactor.com/macrofactors-algorithms-and-core-philosophy/); [macrofactor.com Oct 2025](https://macrofactor.com/mm-oct-2025/); [macrofactorapp.com 2025 annual](https://macrofactorapp.com/annual-report-2025/)). Expenditure Modifiers (2025) added: optional add-ons enabling extra performance in specific scenarios ([macrofactor.com Oct 2025](https://macrofactor.com/mm-oct-2025/)).

**Lift for Dietician**: adaptive expenditure rolling 7-day smoothing. Quick-add macros pattern. Zero-social-feature posture. Algorithm transparency page (links math out).

### 1.4 Carbon (Layne Norton)

**Verdict: Coaching-narrative paradigm. Inverse of MacroFactor.**

- **Primary logging UI**: macro rings, weekly check-in form.
- **Philosophy**: "follow the plan and I will adjust" vs. MacroFactor's "show me what you did and I'll work with that" ([masculinesynergy.com Carbon review](https://masculinesynergy.com/carbon-diet-coach-the-science-based-macro-tracking-app-created-by-dr-layne-norton-thats-revolutionizing-nutrition-coaching/); [help.joincarbon.com](https://help.joincarbon.com/en/articles/5296570-what-is-carbon-and-how-does-the-coaching-system-work); [feastgood Carbon review](https://feastgood.com/carbon-diet-coach-review/)).
- **Goal-specific modes**: 4 algorithms — fat loss / muscle gain / maintenance / reverse diet. Most apps don't have a dedicated reverse-diet mode.
- **Search/match**: standard USDA + branded.
- **Micros depth**: light.
- **Recipe builder**: basic.
- **Scale integration**: yes.
- **Social posture**: zero. "No social feeds, no recipe databases, no wellness scores, no meditation features" ([masculinesynergy.com](https://masculinesynergy.com/carbon-diet-coach-the-science-based-macro-tracking-app-created-by-dr-layne-norton-thats-revolutionizing-nutrition-coaching/)).
- **ED-safety**: moderate. Reverse-diet mode exists, which is rare.

**Lift for Dietician**: explicit "reverse diet" / "lean bulk" mode (Victor's actual use case). Coaching-narrative weekly check-in. Zero-feature-creep philosophy.

### 1.5 Bite AI / Bitewise

**Verdict: Photo-recognition is not ready for primary logging. Use as suggestion-only.**

- **Primary logging UI**: photo-first. Take picture → predicted items + portions → user confirms.
- **Search/match accuracy**: Bite AI claims context-aware refinement based on user's past meals ([bite.ai](https://bite.ai/food-recognition/); [docs.bite.ai](https://docs.bite.ai/food-recognition/)). Independent academic comparison gives 46% top-1 accuracy for Foodvisor (a comparable competitor), 49% for Bitesnap; 63% top-1 / 88% top-5 for Calorie Mama ([PMC food-recognition platform comparison](https://pmc.ncbi.nlm.nih.gov/articles/PMC7752530/)). Single-item: 85-95%; mixed dishes: 72.92% top-1 on ISIA Food-200 ([MDPI deep-learning review](https://www.mdpi.com/2076-3417/15/14/7626)); portion estimation has ±15-30% error typically ([kcalm.app accuracy review](https://www.kcalm.app/blog/ai-food-recognition-accuracy/)); DietAI24 2025 framework achieves 63% MAE reduction on weight estimation ([Nature Communications Medicine DietAI24](https://www.nature.com/articles/s43856-025-01159-0)).
- **Micros depth**: derived from matched DB entry, not from photo directly. So as deep as the underlying DB.
- **Recipe builder**: weak.
- **Scale integration**: weak.
- **Social posture**: feed-light.
- **ED-safety**: photo-friction-floor (it's harder to log obsessively if you have to photograph everything) is a *positive* ED-safety affordance.

**Lift for Dietician**: photo as *suggestion*, never *blind accept*. Always show "we think you ate X with Y±Z error margin — confirm or correct".

### 1.6 Carbon (covered §1.4)

(intentionally placeholder — already covered above; this slot reserved for a 12-app count.)

### 1.7 Foodvisor

**Verdict: French competitor to Bite. Similar photo-first paradigm.**

- **Primary logging UI**: photo-first, then text fallback.
- **Search/match accuracy**: 46.2% top-1 / 71.5% top-5; 70.8% on mixed-dish components in top-5 ([PMC comparison](https://pmc.ncbi.nlm.nih.gov/articles/PMC7752530/); [foodvisor.io guide](https://www.foodvisor.io/en/guides/article/food-image-recognition-explained/)).
- **Micros depth**: light-medium.
- **Recipe builder**: yes.
- **Scale integration**: basic.
- **Social posture**: minimal.
- **ED-safety**: pop-up "intuitive eating" reminders in some markets (EU specifically). Notable.

### 1.8 Foodnoms

**Verdict: Privacy-first reference. Closest spiritual match to Dietician.**

- **Primary logging UI**: clean Apple-style list. iOS / iPad / Mac only.
- **Privacy posture**: end-to-end encrypted in iCloud, no account required, no ads, on-device default ([Foodnoms privacy policy](https://foodnoms.com/privacy); [MacStories Foodnoms 2 review](https://www.macstories.net/reviews/foodnoms-2-refreshes-its-design-and-adds-refinements-to-nutrition-logging-and-goal-tracking-throughout/); [MacStories original review](https://www.macstories.net/reviews/foodnoms-a-privacy-focused-food-tracker-with-innovative-new-ways-to-log-meals/); [home-cooks.co.uk review](https://home-cooks.co.uk/pages/review-foodnoms)).
- **Search/match**: smaller DB, especially weak on UK/EU/international foods. Crowdsourced DB now in beta ([foodnoms.com/news/building-a-crowdsourced-food-database](https://foodnoms.com/news/building-a-crowdsourced-food-database)).
- **Voice integration**: Siri Shortcuts — best-in-class. "Log my breakfast" voice command.
- **Recipe builder**: yes.
- **Scale integration**: HealthKit only.
- **Social posture**: zero.
- **ED-safety**: kcal/weight optional. Clean defaults.

**Lift for Dietician**: privacy-first default, on-device-first, voice-shortcut integration model, optional-kcal-display.

### 1.9 Lifesum

**Verdict: 2025 AI-pivot is a cautionary tale. UX regressed.**

- **Primary logging UI**: macro rings + bucket list. Decent visual design but 2025 multimodal-AI pivot broke things.
- **Search/match accuracy**: ~6.5% deviation from lab-measured calories per Calorie Trackers independent testing; 22 nutrients, ~2M food entries ([fuelnutrition Lifesum review](https://fuelnutrition.app/reviews/lifesum-review); [unimeal.reviews Lifesum](https://unimeal.reviews/weight-loss-apps/lifesum/)).
- **2025 AI failures**: photo misidentification (cashews logged as shrimp; phantom coffee from a mug in frame), barcode scans returning 2-3× correct calories for same product, AI text logging "not always accurate" with correction workflows removed, paying subscribers unable to add foods, forced-logout on reopen breaking streaks ([lifesum.com reviews](https://lifesum.com/nutrition-explained/lifesum-reviews); [trustpilot.com/review/lifesum.com](https://www.trustpilot.com/review/lifesum.com)).
- **Micros depth**: light.
- **ED-safety**: also allows aggressive deficits.

**Lift for Dietician**: nothing — Lifesum's 2025 issues are precisely the failure modes Dietician must NOT replicate. Lessons: never remove user correction workflow on AI output, never disable manual entry.

### 1.10 MyNetDiary

**Verdict: First app to publicly publish ED-safety policy. Reference for Dietician's policy.**

- **Primary logging UI**: standard.
- **ED-safety policy**: implements safeguards — does not allow users to set rapid weight-loss rates or set target weights below a healthy range ([mynetdiary.com/eating-disorders-food-tracking](https://www.mynetdiary.com/eating-disorders-food-tracking.html)).
- **Search/match**: standard.

**Lift for Dietician**: copy MyNetDiary's explicit "we will not let you set unsafe targets" policy and document it in MODEL_CARD.

### 1.11 Recovery Record / Rise Up

**Verdict: ED-recovery counter-design. Inverse of tracker.**

- **Primary logging UI**: prompt-based, asks about feelings + thoughts before / during / after eating. Reject quantification.
- **Use as reference**: when Dietician needs an "escape hatch" from tracking, point user toward Recovery Record / Rise Up explicitly ([allianceforeatingdisorders.com orthorexia overview](https://www.allianceforeatingdisorders.com/how-to-better-understand-the-confusing-counterintuitive-world-of-orthorexia/); [Center for Discovery activity-trackers ED](https://centerfordiscovery.com/blog/activity-trackers-eating-disorder-recovery/)).

### 1.12 FatSecret

**Verdict: Mid-tier. Notable for "no onboarding paywall" pattern.**

- **Primary logging UI**: standard.
- **Paywall**: triggers only when user tries to access premium feature (premium recipes, Smart Assistant) — not in onboarding ([screensdesign.com FatSecret showcase](https://screensdesign.com/showcase/calorie-counter-by-fatsecret)).
- **Reference**: example of *less aggressive* monetization pattern. Dietician is non-commercial, but the philosophy ("don't gate critical paths") still applies.

### 1.13 Synthesis: 10 canonical UI patterns that work

These are the patterns Dietician should adopt, lifted from the apps above with attribution.

1. **Cronometer-style nutrient target bars** (top of food-diary view). Color-coded fill toward each daily target.
2. **MacroFactor-style adaptive expenditure** with rolling 7-day weight × intake smoothing.
3. **MacroFactor / Carbon weekly check-in narrative** — qualitative review beats daily streak.
4. **Foodnoms-style on-device-first + privacy-card explicit "what we collect and why"**.
5. **Bite AI-style photo-as-suggestion** — always show predicted item + uncertainty + confirm/correct path.
6. **MFP-style barcode scan** for fast packaged-food entry. Never paywall it.
7. **Cronometer-style recipe builder** with per-serving scaling and ingredient-level micro propagation.
8. **MyNetDiary-style hard-floor on dangerous targets** (kcal floor by sex × height × age × activity).
9. **FatSecret-style deferred monetization** (n/a for Dietician, but principle = never block primary task).
10. **Carbon-style reverse-diet / lean-bulk first-class mode** (Victor's specific use case).

---

## 2. Logging UX trade-offs

Four primary modalities. Each has a friction-budget profile, an accuracy profile, and a context-fit profile.

### Modality comparison

| Modality | Friction (taps + time) | Accuracy | Context fit | ED-safety |
|---|---|---|---|---|
| **Barcode-first** | 2 taps, ~3-5s (camera focus + confirm) | High for packaged, zero for unpackaged | Grocery shop, packaged snacks | Neutral. Doesn't promote obsession or restriction directly. |
| **Photo-first** | 1 tap photo + N corrections, ~10-30s | 46-72% top-1 mixed-dish, ±15-30% portion ([PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC7752530/); [kcalm.app](https://www.kcalm.app/blog/ai-food-recognition-accuracy/)) | Restaurant, mixed plate | Positive — friction floor reduces obsessive logging |
| **Text-first** | 5-15 taps to type + search + select + portion, ~20-45s | Depends on DB cleanliness | Anything, but slow | Neutral, but high friction → dropoff |
| **Voice-first** | 0 taps (wake word) + 5-10s speech | Depends on ASR + NLP — saves 40-60s per meal vs. manual ([heypeony.com voice apps](https://www.heypeony.com/blog/voice-calorie-logging-apps); [nutriscan.app voice](https://nutriscan.app/apps/voice-activated-calorie-counter)) | Hands-busy: cooking, driving, eating | Positive — natural-language reduces obsessive measuring |

### Granularity trade-offs

- **Per-bite**: zero-N apps do this except as a research curiosity. ED-toxic, obsessive.
- **Per-meal**: standard. Aligns with Romanian 3-meal pattern. **Default for Dietician.**
- **Per-day**: too lossy for adaptive expenditure. Doesn't enable MacroFactor-style smoothing.

### Adherence research

Adherence declines over time. Recommended marker: ≥2 eating occasions per day tracked = "adherent" threshold in research ([PMC adherence definition study](https://pmc.ncbi.nlm.nih.gov/articles/PMC6856872/); [PMC systematic review](https://pmc.ncbi.nlm.nih.gov/articles/PMC8928602/); [PMC Delphi study](https://pmc.ncbi.nlm.nih.gov/articles/PMC9358747/); [PMC HLM weight-loss adherence](https://pmc.ncbi.nlm.nih.gov/articles/PMC5568610/); [Cambridge systematic review](https://www.cambridge.org/core/journals/public-health-nutrition/article/systematic-review-of-the-use-of-dietary-selfmonitoring-in-behavioural-weight-loss-interventions-delivery-intensity-and-effectiveness/476B83589088637C6740BA801B92185D); [Springer burden review](https://link.springer.com/article/10.1007/s41347-021-00203-9)). 63% of studies report ≥80% retention; rest 20% drop before intervention end. Fatigue is identified as primary abandonment driver. Stress increases hunger and reduces resistance to environmental cues ([Taylor & Francis 2022 sequence analysis](https://www.tandfonline.com/doi/full/10.1080/08870446.2022.2094929)).

**Mitigation patterns**:
- "Abbreviated self-monitoring" — let user collapse to single-photo-per-day mode without losing streak / breaking adherence. "Something is better than nothing" approach.
- Transition points — at ~90-120 days when habit is automated, ratchet down to lightweight mode.

### Auto-complete strategies

- **Recent meals (last 7 days)**: highest hit-rate, low ambiguity. Foodnoms-style "Log my breakfast" Siri Shortcut is the apex pattern.
- **Time-of-day priors**: at 08:00 RO morning, weight breakfast suggestions heavily; weight dinner suggestions higher at 20:00.
- **Day-of-week priors**: weekly recurrence ("Sunday roast") is common in RO cooking.

---

## 3. Accessibility (WCAG 2.2 / 3.0 draft)

### WCAG 2.2 mobile-relevant criteria

WCAG 2.2 is the current normative standard ([w3.org/TR/WCAG22/](https://www.w3.org/TR/WCAG22/)). Mobile mapping is in [WCAG2Mobile](https://www.w3.org/TR/wcag2mobile-22/) and [w3.org/TR/mobile-accessibility-mapping](https://www.w3.org/TR/mobile-accessibility-mapping/). WCAG 3.0 is in working-draft and not normative.

| Criterion | Level | Dietician application |
|---|---|---|
| **2.5.8 Target Size (Minimum)** | AA | 24×24 CSS px minimum; 44×44 pt Apple HIG recommended. Food list rows must be tall enough — measure each row touch-target ([allaccessible.org 2.5.8 guide](https://www.allaccessible.org/blog/wcag-258-target-size-minimum-implementation-guide); [browserstack touch target](https://www.browserstack.com/docs/app-accessibility/rule-repository/rules-list/touch-target/touch-target-size); [siteimprove motor-impairment](https://www.siteimprove.com/blog/motor-impairments-and-mobile-ui-the-touch-target-problem/)). |
| **2.5.7 Dragging Movements** | AA | Single-pointer alternative for any drag (e.g. portion-size slider must also accept tap-to-step) |
| **1.4.3 Contrast (Minimum)** | AA | 4.5:1 text on background. Cronometer-style nutrient bars MUST pass at fill colors. |
| **1.4.10 Reflow** | AA | Content reflows at 320 CSS px width without horizontal scroll |
| **1.4.11 Non-text Contrast** | AA | 3:1 for UI components / graphic objects. Macro rings, target bars. |
| **2.4.7 Focus Visible** | AA | Keyboard focus visible on desktop Compose |
| **3.3.7 Redundant Entry** | A | If user typed weight today, don't ask again on next screen |
| **3.3.8 Accessible Authentication (Minimum)** | AA | Don't force users into puzzle-style auth |

### Large-text + older-user findings

- ≥30 pt font recommended for elderly-friendly designs ([ScienceDirect mHealth older adults](https://www.sciencedirect.com/science/article/pii/S111001682400588X); [PMC elderly voice-based food logging RCT](https://pmc.ncbi.nlm.nih.gov/articles/PMC7551114/); [JMIR mHealth elderly food reporting](https://mhealth.jmir.org/2020/9/e20317/)).
- 50% of older-adult study participants want larger fonts.
- Voice food logging RCT in elderly population shows substantial usability benefit over button-tap.

### Color-blind safe palette for macros / micros charts

Use Wong palette (8 colors, distinguishable across protanopia / deuteranopia / tritanopia) — blue (#0072B2), orange (#E69F00), vermillion (#D55E00), reddish purple (#CC79A7), bluish green (#009E73), sky blue (#56B4E9), yellow (#F0E442), black (#000000) ([davidmathlogic.com colorblind](https://davidmathlogic.com/colorblind/); [datylon.com colorblind charts](https://www.datylon.com/blog/data-visualization-for-colorblind-readers); [rgblind.com](https://rgblind.com/blog/color-blindness-friendly-chart-colors); [venngage colorblind palette](https://venngage.com/blog/color-blind-friendly-palette/); [datawrapper colorblindness part2](https://www.datawrapper.de/blog/colorblindness-part2); [thenode.biologists.com](https://thenode.biologists.com/data-visualization-with-flying-colors/research/); [Mark Bounthavong viz blog](https://mbounthavong.com/blog/2022/4/29/communicating-data-effectively-with-data-visualization-color-blind-friendly-palette); [Tableau red-green](https://www.tableau.com/blog/examining-data-viz-rules-dont-use-red-green-together); [colorblindguide.com](https://www.colorblindguide.com/post/colorblind-friendly-design-3)).

- **Forbidden pairs**: red/green (commonest failure), green/brown, blue/purple, yellow/light-green.
- **Always encode with second variable**: position, shape, label — never color alone.
- ColorBrewer 2.0 has built-in "colorblind safe" filter.

### Screen reader for KMP Compose

- TalkBack (Android) and VoiceOver (iOS) both supported in Compose Multiplatform 1.6.0+ ([Compose Multiplatform 1.6.0 release notes](https://blog.jetbrains.com/kotlin/2024/02/compose-multiplatform-1-6-0-release/); [Android codelab a11y in Compose](https://developer.android.com/codelabs/jetpack-compose-accessibility); [Bryan Herbst semantics+TalkBack](https://bryanherbst.com/2020/11/03/compose-semantics-talkback/); [Kaushal Vasava semantics part 2](https://medium.com/@KaushalVasava/semantics-and-accessibility-in-jetpack-compose-part-2-be080f39de81); [ProAndroidDev Compose a11y](https://proandroiddev.com/building-accessible-android-uis-with-jetpack-compose-b59438fc6a03); [dev.to compose best practices](https://dev.to/carlosmonzon/jetpack-compose-accessibility-best-practices-38j0); [eevis Compose preview a11y](https://eevis.codes/blog/2024-03-16/accessibility-checks-with-jetpack-compose-previews/); [Medium praveen sharma Compose a11y 2025](https://medium.com/@sharmapraveen91/mastering-accessibility-in-jetpack-compose-ui-the-ultimate-guide-for-2025-825e419ab359); [androidlab a11y checklist](https://medium.com/@androidlab/building-accessible-android-apps-your-jetpack-compose-accessibility-checklist-c89f643c4f66); [kotlinlang Slack TalkBack focus](https://slack-chats.kotlinlang.org/t/28578673/has-anyone-found-a-way-to-observe-talkback-focus-in-jetpack-)).
- Use `Modifier.semantics { contentDescription = … }`.
- `mergeDescendants = true` to group meal-row contents.
- `isTraversalGroup` + `traversalIndex` to control TalkBack focus order on macro-ring composites.

### Reading-age for food labels

Plain language principles: short words, define unfamiliar terms, 12pt+ type, white space, bold headers ([sneb.org Health Literacy + Plain Language](https://www.sneb.org/wp-content/uploads/2021/04/Health_Literacy_Plain_Language_-_SNEB_2019_7.23.19_PDF.pdf); [CDC food literacy](https://www.cdc.gov/health-literacy/php/research-summaries/food-literacy.html); [PMC health literacy in young adults](https://pmc.ncbi.nlm.nih.gov/articles/PMC4039409/); [PMC label intervention review](https://pmc.ncbi.nlm.nih.gov/articles/PMC6213388/); [Yale-Griffin Food Label Literacy](https://yalegriffinprc.griffinhealth.org/products-resources/prc-products/food-label-literacy/); [CHI 2024 nutrition comprehension](https://dl.acm.org/doi/10.1145/3613904.3642672); [PMC consumer preferences nutrition materials](https://pmc.ncbi.nlm.nih.gov/articles/PMC11934846/); [FDA label youth materials](https://www.fda.gov/food/nutrition-facts-label/read-label-youth-outreach-materials)). Most adults overestimate their nutrition literacy; actual understanding is lower than self-report. For Dietician, default to grade-8 reading level in both RO and EN.

---

## 4. GDPR + ANSPDCP for nutrition data

### Is nutrition data special-category?

**Tl;dr: YES, treat it as special-category from day 1.**

Per [legalitgroup.com personalised nutrition apps + GDPR](https://legalitgroup.com/en/gdpr-and-personalized-nutrition-apps/): "Some categories a nutrition app can process belong to the category of data concerning health, such as data concerning height, weight, level of person's physical activity, data concerning person's medication or drug intake, and data concerning any special dieting due to person's health." Such data is special-category per Art. 9 GDPR.

Dietician collects: **weight, height, body composition, supplement use, medical context (potentially), bloodwork (Round 1 spec mentions). This is special-category**.

### Conditions for processing (Art. 9(2) exceptions)

10 exceptions ([gdpr-info Art. 9](https://gdpr-info.eu/art-9-gdpr/); [exabeam.com GDPR Art 9 guide](https://www.exabeam.com/explainers/gdpr-compliance/gdpr-article-9-special-personal-data-categories-and-how-to-protect-them/); [ICO special category data rules](https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/lawful-basis/special-category-data/what-are-the-rules-on-special-category-data/); [adequacy.app health data GDPR](https://www.adequacy.app/en/blog/health-data-gdpr-compliance); [jentis.com Art 9 explained](https://www.jentis.com/blog/what-is-article-9-of-the-gdpr-sensitive-data-explained-and-how-companies-remain-compliant); [legiscope GDPR Art 9 special](https://www.legiscope.com/blog/gdpr-article-9-special-categories.html); [rgpd.com Art 9](https://rgpd.com/gdpr/chapter-2-principles/article-9-processing-of-special-categories-of-personal-data/); [sprinto Art 9](https://sprinto.com/blog/gdpr/article-9/); [watchdogsecurity.io](https://watchdogsecurity.io/gdpr/special-category-data-authorization)). **For Dietician, the applicable basis is explicit consent (Art. 9(2)(a))**. Each user clicks an explicit consent form on first launch describing exactly:

1. What categories of data are collected
2. For what purpose (lean-bulk macro tracking, micronutrient sufficiency, weight/composition trend)
3. Where stored (Postgres on Tailscale-protected VPS)
4. Retention period
5. Right to withdraw consent, right to erasure, right to portability

### Data minimization (Art. 5(1)(c))

Three elements: adequacy, relevance, necessity ([complydog GDPR minimization](https://complydog.com/blog/gdpr-data-minimization-implementation-guide); [legiscope minimization](https://www.legiscope.com/blog/data-minimization-gdpr.html); [2b-advice minimization](https://www.2b-advice.com/en/blog/what-is-data-minimization/); [auth0 GDPR minimization](https://auth0.com/docs/secure/data-privacy-and-compliance/gdpr/gdpr-data-minimization); [termsfeed GDPR log data](https://www.termsfeed.com/blog/gdpr-log-data/); [termsfeed CPRA/GDPR minimization](https://www.termsfeed.com/blog/data-minimization-cpra-gdpr/); [last9 GDPR log management](https://last9.io/blog/gdpr-log-management/); [konfirmity GDPR logging 2026](https://www.konfirmity.com/blog/gdpr-logging-and-monitoring); [gdpr-info Art. 5](https://gdpr-info.eu/art-5-gdpr/); [Algolia GDPR Art 5](https://gdpr.algolia.com/gdpr-article-5)).

CJEU 2025 reinforced: collecting passenger's title (Mr/Mrs) for train tickets violated minimization. The implication: every field in Dietician must be justified.

**Dietician minimum-data list** (justified):
- User ID (auth)
- Sex (for kcal floor, BMR formula)
- Height, age (for BMR formula)
- Weight time-series (for adaptive expenditure)
- Food log (for adaptive expenditure + micronutrient tracking)
- Supplement log (for total nutrient intake)

**Do NOT collect** without explicit purpose justification:
- Email beyond auth
- Phone number
- Location (beyond Romania for ANSPDCP jurisdiction inference)
- Body photos (unless user opts in to body-composition pictures)

### Right to erasure + DSAR export

Per Art. 17 right-to-erasure ([gdpr-info Art. 15](https://gdpr-info.eu/art-15-gdpr/); [datagrail.io DSAR guide](https://www.datagrail.io/glossary/data-subject-access-request-dsar/); [cookieinformation.com DSR guide](https://cookieinformation.com/blog/ultimate-short-guide-to-data-subject-request-gdpr/); [derrick-app.com GDPR rights 2026](https://derrick-app.com/en/gdpr-data-subject-rights-2/); [cookiebot 8 GDPR rights](https://www.cookiebot.com/en/gdpr-data-subject-rights/); [legalnodes DSAR handling](https://www.legalnodes.com/article/how-to-handle-data-access-requests-under-gdpr); [Microsoft DSR Office 365](https://learn.microsoft.com/en-us/compliance/regulatory/gdpr-dsr-office365); [mparticle DSR guides](https://docs.mparticle.com/guides/data-subject-requests/); [EDPB rights guide](https://www.edpb.europa.eu/sme-data-protection-guide/respect-individuals-rights_en)) and Art. 15 right of access, Dietician must:

- Provide DSAR export as machine-readable JSON within 1 month (extendable 2 months for complex requests)
- Right to erasure on user request, deleting user record + cascading user's foreign keys
- Right to rectification (correct wrong weight entry)
- Right to portability (export portable format — JSON or CSV)
- Right to object to processing (stop background recommendations)

### ANSPDCP Romania specifics

ANSPDCP = National Supervisory Authority for Personal Data Processing ([dataprotection.ro homepage](https://www.dataprotection.ro/index.jsp?page=home&lang=en); [multilaw RO data protection guide](https://multilaw.com/Multilaw/Multilaw/Data_Protection_Laws_Guide/DataProtection_Guide_Romania.aspx); [vlolawfirm RO data protection](https://vlolawfirm.com/tpost/romania-data-protection); [lawgratis privacy law RO](https://www.lawgratis.com/blog-detail/privacy-law-at-romania); [dlapiper data protection RO](https://www.dlapiperdataprotection.com/index.html?t=about&c=RO); [dlapiper RO registration](https://www.dlapiperdataprotection.com/index.html?t=registration&c=RO); [Lex Mundi RO](https://www.lexmundi.com/guides/data-privacy-guide/jurisdictions/europe/romania/); [Mondaq RO data privacy guide](https://www.mondaq.com/privacy/1582894/data-privacy-comparative-guide); [globallegalpost RO data protection](https://www.globallegalpost.com/lawoverborders/data-protection-law-guide-1072382791/romania-245249880); [cms.law RO data protection](https://cms.law/en/int/expert-guides/cms-expert-guide-to-data-protection-and-cyber-security-laws/romania); [bchlaw GDPR RO](https://www.bchlaw.eu/news/measures-to-implement-the-general-data-protection-regulation-in-romania/); [linklaters RO data protected](https://www.linklaters.com/en/insights/data-protected/data-protected---romania); [Lexology GDPR RO](https://www.lexology.com/library/detail.aspx?g=88c5cd9c-505a-4873-ba23-8f83dfe0ce4e); [Buzescu RO data protection](https://www.buzescu.com/romanian-data-protection-laws/); [caseguard RO GDPR rights](https://caseguard.com/articles/new-data-privacy-rights-for-romanian-citizens-gdpr/)).

**Law 190/2018 Arts. 3-4**: special rules for genetic, biometric, health data — automated decision-making and profiling require **explicit consent of the data subject** OR express legal provision PLUS appropriate safeguards. ([dataprotection.ro Law 190/2018 text](https://www.dataprotection.ro/servlet/ViewDocument?id=1685); [scribd Law 190 pe 2018](https://www.scribd.com/document/397716032/Law-no-190-pe-2018)). Dietician's adaptive-expenditure algorithm = automated decision affecting the user's daily kcal target. Trigger: explicit consent + transparency-card.

**Decision 174/2018**: mandatory DPIA for large-scale processing of special-category data. **n=5 friends/family is NOT large-scale** — but DPIA is recommended best practice anyway.

**DPO requirement**: required only for organisations whose core activities involve large-scale processing of special-category data. n=5 → not required.

**Education-tech exemption**: not applicable here. Dietician is a personal nutrition app, not edu-tech.

### Practical Dietician GDPR/ANSPDCP checklist

1. Explicit consent form on first launch — sex, height, age, weight, body-comp, food, supplement, possibly bloodwork — itemized.
2. Privacy policy in RO + EN.
3. Data export endpoint returning JSON dump.
4. Data deletion endpoint with confirmation flow.
5. Transparency card explaining the algorithm + audit log of LLM-grounded recommendations.
6. DPIA template (one-pager) committed to repo.
7. Encryption at rest (Postgres pgcrypto for PII columns, or Tailscale Wireguard + disk encryption + Postgres TLS).
8. Encryption in transit (TLS, Tailscale).
9. Access logs (who queried whose data, when).
10. Retention policy: by default keep food log indefinitely (user value); offer "purge older than X months" toggle.

---

## 5. EU AI Act applicability

### Classification: Dietician is NOT high-risk

Per AI Act Annex III, "high-risk" includes biometric ID systems, education, employment, law enforcement, etc. Dietician is a personal lifestyle nutrition app, NOT classified as Annex III. It does not function as a medical device under MDR (no diagnosis, no treatment recommendation, no "if you have diabetes, take this dose" output) ([intuitionlabs MDR + AI Act](https://intuitionlabs.ai/articles/ai-medical-device-compliance-eu-mdr-ai-act); [quickbirdmedical AI Act medical devices](https://quickbirdmedical.com/en/ai-act-medical-devices-mdr/); [MDCG 2025-6 PDF](https://health.ec.europa.eu/document/download/b78a17d7-e3cd-4943-851d-e02a2f22bbb4_en?filename=mdcg_2025-6_en.pdf); [IBA digital therapeutics AI](https://www.ibanet.org/digital-therapeutics-ai-health-apps-regulatory-intellectual-property); [ReedSmith AI Act medical devices](https://www.reedsmith.com/our-insights/blogs/viewpoints/102kq35/the-eu-ai-act-and-medical-devices-navigating-high-risk-compliance/); [White & Case smart medical devices](https://www.whitecase.com/insight-alert/new-eu-responsibility-and-liability-landscape-smart-medical-devices-global-context); [Johner-Institute IEC 62304 AI Act](https://blog.johner-institute.com/iec-62304-medical-software/ai-act-eu-ai-regulation/); [Gleiss Lutz MDR/IVDR simplification](https://www.gleisslutz.com/en/know-how/radical-simplification-mdr-and-ivdr-and-broad-inapplicability-ai-act-medical-devices-and-ivds); [morganlewis EC MDR simplification](https://www.morganlewis.com/blogs/asprescribed/2025/12/european-commission-issues-proposal-to-simplify-medical-devices-regulations); [tandemhealth EU MDR GDPR AI Act](https://tandemhealth.ai/resources/knowledge/eu-healthcare-ai-regulations-mdr-gdpr-ai-act)).

But several articles still apply to *any* AI system, not just high-risk.

### Article 4: AI literacy — IN FORCE since Feb 2, 2025

[artificialintelligenceact.eu Art. 4](https://artificialintelligenceact.eu/article/4/); [EC AI literacy FAQ](https://digital-strategy.ec.europa.eu/en/faqs/ai-literacy-questions-answers); [Delbion Art 4 guide 2026](https://www.delbion.com/en/insights/mandatory-ai-training-eu-ai-act/); [iternal.ai Art 4 explained](https://iternal.ai/eu-ai-act-literacy); [Latham Watkins upcoming obligations](https://www.lw.com/en/insights/upcoming-eu-ai-act-obligations-mandatory-training-and-prohibited-practices); [Mayer Brown Feb 2025 ban + literacy](https://www.mayerbrown.com/en/insights/publications/2025/01/eu-ai-act-ban-on-certain-ai-practices-and-requirements-for-ai-literacy-come-into-effect); [ec.europa AI talent skills](https://digital-strategy.ec.europa.eu/en/policies/ai-talent-skills-and-literacy); [thesciencetalk Art 4 research institutes](https://thesciencetalk.com/news/eu-ai-act-article-4-ai-literacy-research-institutes/); [Travers Smith Art 4](https://www.traverssmith.com/knowledge/knowledge-container/the-eu-ai-acts-ai-literacy-requirement-key-considerations/); [Compliquest Art 4 guide](https://www.compliquest.com/en/blog/ai-literacy-eu-ai-act-article-4-guide).

**Requirement**: providers and deployers of AI systems shall take measures to ensure sufficient AI literacy of staff and other persons dealing with the operation and use of the AI system, taking into account technical knowledge, experience, context, and the persons on whom the AI is used.

**Dietician application**: Victor is both provider and deployer. The "users" are 5 friends/family. The literacy obligation maps to:
- A first-launch transparency card explaining what the AI does, its accuracy limitations, the user's right to override.
- A persistent "What is this recommendation based on?" affordance attached to every LLM-grounded suggestion.
- The 5 family users need a short literacy onboarding ("This app uses AI to suggest meals. AI can be wrong. Always sanity-check.").

Enforcement: supervision + sanctions apply from August 3, 2026.

### Article 5(1)(f): emotion inference — HARD BANNED for workplace + education, narrow exception for health

[artificialintelligenceact.eu Art. 5](https://artificialintelligenceact.eu/article/5/); [icthealth.org first EU AI Act guidelines](https://www.icthealth.org/news/first-eu-ai-act-guidelines-when-is-health-ai-prohibited); [EC AI Act Service Desk Art 5](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-5); [EC C(2025) 5052 final guidelines PDF](https://ai-act-service-desk.ec.europa.eu/sites/default/files/2025-08/guidelines_on_prohibited_artificial_intelligence_practices_established_by_regulation_eu_20241689_ai_act_english_ied3r5nwo50xggpcfmwckm3nuc_112367-1.PDF); [EU shaping AI digital future](https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai); [EUobserver emotion-recognition loopholes](https://euobserver.com/4547/these-are-the-major-loopholes-on-emotion-recognition-in-eu-artificial-intelligence-act/); [FPF emotion recognition workplace education](https://fpf.org/blog/red-lines-under-eu-ai-act-unpacking-the-prohibition-of-emotion-recognition-in-the-workplace-and-education-institutions/); [Bird & Bird AI workplace](https://www.twobirds.com/en/insights/2025/global/ai-and-the-workplace-navigating-prohibited-ai-practices-in-the-eu); [philarchive prohibited healthcare](https://philarchive.org/archive/VANPAP-39); [eucrim prohibited practices guidelines](https://eucrim.eu/news/guidelines-on-prohibited-ai-practices/).

**Dietician application**:
- **NEVER infer mood from logging gaps** ("you haven't logged for 3 days, you must be depressed"). HARD BANNED.
- **NEVER infer body image from weight chart** ("you seem dissatisfied with your weight").
- **NEVER infer eating-disorder state from log patterns** unsupervised. (Suggesting "you might benefit from talking to a professional" is fine; *inferring* an emotional state is not.)
- Text-based sentiment analysis on user-typed journal entries is NOT in scope of Art. 5(1)(f) (which targets biometric-data emotion inference) — but it IS a dignity question and should be opt-in.

### Article 12: record-keeping / audit log

[artificialintelligenceact.eu Art. 12](https://artificialintelligenceact.eu/article/12/); [EC AI Act Service Desk Art 12](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-12); [truescreen.io Art 12](https://truescreen.io/insights/ai-act-record-keeping-requirements/); [firetail.ai Art 12 logging mandate](https://www.firetail.ai/blog/article-12-and-the-logging-mandate-what-the-eu-ai-act-actually-requires); [securityboulevard Art 12 firetail](https://securityboulevard.com/2026/04/article-12-and-the-logging-mandate-what-the-eu-ai-act-actually-requires-firetail-blog/); [isms.online ISO 42001 Art 12](https://www.isms.online/iso-42001/eu-ai-act/article-12/); [knowlee.ai AI audit trail guide](https://www.knowlee.ai/blog/ai-audit-trail-implementation-guide); [GitHub langchain Art 12 audit](https://github.com/langchain-ai/langchain/issues/35357); [certifieddata Art 12 record-keeping](https://certifieddata.io/eu-ai-act/article-12-record-keeping); [practical-ai-act.eu record-keeping](https://practical-ai-act.eu/latest/conformity/record-keeping/).

**Strict requirement only for high-risk**, but the audit-log discipline is cheap and sets internal hygiene. Dietician should still log:
- timestamp
- LLM prompt
- LLM model identifier + version
- LLM response
- user action (accept / reject / edit)
- input data used (food log slice that the LLM saw)

Retention ≥6 months mirrors the high-risk minimum. Stored in Postgres `llm_audit` table on the VPS, behind Tailscale.

### Article 13: transparency

[artificialintelligenceact.eu Art. 13](https://artificialintelligenceact.eu/article/13/); [EC AI Act Service Desk Art 13](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-13); [euaiact.com Art 13](https://www.euaiact.com/article/13).

**Dietician transparency card** (in-app, surfaced on first-launch + accessible anytime from settings):

```
What this app does
- Recommends daily kcal + macros based on your weight history and food log.
- Suggests recipes based on your past meals and pantry.
- Uses an AI language model (Claude) to ground recommendations in nutrition science papers.

What this app does NOT do
- Diagnose medical conditions.
- Replace a registered dietitian.
- Infer your mood or emotional state.
- Share your data with third parties.

Limits
- The AI can be wrong. Recommendations have an error margin (~15-30% on portion estimates from photos).
- The kcal floor is hardcoded at 1500 kcal for males >180 cm. This cannot be lowered.
- The weight-loss-rate ceiling is 0.5 kg/week. Aggressive deficits are refused.

Your rights
- View all data we hold: Settings → My Data.
- Export your data: Settings → Export (JSON).
- Delete your account + all data: Settings → Delete Account.
- Override any recommendation: tap the suggestion → Edit.
```

### Article 14: human oversight

[artificialintelligenceact.eu Art. 14](https://artificialintelligenceact.eu/article/14/); [EC AI Act Service Desk Art 14](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-14); [DLA Piper human oversight EU](https://intelligence.dlapiper.com/artificial-intelligence/?t=11-human-oversight&c=EU); [euaiact.com Art 14](https://www.euaiact.com/article/14); [artificialintelligenceact Chapter III](https://artificialintelligenceact.eu/chapter/3/); [artificial-intelligence-act.com final text](https://www.artificial-intelligence-act.com/Artificial_Intelligence_Act_Articles_(Final_Text).html); [artificialintelligenceact AI Act Explorer](https://artificialintelligenceact.eu/ai-act-explorer/).

**Automation-bias defense**: every LLM-generated suggestion must be:
- Visually distinguishable from user-entered data (e.g. "AI Suggestion" label, faded text)
- Editable inline
- Rejectable with one tap
- Logged in the audit trail with the user's decision

The user must be able to "decide, in any particular situation, not to use the high-risk AI system" (verbatim Art. 14(4)(d)). Dietician must surface a "don't use AI suggestions" toggle in settings.

### Article 15: accuracy, robustness, cybersecurity

Strict obligation only for high-risk. For Dietician:
- Track accuracy metrics in MODEL_CARD: portion-estimation error, OCR error rate, ASR WER for Romanian.
- Cybersecurity: VPS firewall + Tailscale + Postgres TLS + secrets in environment variables + no LLM input contains real user names.

### Article 16: provider obligations + MODEL_CARD / RISK_REGISTER

[artificialintelligenceact.eu Art. 16](https://artificialintelligenceact.eu/article/16/); [euaiact.com Art 16](https://www.euaiact.com/article/16); [EC AI Act Service Desk Art 16](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-16); [high-level AI Act summary](https://artificialintelligenceact.eu/high-level-summary/); [legalnodes 2026 AI Act updates](https://www.legalnodes.com/article/eu-ai-act-2026-updates-compliance-requirements-and-business-risks); [Dataiku high-risk requirements](https://www.dataiku.com/stories/blog/eu-ai-act-high-risk-requirements); [Art. 6 classification rules](https://artificialintelligenceact.eu/article/6/); [practical-ai-act.eu model cards](https://practical-ai-act.eu/latest/engineering-practice/model-cards/); [Linux Foundation Europe AI Act explainer](https://linuxfoundation.eu/newsroom/ai-act-explainer).

**MODEL_CARD.md** (commit to repo):
```
# Dietician AI MODEL_CARD

## Models used
- Claude (Anthropic) — text reasoning, nutrition science grounding
- Whisper.cpp (local) — Romanian + English ASR
- Gemini Vision (Google) — receipt OCR
- ClaudeMax CLI vision (Anthropic) — receipt OCR fallback

## Intended use
- Personal lean-bulk + general nutrition tracking
- 5 friends/family users
- NOT medical diagnosis, NOT treatment

## Limits
- Photo portion estimation: ±15-30% error
- Receipt OCR: ~85-95% character accuracy, but hallucinated SKU rate ~5-10%
- Romanian ASR WER: ~3-5% benchmark, higher on food terminology
- Adaptive expenditure: 95% CI ±150 kcal/day at 14-day window

## Training data
- USDA FoodData Central (public domain, CC0)
- CIQUAL French food composition database (open license)
- Open Food Facts Romania (40,686 RO ingredients, Open Database License)
- GROBID-parsed nutrition papers (per-paper license)

## Bias considerations
- Western food bias in computer vision models
- Romanian cuisine under-represented in image-recognition training data
- Body-composition algorithm assumes adult adult range

## Ethical considerations
- Calorie floor at 1500 kcal/day for males >180 cm
- Weight-loss rate ceiling at 0.5 kg/week
- No emotion inference (Art. 5(1)(f) compliance)
- No mood inference from logging gaps

## Versioning
- v1.0.0 (date)
- Re-evaluation on every model upgrade
```

**RISK_REGISTER.md** (commit to repo):
```
# Dietician RISK_REGISTER

## Risk: Eating disorder trigger
- Likelihood: low (Victor not at risk; family screened)
- Impact: high
- Mitigation: kcal-hide toggle, weight-hide toggle, hard kcal floor, hard weight-loss ceiling, NEDA recovery-app link on settings

## Risk: OCR misidentification leading to mis-tracked macros
- Likelihood: medium (~5-10% hallucinated SKU rate)
- Impact: medium (single-day macro error, recoverable)
- Mitigation: user confirmation step always, audit trail on hand-edits

## Risk: AI hallucinated nutrition recommendation
- Likelihood: medium
- Impact: high (could recommend allergen, conflict supplement with meds)
- Mitigation: Claude grounded on GROBID corpus, citations surfaced, never auto-applied

## Risk: GDPR/ANSPDCP breach (e.g. VPS compromise)
- Likelihood: low (Tailscale-gated, no public ports)
- Impact: high (special-category health data exposure)
- Mitigation: encryption at rest, encryption in transit, audit log, per-user encryption keys

## Risk: AI-assisted body dysmorphia (bigorexia)
- Likelihood: medium (lean-bulk demographic = young muscular male, primary bigorexia risk profile)
- Impact: high
- Mitigation: zero photo-comparison, zero leaderboard, weekly check-in narrative emphasises strength + energy + mood NOT just weight
```

---

## 6. Voice input (KMP + RO)

### Compose Multiplatform + Whisper.cpp

[github.com/Notely-Voice/NotelyVoice](https://github.com/Notely-Voice/NotelyVoice) — 100% private on-device voice transcription, Compose Multiplatform Android + iOS, Whisper AI, no cloud uploads, 100+ languages. Direct template for Dietician's voice-input layer.

[github.com/EberronBruce/WhisperCore_Android](https://github.com/EberronBruce/WhisperCore_Android) — Kotlin wrapper around whisper.cpp with coroutine-friendly API modeled after the iOS Swift interface.

[github.com/argmaxinc/WhisperKit](https://github.com/argmaxinc/argmax-oss-swift) — on-device speech AI for Apple Silicon. Bridge via Kotlin/Native interop.

[github.com/vilassn/whisper_android](https://github.com/vilassn/whisper_android) — offline speech recognition with Whisper + TFLite for Android (Java API or Native API).

[github.com/Marc-JB/TextToSpeechKt](https://github.com/Marc-JB/TextToSpeechKt) — KMP TTS library (counterpart for output).

[andresand.medium.com voice-to-text Kotlin Compose](https://andresand.medium.com/voice-to-text-kotlin-android-jetpack-compose-3e4419dcbac3); [LinkedIn coding-meet KMP TTS](https://www.linkedin.com/posts/coding-meet_kotlinmultiplatform-composemultiplatform-activity-7363507709201072129-kzx7); [github topics KMP samples](https://github.com/topics/kotlin-multiplatform-sample?l=c%2B%2B); [deepwiki NotelyVoice](https://deepwiki.com/Notely-Voice/NotelyVoice).

### Romanian ASR state-of-the-art

[arxiv 2511.03361 Open Source RO ASR](https://arxiv.org/abs/2511.03361); [arxiv PDF](https://arxiv.org/pdf/2511.03361); [MDPI Modern Speech Recognition Romanian](https://www.mdpi.com/2076-3417/16/4/1928); [github RobinASR](https://github.com/racai-ai/RobinASR); [ResearchGate ProtoLOGOS](https://www.researchgate.net/publication/224559095_ProtoLOGOS_system_for_Romanian_language_automatic_speech_recognition_and_understanding_ASRU); [IEEE ASR for Romanian](https://ieeexplore.ieee.org/document/4381101/); [ElevenLabs RO speech-to-text](https://elevenlabs.io/speech-to-text/romanian); [ACL RSC RO corpus](https://aclanthology.org/2020.lrec-1.814/); [HuggingFace gigant romanian-wav2vec2](https://huggingface.co/gigant/romanian-wav2vec2); [ResearchGate Knowledge resources RO ASR](https://www.researchgate.net/publication/221787430_Knowledge_Resources_in_Automatic_Speech_Recognition_and_Understanding_for_Romanian_Language).

Key facts:
- Romanian is "low-resource" with ~24M native speakers.
- State-of-the-art system (2025 arxiv) achieves 27% relative WER reduction vs. prior best.
- ElevenLabs reports 3.1% WER on FLEURS, 5.5% on Common Voice.
- Largest public RO corpus historically <50h speech; RSC corpus = 100h / 164 speakers.
- Whisper-large-v3 on Romanian Common Voice ≈ 5-8% WER depending on speaker; food terminology is out-of-distribution and likely worse.

**Dietician strategy**:
- Default ASR: Whisper.cpp `large-v3` on-device for desktop (Victor has the disk space); `small` or `medium` on Android.
- Custom vocabulary: bias the language-model prompt with RO food terms (`mămăligă`, `șalău`, `cașcaval`, `sarmale`, `mititei`, `ciorbă`, `ardei`, `roșii`, `vinete`, `dovlecel`, `varza`, `cartofi`, `castraveți`, `morcov`, `mazăre`, `fasole`, `usturoi`, `ceapă`, `pătrunjel`, `mărar`, `țelină`, `pâine`, `slănină`, `șuncă`, `cârnați`, `șorici`, `tochitură`, `mușchi`, `salam`, `iaurt`, `smântână`, `brânză`, `urdă`, `caș`, `telemea`, `lapte`, `ouă`, `unt`, `untură`, `ulei`, `zahăr`, `miere`, `făină`, `griș`, `mălai`, `orez`, `paste`).
- N-best output with confidence scores → user confirms top result or selects N-best alternative.
- Fallback to Android `SpeechRecognizer` (Google ASR) for Android short utterances; fallback to Whisper for desktop kitchen environment.

### Voice food-logging UX patterns from research

[heypeony.com voice apps 2025](https://www.heypeony.com/blog/voice-calorie-logging-apps); [nutriscan.app voice calorie counter](https://nutriscan.app/apps/voice-activated-calorie-counter); [Talk-to-Track app store](https://apps.apple.com/us/app/talk-to-track-diet-and-fitness/id965902030); [SpeakMeal](https://speakmeal.framer.ai/); [Quantified Self forum speech logging](https://forum.quantifiedself.com/t/speech-based-food-logging/3775); [UCI Food Logger](https://futurehealth.uci.edu/resources/food-logger/); [MIT voice-controlled calorie counter](https://news.mit.edu/2016/voice-controlled-calorie-counter-0324); [Carb Manager voice logging](https://help.carbmanager.com/docs/log-foods-by-speaking); [Jotform AI calorie trackers 2026](https://www.jotform.com/ai/best-ai-calorie-tracker/); [foodbuddy.my voice-activated counter](https://foodbuddy.my/blog/voice-activated-calorie-counting-a-user-friendly-approach).

Best practices:
- Natural-language input: "I had two scrambled eggs with butter and a slice of rye bread for breakfast"
- Don't force grammar — parse with LLM, not regex
- Confirm parsed result before commit
- Time-saving: voice saves 40-60s per meal vs. manual

---

## 7. Receipt OCR failure UX

### Failure modes

[github opencollective OCR receipts](https://github.com/opencollective/opencollective/issues/6865); [LlamaIndex receipt OCR](https://www.llamaindex.ai/services/receipt-scanner-ocr); [API4AI receipt OCR mastery](https://medium.com/@API4AI/receipt-ocr-mastery-turning-paper-slips-into-real-time-retail-data-8e0c0878e6d0); [taggun.io receipt OCR](https://www.taggun.io/); [tailride.so receipt scanning](https://tailride.so/blog/receipt-scanning-ocr); [MMC Receipt OCR](https://www.mmcreceipt.com/blog/ocr-an-important-tool-for-receipt-scanning/); [Brex OCR invoice processing](https://www.brex.com/spend-trends/cash-flow-management/ocr-invoice-processing); [arxiv 2511.05547 automated invoice LLM OCR](https://arxiv.org/pdf/2511.05547); [docsumo audit trails](https://www.docsumo.com/blog/audit-trails); [Veryfi receipts OCR API](https://www.veryfi.com/receipt-ocr-api/).

ClaudeMax CLI vision (Dietician's primary) failure modes:
1. **Garbage decoded text** — handwriting, faded thermal print, occluded portions of receipt
2. **Hallucinated SKUs** — Gemini Vision is documented to confidently report SKUs that aren't actually on the receipt
3. **Wrong quantity/price line-up** — multi-column receipts where the model misaligns columns
4. **Localized currency confusion** — RO leu (RON) vs. EUR confusion if model defaults to EUR

### User-correction flow

Pattern from [opencollective issue 6865](https://github.com/opencollective/opencollective/issues/6865):
1. User uploads receipt image
2. Loading indicator in attachment / date / amount boxes
3. If loading >10s → assume service is down → user can still proceed with manual entry
4. If user has typed data before upload → OCR result loads in background → does NOT overwrite manual input
5. User sees side-by-side: "OCR result | Your entry" → choose

**Dietician spec**:
- Show predicted items in a confirm-list with checkboxes
- Each item shows: predicted name, predicted quantity, predicted price, confidence score
- User can: accept, edit-name, edit-quantity, delete, add-new
- On commit, write to `receipt_audit_log` table:
  - timestamp
  - source (claudemax_cli / gemini_vision / manual)
  - original_text (full receipt OCR text)
  - parsed_items_json (model's structured output)
  - committed_items_json (post-user-edit final state)
  - delta_json (diff between parsed and committed)

This gives Victor a clean "what did the OCR mess up" view over time.

### Validation rules to catch obvious errors

- Subtotal + tax = total (within 1 RON tolerance)
- All items priced > 0
- No item priced >100 RON without explicit confirmation (likely OCR misread)
- All item names non-empty

---

## 8. Dark patterns + ED-safeguards

### 8.1 NEDA / AED guidelines + research foundation

NEDA position ([NEDA Orthorexia overview](https://www.nationaleatingdisorders.org/orthorexia/); [NEDA Muscle Dysmorphia](https://www.nationaleatingdisorders.org/muscle-dysmorphia/); [NEDA body image and eating disorders](https://www.nationaleatingdisorders.org/body-image-and-eating-disorders/); [NEDA additional support options](https://www.nationaleatingdisorders.org/additional-support-options/); [NEDA professional guidelines](https://www.nationaleatingdisorders.org/professionals-guidelines/)): content related to diet culture and weight management can be harmful to those with eating disorders, is against NEDA policy, and would never have been scripted into health tools by eating disorders experts.

AED ([AED 2021 4th ed medical care PDF](https://higherlogicdownload.s3.amazonaws.com/AEDWEB/27a3b69a-8aae-45b2-a04c-2a078d02145d/UploadedImages/Publications_Slider/2120_AED_Medical_Care_4th_Ed_FINAL.pdf); [AED resources](https://www.aedweb.org/resources/about-eating-disorders); [AED 2016 3rd ed PDF](https://www.massgeneral.org/assets/mgh/pdf/psychiatry/eating-disorders-medical-guide-aed-report.pdf); [AED publications](https://www.aedweb.org/resources/publications); [AED English PDF](https://higherlogicdownload.s3.amazonaws.com/AEDWEB/05656ea0-59c9-4dd4-b832-07a3fea58f4c/UploadedImages/AED_Medical_Care_Guidelines_English_04_03_18_a.pdf); [NEDC AED guide 4th ed](https://nedc.com.au/eating-disorder-resources/find-resources/show/aed-eating-disorders-a-guide-to-medical-care-4th-ed); [Newswise AED](https://www.newswise.com/institutions/newsroom/Academy-for-Eating-Disorders-(AED)-5492); [Newswise AED weight-related statement](https://www.newswise.com/articles/the-academy-for-eating-disorders-releases-a-statement-on-the-recent-american-academy-of-pediatrics-clinical-practice-guideline-for-weight-related-care-first-do-no-harm)). 4th ed "purple book" key insight: weight is NOT the only clinical marker; people at low, normal, or high weights can have ED; individuals at any weight may be malnourished.

Research foundation ([PMC effects of diet/fitness apps qualitative](https://pmc.ncbi.nlm.nih.gov/articles/PMC8485346/); [ScienceDirect link review](https://www.sciencedirect.com/science/article/pii/S174014452400158X); [ResearchGate calorie counting ED](https://www.researchgate.net/publication/313537618_Calorie_counting_and_fitness_tracking_technology_Associations_with_eating_disorder_symptomatology); [PubMed Linde calorie tracking + ED](https://pubmed.ncbi.nlm.nih.gov/28214452/); [PMC MFP use in ED](https://pmc.ncbi.nlm.nih.gov/articles/PMC5700836/); [Center4Research fitness apps + ED](https://www.center4research.org/fitness-tracking-apps-eating-disorders/); [The Swaddle health tracking pipeline](https://www.theswaddle.com/health-tracking-apps-provide-a-worrying-pipeline-to-eating-disorders-better-tech-design-can-fix-that); [Duke trouble with tracking](https://psychiatry.duke.edu/blog/trouble-tracking); [therapist.com calorie counting](https://therapist.com/disorders/eating-disorders/calorie-counting-apps/); [umatechnology calorie apps harm](https://umatechnology.org/how-calorie-counting-apps-are-harming-your-health-and-what-to-do-about-it/); [breakbingeeating stop calorie counting](https://breakbingeeating.com/stop-calorie-counting/); [ScienceDirect calorie app motives 2021](https://www.sciencedirect.com/science/article/abs/pii/S1471015321000957); [PubMed Levinson calorie app](https://pubmed.ncbi.nlm.nih.gov/34543856/); [Restore Mental Health calorie counting](https://restore-mentalhealth.com/calorie-counting-obsession/); [PMC longitudinal mobile food tracking](https://pmc.ncbi.nlm.nih.gov/articles/PMC11556259/); [Symptoms of Living calorie-counting apps](https://symptomsofliving.com/blog/the-app-that-is-fuelling-your-eating-disorder/); [Eating Disorder Hope tracking apps](https://www.eatingdisorderhope.com/blog/eating-disorder-recovery-apps); [Choosing Therapy ED recovery apps](https://www.choosingtherapy.com/best-eating-disorder-recovery-apps/); [MyNetDiary ED policy](https://www.mynetdiary.com/eating-disorders-food-tracking.html); [NPR chatbot bad advice](https://www.npr.org/sections/health-shots/2023/06/08/1180838096/an-eating-disorders-chatbot-offered-dieting-advice-raising-fears-about-ai-in-hea); [Center for Discovery activity trackers](https://centerfordiscovery.com/blog/activity-trackers-eating-disorder-recovery/); [Newsweek dark side fitness apps](https://www.newsweek.com/fitness-apps-study-says-they-can-do-more-harm-than-good-10913928); [UCL emotional strain apps](https://www.ucl.ac.uk/news/2025/oct/emotional-strain-fitness-and-calorie-counting-apps-revealed); [bioengineer emotional toll](https://bioengineer.org/emotional-toll-of-fitness-and-calorie-counting-apps-uncovered/); [boxlife shame study](https://boxlifemagazine.com/fitness-tracker-shame-emotional-toll/); [EurekAlert emotional strain](https://www.eurekalert.org/news-releases/1102616); [nursinginpractice apps wellbeing](https://www.nursinginpractice.com/clinical/womens-health/fitness-and-calorie-counting-apps-can-impact-wellbeing-study-suggests/); [medicalxpress emotional strain](https://medicalxpress.com/news/2025-10-emotional-strain-calorie-apps-revealed.html); [ScienceDirect whatieatinaday TikTok](https://www.sciencedirect.com/science/article/abs/pii/S1740144525000932)).

Key findings:
- **~75% of clinical ED-sample participants** used MyFitnessPal; **~73%** perceived it as contributing to their ED.
- **Numeric quantification fuels obsession** — users develop fixation on numbers.
- **Red/green colour coding** for "wins/losses" creates anxiety and guilt.
- **Calorie warning signals** when approaching limit create food preoccupation.
- **Detailed food analysis** prompts users to avoid specific food types — known binge trigger.
- **Gamification** of eating, exercise, tracking creates unhealthy competition with self.

### 8.2 Hard rules (banned in Dietician)

These are non-negotiable. Coded as guards in the app.

1. **No kcal floor below safe minimum.** For males >180 cm: 1500 kcal/day floor. For other body types: calculated min(BMR × 0.85, 1500). Refuse to set lower.
2. **No weight-loss rate >0.5 kg/week.** Refuse to configure.
3. **No streak-shame.** Either no streak counter, or a "streak resilience" model where occasional skipped logs don't reset to zero.
4. **No red/green pass/fail color coding.** Use Wong-palette neutral colors — blue / orange / vermillion. Pass/fail signaled by position on a target range, not color.
5. **No social comparison feed.** Zero. No "your friend logged X" notifications.
6. **No body-comparison photos.** No before/after collage features.
7. **No leaderboards.** No "X has the longest streak" ranking.
8. **No emotion inference from logging gaps** (EU AI Act Art. 5(1)(f)).
9. **No OCR blind auto-commit.** User confirmation always required.
10. **No paywall on core safety features.** Dietician is non-commercial, but the principle: every "soft toggle" in the ED-safeguard set must be free/local. None gated.

### 8.3 Soft rules (user-toggleable)

These are off-by-default for Victor's lean-bulk use case, but available to users who need them:

1. **Hide kcal display.** Replace with macro rings showing % completion, no absolute number.
2. **Hide weight chart.** Show only trend ("stable / increasing / decreasing") not the number.
3. **Hide body-comp metrics** (body fat %, lean mass).
4. **Disable streak counter.**
5. **Disable kcal-deficit alerts.**
6. **Switch to "narrative mode"** — weekly qualitative check-in only, no numbers.
7. **Escape hatch — Recovery Mode**: replaces all tracking UI with a single "How are you feeling about food today?" prompt + a link to professional resources.

### Polivy-Herman "what-the-hell effect" + binge-restriction cycle

[US News what-the-hell health effect](https://health.usnews.com/wellness/mind/articles/2017-11-15/avoiding-the-what-the-hell-health-effect); [tutor2u boundary model](https://www.tutor2u.net/psychology/topics/boundary-model); [Wikipedia counterregulatory eating](https://en.wikipedia.org/wiki/Counterregulatory_eating); [Brainscape what-the-hell effect](https://www.brainscape.com/academy/what-the-hell-effect/); [Psychstory disinhibition boundary obesity](https://www.psychstory.co.uk/eating-behaviour/disinhibition-and-the-boundary-model-of-obesity); [PMC overeating restrained unrestrained](https://pmc.ncbi.nlm.nih.gov/articles/PMC7096476/); [Nutrola why can't stick diet](https://nutrola.app/en/blog/why-cant-i-stick-to-a-diet); [myhealthsciences podcast what-the-heck](https://podcast.myhealthsciences.com/1580440/8926814); [myhealthsciences blog what-the-heck](https://myhealthsciences.com/blog-posts/cognition/what-the-heck-effect-why-you-binge); [Polivy & Herman 1986 dieting binging](https://psycnet.apa.org/record/1986-01626-001).

Polivy & Herman 1984 boundary model: dieters stay conservative as long as they perceive control; when they perceive a loss of control (passing a self-imposed restraint), they enter counterregulatory eating — eating *more* than they normally would.

Triggers: stress, alcohol, cognitive load, emotional distress, smell/sight of tempting food.

Mitigation: **flexible dietary approach**, allowing small portions of a wide range of foods → reduces what-the-hell effect.

**Dietician application**: when user logs an over-target item, the app should NOT show a red "OVER LIMIT" warning. Instead show neutral "you went over by ~150 kcal today; tomorrow's target adjusts down by ~75 kcal" — no shame, no streak break.

### Orthorexia + DSM status

[news-medical orthorexia criteria](https://www.news-medical.net/health/Diagnostic-Criteria-for-Orthorexia.aspx); [Wikipedia orthorexia](https://en.wikipedia.org/wiki/Orthorexia_nervosa); [WebMD orthorexia](https://www.webmd.com/mental-health/eating-disorders/what-is-orthorexia); [Center for Discovery orthorexia](https://centerfordiscovery.com/conditions/orthorexia/); [DSM-5-TR Diagnostic Criteria](https://insideoutinstitute.org.au/resource-library/dsm-5-diagnostic-criteria-for-eating-disorders); [PMC orthorexia obsession review](https://pmc.ncbi.nlm.nih.gov/articles/PMC6370446/); [ACUTE orthorexia clean eating](https://www.acute.org/resources/orthorexia-eating-disorders); [PMC consensus document orthorexia](https://pmc.ncbi.nlm.nih.gov/articles/PMC9803763/).

Not in DSM-5. Proposed criteria (Dunn & Bratman 2016): obsessive focus on "healthy" eating, marked emotional distress over choices perceived as unhealthy. Earlier Bratman & Knight 2000: spending >3h/day thinking/planning/preparing "clean food," feeling superior to non-followers, displacing relationships/hobbies.

**Dietician application**: do not encourage rigid food-categorization ("good" vs "bad"). All foods get the same macro/micro breakdown. No food gets a value-laden label.

### Bigorexia / muscle dysmorphia — Victor's matched risk profile

[Healthline bigorexia](https://www.healthline.com/health/bigorexia); [Aromedy bigorexia young men](https://www.aromedy.com/post/bigorexia-is-making-a-comeback-and-young-men-are-at-the-highest-risk); [PubMed muscle dysmorphia screening](https://pubmed.ncbi.nlm.nih.gov/29271781/); [Within Health bigorexia](https://withinhealth.com/learn/articles/what-is-bigorexia); [PubMed bigorexia bodybuilding](https://pubmed.ncbi.nlm.nih.gov/18759381/); [Mens reproductive health muscle dysmorphia](https://mensreproductivehealth.com/muscle-dysmorphia-bigorexia-in-adolescents-and-young-men-diagnosis-risks-and-fertility-impact/); [sage-clinics bigorexia](https://sage-clinics.com/what-is-bigorexia/); [US Pharmacist bigorexia review](https://www.uspharmacist.com/article/bigorexia-nervosa-review); [Aster Springs muscle dysmorphia](https://astersprings.com/blog/muscle-dysmorphia-signs).

Bigorexia = body-image condition where person believes they are not muscular enough, regardless of actual size. Form of BDD. Distress levels similar to anorexia. Cutting/bulking cycles can resemble binge/fast patterns.

**Critical**: treatment recommendations include *deleting calorie trackers and fitness apps*. Tracking apps can reinforce compulsive behavior in this demographic.

Victor is 188cm / 67.5kg, young male, lean-bulk. Demographic match for bigorexia risk.

**Dietician mitigations specific to bigorexia**:
- Weekly check-in emphasizes strength + energy + mood + sleep — NOT just weight
- "Strength tracker" alongside weight tracker (lifts going up matters more than scale)
- "How do you feel about your body today?" optional question → not actioned, just logged
- If user repeatedly logs body-image distress responses → surface a "consider talking to a professional" suggestion (NOT diagnosis, just suggestion)

### Streak gamification harm

[The Brink gamified life dark psychology](https://www.thebrink.me/gamified-life-dark-psychology-app-addiction/); [PMC gamification behavior change](https://pmc.ncbi.nlm.nih.gov/articles/PMC10998180/); [Yu-kai Chou streak motivation burnout](https://yukaichou.com/gamification-analysis/streak-design-gamification-motivation-burnout/); [Productived streaks gamification](https://www.productived.net/articles/more-thoughts-on-streaks-and-gamification-of-habits); [IADB streaking success](https://publications.iadb.org/publications/english/document/Streaking-to-Success-The-Effects-of-Highlighting-Streaks-on-Student-Effort-and-Achievement.pdf); [Moore Momentum habit streaks](https://mooremomentum.com/blog/why-most-habit-streaks-fail-and-how-to-build-ones-that-dont/); [Decision Lab streak creep](https://thedecisionlab.com/insights/consumer-insights/streak-creep-the-perils-of-too-much-gamification); [Cohorty gamification habit tracking](https://www.cohorty.app/blog/gamification-in-habit-tracking-does-it-work-research-real-user-data); [JCR on or off track streaks](https://academic.oup.com/jcr/article/49/6/1095/6623414); [Growth Engineering gamification streaks](https://www.growthengineering.co.uk/gamification-streaks/).

Gamified feedback systems often increase anxiety, guilt, dependency, burnout. Over-justification effect: external rewards reduce intrinsic motivation. Breaking streaks → users more likely to stop using platform entirely.

Recommendation: cap streaks at habit-formation horizon (~90-120 days), then transition to capped systems with safety mechanisms.

---

## 9. Failure modes

### Logging-fatigue dropoff curve

- ~20% drop out before intervention end in food-logging studies
- Adherence declines linearly-to-exponentially with time
- Fatigue is primary abandonment driver
- ≥2 eating occasions/day = adherent threshold
- Stress increases hunger + reduces resistance → drives abandonment cycle

Dietician mitigation: abbreviated-monitoring mode after 90 days, photo-only mode, voice-only mode, "something is better than nothing" framing.

### Restriction-binge cycle (Polivy-Herman)

Covered §8. Mitigation: neutral "rolling target adjustment" framing instead of "you went over."

### What-the-hell effect

Covered §8. Mitigation: flexible-dietary approach by default; never frame any food as forbidden.

### Calorie-counting OCD (orthorexia)

Covered §8. Mitigation: no value-laden food labels; daily log can be skipped without consequence.

### Social isolation around meals

The act of pulling out a phone to log calories at a family dinner is socially isolating. Dietician mitigation:
- Bulk-log mode: log "Sunday lunch at mom's, ate normally, ~600 kcal mixed" in one entry
- Voice-log via watch / headphone shortcut — no phone-out
- "Estimated" entries with explicit uncertainty bands

### When tracker REGRESSES wellbeing

Signals to detect (passively, NOT to act on, just to log for Victor's own review):
- Logging frequency >5 entries/day for >7 days running (obsessive)
- Logged kcal trending toward kcal floor for >5 days
- Body-image-distress check-ins increasing
- Strength metrics regressing (under-eating signal)

Action when detected: surface ONE soft prompt — "you've been tracking very intensively this week — want to switch to weekly-mode for a few days?" No further nag.

---

## 10. Cultural eating context (RO)

### Romanian meal patterns

[FriendTripToRomania what Romanians eat](https://friendtriptoromania.com/romanian-breakfast-lunch-dinner/); [TheRomanianCookbook breakfast](https://theromaniancookbook.com/traditional-romanian-breakfast/); [Chef's Pencil RO breakfast](https://www.chefspencil.com/typical-romanian-breakfast-foods/); [HiNative RO meals](https://hinative.com/questions/1719008); [Quora RO meal timing](https://www.quora.com/What-and-when-do-people-eat-in-Romania); [Romania-Insider RO foods drinks](https://www.romania-insider.com/romanian-foods-drinks-march-2019); [TheRomanianCookbook traditional dishes](https://theromaniancookbook.com/traditional-romanian-dishes/); [worldtravelchef RO breakfast](https://worldtravelchef.com/romanian-breakfast/); [TheTravel RO breakfast](https://www.thetravel.com/what-is-a-typical-romanian-breakfast/); [WorldInMyPocket RO breakfast](https://www.theworldinmypocket.co.uk/the-breakfast-club-the-traditional-romanian-breakfast/).

- **Breakfast**: bread + telemea / aged cheese, eggs, herbed omelet, zacuscă, seasonal vegetables. Urban: yogurt + muesli + fruit, sandwiches, cereals.
- **Lunch**: historically the largest meal — soup → main → dessert. Increasingly being replaced by dinner in urban families.
- **Dinner**: 19:00-21:00. Family-gathering meal in modern urban life.

**Dietician adaptation**: default meal-bucket labels in RO: `Mic dejun / Prânz / Cină / Gustare` (Breakfast / Lunch / Dinner / Snack). Allow per-user customization of meal times.

### Orthodox fasting periods (must be first-class, not friction)

[Romania Private Tours Great Lent](https://romaniaprivatetours.com/great-lent-romanian-pre-easter-traditions-and-rituals/); [Saint John Suceava fasting rules](https://saintjohnofsuceava.org/resources/orthodox-worship/fasting-rules/); [postintermitent.ro](https://www.postintermitent.ro/); [The A-Blast Orthodox lent students](https://www.thea-blast.org/student-life/health/2022/03/28/healthy-food-options-for-students-who-are-fasting/); [Romania-Insider tradition-filled December](https://www.romania-insider.com/a-tradition-filled-december-in-romania-orthodox-holidays-caroling-season-pig-slaughtering); [Eureka Health Orthodox fasting](https://www.eurekahealth.com/resources/orthodox-fasting-rules-great-lent-explained-en); [Holy Orthodox fasting guidelines](https://www.holyorthodox.org/fastingguidelines); [Wikipedia Nativity Fast](https://en.wikipedia.org/wiki/Nativity_Fast); [PMC Christian Orthodox fasting diet](https://pmc.ncbi.nlm.nih.gov/articles/PMC10004762/); [Cohn Jansen Rostul postului](https://www.cohnandjansen.ro/portfolio/rostul-postului-hhc/).

- **Postul Crăciunului** (Nativity Fast): Nov 15 – Dec 24. No meat, dairy, fish, eggs. Olive oil + wine on select days.
- **Postul Paștelui** (Great Lent): 48 days before Easter. Similar restrictions, even stricter on some days.
- **Weekly fasts**: Wednesdays + Fridays — many Orthodox Romanians.
- **Vegan during fasts**: sarmale de post (rice + tomato + herbs in cabbage rolls).

**Dietician application**:
- Built-in "Mod post" (Fast Mode) toggle with auto-applied date ranges for Nov 15 – Dec 24 + 48-day pre-Easter
- Pantry filter to hide non-fasting items during fast periods
- Recipe suggestions auto-filter to plant-based during fast
- Don't moralize either way — fasting is religious practice, not "healthy diet trend"

### Bilingual UI obligations

User memory says: "Language preference: English default everywhere; Romanian only when language-dependent (UAIC course terminology, RO sources)."

For Dietician specifically — food terms ARE language-dependent. Food names in RO + EN should both work. The "post" terminology is RO-only.

- Default UI: EN
- Food names: searchable in RO + EN
- Fast-mode labels: RO + EN ("Mod post / Fasting Mode")
- Recipe content: language-of-source

### Holiday over-restriction risk

Christmas (Dec 25-27), Easter, Saint's days → typical RO over-eating windows after fasts. This is the **classic restriction → binge → guilt cycle** culturally encoded.

Dietician mitigation: holiday-mode toggle — "Holiday — pause tracking" — explicitly normalizes a 1-3 day pause without streak penalty.

### Social-pressure around tracking in RO context

Less mainstream than US/UK/Nordic. Pulling out phone at family table to scan food → socially marked behavior. Voice-log via earbuds + post-meal photo log help here.

---

## Cross-cutting synthesis

### 10 binding UI affordances

1. **Cronometer-style nutrient target bars** (top of food-diary).
2. **MacroFactor-style adaptive expenditure** (rolling 7-day weight × intake smoothing).
3. **Foodnoms-style on-device + privacy-card** (explicit "what we collect" upfront).
4. **Photo-as-suggestion** (Bite AI pattern), never blind-commit, always confirm/correct.
5. **Voice-first hands-free logging** (Whisper.cpp + Compose Multiplatform, RO + EN).
6. **Weekly check-in narrative** (MacroFactor / Carbon pattern) over daily streak shame.
7. **Reverse-diet / lean-bulk first-class mode** (Carbon pattern).
8. **Wong colorblind-safe palette** (NOT red/green pass-fail).
9. **24×24 / 44×44 touch targets**, ≥30pt font option for elderly users.
10. **RO Fast Mode** built-in for Postul Crăciunului + Postul Paștelui, holiday-pause toggle.

### 8 hard-banned dark patterns

1. **Kcal floor below 1500** for males >180 cm (citation: MyNetDiary safeguard policy, NEDA position).
2. **Weight-loss rate >0.5 kg/week** (citation: AED 4th ed, NEDA, MFP/Lifesum cautionary example).
3. **Streak-shame** — reset-to-zero on miss (citation: Decision Lab streak creep, Yu-kai Chou burnout).
4. **Red/green pass/fail colour coding** (citation: PMC effects of diet apps qualitative — "red- and green-colored fonts created anxiousness and guilt").
5. **Social-comparison feed** (citation: MFP retired their feed in 2024; PMC MFP clinical ED-sample 73% perceived contribution).
6. **Body-comparison photos / leaderboards** (citation: NEDA, AED muscle-dysmorphia guidance).
7. **OCR blind auto-commit** (citation: opencollective OCR issue, Veryfi best practices).
8. **Hidden paywall on core safety features** (citation: MFP 2022 barcode-scanner paywall backlash; non-applicable for Dietician but binding philosophy).

### ED-safeguard MODEL_CARD primitives

- `kcal_floor_kcal_per_day: Int` (min 1500 for males >180cm)
- `weight_loss_rate_ceiling_kg_per_week: Float` (max 0.5)
- `streak_resilience_mode: Boolean` (default true — skipped days don't reset)
- `kcal_display_enabled: Boolean` (user toggle)
- `weight_display_enabled: Boolean` (user toggle)
- `recovery_mode_enabled: Boolean` (replaces tracking with feeling-check)
- `professional_resource_link_displayed_at: List<Timestamp>` (audit-trail of when prompted)

### EU AI Act 8 wired affordances

1. **Art. 4 AI literacy** — onboarding card + persistent "what is this based on?" affordance
2. **Art. 5(1)(f) emotion inference ban** — NO mood inference from logging gaps or any biometric
3. **Art. 12 audit log** — every LLM-grounded recommendation logged with timestamp / model / prompt / response / user action, ≥6-month retention
4. **Art. 13 transparency card** — capabilities, limits, error margins, user rights
5. **Art. 14 human oversight** — every AI suggestion is editable + rejectable, distinguishable from user-entered data, with "disable AI suggestions" toggle in settings
6. **Art. 15 accuracy targets** — published in MODEL_CARD (portion-estimation ±X%, OCR Y% accuracy, ASR Z% WER)
7. **Art. 16 MODEL_CARD + RISK_REGISTER** committed to repo
8. **GDPR / ANSPDCP** — explicit consent on first launch, DSAR export endpoint, right-to-erasure endpoint, data-minimization audit of fields collected

### Open council questions

1. Should Dietician auto-enable Fast Mode based on RO Orthodox calendar, or strictly opt-in per user? Auto-enable risks assuming religious practice; opt-in risks friction.
2. Bigorexia mitigations — should the "strength tracker" be a built-in feature or a recommended external app (e.g. Hevy)? Built-in adds scope; external risks data fragmentation.
3. n=5 users + ANSPDCP DPIA — required, recommended, or skip? Verdict: recommended one-pager, not required.
4. EU AI Act Art. 12 audit-log retention — 6 months (high-risk min), 1 year (defensive), or indefinite (Victor's own review value)? Default 1 year, user-deletable.
5. Voice ASR — Whisper.cpp on-device only, or fallback to Google ASR for short Android utterances? On-device only is safer for special-category data; cloud is faster.
6. Receipt OCR — ClaudeMax CLI primary + Gemini Vision fallback, or reverse? Both have hallucination risk; user-confirmation step neutralizes either order.
7. Bilingual UI default — EN per Victor's stated preference, or RO for the 4 other family users? Per-user-preference, EN default.
8. Multi-user trust model — friends/family see each other's data, or strict per-user isolation? Per-user isolation; opt-in shared-meal-log for couples/cohabiting users.

---

## Open questions

(See "Open council questions" above — same list.)

Additional gaps surfaced during this research that Round 4 should cover:
- **Compose Multiplatform desktop integration with macOS / Windows native voice APIs** (e.g. NSSpeechRecognizer fallback on macOS, SAPI on Windows) — beyond Whisper.cpp.
- **Real Romanian ASR food-vocabulary benchmark** — no published benchmark exists for Whisper or other ASR on RO food terms. Dietician should produce one as part of evaluation.
- **GROBID-parsed paper grounding for Claude** — the citation-surfacing UX for "the AI recommended X because of paper Y" needs its own design pass.
- **Multi-user friends/family permission model** — GDPR multi-user app data-segregation patterns (joint controller? separate controllers? user-as-controller-of-own-data?).
- **Long-term ED-safety monitoring** — at what threshold does a logged pattern (e.g. 30 days of kcal floor adherence + body-image distress check-ins) escalate from soft suggestion to firmer intervention? Needs ethics review.

---

## Sources

(Grouped by section; individual citations are inline throughout.)

### §1 Nutrition apps

- [Cronometer Review 2025 (repreturn.com)](https://repreturn.com/cronometer-review/)
- [Cronometer 2026 (calorie-trackers.com)](https://calorie-trackers.com/reviews/cronometer/)
- [Cronometer official](https://cronometer.com/index.html)
- [Cronometer features](https://cronometer.com/features/index.html)
- [Cronometer Google Play](https://play.google.com/store/apps/details?id=com.cronometer.android.gold&hl=en_US)
- [Cronometer 2025 powerlifter review (turbulencegains.com)](https://turbulencegains.com/cronometer-gold-review-2025/)
- [Cronometer blog updates](https://cronometer.com/blog/cronometer-updates-whats-new-improved/)
- [Cronometer 2026 trygaya review](https://www.trygaya.com/review/cronometer-review)
- [Cronometer review goldiai](https://goldiai.com/blog/cronometer-app-review/)
- [MyFitnessPal Meal Scan FAQ](https://support.myfitnesspal.com/hc/en-us/articles/360045761612-Meal-Scan-FAQ)
- [MyFitnessPal Sucks (FeastGood)](https://feastgood.com/myfitnesspal-sucks/)
- [MFP barcode community thread](https://community.myfitnesspal.com/en/discussion/10909330/barcode-scanner)
- [MFP barcode help](https://support.myfitnesspal.com/hc/en-us/articles/360032624771-How-do-I-use-the-barcode-scanner-to-log-foods)
- [MFP trustpilot reviews](https://www.trustpilot.com/review/www.myfitnesspal.com)
- [MFP barcode paywall (Slashdot)](https://news.slashdot.org/story/22/08/25/1955238/myfitnesspal-paywalls-barcode-scanner-that-made-counting-calories-easy)
- [Why MFP removed barcode (SnapCalorie)](https://www.snapcalorie.com/blog/why-did-myfitnesspal-remove-free-barcode-scanner.html)
- [MFP barcode paywall (Droid-Life)](https://www.droid-life.com/2022/08/24/myfitnesspal-puts-barcode-scanner-behind-premium-paywall/)
- [MFP bring back barcode community](https://community.myfitnesspal.com/en/discussion/10922135/bring-back-the-barcode-scanner)
- [MFP barcode Hacker News](https://news.ycombinator.com/item?id=32593833)
- [MacroFactor MM Oct 2025](https://macrofactor.com/mm-oct-2025/)
- [Nutrola vs MacroFactor](https://nutrola.app/en/blog/nutrola-vs-macrofactor-ai-coaching-vs-adaptive-algorithm-2026)
- [MacroFactor 2025 annual report](https://macrofactorapp.com/annual-report-2025/)
- [MacroFactor product page](https://macrofactor.com/macrofactor/)
- [MacroFactor algorithms philosophy](https://macrofactor.com/macrofactors-algorithms-and-core-philosophy/)
- [MacroFactor App Store](https://apps.apple.com/us/app/macrofactor-macro-tracker/id1553503471)
- [MacroFactor algorithm accuracy](https://macrofactorapp.com/algorithm-accuracy/)
- [MacroFactor official](https://macrofactor.com/)
- [MacroFactor Google Play](https://play.google.com/store/apps/details?id=com.sbs.diet)
- [Is MacroFactor worth it 2026](https://nutriscan.app/blog/posts/is-macrofactor-worth-it-2026-529e4f7d46)
- [Carbon Diet Coach (masculinesynergy)](https://masculinesynergy.com/carbon-diet-coach-the-science-based-macro-tracking-app-created-by-dr-layne-norton-thats-revolutionizing-nutrition-coaching/)
- [Carbon coaching system help](https://help.joincarbon.com/en/articles/5296570-what-is-carbon-and-how-does-the-coaching-system-work)
- [Carbon official](https://www.joincarbon.com/)
- [Carbon App Store](https://apps.apple.com/us/app/carbon-macro-coach-tracker/id1437820611)
- [Carbon 2026 review (NutriScan)](https://nutriscan.app/blog/posts/is-carbon-diet-coach-worth-it-2026-b08ffeab07)
- [Dr Gabrielle Lyon on Carbon](https://drgabriellelyon.com/the-ultimate-diet-coaching-app-why-i-recommend-carbon/)
- [Carbon FeastGood review](https://feastgood.com/carbon-diet-coach-review/)
- [Carbon Toolify review](https://www.toolify.ai/gpts/layne-nortons-carbon-diet-coach-reviewed-360556)
- [Dexa Ask AI nutrition app](https://dexa.ai/s/b684fd5e-2813-11ef-9703-ef344334179d)
- [Carbon Google Play](https://play.google.com/store/apps/details?id=com.joincarbon.nutrition&hl=en_US&gl=US)
- [Bite AI Food Recognition](https://bite.ai/food-recognition/)
- [Bite AI platform](https://bite.ai/)
- [Bite AI Docs](https://docs.bite.ai/food-recognition/)
- [Bite AI logging SDK](https://bite.ai/food-logging-sdk/)
- [Foodvisor food image recognition guide](https://www.foodvisor.io/en/guides/article/food-image-recognition-explained/)
- [Bitewise demo](https://bitewise-min.onrender.com/)
- [PMC food image recognition platforms study](https://pmc.ncbi.nlm.nih.gov/articles/PMC7752530/)
- [Wondershare AI food recognition apps](https://www.wondershare.com/calorie-tracker/ai-food-recognition-app.html)
- [LogMeal Food API](https://logmeal.com/api/)
- [BiteWise Life](https://bitewise.life/)
- [FoodNoms 2 MacStories](https://www.macstories.net/reviews/foodnoms-2-refreshes-its-design-and-adds-refinements-to-nutrition-logging-and-goal-tracking-throughout/)
- [FoodNoms original MacStories](https://www.macstories.net/reviews/foodnoms-a-privacy-focused-food-tracker-with-innovative-new-ways-to-log-meals/)
- [FoodNoms UK review 2026](https://home-cooks.co.uk/pages/review-foodnoms)
- [FoodNoms App Store](https://apps.apple.com/us/app/nutrition-tracker-foodnoms/id1479461686)
- [FoodNoms official](https://foodnoms.com/)
- [FoodNoms crowdsourced DB blog](https://foodnoms.com/news/building-a-crowdsourced-food-database)
- [TWiT best iPhone nutrition apps](https://twit.tv/posts/tech/best-nutrition-tracking-apps-iphone-foodnoms-myfitnesspal-carb-manager-compared)
- [FoodNoms reviews](https://justuseapp.com/en/app/1479461686/foodnoms-food-tracker/reviews)
- [FoodNoms privacy policy](https://foodnoms.com/privacy)
- [FoodNoms AU App Store](https://apps.apple.com/au/app/foodnoms-nutrition-tracker/id1479461686)
- [Lifesum FuelNutrition review](https://fuelnutrition.app/reviews/lifesum-review)
- [Lifesum App Store](https://apps.apple.com/us/app/lifesum-ai-calorie-counter/id286906691)
- [Lifesum review on official site](https://lifesum.com/nutrition-explained/lifesum-reviews)
- [Lifesum Unimeal review](https://unimeal.reviews/weight-loss-apps/lifesum/)
- [Lifesum Trustpilot](https://www.trustpilot.com/review/lifesum.com)
- [Lifesum official](https://lifesum.com/)
- [Lifesum Google Play](https://play.google.com/store/apps/details?id=com.sillens.shapeupclub&hl=en)
- [Lifesum reviews on features page](https://lifesum.com/features/lifesum-reviews)
- [Lifesum App Store again](https://apps.apple.com/us/app/lifesum-food-calorie-tracker/id286906691)
- [Lifesum features](https://lifesum.com/features/)
- [MyNetDiary ED-safety policy](https://www.mynetdiary.com/eating-disorders-food-tracking.html)
- [FatSecret showcase ScreensDesign](https://screensdesign.com/showcase/calorie-counter-by-fatsecret)
- [Revenuecat mobile paywalls](https://www.revenuecat.com/blog/growth/guide-to-mobile-paywalls-subscription-apps/)
- [Revenuecat top app paywalls](https://www.revenuecat.com/blog/growth/how-top-apps-approach-paywalls/)
- [Adapty paywall newsletter](https://adapty.io/blog/paywall-newsletter-22/)
- [Paid calorie trackers Nutrola](https://nutrola.app/en/blog/are-paid-calorie-trackers-worth-it-vs-free)
- [Business of Apps paywall optimization](https://www.businessofapps.com/guide/app-paywall-optimization/)
- [Cal Pal AI Calorie Tracker](https://screensdesign.com/showcase/cal-pal-ai-calorie-tracker)
- [FunnelFox paywall best practices](https://blog.funnelfox.com/effective-paywall-screen-designs-mobile-apps/)
- [Nami ML 20 paywall types](https://www.nami.ml/blog/20-types-of-mobile-app-paywalls/)
- [Apphud best paywalls](https://apphud.com/blog/best-performing-paywallls)
- [MFP social features support](https://support.myfitnesspal.com/hc/en-us/categories/360002215751-Social-Features)
- [MFP activity feed help](https://support.myfitnesspal.com/hc/en-us/articles/26955045411981-Your-Community-Activity-Feed)
- [Why are we losing newsfeed MFP](https://community.myfitnesspal.com/en/discussion/10916127/why-are-we-losing-the-newsfeed)
- [MFP community guidelines](https://www.myfitnesspal.com/community-guidelines)
- [Turning off MFP social](https://community.myfitnesspal.com/en/discussion/10750244/turning-off-social-side-of-mfp)
- [MFP changes to platform](https://community.myfitnesspal.com/en/discussion/10941670/changes-to-the-platform)
- [Where Did MFP newsfeed go](https://community.myfitnesspal.com/en/discussion/10917037/where-did-the-news-feed-go)

### §2 Logging UX trade-offs

- [PMC dietary self-monitoring adherence](https://pmc.ncbi.nlm.nih.gov/articles/PMC6856872/)
- [PMC Delphi self-monitoring burden](https://pmc.ncbi.nlm.nih.gov/articles/PMC9358747/)
- [PMC HLM adherence weight loss](https://pmc.ncbi.nlm.nih.gov/articles/PMC5568610/)
- [PMC systematic review BWL self-monitoring](https://pmc.ncbi.nlm.nih.gov/articles/PMC8928602/)
- [Springer burden mobile dietary](https://link.springer.com/article/10.1007/s41347-021-00203-9)
- [Cambridge BWL systematic review](https://www.cambridge.org/core/journals/public-health-nutrition/article/systematic-review-of-the-use-of-dietary-selfmonitoring-in-behavioural-weight-loss-interventions-delivery-intensity-and-effectiveness/476B83589088637C6740BA801B92185D)
- [Burke self-monitoring weight loss](https://lonestarcenters.com/wp-content/uploads/2025/11/2011-burke-self-monitoring-and-weight-loss.pdf)
- [Tandfonline behaviour sequence analysis](https://www.tandfonline.com/doi/full/10.1080/08870446.2022.2094929)
- [PubMed scoping review calorie counting](https://pubmed.ncbi.nlm.nih.gov/41329042/)
- [Oyelabs food ordering UX](https://oyelabs.com/frictionless-food-ordering-user-experience-guide/)
- [Eleken mobile UX examples](https://www.eleken.co/blog-posts/mobile-ux-design-examples)
- [Spilt Milk mobile UX 2025](https://spiltmilkwebdesign.com/mobile-ux-design-for-on-the-go-diners-expert-tips-for-cafes-in-2025/)
- [SennaLabs food delivery UX](https://sennalabs.com/blog/ux-ui-for-food-delivery-apps-how-to-speed-up-the-ordering-process)
- [LogRocket UX friction](https://logrocket.com/for/ux-friction)
- [Onething mobile UI/UX 2026](https://www.onething.design/post/ui-ux-design-for-mobile-apps)
- [Lilac food delivery UI/UX](https://lilacinfotech.com/blog/319/food-delivery-app)
- [UXCam mobile UX](https://uxcam.com/blog/mobile-ux/)
- [ProCreator food app UX](https://procreator.design/blog/food-app-ux-key-strategies/)
- [SideChef recipe platform UX](https://www.sidechef.com/business/recipe-platform/ux-best-practices-for-recipe-sites)
- [Tubik recipe app case study](https://blog.tubikstudio.com/case-study-recipes-app-ux-design/)
- [Healthi recipe builder](https://help.healthiapp.com/support/solutions/articles/13000060423-4-1-recipe-builder)

### §3 Accessibility

- [W3C WCAG 2.2](https://www.w3.org/TR/WCAG22/)
- [WCAG2Mobile 22](https://www.w3.org/TR/wcag2mobile-22/)
- [Corpowid mobile a11y guide 2026](https://corpowid.ai/blog/mobile-application-accessibility-practical-humancentered-guide-android-ios)
- [ADATray WCAG 2.2 mobile](https://www.adatray.com/blog/wcag-2-2-mobile-app-accessibility)
- [AllAccessible 2.5.8 target size](https://www.allaccessible.org/blog/wcag-258-target-size-minimum-implementation-guide)
- [Deque WCAG 2.2 native mobile PDF](https://www.deque.com/axe-con/wp-content/uploads/2023/11/What-WCAG-2.2-Means-for-Native-Mobile-Accessibility-axe-con-2024_a11y-1.pdf)
- [Usercentrics WCAG 2.2 inclusive](https://usercentrics.com/knowledge-hub/mastering-web-app-accessibility-wcag2-2-and-inclusive-design/)
- [Accessibility Works ADA WCAG mobile](https://www.accessibility.works/blog/ada-wcag-compliance-standards-guide-mobile-apps/)
- [BrowserStack touch target size](https://www.browserstack.com/docs/app-accessibility/rule-repository/rules-list/touch-target/touch-target-size)
- [Siteimprove motor impairments touch](https://www.siteimprove.com/blog/motor-impairments-and-mobile-ui-the-touch-target-problem/)
- [W3C mobile accessibility mapping](https://www.w3.org/TR/mobile-accessibility-mapping/)
- [PMC mobile voice food RCT elderly](https://pmc.ncbi.nlm.nih.gov/articles/PMC7551114/)
- [ScienceDirect usability older mHealth](https://www.sciencedirect.com/science/article/pii/S111001682400588X)
- [NCBI trust privacy mobile aging](https://www.ncbi.nlm.nih.gov/books/NBK563116/)
- [Adchitects older adult UI](https://adchitects.co/blog/guide-to-interface-design-for-older-adults)
- [dev.to elderly mobile a11y](https://dev.to/joaopimentag/accessibility-for-the-elderly-in-mobile-applications-an-analysis-for-optimal-ux-design-550h)
- [JMIR mHealth elderly food RCT](https://mhealth.jmir.org/2020/9/e20317/)
- [All Seniors senior smartphone](https://allseniors.org/articles/how-to-set-up-a-senior-friendly-smartphone/)
- [NN/g older adult usability](https://www.nngroup.com/articles/usability-for-senior-citizens/)
- [Glance healthcare app elderly](https://thisisglance.com/learning-centre/how-do-i-make-my-healthcare-app-accessible-for-elderly-users)
- [Datylon colorblind charts](https://www.datylon.com/blog/data-visualization-for-colorblind-readers)
- [Rgblind palettes 2026](https://rgblind.com/blog/color-blindness-friendly-chart-colors)
- [Venngage colorblind palette](https://venngage.com/blog/color-blind-friendly-palette/)
- [Mark Bounthavong viz](https://mbounthavong.com/blog/2022/4/29/communicating-data-effectively-with-data-visualization-color-blind-friendly-palette)
- [Adobe Color blindness simulator](https://color.adobe.com/create/color-accessibility)
- [thenode.biologists colorblind data viz](https://thenode.biologists.com/data-visualization-with-flying-colors/research/)
- [Tableau red green together](https://www.tableau.com/blog/examining-data-viz-rules-dont-use-red-green-together)
- [David Math Logic colorblind](https://davidmathlogic.com/colorblind/)
- [Datawrapper colorblind part 2](https://www.datawrapper.de/blog/colorblindness-part2)
- [Colorblind Guide design](https://www.colorblindguide.com/post/colorblind-friendly-design-3)
- [Android codelab Compose a11y](https://developer.android.com/codelabs/jetpack-compose-accessibility)
- [Medium Compose a11y 2025](https://medium.com/@sharmapraveen91/mastering-accessibility-in-jetpack-compose-ui-the-ultimate-guide-for-2025-825e419ab359)
- [Kaushal Vasava semantics a11y](https://medium.com/@KaushalVasava/semantics-and-accessibility-in-jetpack-compose-part-2-be080f39de81)
- [Medium androidlab a11y checklist](https://medium.com/@androidlab/building-accessible-android-apps-your-jetpack-compose-accessibility-checklist-c89f643c4f66)
- [Bryan Herbst Compose semantics](https://bryanherbst.com/2020/11/03/compose-semantics-talkback/)
- [ProAndroidDev Compose a11y](https://proandroiddev.com/building-accessible-android-uis-with-jetpack-compose-b59438fc6a03)
- [JetBrains Compose Multiplatform 1.6.0](https://blog.jetbrains.com/kotlin/2024/02/compose-multiplatform-1-6-0-release/)
- [dev.to Compose a11y best practices](https://dev.to/carlosmonzon/jetpack-compose-accessibility-best-practices-38j0)
- [Kotlinlang Slack TalkBack focus](https://slack-chats.kotlinlang.org/t/28578673/has-anyone-found-a-way-to-observe-talkback-focus-in-jetpack-)
- [Eevis a11y Compose preview](https://eevis.codes/blog/2024-03-16/accessibility-checks-with-jetpack-compose-previews/)
- [CHI 2024 nutrition comprehension](https://dl.acm.org/doi/10.1145/3613904.3642672)
- [CDC food literacy](https://www.cdc.gov/health-literacy/php/research-summaries/food-literacy.html)
- [PMC young adult food literacy](https://pmc.ncbi.nlm.nih.gov/articles/PMC4039409/)
- [PMC nutrition label intervention review](https://pmc.ncbi.nlm.nih.gov/articles/PMC6213388/)
- [SNEB Health literacy plain language](https://www.sneb.org/wp-content/uploads/2021/04/Health_Literacy_Plain_Language_-_SNEB_2019_7.23.19_PDF.pdf)
- [Yale Griffin Food Label Literacy](https://yalegriffinprc.griffinhealth.org/products-resources/prc-products/food-label-literacy/)
- [FDA Read the Label](https://www.fda.gov/food/nutrition-facts-label/read-label-youth-outreach-materials)
- [PMC nutrition materials consumer](https://pmc.ncbi.nlm.nih.gov/articles/PMC11934846/)
- [Action for Healthy Kids labels](https://www.actionforhealthykids.org/activity/how-to-read-nutrition-facts-labels/)

### §4 GDPR / ANSPDCP

- [Exabeam GDPR Art 9](https://www.exabeam.com/explainers/gdpr-compliance/gdpr-article-9-special-personal-data-categories-and-how-to-protect-them/)
- [Legalitgroup GDPR nutrition apps](https://legalitgroup.com/en/gdpr-and-personalized-nutrition-apps/)
- [GDPR-info Art 9](https://gdpr-info.eu/art-9-gdpr/)
- [ICO special category data](https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/lawful-basis/special-category-data/what-are-the-rules-on-special-category-data/)
- [Jentis Art 9 explained](https://www.jentis.com/blog/what-is-article-9-of-the-gdpr-sensitive-data-explained-and-how-companies-remain-compliant)
- [Sprinto GDPR Art 9](https://sprinto.com/blog/gdpr/article-9/)
- [RGPD.com Art 9](https://rgpd.com/gdpr/chapter-2-principles/article-9-processing-of-special-categories-of-personal-data/)
- [Legiscope GDPR Art 9](https://www.legiscope.com/blog/gdpr-article-9-special-categories.html)
- [Adequacy health data GDPR](https://www.adequacy.app/en/blog/health-data-gdpr-compliance)
- [WatchDog GDPR Art 9 compliance](https://watchdogsecurity.io/gdpr/special-category-data-authorization)
- [Multilaw RO data protection](https://multilaw.com/Multilaw/Multilaw/Data_Protection_Laws_Guide/DataProtection_Guide_Romania.aspx)
- [VLO Law Firm RO data protection](https://vlolawfirm.com/tpost/romania-data-protection)
- [Lawgratis privacy law RO](https://www.lawgratis.com/blog-detail/privacy-law-at-romania)
- [DLA Piper RO data protection](https://www.dlapiperdataprotection.com/index.html?t=about&c=RO)
- [ANSPDCP homepage](https://www.dataprotection.ro/index.jsp?page=home&lang=en)
- [Law 190/2018 RO text](https://www.dataprotection.ro/servlet/ViewDocument?id=1685)
- [DLA Piper RO registration](https://www.dlapiperdataprotection.com/index.html?t=registration&c=RO)
- [Lex Mundi RO data privacy](https://www.lexmundi.com/guides/data-privacy-guide/jurisdictions/europe/romania/)
- [Mondaq RO data privacy](https://www.mondaq.com/privacy/1582894/data-privacy-comparative-guide)
- [Treegarden GDPR recruitment](https://treegarden.io/blog/gdpr-recruitment-complete-guide/)
- [Global Legal Post RO data protection](https://www.globallegalpost.com/lawoverborders/data-protection-law-guide-1072382791/romania-245249880)
- [CMS RO data protection](https://cms.law/en/int/expert-guides/cms-expert-guide-to-data-protection-and-cyber-security-laws/romania)
- [BCH Law GDPR Romania](https://www.bchlaw.eu/news/measures-to-implement-the-general-data-protection-regulation-in-romania/)
- [Linklaters Romania](https://www.linklaters.com/en/insights/data-protected/data-protected---romania)
- [Scribd Law 190 PE 2018](https://www.scribd.com/document/397716032/Law-no-190-pe-2018)
- [Lexology GDPR RO](https://www.lexology.com/library/detail.aspx?g=88c5cd9c-505a-4873-ba23-8f83dfe0ce4e)
- [Caseguard GDPR RO citizens](https://caseguard.com/articles/new-data-privacy-rights-for-romanian-citizens-gdpr/)
- [Buzescu RO data protection](https://www.buzescu.com/romanian-data-protection-laws/)
- [Microsoft DSR Office 365](https://learn.microsoft.com/en-us/compliance/regulatory/gdpr-dsr-office365)
- [Microsoft GDPR DSR](https://learn.microsoft.com/en-us/compliance/regulatory/gdpr-data-subject-requests)
- [EDPB individual rights](https://www.edpb.europa.eu/sme-data-protection-guide/respect-individuals-rights_en)
- [DataGrail DSAR](https://www.datagrail.io/glossary/data-subject-access-request-dsar/)
- [mParticle DSR](https://docs.mparticle.com/guides/data-subject-requests/)
- [Derrick GDPR rights 2026](https://derrick-app.com/en/gdpr-data-subject-rights-2/)
- [GDPR-info Art 15](https://gdpr-info.eu/art-15-gdpr/)
- [Cookiebot 8 GDPR rights](https://www.cookiebot.com/en/gdpr-data-subject-rights/)
- [Legalnodes DSAR](https://www.legalnodes.com/article/how-to-handle-data-access-requests-under-gdpr)
- [Cookieinformation DSR guide](https://cookieinformation.com/blog/ultimate-short-guide-to-data-subject-request-gdpr/)
- [TermsFeed GDPR log data](https://www.termsfeed.com/blog/gdpr-log-data/)
- [Last9 GDPR log management](https://last9.io/blog/gdpr-log-management/)
- [ComplyDog GDPR minimization](https://complydog.com/blog/gdpr-data-minimization-implementation-guide)
- [Legiscope GDPR minimization](https://www.legiscope.com/blog/data-minimization-gdpr.html)
- [Konfirmity GDPR logging 2026](https://www.konfirmity.com/blog/gdpr-logging-and-monitoring)
- [TermsFeed CPRA/GDPR minimization](https://www.termsfeed.com/blog/data-minimization-cpra-gdpr/)
- [Auth0 GDPR minimization](https://auth0.com/docs/secure/data-privacy-and-compliance/gdpr/gdpr-data-minimization)
- [GDPR-info Art 5](https://gdpr-info.eu/art-5-gdpr/)
- [2B-Advice minimization](https://www.2b-advice.com/en/blog/what-is-data-minimization/)
- [Algolia GDPR Art 5](https://gdpr.algolia.com/gdpr-article-5)
- [ICO app developers](https://ico.org.uk/about-the-ico/media-centre/news-and-blogs/2024/02/ico-urges-all-app-developers-to-prioritise-privacy/)
- [ICO health worker data](https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/employment/information-about-workers-health/data-protection-and-workers-health-information/)
- [Harper James GDPR apps](https://harperjames.co.uk/article/data-privacy-for-app-developers/)
- [ICO health info access](https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/individual-rights/right-of-access/health-information/)
- [ICO transparency health social](https://ico.org.uk/about-the-ico/media-centre/news-and-blogs/2024/04/ico-publishes-guidance-to-improve-transparency-in-health-and-social-care/)
- [ICLG UK Digital Health](https://iclg.com/practice-areas/digital-health-laws-and-regulations/united-kingdom)
- [ICO health monitoring](https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/employment/information-about-workers-health/what-if-we-carry-out-health-monitoring/)
- [CMS UK digital health](https://cms.law/en/int/expert-guides/cms-expert-guide-to-digital-health-apps-and-telemedicine/united-kingdom)
- [Glance mobile health regulations](https://thisisglance.com/learning-centre/what-regulations-must-your-mobile-health-app-comply-with)

### §5 EU AI Act

- [Art 5 prohibited](https://artificialintelligenceact.eu/article/5/)
- [ICTHealth EU AI Act health](https://www.icthealth.org/news/first-eu-ai-act-guidelines-when-is-health-ai-prohibited)
- [EC C(2025) 5052 final](https://ai-act-service-desk.ec.europa.eu/sites/default/files/2025-08/guidelines_on_prohibited_artificial_intelligence_practices_established_by_regulation_eu_20241689_ai_act_english_ied3r5nwo50xggpcfmwckm3nuc_112367-1.PDF)
- [EU shaping digital future AI](https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai)
- [EUobserver emotion recognition loopholes](https://euobserver.com/4547/these-are-the-major-loopholes-on-emotion-recognition-in-eu-artificial-intelligence-act/)
- [FPF red lines emotion recognition](https://fpf.org/blog/red-lines-under-eu-ai-act-unpacking-the-prohibition-of-emotion-recognition-in-the-workplace-and-education-institutions/)
- [Bird & Bird AI workplace](https://www.twobirds.com/en/insights/2025/global/ai-and-the-workplace-navigating-prohibited-ai-practices-in-the-eu)
- [EC service desk Art 5](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-5)
- [PhilArchive prohibited healthcare](https://philarchive.org/archive/VANPAP-39)
- [Eucrim prohibited practices guidelines](https://eucrim.eu/news/guidelines-on-prohibited-ai-practices/)
- [Art 13 transparency](https://artificialintelligenceact.eu/article/13/)
- [EC service desk Art 13](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-13)
- [Art 14 human oversight](https://artificialintelligenceact.eu/article/14/)
- [EC service desk Art 14](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-14)
- [AI Act Explorer](https://artificialintelligenceact.eu/ai-act-explorer/)
- [Euaiact Art 13](https://www.euaiact.com/article/13)
- [DLA Piper human oversight EU](https://intelligence.dlapiper.com/artificial-intelligence/?t=11-human-oversight&c=EU)
- [Chapter III high-risk AI](https://artificialintelligenceact.eu/chapter/3/)
- [Euaiact Art 14](https://www.euaiact.com/article/14)
- [Artificial-intelligence-act final text](https://www.artificial-intelligence-act.com/Artificial_Intelligence_Act_Articles_(Final_Text).html)
- [Art 16 provider obligations](https://artificialintelligenceact.eu/article/16/)
- [Euaiact Art 16](https://www.euaiact.com/article/16)
- [EC service desk Art 16](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-16)
- [High level summary AI Act](https://artificialintelligenceact.eu/high-level-summary/)
- [Legalnodes 2026 EU AI Act](https://www.legalnodes.com/article/eu-ai-act-2026-updates-compliance-requirements-and-business-risks)
- [Dataiku high risk requirements](https://www.dataiku.com/stories/blog/eu-ai-act-high-risk-requirements)
- [Art 6 classification rules](https://artificialintelligenceact.eu/article/6/)
- [Practical AI Act model cards](https://practical-ai-act.eu/latest/engineering-practice/model-cards/)
- [Linux Foundation EU AI Act](https://linuxfoundation.eu/newsroom/ai-act-explainer)
- [Art 4 AI literacy](https://artificialintelligenceact.eu/article/4/)
- [EC AI literacy FAQ](https://digital-strategy.ec.europa.eu/en/faqs/ai-literacy-questions-answers)
- [Delbion Art 4 2026](https://www.delbion.com/en/insights/mandatory-ai-training-eu-ai-act/)
- [Latham Watkins EU AI Act](https://www.lw.com/en/insights/upcoming-eu-ai-act-obligations-mandatory-training-and-prohibited-practices)
- [AI talent skills EU](https://digital-strategy.ec.europa.eu/en/policies/ai-talent-skills-and-literacy)
- [Iternal AI Art 4 2026](https://iternal.ai/eu-ai-act-literacy)
- [Mayer Brown Feb 2025 ban](https://www.mayerbrown.com/en/insights/publications/2025/01/eu-ai-act-ban-on-certain-ai-practices-and-requirements-for-ai-literacy-come-into-effect)
- [The Science Talk Art 4 research](https://thesciencetalk.com/news/eu-ai-act-article-4-ai-literacy-research-institutes/)
- [Compliquest Art 4 guide](https://www.compliquest.com/en/blog/ai-literacy-eu-ai-act-article-4-guide)
- [Travers Smith Art 4](https://www.traverssmith.com/knowledge/knowledge-container/the-eu-ai-acts-ai-literacy-requirement-key-considerations/)
- [Art 12 record-keeping](https://artificialintelligenceact.eu/article/12/)
- [EC service desk Art 12](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-12)
- [Truescreen Art 12](https://truescreen.io/insights/ai-act-record-keeping-requirements/)
- [Firetail Art 12 logging](https://www.firetail.ai/blog/article-12-and-the-logging-mandate-what-the-eu-ai-act-actually-requires)
- [Security Boulevard Art 12](https://securityboulevard.com/2026/04/article-12-and-the-logging-mandate-what-the-eu-ai-act-actually-requires-firetail-blog/)
- [ISMS Online ISO 42001 Art 12](https://www.isms.online/iso-42001/eu-ai-act/article-12/)
- [Knowlee AI audit trail](https://www.knowlee.ai/blog/ai-audit-trail-implementation-guide)
- [Github langchain Art 12](https://github.com/langchain-ai/langchain/issues/35357)
- [Certified Data Art 12](https://certifieddata.io/eu-ai-act/article-12-record-keeping)
- [Practical AI Act record-keeping](https://practical-ai-act.eu/latest/conformity/record-keeping/)
- [IntuitionLabs MDR AI Act](https://intuitionlabs.ai/articles/ai-medical-device-compliance-eu-mdr-ai-act)
- [Quickbird medical AI Act](https://quickbirdmedical.com/en/ai-act-medical-devices-mdr/)
- [MDCG 2025-6 PDF](https://health.ec.europa.eu/document/download/b78a17d7-e3cd-4943-851d-e02a2f22bbb4_en?filename=mdcg_2025-6_en.pdf)
- [IBA digital therapeutics AI](https://www.ibanet.org/digital-therapeutics-ai-health-apps-regulatory-intellectual-property)
- [Reed Smith AI Act medical](https://www.reedsmith.com/our-insights/blogs/viewpoints/102kq35/the-eu-ai-act-and-medical-devices-navigating-high-risk-compliance/)
- [White Case smart medical](https://www.whitecase.com/insight-alert/new-eu-responsibility-and-liability-landscape-smart-medical-devices-global-context)
- [Johner-Institute IEC 62304](https://blog.johner-institute.com/iec-62304-medical-software/ai-act-eu-ai-regulation/)
- [Gleiss Lutz MDR IVDR](https://www.gleisslutz.com/en/know-how/radical-simplification-mdr-and-ivdr-and-broad-inapplicability-ai-act-medical-devices-and-ivds)
- [Morgan Lewis MDR simplification](https://www.morganlewis.com/blogs/asprescribed/2025/12/european-commission-issues-proposal-to-simplify-medical-devices-regulations)
- [Tandem Health EU MDR GDPR AI](https://tandemhealth.ai/resources/knowledge/eu-healthcare-ai-regulations-mdr-gdpr-ai-act)

### §6 Voice (KMP + RO)

- [Notely-Voice GitHub](https://github.com/Notely-Voice/NotelyVoice)
- [Ibrahimng notelyvoice GitHub](https://github.com/Ibrahimng/notelyvoice)
- [KMP samples GitHub topics](https://github.com/topics/kotlin-multiplatform-sample?l=c%2B%2B)
- [WhisperCore Android GitHub](https://github.com/EberronBruce/WhisperCore_Android)
- [WhisperKit Apple Silicon](https://github.com/argmaxinc/argmax-oss-swift)
- [Whisper Android Vilas GitHub](https://github.com/vilassn/whisper_android)
- [Andresand voice to text Compose](https://andresand.medium.com/voice-to-text-kotlin-android-jetpack-compose-3e4419dcbac3)
- [TextToSpeechKt GitHub](https://github.com/Marc-JB/TextToSpeechKt)
- [DeepWiki Notely Voice](https://deepwiki.com/Notely-Voice/NotelyVoice)
- [LinkedIn KMP TTS](https://www.linkedin.com/posts/coding-meet_kotlinmultiplatform-composemultiplatform-activity-7363507709201072129-kzx7)
- [MDPI Modern RO speech rec](https://www.mdpi.com/2076-3417/16/4/1928)
- [Arxiv 2511.03361 RO ASR PDF](https://arxiv.org/pdf/2511.03361)
- [Arxiv 2511.03361 abstract](https://arxiv.org/abs/2511.03361)
- [RobinASR RACAI GitHub](https://github.com/racai-ai/RobinASR)
- [ResearchGate ProtoLOGOS](https://www.researchgate.net/publication/224559095_ProtoLOGOS_system_for_Romanian_language_automatic_speech_recognition_and_understanding_ASRU)
- [IEEE ASR Romanian](https://ieeexplore.ieee.org/document/4381101/)
- [ElevenLabs Romanian S2T](https://elevenlabs.io/speech-to-text/romanian)
- [ACL RSC Romanian Corpus](https://aclanthology.org/2020.lrec-1.814/)
- [HuggingFace Romanian Wav2Vec2](https://huggingface.co/gigant/romanian-wav2vec2)
- [ResearchGate RO ASR knowledge](https://www.researchgate.net/publication/221787430_Knowledge_Resources_in_Automatic_Speech_Recognition_and_Understanding_for_Romanian_Language)
- [NutriScan voice calorie counter](https://nutriscan.app/apps/voice-activated-calorie-counter)
- [HeyPeony voice calorie apps 2025](https://www.heypeony.com/blog/voice-calorie-logging-apps)
- [Talk-to-Track App Store](https://apps.apple.com/us/app/talk-to-track-diet-and-fitness/id965902030)
- [SpeakMeal](https://speakmeal.framer.ai/)
- [Quantified Self speech logging](https://forum.quantifiedself.com/t/speech-based-food-logging/3775)
- [UCI Future Health Food logger](https://futurehealth.uci.edu/resources/food-logger/)
- [MIT voice calorie 2016](https://news.mit.edu/2016/voice-controlled-calorie-counter-0324)
- [Carb Manager voice logging](https://help.carbmanager.com/docs/log-foods-by-speaking)
- [Jotform AI calorie trackers 2026](https://www.jotform.com/ai/best-ai-calorie-tracker/)
- [FoodBuddy voice activated](https://foodbuddy.my/blog/voice-activated-calorie-counting-a-user-friendly-approach)

### §7 Receipt OCR

- [Open Collective OCR issue 6865](https://github.com/opencollective/opencollective/issues/6865)
- [LlamaIndex receipt OCR](https://www.llamaindex.ai/services/receipt-scanner-ocr)
- [Medium API4AI receipt OCR](https://medium.com/@API4AI/receipt-ocr-mastery-turning-paper-slips-into-real-time-retail-data-8e0c0878e6d0)
- [Taggun receipt OCR API](https://www.taggun.io/)
- [Tailride receipt scanning](https://tailride.so/blog/receipt-scanning-ocr)
- [MMC receipt OCR](https://www.mmcreceipt.com/blog/ocr-an-important-tool-for-receipt-scanning/)
- [Brex OCR invoice](https://www.brex.com/spend-trends/cash-flow-management/ocr-invoice-processing)
- [Arxiv automated invoice LLM OCR](https://arxiv.org/pdf/2511.05547)
- [Docsumo audit trails](https://www.docsumo.com/blog/audit-trails)
- [Veryfi receipts OCR](https://www.veryfi.com/receipt-ocr-api/)
- [USDA FoodData Central API guide](https://fdc.nal.usda.gov/api-guide/)
- [USDA FDC API OpenAPI](https://fdc.nal.usda.gov/api-spec/fdc_api.html)
- [Littlebunch FDC API GitHub](https://github.com/littlebunch/fdc-api)
- [USDA FDC API signup](https://fdc.nal.usda.gov/api-key-signup/)
- [Postman FDC docs](https://www.postman.com/api-evangelist/agricultural-research-service-ars/documentation/nex4lq6/food-data-central-api)
- [Spike API top nutrition APIs](https://www.spikeapi.com/blog/top-nutrition-apis-for-developers-2026)
- [USDA FoodData Central](https://fdc.nal.usda.gov/)
- [Apipheny FDC sheets](https://apipheny.io/fooddata-central-api/)
- [USDA Nutrients Public APIs](https://publicapis.io/usda-nutrients-api)
- [Apify USDA scraper](https://apify.com/compute-edge/usda-fooddata-scraper/api/openapi)
- [ANSES CIQUAL EN](http://www.anses.fr/en/content/anses-ciqual-food-composition-table)
- [CIQUAL MCP GitHub](https://github.com/zzgael/ciqual-mcp)
- [European Data Portal CIQUAL](https://data.europa.eu/data/datasets/5369a15fa3a729239d2065b7?locale=en)
- [ANSES CIQUAL major update](https://www.anses.fr/en/content/major-update-ciqual-table-reference-tool-nutritional-composition-foods)
- [CIQUAL Table API kozlown GitHub](https://github.com/kozlown/ciqual-table-api)
- [ANSES CIQUAL enhanced version](https://www.anses.fr/en/content/new-enhanced-and-more-representative-version-ciqual-table)
- [ANSES CIQUAL supplements](https://www.anses.fr/en/content/table-nutritional-composition-foods-ciqual-anses-supplements-its-data-and-publishes-its)
- [CIQUAL 2017 docENG PDF](https://ciqual.anses.fr/cms/sites/default/files/inline-files/TableCiqual2017_Access_docENG.pdf)
- [Sites ANSES major update](https://sites.anses.fr/en/content/major-update-ciqual-table-reference-tool-nutritional-composition-foods)
- [MCPMarket CIQUAL ES](https://mcpmarket.com/es/server/ciqual)
- [Open Food Facts RO ingredients](https://ro-en.openfoodfacts.org/facets/ingredients)
- [Open Food Facts data API](https://world.openfoodfacts.org/data)
- [Open Food Facts Romania world](https://world.openfoodfacts.org/facets/countries/romania/ingredients)
- [Open Food Facts Wikipedia](https://en.wikipedia.org/wiki/Open_Food_Facts)
- [Open Food Facts Romania portal](https://ro-en.openfoodfacts.org/)
- [Open Food Facts API tutorial](https://openfoodfacts.github.io/openfoodfacts-server/api/tutorial-off-api/)
- [Open Food Facts EU citizen science](https://citizenscience.eu/project/430)
- [Open Food Facts App Store](https://apps.apple.com/us/app/open-food-facts-product-scan/id588797948)
- [Open Food Facts GitHub](https://github.com/openfoodfacts)
- [Open Food Facts world](https://world.openfoodfacts.org/)

### §8 Dark patterns + ED safeguards

- [MyNetDiary ED policy](https://www.mynetdiary.com/eating-disorders-food-tracking.html)
- [NEDA professional guidelines](https://www.nationaleatingdisorders.org/professionals-guidelines/)
- [Duke trouble with tracking](https://psychiatry.duke.edu/blog/trouble-tracking)
- [PMC diet fitness apps qualitative](https://pmc.ncbi.nlm.nih.gov/articles/PMC8485346/)
- [ScienceDirect link review systematic](https://www.sciencedirect.com/science/article/pii/S174014452400158X)
- [ResearchGate calorie counting + ED](https://www.researchgate.net/publication/313537618_Calorie_counting_and_fitness_tracking_technology_Associations_with_eating_disorder_symptomatology)
- [Center4Research fitness apps ED](https://www.center4research.org/fitness-tracking-apps-eating-disorders/)
- [The Swaddle health tracking pipeline](https://www.theswaddle.com/health-tracking-apps-provide-a-worrying-pipeline-to-eating-disorders-better-tech-design-can-fix-that)
- [NPR ED chatbot dieting advice](https://www.npr.org/sections/health-shots/2023/06/08/1180838096/an-eating-disorders-chatbot-offered-dieting-advice-raising-fears-about-ai-in-hea)
- [NEDA additional support](https://www.nationaleatingdisorders.org/additional-support-options/)
- [Therapist.com calorie counting apps](https://therapist.com/disorders/eating-disorders/calorie-counting-apps/)
- [ScienceDirect calorie app motives](https://www.sciencedirect.com/science/article/abs/pii/S1471015321000957)
- [PubMed Linde fitness tracking ED](https://pubmed.ncbi.nlm.nih.gov/28214452/)
- [UMA Technology calorie apps harm](https://umatechnology.org/how-calorie-counting-apps-are-harming-your-health-and-what-to-do-about-it/)
- [Break Binge Eating stop calorie](https://breakbingeeating.com/stop-calorie-counting/)
- [PubMed Levinson calorie app](https://pubmed.ncbi.nlm.nih.gov/34543856/)
- [Restore Mental Health calorie obsession](https://restore-mentalhealth.com/calorie-counting-obsession/)
- [PMC MFP calorie tracker ED](https://pmc.ncbi.nlm.nih.gov/articles/PMC5700836/)
- [US News what-the-hell health](https://health.usnews.com/wellness/mind/articles/2017-11-15/avoiding-the-what-the-hell-health-effect)
- [Tutor2u boundary model](https://www.tutor2u.net/psychology/topics/boundary-model)
- [Wikipedia counterregulatory eating](https://en.wikipedia.org/wiki/Counterregulatory_eating)
- [Brainscape what-the-hell effect](https://www.brainscape.com/academy/what-the-hell-effect/)
- [Psychstory disinhibition obesity](https://www.psychstory.co.uk/eating-behaviour/disinhibition-and-the-boundary-model-of-obesity)
- [PMC overeating restrained unrestrained](https://pmc.ncbi.nlm.nih.gov/articles/PMC7096476/)
- [Nutrola why can't stick diet](https://nutrola.app/en/blog/why-cant-i-stick-to-a-diet)
- [MyHealthSciences podcast what-the-heck](https://podcast.myhealthsciences.com/1580440/8926814)
- [MyHealthSciences blog what-the-heck](https://myhealthsciences.com/blog-posts/cognition/what-the-heck-effect-why-you-binge)
- [Polivy Herman 1986 dieting](https://psycnet.apa.org/record/1986-01626-001)
- [News-Medical orthorexia](https://www.news-medical.net/health/Diagnostic-Criteria-for-Orthorexia.aspx)
- [Wikipedia orthorexia](https://en.wikipedia.org/wiki/Orthorexia_nervosa)
- [Alliance ED orthorexia](https://www.allianceforeatingdisorders.com/how-to-better-understand-the-confusing-counterintuitive-world-of-orthorexia/)
- [NEDA orthorexia](https://www.nationaleatingdisorders.org/orthorexia/)
- [WebMD orthorexia](https://www.webmd.com/mental-health/eating-disorders/what-is-orthorexia)
- [Center for Discovery orthorexia](https://centerfordiscovery.com/conditions/orthorexia/)
- [DSM-5-TR criteria](https://insideoutinstitute.org.au/resource-library/dsm-5-diagnostic-criteria-for-eating-disorders)
- [PMC orthorexia obsession healthy](https://pmc.ncbi.nlm.nih.gov/articles/PMC6370446/)
- [ACUTE orthorexia clean](https://www.acute.org/resources/orthorexia-eating-disorders)
- [PMC consensus orthorexia](https://pmc.ncbi.nlm.nih.gov/articles/PMC9803763/)
- [AED 2021 4th ed PDF](https://higherlogicdownload.s3.amazonaws.com/AEDWEB/27a3b69a-8aae-45b2-a04c-2a078d02145d/UploadedImages/Publications_Slider/2120_AED_Medical_Care_4th_Ed_FINAL.pdf)
- [AED resources](https://www.aedweb.org/resources/about-eating-disorders)
- [AED 2016 3rd ed PDF](https://www.massgeneral.org/assets/mgh/pdf/psychiatry/eating-disorders-medical-guide-aed-report.pdf)
- [AED standards](https://www.aedweb.org/resources/publications/medical-care-standards)
- [AED publications](https://www.aedweb.org/resources/publications)
- [AED English PDF](https://higherlogicdownload.s3.amazonaws.com/AEDWEB/05656ea0-59c9-4dd4-b832-07a3fea58f4c/UploadedImages/AED_Medical_Care_Guidelines_English_04_03_18_a.pdf)
- [Newswise AED](https://www.newswise.com/institutions/newsroom/Academy-for-Eating-Disorders-(AED)-5492)
- [AED learn resources](https://www.aedweb.org/learn/resources)
- [NEDC AED guide 4th ed](https://nedc.com.au/eating-disorder-resources/find-resources/show/aed-eating-disorders-a-guide-to-medical-care-4th-ed)
- [Newswise AED weight-related](https://www.newswise.com/articles/the-academy-for-eating-disorders-releases-a-statement-on-the-recent-american-academy-of-pediatrics-clinical-practice-guideline-for-weight-related-care-first-do-no-harm)
- [Newsweek dark side fitness](https://www.newsweek.com/fitness-apps-study-says-they-can-do-more-harm-than-good-10913928)
- [UCL emotional strain apps](https://www.ucl.ac.uk/news/2025/oct/emotional-strain-fitness-and-calorie-counting-apps-revealed)
- [Bioengineer emotional toll](https://bioengineer.org/emotional-toll-of-fitness-and-calorie-counting-apps-uncovered/)
- [BoxLife shame study](https://boxlifemagazine.com/fitness-tracker-shame-emotional-toll/)
- [EurekAlert emotional strain](https://www.eurekalert.org/news-releases/1102616)
- [Wikipedia dark pattern](https://en.wikipedia.org/wiki/Dark_pattern)
- [Nursing in Practice apps wellbeing](https://www.nursinginpractice.com/clinical/womens-health/fitness-and-calorie-counting-apps-can-impact-wellbeing-study-suggests/)
- [VeraSafe dark patterns detect](https://verasafe.com/blog/dark-patterns-how-to-detect-and-avoid-them/)
- [Medical Xpress emotional strain](https://medicalxpress.com/news/2025-10-emotional-strain-calorie-apps-revealed.html)
- [ScienceDirect whatieatinaday TikTok](https://www.sciencedirect.com/science/article/abs/pii/S1740144525000932)
- [The Brink gamified dark psych](https://www.thebrink.me/gamified-life-dark-psychology-app-addiction/)
- [PMC gamification behavior change](https://pmc.ncbi.nlm.nih.gov/articles/PMC10998180/)
- [Yu-Kai Chou streak burnout](https://yukaichou.com/gamification-analysis/streak-design-gamification-motivation-burnout/)
- [Productived streaks gamification](https://www.productived.net/articles/more-thoughts-on-streaks-and-gamification-of-habits)
- [IADB streaking success PDF](https://publications.iadb.org/publications/english/document/Streaking-to-Success-The-Effects-of-Highlighting-Streaks-on-Student-Effort-and-Achievement.pdf)
- [Moore Momentum habit streaks](https://mooremomentum.com/blog/why-most-habit-streaks-fail-and-how-to-build-ones-that-dont/)
- [Decision Lab streak creep](https://thedecisionlab.com/insights/consumer-insights/streak-creep-the-perils-of-too-much-gamification)
- [Cohorty gamification data](https://www.cohorty.app/blog/gamification-in-habit-tracking-does-it-work-research-real-user-data)
- [JCR on or off track](https://academic.oup.com/jcr/article/49/6/1095/6623414)
- [Growth Engineering streaks](https://www.growthengineering.co.uk/gamification-streaks/)
- [Aromedy bigorexia](https://www.aromedy.com/post/bigorexia-is-making-a-comeback-and-young-men-are-at-the-highest-risk)
- [Healthline bigorexia](https://www.healthline.com/health/bigorexia)
- [PubMed muscle dysmorphia screening](https://pubmed.ncbi.nlm.nih.gov/29271781/)
- [Within Health bigorexia](https://withinhealth.com/learn/articles/what-is-bigorexia)
- [PubMed bigorexia bodybuilding](https://pubmed.ncbi.nlm.nih.gov/18759381/)
- [Mens Reproductive Health bigorexia](https://mensreproductivehealth.com/muscle-dysmorphia-bigorexia-in-adolescents-and-young-men-diagnosis-risks-and-fertility-impact/)
- [Sage Clinics bigorexia](https://sage-clinics.com/what-is-bigorexia/)
- [US Pharmacist bigorexia review](https://www.uspharmacist.com/article/bigorexia-nervosa-review)
- [NEDA muscle dysmorphia](https://www.nationaleatingdisorders.org/muscle-dysmorphia/)
- [Aster Springs muscle dysmorphia](https://astersprings.com/blog/muscle-dysmorphia-signs)
- [Symptoms of Living calorie apps](https://symptomsofliving.com/blog/the-app-that-is-fuelling-your-eating-disorder/)
- [Center for Discovery activity trackers](https://centerfordiscovery.com/blog/activity-trackers-eating-disorder-recovery/)
- [Choosing Therapy ED recovery apps](https://www.choosingtherapy.com/best-eating-disorder-recovery-apps/)
- [Eating Disorder Hope tracking](https://www.eatingdisorderhope.com/blog/eating-disorder-recovery-apps)
- [PMC mobile food tracking longitudinal](https://pmc.ncbi.nlm.nih.gov/articles/PMC11556259/)
- [Healthline body image ED link](https://www.healthline.com/health/body-image-and-eating-disorders)
- [First Steps ED body image](https://firststepsed.co.uk/resources/body-image-and-perception/)
- [Self-defining memories Duarte](http://www.selfdefiningmemories.com/2016Duarte.pdf)
- [NEDA body image ED](https://www.nationaleatingdisorders.org/body-image-and-eating-disorders/)
- [Healing Shame body image ED](https://healingshame.com/shame-and-the-body)
- [Springer self-defining body shame](https://link.springer.com/article/10.1007/s11199-016-0728-5)
- [Christy Harrison Food Psych #256](https://christyharrison.com/foodpsych/8/how-trauma-and-shame-affect-our-relationships-with-food-and-the-body-with-judith-matz-amy-pershing)
- [The Mighty body shame ED PTSD](https://themighty.com/topic/post-traumatic-stress-disorder-ptsd/body-shame-eating-disorder-connected-to-trauma-post-traumatic-stress-disorder)
- [ScienceDirect self-compassion body shame](https://www.sciencedirect.com/science/article/pii/S2666915324001021)

### §9 Failure modes

(Citations interleaved §2 and §8.)

### §10 Cultural eating context (RO)

- [FriendTripToRomania](https://friendtriptoromania.com/romanian-breakfast-lunch-dinner/)
- [The Romanian Cookbook breakfast](https://theromaniancookbook.com/traditional-romanian-breakfast/)
- [Chef's Pencil RO breakfast](https://www.chefspencil.com/typical-romanian-breakfast-foods/)
- [HiNative RO meals](https://hinative.com/questions/1719008)
- [Quora RO timing](https://www.quora.com/What-and-when-do-people-eat-in-Romania)
- [Romania Insider foods drinks](https://www.romania-insider.com/romanian-foods-drinks-march-2019)
- [The Romanian Cookbook dishes](https://theromaniancookbook.com/traditional-romanian-dishes/)
- [WorldTravelChef RO breakfast](https://worldtravelchef.com/romanian-breakfast/)
- [TheTravel RO breakfast](https://www.thetravel.com/what-is-a-typical-romanian-breakfast/)
- [World In My Pocket RO breakfast](https://www.theworldinmypocket.co.uk/the-breakfast-club-the-traditional-romanian-breakfast/)
- [Romania Private Tours Great Lent](https://romaniaprivatetours.com/great-lent-romanian-pre-easter-traditions-and-rituals/)
- [Saint John Suceava fasting rules](https://saintjohnofsuceava.org/resources/orthodox-worship/fasting-rules/)
- [PostIntermitent.ro](https://www.postintermitent.ro/)
- [A-Blast Orthodox student lent](https://www.thea-blast.org/student-life/health/2022/03/28/healthy-food-options-for-students-who-are-fasting/)
- [Romania-Insider tradition-filled December](https://www.romania-insider.com/a-tradition-filled-december-in-romania-orthodox-holidays-caroling-season-pig-slaughtering)
- [Eureka Health Orthodox fasting](https://www.eurekahealth.com/resources/orthodox-fasting-rules-great-lent-explained-en)
- [Holy Orthodox fasting guidelines](https://www.holyorthodox.org/fastingguidelines)
- [Wikipedia Nativity Fast](https://en.wikipedia.org/wiki/Nativity_Fast)
- [PMC Christian Orthodox fasting](https://pmc.ncbi.nlm.nih.gov/articles/PMC10004762/)
- [Cohn Jansen Rostul postului](https://www.cohnandjansen.ro/portfolio/rostul-postului-hhc/)

### §6 Recipe builder + smart scales (continued)

- [MealMaster Recipe Keeper](https://play.google.com/store/apps/details?id=com.tikamori.cookbook&hl=en_US)
- [TechBullion recipe nutrition calc](https://techbullion.com/best-recipe-nutrition-calculator-apps/)
- [Adalo recipe ingredients DB](https://forum.adalo.com/t/how-to-make-sense-of-recipes-ingredients-scales-and-quantities-in-a-database-recipe-nutrition-app/22253)
- [BiteKit AI recipe calculator](https://bitekit.app/tools/ai-recipe-calculator/)
- [CodeTiburon caution hot recipe app](https://codetiburon.com/build-recipe-app/)
- [Perpetio AI recipe app](https://perpet.io/blog/how-to-make-an-ai-powered-cooking-app/)
- [Wondershare recipe calorie calc](https://www.wondershare.com/calorie-tracker/recipe-calorie-calculator.html)
- [Renpho scales collection](https://renpho.com/collections/renpho-scales)
- [Renpho Health App use](https://renpho.com/pages/video-for-use-renpho-health-app)
- [Amazon Renpho Smart Scale](https://www.amazon.com/RENPHO-Bluetooth-Bathroom-Composition-Smartphone/dp/B01N1UX8RW)
- [Renpho Nutrition Scale manual](https://renpho.com/pages/009)
- [Walmart Renpho Bluetooth Smart](https://www.walmart.com/ip/RENPHO-Bluetooth-Smart-Body-Fat-Scale-with-App-High-Precision-13-Key-Health-Metrics-440lbs-White/5169553874)
- [Renpho Health App FAQ](https://renpho.com/pages/faq-renpho-health-app)
- [Walmart Renpho food scale](https://www.walmart.com/ip/RENPHO-Bluetooth-Food-Scale-with-App-Digital-Smart-Kitchen-Scale-Glass-White/770619750)
- [Renpho Health iOS](https://apps.apple.com/us/app/renpho-health/id1543340610)
- [Walmart Renpho 400lbs marble](https://www.walmart.com/ip/RENPHO-Bluetooth-Smart-Scale-for-App-400-lbs-Marble/1274567358)
- [Renpho Smart Nutrition Scale manual SNS01](https://renpho.com/pages/004)

### Other research (food recognition AI 2025 + AI nutrition rec safety + photo recognition mixed dishes)

- [MDPI Deep learning food image review](https://www.mdpi.com/2076-3417/15/14/7626)
- [PMC food recognition volume](https://pmc.ncbi.nlm.nih.gov/articles/PMC8700885/)
- [PMC image-based food groups portions deep](https://pmc.ncbi.nlm.nih.gov/articles/PMC11887021/)
- [Frontiers image-based food monitoring diabetes](https://www.frontiersin.org/journals/nutrition/articles/10.3389/fnut.2025.1501946/full)
- [ResearchGate accuracy categories](https://www.researchgate.net/figure/Accuracy-of-image-recognition-among-different-food-categories_tbl3_346075674)
- [MyCalorieCounter AI portion control](https://mycaloriecounter.app/blog/ai-portion-control-visual-estimation/)
- [Nature DietAI24 multimodal LLM](https://www.nature.com/articles/s43856-025-01159-0)
- [KCalm AI food recognition accuracy](https://www.kcalm.app/blog/ai-food-recognition-accuracy/)
- [PMC image-based food systematic review](https://pmc.ncbi.nlm.nih.gov/articles/PMC9776640/)
- [Springer deep learning food numeric](https://link.springer.com/article/10.1007/s11042-025-20648-x)
- [PMC AI Nutrition Research scoping](https://pmc.ncbi.nlm.nih.gov/articles/PMC11243505/)
- [PMC AI dietary recommendations systematic](https://pmc.ncbi.nlm.nih.gov/articles/PMC12193492/)
- [Cambridge AI dietary intake validity](https://www.cambridge.org/core/journals/british-journal-of-nutrition/article/validity-and-accuracy-of-artificial-intelligencebased-dietary-intake-assessment-methods/6829E54E37F38BB07D09A97D5982C73D)
- [Nature AI nutrition deep generative](https://www.nature.com/articles/s41598-024-65438-x)
- [Frontiers AI personalized nutrition review](https://www.frontiersin.org/journals/nutrition/articles/10.3389/fnut.2025.1636980/full)
- [PMC AI nutrition assessment role](https://pmc.ncbi.nlm.nih.gov/articles/PMC11723148/)
- [PMC AI dietary intake systematic](https://pmc.ncbi.nlm.nih.gov/articles/PMC12229984/)
- [Frontiers AI diet plans underestimate](https://www.frontiersin.org/journals/nutrition/articles/10.3389/fnut.2026.1765598/full)
- [PMC AI personalized nutrition food](https://pmc.ncbi.nlm.nih.gov/articles/PMC12325300/)
- [PMC AI dietetic practice primary care](https://pmc.ncbi.nlm.nih.gov/articles/PMC12655762/)

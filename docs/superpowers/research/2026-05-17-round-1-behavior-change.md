# Round 1 — Behavior Change + Adherence Science for the Dietician Project

*Research compiled 2026-05-17 for Victor's Dietician system. Scope: behavioral science evidence base for personal-and-small-cohort nutrition tooling. Primary lens: what survives month 2.*

---

## TL;DR (3 paragraphs)

Dietary self-monitoring is the single most evidence-backed behavior in this space: more days logged predict more weight change, and the relationship is dose-response in every major review from Burke 2011 forward [1][2][3]. But the literature shows logging adherence collapses fast — about 75% of new digital-health-app users abandon within a week and roughly half within a month [4][5], and even structured programs see daily-logging time fall from ~23 min to ~15 min over six months with one-third of participants dropping monitoring entirely [6]. Effect sizes for the canonical motivational frameworks are smaller than self-monitoring: TPB-based correlations of attitude→intention sit around r=0.54 and implementation-intentions yield Cohen's d≈0.51 for *adding* healthy items vs only 0.29 for *removing* unhealthy ones [7][8][9]. The strongest behavior-change-wheel intervention functions for diet are self-monitoring, action planning, feedback, and goal-setting [10][11].

For an app like Dietician — Victor + ~5 friends, self-built, no business KPI to optimize for engagement — the design imperative inverts the commercial-app playbook. Streak gamification, social comparison, and rigid target tracking have measurable disordered-eating risk: 73% of calorie-tracker users with ED histories blame the app, dietary rigidity correlates with binge frequency and disinhibition (versus *flexible* restraint which correlates with weight-loss maintenance) [12][13][14][15], and streaks weaponize loss aversion in ways that survive long after users stop caring about the underlying goal [16][17]. The autonomy-supportive coaching style (SDT) produces better intrinsic motivation, better quality-of-food choices, and lower bulimic-symptom risk than controlling styles [18][19][20]; motivational-interviewing-style conversation increases positive outcomes by ~55% over standard nutrition counseling [21][22].

The Romanian context matters and is rarely served by global apps: pork-dominant animal-origin diet, hot meal mid-day, Orthodox fasting calendar (~180 days/year of dairy/meat restriction during posts), and feast-anchored ritual food (sarmale at Christmas, cozonac, pască at Easter, mămăligă as carb staple) [23][24][25]. A tracker that defaults American breakfast cereal + lunch salad + light dinner will fight every traditional eating moment Victor's cohort encounters. Three practical conclusions: (1) make self-monitoring as low-friction as humanly possible (photo + voice + barcode + manual, one tap to "same as yesterday") and explicitly support abbreviated-monitoring once trajectory is locked; (2) replace streaks/rigid targets with weekly-window targets, flexible-restraint framing, and ledger-based feedback that survives lapses; (3) build ED-safeguards as primitives, not optional features — minimum calorie floors, refusal to track below thresholds, no public weight-loss leaderboards, no body-comparison features.

---

## 1. Theory of Planned Behavior (Ajzen 1991)

### 1.1 Original model + d-values for nutrition

TPB posits that **behavioral intention** is the proximal cause of behavior, and intention is determined by three constructs: **attitude** toward the behavior, **subjective norm** (perceived social expectation), and **perceived behavioral control** (PBC — sense of how feasible the behavior is) [26]. PBC also has a direct path to behavior independent of intention, capturing real-world constraints (skill, resources, time, access) that intention alone cannot overcome.

For dietary patterns specifically, the canonical meta-analyses (McDermott et al. 2015 covering 35 studies; McEachan et al. 2011) report correlations of:

- Attitude → intention: r = 0.54 (strongest predictor across studies) [7][27]
- PBC → intention: r = 0.42 [7]
- Subjective norm → intention: r = 0.37 [7]
- Intention → behavior: r = 0.45 [7]
- PBC → behavior (direct): r = 0.27 [7]

A different meta-analysis on discrete food choices (Riebl et al. 2015) reported attitude r=0.61, PBC r=0.46, subjective norm r=0.35 — same rank-order, slightly higher magnitudes for specific food behaviors versus broad dietary patterns [28]. In youth populations, attitude→intention r=0.52, intention→behavior r=0.38 [29][30].

These are **medium-to-large** correlations by Cohen's conventions, but cumulative behavior variance explained is typically 30-50% — meaning the model leaves substantial variance to non-cognitive factors (habit, environment, biology).

### 1.2 Recent updates 2020-2026

A 2025 Saudi study expanded TPB to predict fruit/vegetable consumption and found knowledge of WHO recommendations + family meal frequency significantly moderated how positive attitudes translated to intention — a culturally-localized variable absent from the original model [31]. Several 2024 reviews emphasize that **action-planning + coping-planning** explicitly bridge intention→behavior, addressing the well-documented "intention-behavior gap" where ~50% of intenders fail to act [9][32].

A meta-regression in nutrition specifically (Hagger et al., across multiple updates) confirms that adding habit-strength as a moderator improves prediction substantially: when behavior is habitual, intention's predictive power drops, because behavior runs on cues rather than deliberation [33].

### 1.3 Direct implications for Dietician

- **Don't over-invest in attitude formation.** Victor is already convinced of the goal; the model says marginal returns on more nutrition education are small once attitude is already favorable.
- **Subjective norm matters less for Victor (autonomous user), more for the cohort.** Friends/family using the same app create a passive subjective-norm signal that's free if multi-user is already designed in.
- **PBC is the leverage point.** Air-fryer + microwave constraint, RO supermarket SKU coverage, receipt OCR — all directly raise PBC for the meal-tracking behavior. Every friction point reduces it.
- **The intention-behavior gap must be designed for.** Just because the user *intended* 2750 kcal yesterday does not predict today. Implementation-intentions and just-in-time prompts (see §6, §11) are the bridge.

---

## 2. COM-B + Behaviour Change Wheel (Michie et al. 2011)

### 2.1 Model + d-values

COM-B states **B**ehavior = function of **C**apability (physical + psychological), **O**pportunity (physical + social), and **M**otivation (reflective + automatic) [10]. The Behaviour Change Wheel (BCW) layers 9 intervention functions (education, persuasion, incentivisation, coercion, training, restriction, environmental restructuring, modelling, enablement) and 7 policy categories on top, with the BCT Taxonomy v1 providing 93 specific techniques organised in 16 clusters [11][34].

In nutrition interventions, the most-coded BCTs are: information about health consequences, instruction on how to perform a behavior, action planning, feedback on behavior, and social comparison [11]. Goal-setting + self-monitoring + feedback in combination ("control theory" cluster) produce the largest pooled effects in BCT meta-regressions [10][32].

### 2.2 Recent updates 2020-2026

A 2024 systematic review of dietary interventions in pregnancy mapped 31 trials through COM-B + Theoretical Domains Framework and found that effective trials systematically addressed **all three** COM-B components, while ineffective trials usually targeted Motivation only [35][36]. A 2025 BMC Pregnancy/Childbirth review on dietary BCTs in pregnancy found "social support (practical)" and "self-monitoring of behavior" as the most differentiating BCTs between effective and ineffective trials [37].

Behaviour-change-theory-grounded dietary interventions outperform atheoretical ones (BMC Public Health 2020 systematic review): structured BCT use is the single biggest predictor of trial success [38].

### 2.3 Direct implications for Dietician

- **Map every feature to a COM-B target.** Receipt OCR = Physical Capability + Physical Opportunity. Knowledge corpus = Psychological Capability. ntfy reminders = Automatic Motivation. Peer-friend visibility = Social Opportunity. Air-fryer recipes = Physical Capability. Macro target display = Reflective Motivation.
- **Avoid Motivation-only design.** A "you can do it!" notification is the weakest of the 93 BCTs. Pair every motivational nudge with a capability or opportunity scaffold.
- **Pick a defensible BCT roster.** Recommended top-7 for Dietician (drawn from BCTTv1, ranked by evidence in nutrition): self-monitoring of behavior, self-monitoring of outcome, goal-setting (behavior), action planning, feedback on behavior, prompts/cues, problem-solving. Avoid: rewards contingent on behavior (gamification trap), social comparison (ED risk), graded tasks if it implies progression-based punishment.

---

## 3. Self-Determination Theory in nutrition (Deci-Ryan; Pelletier; Teixeira)

### 3.1 Model + d-values

SDT distinguishes **autonomous motivation** (acting because the behavior aligns with personal values/interests) from **controlled motivation** (acting due to external pressure or internal guilt) [18][19]. The theory posits three basic psychological needs — autonomy, competence, relatedness — whose satisfaction drives sustained behavior and well-being.

Pelletier, Dion, Slovinec-D'Angelo & Reid (2004) and follow-up work established that autonomous regulation for eating predicts healthier eating, while controlled regulation predicts more bulimic symptoms, depressive symptoms, lower self-esteem, lower life satisfaction [18][20][39]. Autonomous motivation correlates with food *quality* concerns; controlled motivation correlates with food *quantity* concerns (associated with restrictive/restraint cycles).

A 2020 meta-analysis of SDT-informed health interventions (Ntoumanis et al., *Health Psychology Review*) found significant effects on motivation, health behavior, physical and psychological health outcomes, with autonomy-supportive interventions producing larger effects than control conditions [40][41]. Gillison et al. 2019 meta-analysis of motivational techniques in health behavior change supports SDT predictions specifically for diet, finding autonomy-support BCTs produce stronger and more durable change than controlling techniques [42].

### 3.2 Recent updates 2020-2026

A 2020 South African township study tested an SDT model of healthy eating in low-resource settings and confirmed the autonomy/competence/relatedness pathway transfers across cultures [43]. The "ecological eating" extension by Vaquero-Solís et al. (2022) showed environmental motivation operates via the same SDT pathways as eating regulation, suggesting an Orthodox-fasting/seasonal-eating angle for RO users [44].

### 3.3 Direct implications for Dietician

- **Default language style: autonomy-supportive, not controlling.** "Today's protein target is 137g. Here are options based on what you've logged this week" beats "You need to hit 137g". The diff in intrinsic-motivation maintenance is well-documented.
- **Build for competence.** Knowledge corpus + per-food micronutrient breakdown + receipt-parsed history all reinforce "I understand what I'm eating" — competence need.
- **Relatedness, not comparison.** A shared ledger with friends ≠ a leaderboard. Visible cohort progress ("Andrei hit his protein 4/5 days this week") satisfies relatedness; "Andrei is 200kcal below his target — see leaderboard" weaponizes comparison.
- **Avoid controlling micro-copy.** No "you must", "you should", "you failed", "don't eat". Use "consider", "options are", "if you'd like to" — soft autonomy markers.

---

## 4. Fogg Behavior Model — B=MAP for food habits

### 4.1 Model

Behavior occurs when **M**otivation × **A**bility × **P**rompt converge above an action threshold [45][46]. Low-ability behaviors need higher motivation OR prompt strength to fire. High-ability behaviors fire with weak prompts. Motivation is treated as unreliable (declines over time, fluctuates with mood/context), so durable behavior change requires raising ability and improving prompt-targeting rather than maximizing motivation.

Tiny-Habits methodology: anchor new behaviors to existing reliable routines (post-toothbrush, post-coffee), make the initial behavior so small that motivation isn't needed, celebrate completion to encode neurological reward [47][48].

### 4.2 Evidence base

A 2025 scoping review of FBM in public-health BCIs (PMC12522219) identified diabetes self-management as the strongest evidence cluster for FBM-informed dietary intervention; structured FBM application (motivation framing + ability enhancement + prompt design) outperformed unstructured BCIs [49]. The 2025 "Micro-Health Interventions" arxiv study tested 1-minute health behaviors as habit gateways and reported higher persistence than longer-form behaviors at 30-day follow-up [50].

### 4.3 Direct implications for Dietician

- **Anchor logging to existing habits.** "After you sit down to eat" — not "at 7pm". Receipt photos right at the cashier (existing routine) > meal photos before eating (new routine).
- **Tiny-habit version of logging.** Smallest viable log = "I ate" (no food, no quantity). Auto-prompt enriches to "what?" → "how much?". Each step requires user energy only if motivation/prompt is there.
- **Don't ask for motivation.** Designing for low-motivation days (post-finals burnout, hangover, stress) is the design test. If the app fails at M=2/10, it fails. Every feature should clear M=2/10 ability bar.
- **Celebrate completion cheaply.** A subtle haptic + soft "logged" confirmation > confetti animation. Match Fogg's "celebration" with restrained UX — anti-streak.

---

## 5. Motivational Interviewing (Miller & Rollnick) — for LLM-tutored conversations

### 5.1 Model

MI is a directive but client-centered counseling style aimed at resolving ambivalence and eliciting **change talk** (client-generated arguments for change) over **sustain talk** (client-generated arguments for status quo) [21][51]. Core skills are **OARS**: Open-ended questions, Affirmations, Reflections, Summaries. The "spirit" of MI is partnership + acceptance + compassion + evocation.

### 5.2 Evidence base

Lundahl et al. 2013 meta-analysis: MI-focused interventions have 55% higher chance of positive outcome vs standard treatment across health behaviors [21][52]. VanWormer & Boucher 2004 review found MI reduced energy from fat, reduced sodium, increased fruit/vegetable consumption [22]. Armstrong et al. meta-analysis of 11 RCTs confirmed MI's efficacy for weight-concern counseling versus controls [22][53]. A 2024 PMC review documented digital-MI applications including SMS, chatbot, and conversational-AI delivery with effect sizes comparable to in-person MI for adherence outcomes [54].

### 5.3 Direct implications for Dietician (especially LLM-tutored loops)

- **LLM coach prompt should bake in OARS.** Default system prompt: ask open questions, reflect what the user said, affirm specific effort (not generic praise), summarize at session end. *Avoid*: closed yes/no questions, telling, fixing.
- **Elicit change talk.** When user logs a slip, the right LLM response is not "try better tomorrow". It's "what would you want to happen differently?" → user generates the plan → user's commitment-level rises.
- **Roll with resistance.** When user pushes back on a target, MI says reflect not argue. "Sounds like 2750 kcal feels too tight right now" beats "the math says you need it".
- **The "OARS guard rail" for AI.** Reject LLM outputs that contain commands, threats, fear-appeals, or shame language ("you really shouldn't have…"). Re-roll with autonomy-supportive frame.

---

## 6. Self-monitoring effects — the dose-response engine

### 6.1 Burke 2011 + subsequent work

Burke, Wang & Sevick 2011 systematic review (22 studies on diet, exercise, weight self-monitoring, 1993-2009) found consistent significant association between self-monitoring and weight loss [1][2]. The "kept-food-diary lost 2× weight" framing comes from Hollis et al. 2008 and subsequent studies in the same lineage. Self-monitoring is the **highest-evidence single BCT** in the dietary weight-management literature.

Harvey et al. 2019 ("Log Often, Lose More") in *Obesity*: in a 6-month e-monitoring program, the percentage of days with monitoring was significantly associated with weight loss; participants in the top tertile of logging frequency lost ~3x as much weight as the bottom tertile [55][56].

Patel et al. 2021/2022 systematic review of mobile-app dietary self-monitoring in weight-loss interventions (PMC8928602): self-monitoring intensity and consistency both independently predict weight outcome, but app-based delivery did not show overall superiority to paper diary — the *behavior* matters more than the *modality* [57].

### 6.2 Granularity trade-offs

A Delphi study of expert opinion on reducing self-monitoring burden (PMC9358747) endorsed three "abbreviated monitoring" strategies once weight-loss trajectory is established: (1) self-weighing only, (2) monitoring only foods/beverages that are higher-density or less routine, (3) fewer days/week of full monitoring [58]. Critically, abbreviated monitoring is recommended *only after* either 2 weeks of no monitoring among strugglers (to re-engage) or 5-10% weight loss achieved (to consolidate gains).

Time burden: ~23 min/day month 1 dropping to ~15 min/day month 6 [6][55]. Two-thirds of completers (in a structured 6mo trial) still doing some monitoring at endpoint, one-third dropped entirely.

Photo logging is less burdensome but less accurate than barcode/manual for known SKUs; AI-image-recognition reduces burden but accuracy degrades for mixed dishes and non-Western cuisines [59][60][61]. The best-of-both pattern: photo + barcode hybrid, with manual fallback.

### 6.3 Direct implications for Dietician

- **Self-monitoring is the load-bearing feature.** Everything else is decorative if logging fails.
- **Hybrid input is mandatory.** Photo → vision-LLM parse → barcode top-up → "same as Tuesday" macro → manual correction. Each tier handles a different fatigue level.
- **Build abbreviated-monitoring mode early.** After 4 weeks of consistent logging, offer "weigh-only mode" or "fewer-days mode" as a graduation, not a punishment. Anti-pattern: forcing the same logging intensity forever.
- **Don't penalize gaps.** "Carry-forward yesterday" default + retroactive editing means a missed day doesn't break the ledger.

---

## 7. Implementation intentions for eating

### 7.1 Gollwitzer 1999; Adriaanse 2011

Implementation intentions are if-then plans linking a specific situational cue to a specific behavior: "If situation X arises, then I will do Y" [62]. They convert intention into a pre-formed automatic response that fires without willpower. Adriaanse, Vinkers, De Ridder, Hox & De Wit 2011 meta-analysis of 23 studies on eating:

- Promoting *healthy* eating additions: Cohen's d = 0.51 (medium-large) [8][9]
- *Reducing* unhealthy eating: Cohen's d = 0.29 (small-medium) [8]

A 2024 meta-regression of implementation intentions (Bieleke et al., 642 tests) confirms the asymmetry and identifies behavior-specificity and cue-specificity as key moderators [63]. Adriaanse et al. 2011 ("Breaking Habits With Implementation Intentions") found if-then plans against unwanted habits work primarily by inhibiting the cue→behavior link, not by replacing it — supporting the "add healthy" > "remove unhealthy" asymmetry [64].

### 7.2 Direct implications for Dietician

- **Make if-then prompts the default planning UI.** Not "I will eat 30g protein for breakfast", but "When I make coffee in the morning, I will eat 30g protein within 30 min".
- **Bias toward addition framings.** "When I'm hungry mid-afternoon, I will have Greek yogurt" beats "I won't eat chips when bored". The d-value asymmetry is real.
- **Use the receipt parser as a cue-detector.** "We noticed you bought chips Tuesday. Want to make an if-then plan for the next bag?" — opt-in implementation-intention prompt, autonomy-supportive framing.

---

## 8. Commitment devices (Stickk, beeminder)

### 8.1 Model + evidence

Commitment devices voluntarily constrain future-self choice to overcome present-bias. Soft commitments (public pledge, accountability partner) and hard commitments (stake monetary or social value forfeitable on failure) [65][66]. Loss aversion makes hard-commitment stakes ~2x more motivating than equivalent positive rewards.

Meta-evidence: data from three RCTs (n=409) found commitment interventions increase short-term weight loss by mean 1.5 kg (95% CI 0.7-2.4) [67]. A 2024 commitment-devices + text-reminders RCT on time-restricted eating found improved adherence vs control [68]. Stickk and Beeminder are the dominant commercial platforms; both rely on stake-forfeiture (anti-charity donations or direct money loss).

### 8.2 Ethical concerns + ED risk

Loss-aversion commitment to a weight target is functionally indistinguishable from a dietary rule with self-imposed punishment — a known orthorexia / restrictive-eating risk pathway [12][14][69]. The "what-the-hell" effect (§10) makes a missed commitment cascade into binge-and-give-up patterns. Hard-stakes on weight loss have not been studied for ED-population safety.

### 8.3 Direct implications for Dietician

- **Default off.** Do not bake commitment-stakes into Dietician's core loop.
- **If offered, only soft commitments.** "Tell Andrei you're aiming for 2750 kcal this week" = peer-witness commitment, no stake.
- **Never on body-weight outcomes.** Only on *process* targets (logged days, protein-hit days). Process > outcome reduces ED risk per Westenhoefer flexible-restraint research (§13).
- **Skip entirely for friends/family cohort.** If Victor wants Stickk-style commitment for himself, fine; do not extend to others without explicit consent and a screening check.

---

## 9. Identity-based motivation (Oyserman)

### 9.1 Model

Identity-based motivation (IBM) theory: people pursue goals when (a) goal-congruent action *feels identity-congruent* and (b) interpretation of difficulty signals "this is for me" not "this isn't for people like me" [70][71][72]. Identity is procedural, not static — primed identities shape feasibility judgments and effort interpretation.

Oyserman, Fryberg & Yoder 2007: priming racial-ethnic minority identity reduced perceived efficacy of "healthy" behaviors that participants associated with out-group identity (middle-class White) [70]. Health behaviors are identity-coded; mismatch reduces uptake even when knowledge and motivation are present.

### 9.2 Direct implications for Dietician

- **"Athlete-tracking-macros" > "dieting"**. Frame the app's identity-marker for Victor as "person who builds muscle / fuels training" not "person on a diet". The first is identity-additive (gain), the second restriction-coded (loss).
- **Romanian-eater identity preserved.** Do not require Victor's friends to think of themselves as "people on a diet" — that's hostile to Romanian cultural eating contexts (§14). Make tracking feel like meal-planning, household-organisation, or cooking-curiosity.
- **Reframe difficulty as on-path.** Marlatt's reframe: a hard day is information, not identity disconfirmation. Microcopy should never say "you broke your diet" — say "you logged 3100 kcal Tuesday. Want to plan around that?"

---

## 10. Defaults & nudges (Thaler-Sunstein) in food contexts

### 10.1 Evidence

Mertens et al. 2022 PNAS meta-analysis of choice-architecture interventions across domains: overall Cohen's d = 0.43 [73][74]. Cadario & Chandon 2020 meta-analysis of healthful-eating nudges: cognitively-oriented nudges (descriptive labels, visibility) d=0.12; affectively-oriented (sensory/emotional cues) d=0.24; behaviorally-oriented (defaults, positioning) d=0.39 — defaults are the strongest sub-category.

In cafeteria/supermarket settings: 57.6% of 33 reviewed nudge studies were effective, 33.3% mixed, 9.1% ineffective [75]. Priming + salience-affect combos effective in all 5 trials testing combination [75]. Healthy defaults (preset choice) effective in 1 of 2 trials.

### 10.2 Direct implications for Dietician

- **Default meal templates that hit macros.** When a user opens "plan today's meals", the first-paint suggestion should be a default that already hits 137g protein + 2750 kcal, drawn from the user's recent logged-food pattern. Override is one-tap.
- **Air-fryer + microwave constraint = pre-filtered default.** Recipes shown by default only use available equipment. Removes "this won't work for me" friction.
- **Receipt-anchored defaults.** "You bought chicken breast yesterday. Tomorrow's lunch suggestion uses it." Defaults that match purchased inventory are more sticky than abstract meal plans.
- **Avoid default-bypass via "set custom goal".** If a user sets calories below safe floor (§16), the app refuses — defaults can be paternalistic when ED-safety justifies it.

---

## 11. Abstinence-violation effect (Marlatt) — single-lapse design

### 11.1 Model

Marlatt & Gordon's relapse-prevention model identifies the abstinence-violation effect (AVE): after a slip, individuals who attribute the lapse to **stable, internal, global** causes ("I have no willpower") show despair, shame, and the "what-the-hell" effect (give up entirely because you feel like a failure already) [76][77][78]. Single-lapse → relapse transition is mediated by cognitive attribution, not the slip itself.

In a Very-Low-Calorie-Diet study (Mooney et al. 1992), 41 of 76 patients lapsed in 11 weeks; those reporting higher characterological (vs situational) attributions lost less weight [79]. The mechanism transfers to dietary self-monitoring: a missed log day, framed catastrophically, predicts dropout.

### 11.2 Direct implications for Dietician

- **Reframe lapses in copy.** "You missed Tuesday's log" → never write that. Use "Tuesday is unlogged. Want to backfill or move on?" — situational, not characterological, and gives an option.
- **No streak destruction.** Streaks weaponize AVE. If the app shows "23-day streak broken", that's the loss-aversion punch that flips a single slip into a quit.
- **Rolling 7-day windows, not daily binaries.** Replace daily pass/fail with "averaged 2780 kcal over the last 7 days, target 2750". One bad day in a 7-day window is a 14% perturbation, not a failure event.
- **"What would you do differently?" prompt after a logged lapse.** Marlatt's situational-attribution reframe baked into the LLM coach loop.

---

## 12. MyFitnessPal / Cronometer / Lose-It / MacroFactor / Carbon — real-world data

### 12.1 App-by-app

**MyFitnessPal**: largest user base, extensive food database including user-submitted entries (accuracy concerns), strong social features. Validation study (PMC7641788) reports significant discrepancies for nutrient calculations vs reference [80]. NEDA-collaboration safeguards exist [12][81].

**Cronometer**: emphasis on micronutrient accuracy, curated database, less social. Lower churn anecdotally among data-oriented users.

**MacroFactor**: "adherence-neutral" algorithm — recalculates targets based on actual intake, not adherence-to-plan. No streak; no scolding. Demonstrably the closest commercial app to the flexible-restraint + autonomy-supportive design pattern [82][83][84].

**Carbon Diet Coach**: requires adherence to update targets — "Carbon's approach—it requires perfection". Higher punishment-coupling; less ED-safe by design [82][83].

**Noom**: behavior-change-coach framing, daily lesson + coach chat + logging. 2.5-year RCT (n=600) ongoing; cross-sectional follow-up (PMC10551118) reports 75% maintained ≥5% loss at 1 year, 49% maintained ≥10% [85][86][87]. Strong evidence for behavior-change framing > pure tracking.

### 12.2 Churn data

App-abandonment scoping review (PMC11694054, 18 studies 2014-2022): curvilinear abandonment, sharp drop in week 1, slowing thereafter; mean engagement ~4.1 days in one large-scale study [88][89]. The "Hwang 2014" reference is shorthand for the consistent finding that 75% of new users abandon within a week and ~50% within a month — confirmed by current scoping reviews though the 2014 origin study isn't always indexed.

### 12.3 Design correlates of month-3 retention

- **Personalization** (target adjustment based on user data) — MacroFactor's edge
- **Low logging friction** (multi-modal input, smart defaults)
- **Behavior-change scaffolding** beyond logging (Noom's lessons)
- **Reasonable, achievable targets** (no <1200 kcal floors, no >2 lbs/wk loss)
- **Autonomy-supportive coach voice** (MacroFactor, weak in Carbon)
- **Survival of imperfect adherence** (MacroFactor's killer feature)

---

## 13. Why most apps fail in month 2 — failure modes

### 13.1 Logging fatigue

23 min/day in month 1 → ~15 min/day in month 6, with one-third dropping monitoring entirely [6][55]. Self-monitoring decline is the proximate cause of dropout. Photo-based logging is *less* enjoyable than expected per Wei et al. user studies, reducing rather than improving adherence in some cohorts [55][90].

### 13.2 Target-creep

Apps that reduce calorie targets as users approach goal weight (without explicit consent) trigger felt-arbitrariness and quitting. MacroFactor avoids this by transparent algorithm; Carbon's silent recalculations correlate with higher drop-off in user reports [82][83].

### 13.3 Social-comparison toxicity

Social features in calorie apps significantly elevate disordered-eating-symptomology risk in users without prior ED (PubMed 28214452 + 2024 review) [91][92]. Forum/community features create social-comparison loops that intensify body-image concern. The "Trouble with Tracking" Duke Psychiatry review (2023) catalogues mechanisms [93].

### 13.4 Over-restriction → binge cycle

Rigid restraint correlates with higher disinhibition, higher BMI, more frequent and more severe binge episodes (Westenhoefer 1999; replications 2012-2025) [13][14][15][94]. Apps that enforce rigid targets without flexible-restraint scaffolding trigger this loop. The mechanism: failure → AVE → "what the hell" → binge → shame → restrict → fail again.

### 13.5 Lack of human element

mHealth-app meta-analyses consistently find effectiveness limited beyond 6 months and standalone digital tools insufficient — "mHealth interventions may be more appropriate as initial catalysts rather than stand-alone solutions for long-term weight maintenance" [4][95][96]. Some form of human interaction (coach, peer, accountability partner) is the durability ingredient.

---

## 14. Romanian cultural eating context

### 14.1 The traditional pattern

Romanian cuisine is animal-origin-heavy (pork dominant, beef/mutton/fish secondary), with mămăligă (polenta) as carb staple and a structured 4-course meal pattern: appetizer (cured meats + cheese + vegetables) → ciorbă (sour soup) → main (meat + vegetables/pickles) → dessert [23][24][97]. Dairy/cheese intake is high (telemea, urdă, cașcaval). Bread is everywhere.

Mid-day hot meal is the cultural anchor — not breakfast or dinner. A tracker that defaults to American "small lunch, big dinner" patterns fights the rhythm.

### 14.2 Orthodox fasting calendar

The Orthodox liturgical year includes ~180 days of posts (fasts) annually, with vegan-strict fasts pre-Easter (Lent ~48 days), pre-Christmas (Nativity ~40 days), and shorter Apostles' Fast + Dormition Fast [98]. During posts, observant users avoid meat, dairy, eggs; semi-observant users variably restrict. Wednesdays + Fridays year-round are also fast days for the observant.

This is *not* an edge case for an RO multi-user system. Victor's friends may be variably observant; the app should accept "post" mode as a temporary dietary pattern, not flag it as deficiency.

### 14.3 Feast-anchored ritual eating

- **Sarmale at Christmas** (cabbage rolls with meat — high-calorie, eaten in quantity over multi-day feast)
- **Cozonac at Christmas** (sweet bread, walnut/poppy seed)
- **Pască + lamb at Easter** (sweet cheese pie + roast lamb)
- **Drob de miel at Easter** (lamb offal meatloaf)
- **Mărțișor + spring vegetable feasts**
- **Family Sunday lunches** (multi-hour, multi-course, alcohol-included)

Tracking expectations during these moments must accept dramatic single-day excursions without triggering AVE-style "you ruined it" messaging. The flexible-restraint framing (§13) is the design pattern.

### 14.4 Available studies

- 2024 Romanian post-pandemic dietary quality study (n=4704): 74% moderately-healthy, 20.7% healthy, 5.4% unhealthy dietary patterns [99]
- Adult obesity prevalence 22.5% (range 18.3-29% by region); projected 35% by 2035 per World Obesity Atlas 2023 [100][101]
- Three principal-component-analysis dietary patterns identified: High-meat/high-fat, Western, Prudent [102]
- Bucharest tertiary-care obesity intervention protocols: dietary + lifestyle changes are effective; protein/micronutrient adequacy often missed in obesity-clinic patients [103]

### 14.5 Implications for Dietician

- **Knowledge corpus must include CIQUAL + USDA + RO-specific SKUs** (Mega/Carrefour/Kaufland/Lidl/Auchan/Profi) and ideally a small curated RO traditional-foods database for sarmale, mămăligă-with-brânză, ciorbă-de-perișoare, etc.
- **Fasting mode** = first-class app feature, not a hack. Mark a date range as "post"; the system tolerates lower protein, suggests legume/grain protein sources, doesn't flag missed protein targets.
- **Feast tolerance.** A single 4000-kcal day during Crăciun should auto-context-tag as "feast day" and roll into a 14-day or 30-day window without penalty.
- **Bilingual UI** (RO default for cohort, EN available for Victor's preference). Food names primary in RO with EN secondary tooltip.

---

## 15. Cross-cutting synthesis

### 15.1 Adherence-driver-ranked table

| Driver | Effect size (d or r) | Context | Cost-to-implement |
|---|---|---|---|
| Self-monitoring (any modality) | Burke r large, dose-response | Universal | Med (build logging UI) |
| Action planning / implementation intentions (healthy-add framing) | d=0.51 | Diet specifically | Low (LLM prompt + UI) |
| Goal setting (process not outcome) | Large in BCT meta | Diet | Low |
| Feedback on behavior (timely, frequent) | Daily > weekly | Diet | Med (analytics) |
| Autonomy-supportive coaching voice | SDT meta significant | Diet, exercise | Low (microcopy + LLM prompt) |
| MI conversation style | +55% positive outcomes | Diet counseling | Low-med (LLM prompt) |
| Defaults & choice-architecture | d=0.39 for behavioral nudges | Food retail; transferable | Med (meal-template engine) |
| Implementation intentions (unhealthy-remove framing) | d=0.29 | Diet | Same as above |
| Social support — peer accountability | Significant in team-loss studies | Group | Med (multi-user) |
| Mindfulness-based interventions | g=-0.65 to -0.71 vs no-Tx | Binge eating | Low (content + check-ins) |
| Tiny-habits anchoring | Strong in scoping reviews | Habit gateways | Low |
| Hard commitment devices (Stickk) | +1.5 kg short-term weight loss | General weight loss | Low (risky — see §8.2) |
| Social comparison / leaderboards | Mixed; ED risk significant | General | DO NOT BUILD |
| Streaks | Short-term engagement up; long-term ED risk | General | DO NOT BUILD |

### 15.2 Top-10 actionable design moves for Dietician

1. **Multi-modal logging with smart defaults**: photo → vision LLM parse → barcode → "same as last Tuesday" → manual. Each tier handles a different fatigue level. (§6, §12)
2. **Rolling 7-day window targets, not daily binaries.** Display "averaged 2780 kcal last 7 days, target 2750" instead of pass/fail per day. (§11)
3. **Implementation-intention planner.** When user views tomorrow, offer to lock in 1-2 if-then plans anchored to existing routines. Bias toward addition framings. (§7)
4. **Autonomy-supportive LLM coach with OARS skeleton.** System prompt enforces open questions, reflections, no commands. Reject outputs containing shame/fear/control language. (§3, §5)
5. **Abbreviated-monitoring graduation path.** After 4 weeks of consistent logging, offer weigh-only mode or fewer-days mode as a feature, not a setback. (§6.2)
6. **Fasting / feast mode.** Tag date ranges as post or feast; system shifts targets and tolerance accordingly. RO-cultural first-class feature. (§14)
7. **Process-target dashboards.** Show "5/7 days protein hit" and "logged 6/7 days" prominently; show weight trend with smoothing only. No daily weight pass/fail. (§13.3)
8. **Receipt-anchored meal defaults.** Tomorrow's suggestion uses inventory the user already bought, parsed from latest receipt. (§10, §14)
9. **Peer-visible-progress, no comparison.** Andrei's "5/7 protein this week" visible to Victor (and vice versa) as ambient relatedness; no ranking, no diff display. (§3.3, §9)
10. **Knowledge corpus accessible mid-flow.** "Why this protein target?" → 2-paragraph explanation grounded in evidence (rationale builds competence per SDT §3). (§3)

### 15.3 Anti-patterns

- **Streaks.** Loss-aversion weaponization, AVE amplifier, ED risk. Just don't.
- **Public leaderboards / comparison feeds.** Significant disordered-eating-symptomology risk. (§13.3)
- **Body-weight outcome commitments / stakes.** (§8.2)
- **Rigid daily targets with pass/fail UI.** Rigid restraint pathway → binge cycle. (§13.4)
- **Hidden target recalculation.** Carbon-style "you weren't perfect so I'm not updating" → quit.
- **Restrictive language defaults.** "Don't eat", "you must", "you failed". Use autonomy markers. (§3)
- **Asking for motivation.** Design for M=2/10. If the only path forward needs willpower, the app failed. (§4)
- **Catastrophe framing of single lapses.** "Streak broken", "you ruined your week". Marlatt AVE territory. (§11)
- **Feature-creep into social media.** A nutrition app with comments + likes + reactions → ED-symptomology paths. (§13.3)
- **Calorie floors absent.** Default protections against <1200 (F) / <1500 (M) targets, against >2 lb/wk loss rate. Non-negotiable safety. (§16)

### 15.4 ED-safeguard primitives — non-negotiable

Sourced from NEDA, Alliance for Eating Disorders, AED guidelines, and the ED-symptomology literature on tracking apps [12][13][81][91][92][104][105]:

1. **Minimum calorie floors.** Refuse to set below 1500 kcal (M) / 1200 kcal (F) without explicit clinical-override flag. Soft warning at 1800/1500. Cite NEDA.
2. **Maximum rate-of-loss limit.** Refuse to project >0.9 kg/week (~2 lb/week) sustained loss. Soft warning at >0.5 kg/week. Same source.
3. **No body-comparison features.** No leaderboards, no "see how friends compare", no body-photo gallery.
4. **No food-categorization as "good" / "bad" / "cheat".** Use neutral descriptors only: "high protein", "high energy density", "fiber-rich", etc.
5. **No mandatory body measurements beyond weight (optional).** Waist/hip ratios, BMI flagging, body-fat estimation should all be opt-in and quiet.
6. **Behavior over outcome.** Rewards/affirmations for process targets (logged, planned, hit protein), not weight outcomes.
7. **Detection signals.** If user logs <80% of estimated need for 3+ consecutive days, surface a gentle check-in: "Some days have run low — anything we can adjust?" Autonomy-supportive, non-diagnostic.
8. **Hard-coded escape hatch.** A "pause tracking" button always one tap from home. Resumes anytime. No streak penalty.
9. **No exercise-compensation math.** Don't show "calories burned" balancing "calories eaten" as a daily-budget UI. Exercise tracking is separate from intake tracking.
10. **Resource link.** A "support resources" link in settings pointing to RO-specific ED resources (Centrul Alianța, etc.) + international (NEDA, beateatingdisorders.org.uk).

### 15.5 Romanian cultural transfer assumptions that break

- **Breakfast-as-protein-meal**: Romanian breakfast is bread + dairy + cured meats + jam/honey; protein density is mid, not high. A tracker that flags this as "low protein breakfast" is fighting the culture.
- **Lunch-light dinner**: Reversed in RO. Hot mid-day meal is structural.
- **Restaurant meals**: Less frequent than US/UK averages. Home-cooking dominates. Receipt parsing must handle grocery-heavy patterns.
- **Alcohol cultural role**: Țuică, wine, beer integrated into family meals; framing alcohol as "wasted calories" is culturally hostile.
- **Christmas/Easter feast**: Multi-day feasts (2-4 days) with sustained 3500-4500 kcal/day. Tolerance window must extend across days, not just one day.
- **Post (fasting) integration with secular tracking**: System must accept zero-meat-zero-dairy weeks as valid, not deficient.

---

## 16. Open questions for downstream rounds

- **Round 2 candidate — receipt OCR + vision-LLM food parsing**: accuracy benchmarks for RO grocery receipts, ClaudeMax CLI vs Gemini Vision A/B, Mega/Kaufland receipt format quirks.
- **Round 3 candidate — RO supermarket SKU coverage**: which databases (Open Food Facts RO, manufacturer sites, supermarket APIs) provide actionable data, what's missing, what scraping/parsing is needed.
- **Round 4 candidate — air-fryer + microwave recipe corpus**: existing curated databases, RO-language recipes specifically, macro-tagged recipe sources.
- **Round 5 candidate — GROBID + paper-ingestion pipeline**: nutrition-research paper structure, citation extraction, evidence-tier classification for surfacing in knowledge corpus.
- **Round 6 candidate — LLM coach prompt engineering for MI/SDT compliance**: test prompts, rejection criteria, eval framework.
- **Round 7 candidate — peer/multi-user privacy + visibility model**: what defaults preserve autonomy + relatedness without inviting comparison.
- **Round 8 candidate — RO-specific ED clinical guidelines + resources for safeguard surfacing**.
- **Open**: Does the abbreviated-monitoring graduation pattern hold for a recomp/lean-bulk user (Victor) the same way it does for weight-loss users? Most literature is weight-loss; lean-bulk literature is sparse. (§6.2, §17)
- **Open**: How does the Orthodox fasting calendar interact with protein-target maintenance — should the app suggest legume + grain pairings during posts, or accept lower protein with explicit user consent?
- **Open**: How to design ambient relatedness (peer visibility) such that it satisfies SDT without slipping into social-comparison toxicity. Practical UX question.
- **Open**: How frequent should LLM-coach-driven check-ins be? Daily is too much (notification fatigue); weekly may be too rare. JITAI literature suggests context-triggered.

---

## Sources

1. Burke LE, Wang J, Sevick MA. Self-monitoring in weight loss: a systematic review of the literature. *J Am Diet Assoc*. 2011;111(1):92-102. https://pubmed.ncbi.nlm.nih.gov/21185970/
2. Burke LE et al. Self-Monitoring in Weight Loss: A Systematic Review of the Literature. *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC3268700/
3. Patel ML et al. A systematic review of the use of dietary self-monitoring in behavioural weight loss interventions: delivery, intensity and effectiveness. *Public Health Nutr*. 2022. https://pmc.ncbi.nlm.nih.gov/articles/PMC8928602/
4. The Role of Mobile Apps in Obesity Management: Systematic Review and Meta-Analysis. *JMIR* 2025. https://www.jmir.org/2025/1/e66887
5. When and Why Adults Abandon Lifestyle Behavior and Mental Health Mobile Apps: Scoping Review. *JMIR* 2024. https://www.jmir.org/2024/1/e56897
6. Expert opinions on reducing dietary self‐monitoring burden and maintaining efficacy in weight loss programs: A Delphi study. *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC9358747/
7. McDermott MS et al. The Theory of Planned Behaviour and dietary patterns: A systematic review and meta-analysis. *Prev Med*. 2015. https://pubmed.ncbi.nlm.nih.gov/26348455/
8. Adriaanse MA, Vinkers CDW, De Ridder DTD, Hox JJ, De Wit JBF. Do implementation intentions help to eat a healthy diet? A systematic review and meta-analysis of the empirical evidence. *Appetite*. 2011;56(1):183-193. https://pubmed.ncbi.nlm.nih.gov/21056605/
9. Do implementation intentions help to eat a healthy diet? https://www.sciencedirect.com/science/article/pii/S0195666310005325
10. Michie S, van Stralen MM, West R. The behaviour change wheel: a new method for characterising and designing behaviour change interventions. *Implement Sci*. 2011. https://pubmed.ncbi.nlm.nih.gov/21513547/
11. Michie S et al. The Behavior Change Technique Taxonomy (v1) of 93 Hierarchically Clustered Techniques. *Ann Behav Med*. 2013. https://pubmed.ncbi.nlm.nih.gov/23512568/
12. Eikey EV et al. Effects of diet and fitness apps on eating disorder behaviours: qualitative study. *BJPsych Open*. 2021. https://pmc.ncbi.nlm.nih.gov/articles/PMC8485346/
13. Westenhoefer J et al. Validation of the flexible and rigid control dimensions of dietary restraint. *Int J Eat Disord*. 1999. https://pubmed.ncbi.nlm.nih.gov/10349584/
14. Cognitive and weight-related correlates of flexible and rigid restrained eating behaviour. 2013. https://pubmed.ncbi.nlm.nih.gov/23265405/
15. Validation of the Flexible and Rigid Cognitive Restraint Scales in a General French Population. 2022. https://pmc.ncbi.nlm.nih.gov/articles/PMC9564632/
16. On or Off Track: How (Broken) Streaks Affect Consumer Decisions. *J Consumer Res*. 2023. https://academic.oup.com/jcr/article/49/6/1095/6623414
17. Woolley K. Digital tracking, gamification, social media, and AI: How technology influences motivation. *Consum Psych Rev*. 2026. https://myscp.onlinelibrary.wiley.com/doi/10.1002/arcp.70004
18. Pelletier LG, Dion SC, Slovinec-D'Angelo M, Reid R. Why do you regulate what you eat? Relationships between forms of regulation, eating behaviors, sustained dietary behavior change, and psychological adjustment. *Motiv Emot*. 2004. https://selfdeterminationtheory.org/SDT/documents/2011_TeixeiraPatrickMata_NutrBul.pdf
19. Verstuyf J et al. Motivational dynamics of eating regulation: a self-determination theory perspective. *IJBNPA*. 2012. https://link.springer.com/article/10.1186/1479-5868-9-21
20. Teixeira PJ, Patrick H, Mata J. Why we eat what we eat: the role of autonomous motivation in eating behaviour. *Nutr Bull*. 2011. https://www.selfdeterminationtheory.org/SDT/documents/2011_TeixeiraPatrickMata_NutrBul.pdf
21. Lundahl B et al. Motivational interviewing in medical care settings: a systematic review and meta-analysis of randomized controlled trials. *Patient Educ Couns*. 2013. https://pmc.ncbi.nlm.nih.gov/articles/PMC1463134/
22. Today's Dietitian CPE Monthly: Motivational Interviewing. https://www.todaysdietitian.com/cpe-monthly-motivational-interviewing/
23. Romanian cuisine. *Wikipedia*. https://en.wikipedia.org/wiki/Romanian_cuisine
24. Petrescu DC et al. Reshaping the Traditional Pattern of Food Consumption in Romania through the Integration of Sustainable Diet Principles. *Sustainability*. 2020. https://www.mdpi.com/2071-1050/12/14/5826
25. Research on Food Behavior in Romania from the Perspective of Supporting Healthy Eating Habits. https://researchgate.net/publication/336061687
26. Ajzen I. The theory of planned behavior. *Organ Behav Hum Decis Process*. 1991;50(2):179-211.
27. McEachan RRC, Conner M, Taylor NJ, Lawton RJ. Prospective prediction of health-related behaviours with the Theory of Planned Behaviour: A meta-analysis. *Health Psychol Rev*. 2011.
28. Riebl SK et al. The theory of planned behaviour and discrete food choices: a systematic review and meta-analysis. *IJBNPA*. 2015. https://pmc.ncbi.nlm.nih.gov/articles/PMC4696173/
29. Riebl SK et al. A systematic literature review and meta-analysis: The Theory of Planned Behavior's application to understand and predict nutrition-related behaviors in youth. *Eat Behav*. 2015. https://pubmed.ncbi.nlm.nih.gov/26112228/
30. Riebl SK et al. ScienceDirect listing for youth TPB nutrition review. https://www.sciencedirect.com/science/article/abs/pii/S1471015315000707
31. Expanding the theory of planned behavior to predict fruits and vegetables consumption among a sample of women in Saudi Arabia. *Frontiers Public Health*. 2025. https://www.frontiersin.org/journals/public-health/articles/10.3389/fpubh.2025.1720598/full
32. Sheeran P. Intention-behavior relations: A conceptual and empirical review. *Eur Rev Soc Psychol*. 2002.
33. Hagger MS. Habit and physical activity: Theoretical advances, practical implications, and agenda for future research. *Psychol Sport Exerc*. 2019.
34. The 93-BCT taxonomy hierarchically clustered. *bct-taxonomy.com*. https://www.bct-taxonomy.com/about
35. Meloncelli N et al. Designing a behaviour change intervention using COM‐B and the Behaviour Change Wheel: Co‐designing the Healthy Gut Diet for preventing gestational diabetes. *J Hum Nutr Diet*. 2024. https://pubmed.ncbi.nlm.nih.gov/39054768/
36. Cooper et al. Using the COM-B model and Behaviour Change Wheel to develop a theory and evidence-based intervention for women with gestational diabetes (IINDIAGO). *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC10186807/
37. Behavior change techniques in pregnancy dietary interventions: a systematic review through the lens of the COM-B model. *BMC Pregnancy Childbirth*. 2025. https://link.springer.com/article/10.1186/s12884-025-07876-7
38. Are dietary interventions with a behaviour change theoretical framework effective in changing dietary patterns? A systematic review. *BMC Public Health*. 2020. https://link.springer.com/article/10.1186/s12889-020-09985-8
39. Pelletier LG, Dion SC. An examination of general and specific motivational mechanisms for the relations between body dissatisfaction and eating behaviors. *J Soc Clin Psychol*. 2007.
40. Ntoumanis N et al. A meta-analysis of self-determination theory-informed intervention studies in the health domain. *Health Psychol Rev*. 2020. https://www.tandfonline.com/doi/full/10.1080/17437199.2020.1718529
41. Self-Determination Theory Applied to Health Contexts: A Meta-Analysis. https://www.researchgate.net/publication/229121897
42. Gillison FB et al. A meta-analysis of techniques to promote motivation for health behaviour change from a self-determination theory perspective. *Health Psychol Rev*. 2019. https://selfdeterminationtheory.org/wp-content/uploads/2019/03/2019_GillisonEtAl_HPR_MetaAnalysis.pdf
43. Testing a Self-Determination Theory Model of Healthy Eating in a South African Township. *Frontiers Psychology*. 2020. https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2020.02181/full
44. Motivated to eat green or your greens? Comparing the role of motivation towards the environment and for eating regulation on ecological eating behaviours. *Food Qual Pref*. 2022. https://www.sciencedirect.com/science/article/abs/pii/S0950329322000453
45. Fogg BJ. A behavior model for persuasive design. *Persuasive '09*. 2009. https://www.behaviormodel.org/
46. Fogg BJ. *Tiny Habits: The Small Changes That Change Everything*. 2019.
47. Tiny Habits framework reference. https://focusme.com/blog/tiny-habits-review-hack-the-fogg-behavior-model/
48. BeWay Consulting summary of B=MAP. https://blog.beway.com/en/bmap-big-changes-with-tiny-habits/
49. Behavioral science meets public health: a scoping review of the Fogg behavior model in behavior change interventions. *PMC* 2025. https://pmc.ncbi.nlm.nih.gov/articles/PMC12522219/
50. Micro-Health Interventions: Exploring Design Strategies for 1-Minute Interventions as a Gateway to Healthy Habits. arxiv 2025. https://arxiv.org/html/2508.09312v1
51. Miller WR, Rollnick S. *Motivational Interviewing: Helping People Change*. 3rd ed. 2013.
52. Motivational Interviewing to Promote Healthy Lifestyle Behaviors: Evidence, Implementation, and Digital Applications. *PMC* 2024. https://pmc.ncbi.nlm.nih.gov/articles/PMC12526391/
53. Armstrong MJ et al. Motivational interviewing to improve weight loss in overweight and/or obese patients: a systematic review and meta-analysis of RCTs. *Obes Rev*. 2011.
54. Motivational Interviewing: An Evidence-Based Approach for Use in Medical Practice. *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC8200683/
55. Harvey J et al. Log Often, Lose More: Electronic Dietary Self‐Monitoring for Weight Loss. *Obesity*. 2019. https://pubmed.ncbi.nlm.nih.gov/30801989/
56. Harvey J et al. Log Often, Lose More. *Wiley*. https://onlinelibrary.wiley.com/doi/full/10.1002/oby.22382
57. Patel ML, Wakayama LN, Bennett GG. Self-Monitoring via Digital Health in Weight Loss Interventions: A Systematic Review Among Adults with Overweight or Obesity. *Obesity*. 2021. https://pmc.ncbi.nlm.nih.gov/articles/PMC8928602/
58. Expert opinions Delphi study on reducing dietary self-monitoring burden. https://pmc.ncbi.nlm.nih.gov/articles/PMC9358747/
59. Evaluating the Quality and Comparative Validity of Manual Food Logging and Artificial Intelligence-Enabled Food Image Recognition in Apps for Nutrition Care. *PMC* 2024. https://pmc.ncbi.nlm.nih.gov/articles/PMC11314244/
60. Use of Different Food Image Recognition Platforms in Dietary Assessment: Comparison Study. https://pmc.ncbi.nlm.nih.gov/articles/PMC7752530/
61. Mobile Apps for Dietary and Food Timing Assessment: Evaluation for Use in Clinical Research. *PMC* 2023. https://pmc.ncbi.nlm.nih.gov/articles/PMC10337248/
62. Gollwitzer PM. Implementation intentions: Strong effects of simple plans. *Am Psychol*. 1999;54(7):493-503.
63. Bieleke M et al. The When and How of Planning: Meta-Analysis of the Scope and Components of Implementation Intentions in 642 Tests. 2024. https://www.researchgate.net/publication/378870694
64. Adriaanse MA, Gollwitzer PM, De Ridder DTD, de Wit JBF, Kroese FM. Breaking habits with implementation intentions: A test of underlying processes. *Pers Soc Psychol Bull*. 2011. https://journals.sagepub.com/doi/10.1177/0146167211399102
65. Bryan G, Karlan D, Nelson S. Commitment devices. *Annu Rev Econ*. 2010.
66. Commitment Devices in Online Behavior Change Support Systems. KAIST. https://ic.kaist.ac.kr/publications/papers/lee2019commitment.pdf
67. The effect of commitment-making on weight loss and behaviour change in adults with obesity/overweight; a systematic review. *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC6591991/
68. Feasibility and outcomes from using commitment devices and text message reminders to increase adherence to time-restricted eating: A randomized trial. *PMC* 2024. https://pmc.ncbi.nlm.nih.gov/articles/PMC11010633/
69. Stickk platform. https://www.stickk.com/
70. Oyserman D, Fryberg SA, Yoder N. Identity-based motivation and health. *J Pers Soc Psychol*. 2007;93(6):1011-1027. https://pubmed.ncbi.nlm.nih.gov/18072851/
71. Oyserman D, Smith G, Elmore K. Identity‐Based Motivation: Implications for Health and Health Disparities. *J Soc Issues*. 2014. https://spssi.onlinelibrary.wiley.com/doi/abs/10.1111/josi.12056
72. Oyserman D et al. An Identity-Based Motivation Framework for Self-Regulation. 2017. https://dornsife.usc.edu/daphna-oyserman/wp-content/uploads/sites/232/2023/11/Oyserman_et_al._2017_-_An_identity-based_motivaiton_framework_for_self-regulation.pdf
73. Mertens S, Herberz M, Hahnel UJJ, Brosch T. The effectiveness of nudging: A meta-analysis of choice architecture interventions across behavioral domains. *PNAS*. 2022;119(1). https://ui.adsabs.harvard.edu/abs/2022PNAS..11907346M/abstract
74. Cadario R, Chandon P. Which healthy eating nudges work best? A meta-analysis of field experiments. *Marketing Science*. 2020.
75. Nudges and choice architecture to promote healthy food purchases in adults: A systematized review. *PubMed* 2022. https://pubmed.ncbi.nlm.nih.gov/36395010/
76. Marlatt GA, Gordon JR. *Relapse Prevention: Maintenance Strategies in the Treatment of Addictive Behaviors*. Guilford Press; 1985.
77. Collins SE, Witkiewitz K. Abstinence Violation Effect. *ScienceDirect Encyclopedia*. https://www.sciencedirect.com/topics/psychology/abstinence-violation
78. Marlatt GA, Witkiewitz K. Relapse prevention for alcohol and drug problems. *Am Psychol*. 2005.
79. Mooney JP et al. The abstinence violation effect and very low calorie diet success. *Addict Behav*. 1992. https://pubmed.ncbi.nlm.nih.gov/1502966/
80. Accuracy of Nutrient Calculations Using the Consumer-Focused Online App MyFitnessPal: Validation Study. *PMC* 2020. https://pmc.ncbi.nlm.nih.gov/articles/PMC7641788/
81. MyNetDiary commitment to ED-safe design. https://www.mynetdiary.com/eating-disorders-food-tracking.html
82. MacroFactor vs Carbon Diet Coach: I Tested Both. *FeastGood*. 2026. https://feastgood.com/macrofactor-vs-carbon-diet-coach/
83. MacroFactor vs Carbon Diet Coach — Which Is Better in 2026. *Nutrola*. https://nutrola.app/en/blog/macrofactor-vs-carbon-diet-coach-which-is-better-2026
84. MacroFactor app official. https://macrofactor.com/macrofactor/
85. Weight loss maintenance after a digital commercial behavior change program (Noom Weight). *PMC* 2023. https://pmc.ncbi.nlm.nih.gov/articles/PMC10551118/
86. Noom 2.5-Year Weight Management Program Protocol RCT. https://pubmed.ncbi.nlm.nih.gov/35969439/
87. Adherence as a predictor of weight loss in a commonly used smartphone application. *Obes Res Clin Pract*. 2016. https://www.sciencedirect.com/science/article/abs/pii/S1871403X16300291
88. When and Why Adults Abandon Lifestyle Behavior and Mental Health Mobile Apps: Scoping Review. *PMC* 2024. https://pmc.ncbi.nlm.nih.gov/articles/PMC11694054/
89. User Engagement and Abandonment of mHealth: A Cross-Sectional Survey. *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC8872344/
90. Trade-off between Automation and Accuracy in Mobile Photo Recognition Food Logging. 2017. https://www.researchgate.net/publication/317704636
91. Levinson CA, Fewell L, Brosof LC. My Fitness Pal calorie tracker usage in the eating disorders. *Eat Behav*. 2017. https://pubmed.ncbi.nlm.nih.gov/28214452/
92. Wallace T, Koebbel C, Heath J. A systematic search and review focusing on the influence of health-tracking technologies on eating habits and attitudes. 2026. https://journals.sagepub.com/doi/10.1177/13591053251351222
93. Duke Department of Psychiatry & Behavioral Sciences. The Trouble with Tracking. https://psychiatry.duke.edu/blog/trouble-tracking
94. Flexible vs. rigid dieting in resistance-trained individuals seeking to optimize their physiques: A randomized controlled trial. *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC8243453/
95. Mobile and Web Apps for Weight Management in Overweight and Obese Adults: An Updated Umbrella Review and Meta-Analysis. *MDPI*. 2025. https://www.mdpi.com/1660-4601/22/7/1152
96. Huntriss et al. The effectiveness of mobile app usage in facilitating weight loss: An observational study. *Obes Sci Pract*. 2024. https://onlinelibrary.wiley.com/doi/full/10.1002/osp4.757
97. Punto Iberica. Romanian Food Culture. https://puntoiberica.com/romanian-food-culture/
98. Holy Apostles Convent. Orthodox fasting calendar references.
99. Assessment of Dietary and Lifestyle Quality among the Romanian Population in the Post-Pandemic Period. *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC11121699/
100. World Obesity Federation Global Obesity Observatory - Romania. https://data.worldobesity.org/country/romania-178/
101. ROSA 2023 in review. *World Obesity Federation*. https://www.worldobesity.org/news/2023-in-review-romanian-association-for-the-study-of-obesity-rosa
102. DIETARY PATTERNS AND THEIR ASSOCIATION WITH OBESITY: A CROSS-SECTIONAL STUDY (Romania). *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC6535323/
103. Efficacy of dietary and lifestyle interventions in obesity management: a therapeutic protocol at the Diabetes Department, Marius Nasta Institute, Bucharest. *PMC* 2025. https://pmc.ncbi.nlm.nih.gov/articles/PMC12022728/
104. National Eating Disorders Association — Professional Guidelines. https://www.nationaleatingdisorders.org/professionals-guidelines/
105. NEDA Orthorexia. https://www.nationaleatingdisorders.org/orthorexia/
106. Lally P, van Jaarsveld CHM, Potts HWW, Wardle J. How are habits formed: Modelling habit formation in the real world. *Eur J Soc Psychol*. 2010. https://onlinelibrary.wiley.com/doi/10.1002/ejsp.674
107. Gardner B, Lally P, Wardle J. Making health habitual: the psychology of 'habit-formation' and general practice. *Br J Gen Pract*. 2012. https://pmc.ncbi.nlm.nih.gov/articles/PMC3505409/
108. Mastellos N et al. Transtheoretical model stages of change for dietary and physical exercise modification in weight loss management for overweight and obese adults. *Cochrane*. 2014. https://www.cochranelibrary.com/cdsr/doi/10.1002/14651858.CD008066.pub3/full
109. Dietary Stages of Change and Decisional Balance: A Meta-Analytic Review. https://www.researchgate.net/publication/44650876
110. Brytek-Matera A. Orthorexia Nervosa: An Obsession With Healthy Eating. *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC6370446/
111. Dunn TM, Bratman S. On orthorexia nervosa: A review of the literature and proposed diagnostic criteria. *Eat Behav*. 2016.
112. Orthorexia as an Eating Disorder Spectrum—A Review of the Literature. *PMC* 2024. https://pmc.ncbi.nlm.nih.gov/articles/PMC11478848/
113. Mindfulness-based interventions for binge eating: an updated systematic review and meta-analysis. *J Behav Med*. 2025. https://pmc.ncbi.nlm.nih.gov/articles/PMC11893636/
114. Examining the Efficacy of Mindfulness-Based Interventions in Treating Obesity, Obesity-Related Eating Disorders, and Diabetes Mellitus. *J Am Nutr Assoc*. 2024. https://www.tandfonline.com/doi/full/10.1080/27697061.2024.2428290
115. Two decades of mindfulness-based interventions for binge eating: A systematic review and meta-analysis. *J Psychosom Res*. 2021. https://www.sciencedirect.com/science/article/abs/pii/S0022399921002373
116. Comparing Self-Monitoring Strategies for Weight Loss in a Smartphone App: Randomized Controlled Trial. *JMIR mHealth* 2019. https://pmc.ncbi.nlm.nih.gov/articles/PMC6416539/
117. Impact of feedback generation and presentation on self-monitoring behaviors, dietary intake, physical activity, and weight: a systematic review and meta-analysis. *IJBNPA* 2023. https://ijbnpa.biomedcentral.com/articles/10.1186/s12966-023-01555-6
118. Nahum-Shani I et al. Just-in-Time Adaptive Interventions (JITAIs) in Mobile Health: Key Components and Design Principles for Ongoing Health Behavior Support. *Ann Behav Med*. 2018. https://pmc.ncbi.nlm.nih.gov/articles/PMC5364076/
119. Optimizing a mobile just-in-time adaptive intervention (JITAI) for weight loss in young adults: AGILE factorial RCT. 2025. https://pubmed.ncbi.nlm.nih.gov/39824380/
120. A systematic review of just-in-time adaptive interventions (JITAIs) to promote physical activity. *IJBNPA*. 2019. https://link.springer.com/article/10.1186/s12966-019-0792-7
121. Hwang H, Ham B, Lee K. The benefits and costs of using a smartphone-based recommendation system for healthy eating decisions: A study among South Korean college students. *Computers in Human Behavior* (cited canonically as Hwang 2014 for 75%/50% week-1/month-1 abandonment pattern; updated by [88] scoping review).
122. Benefits of recruiting participants with friends and increasing social support for weight loss and maintenance. *Health Psychol*. 1999. https://pubmed.ncbi.nlm.nih.gov/10028217/
123. Leahey TM et al. Teammates and social influence affect weight loss outcomes in a team-based weight loss competition. *Obesity*. 2013. https://pmc.ncbi.nlm.nih.gov/articles/PMC3676749/
124. Hwang KO et al. Social support in an Internet weight loss community. *Int J Med Inform*. 2010. https://pmc.ncbi.nlm.nih.gov/articles/PMC3060773/
125. Wang JB et al. Effect of a mobile-app intervention on dietary monitoring in adults. *Obesity*. 2020.
126. Linardon J et al. Rates of abstinence following psychological or behavioral treatments for binge-eating disorder: Meta-analysis. *Int J Eat Disord*. 2018.
127. Patel ML et al. Adherence to mobile‐app‐based dietary self‐monitoring—Impact on weight loss in adults. *PMC* 2022. https://pmc.ncbi.nlm.nih.gov/articles/PMC9159560/
128. Recent Perspectives Regarding the Role of Dietary Protein for the Promotion of Muscle Hypertrophy with Resistance Exercise Training. *Nutrients*. 2018. https://pmc.ncbi.nlm.nih.gov/articles/PMC5852756/
129. Beyond the norm: high protein adherence impacts muscular force and size adaptations. *PMC* 2025. https://pmc.ncbi.nlm.nih.gov/articles/PMC12379684/
130. Nutritional interventions in muscle hypertrophy research: a scientometric analysis within the context of resistance training (1992–2025). *PMC*. https://pmc.ncbi.nlm.nih.gov/articles/PMC12317481/

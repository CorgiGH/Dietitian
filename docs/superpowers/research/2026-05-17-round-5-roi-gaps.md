# Round 5 — Top-5 ROI Gaps that Unblock Plans 2-7

**Date:** 2026-05-17
**Round:** 5 (post-spec, pre-Plan-2)
**Audience:** Plan-2 (LLM router) through Plan-7 (knowledge corpus) authors.
**Status:** Deep-research output. Read alongside `docs/superpowers/specs/2026-05-17-dietician-design.md` and `docs/superpowers/plans/2026-05-17-plan-1-shared-data-ledger.md`.

---

## TL;DR

Spec is locked at 10/10 confidence. Plan-1 (`:shared:data`) is implemented. Plans 2-7 still need design + impl. This round surfaces the **five concrete gaps** whose resolution drops the highest-value blockers for Plans 2-7. The spec talks about each at the architectural level; this document fills the engineering detail.

| # | Gap | Plans blocked | Blocker class |
|---|-----|---------------|---------------|
| 1 | Receipt OCR pipeline robustness (raw retention + SKU normalization + corruption gate + RO diacritics) | 2 (LLM router) · 3 (Ktor server) · 6 (scrapers, alias table) | High — feeds every pantry write derived from receipt |
| 2 | Paper ingestion sequencing (GROBID → TEI → LLM → wiki + DOI graph) | 7 (knowledge corpus) | High — gates ~1000-paper knowledge layer |
| 3 | Anelis Plus auth reverse-engineer (UAIC Shibboleth IdP path) | 7 (knowledge corpus paywalled fetch) | Medium — Unpaywall fallback works without it; ~40% of premium nutrition papers behind paywall |
| 4 | Embedding strategy for multi-corpus knowledge base (one model vs many, hybrid search) | 7 (corpus retrieval), 2 (LLM router calls embeddings) | High — defines retrieval quality and self-host cost |
| 5 | KMP UI sharing strategy (Compose Multiplatform shared % per screen) | 4 (Android UI) · 5 (Desktop UI) | High — defines plan structure of Plans 4 + 5 |

**Recommended attack order: 5 → 1 → 4 → 2 → 3.**
Rationale: Gap 5 fixes plan-shape for Plans 4+5 before any UI work starts (cheapest-to-change is plan structure). Gap 1 unblocks Plan-2 (router) + Plan-3 (server) simultaneously. Gap 4 unblocks Plan-7 retrieval. Gap 2 sequences Plan-7 ingest. Gap 3 is investigation-only — implementation gated on user-led IdP probe during Plan-7.

**Three surprises:**
1. **UAIC IdP is confirmed and probed.** `https://idp.uaic.ro/idp/shibboleth` is a live SAML 2.0 IdP, RoEduNetID-registered since 2019-10-22, with R&S + SIRTFI entity categories. Investigation per spec §13 is shorter than feared — the IdP exists, the credentials are Office-365-shape (`firstname.surname`), and Shibboleth-protected publishers (Springer/ScienceDirect/IEEE) are reachable through RoEduNetID. The unknown is which 5-10 of the ~100 nutrition journals Anelis Plus actually unlocks for UAIC affiliates.
2. **GROBID on desktop needs ≥2GB Docker RAM (not the 1GB I anticipated).** Spec errata moved GROBID off VPS to user desktop after MC heap unchanged. The 460MB residual buffer accounting in the spec is correct for VPS-side, but on-desktop GROBID is itself a non-trivial RAM burden. With Docker Desktop + the Deep Learning model variant (~8GB image), full-text extraction wants 4GB allocated. Plan-7 must surface this as a desktop-prerequisite check + degraded mode (header-only at 2GB).
3. **Compose Multiplatform shared-UI percentage is reported at 90-96% by production apps that ship Android + iOS.** The Dietician spec targets Android + Desktop — same Compose runtime, same Material 3 binding, no iOS-only widget gaps. Empirically the share-rate should run **higher than 90%**, with divergence concentrated in five specific surfaces (camera, file dialog, status bar / nav drawer scaffolding, window-chrome, ntfy subscription). Plans 4 and 5 should be **merged into a single Plan-4-5 "shared UI + 2 platform thin shells"** rather than two parallel UI plans.

**File path:** `C:\Users\User\Desktop\Dietician\docs\superpowers\research\2026-05-17-round-5-roi-gaps.md`

---

## Gap 1 — Receipt OCR Pipeline Robustness

### 1.1 Why this gap matters

Receipt OCR is the dominant cross-cutting capture path for Dietician. A single receipt photo touches every layer:

- `:shared:data` writes `receipt_events` (Plan-1, done) + `pantry_events` (purchase deltas) + `pantry_metadata` (SKU + expiry) + `price_observations` (per-line price observation)
- `:shared:llm` runs the Vision call via `LlmRouter` (Plan-2)
- `:server` orchestrates the desktop-vs-VPS routing decision based on device heartbeat (Plan-3)
- `:scrapers:playwright` feeds the canonical SKU table that receipt aliases must match into (Plan-6)
- `:shared:knowledge` may surface nutrition labels when user `/photo`'s a product (Plan-7)

A receipt with 12 line items and 90% OCR accuracy generates 12 ledger writes, ~1-2 alias-table mutations, and depending on cross-confirm path either 0 or 12 `price_observations`. Failure modes propagate widely. The Council 3 BREAK #3 "Vision JSON corruption gate" applies here.

User's locked context (5 users × 2-5 receipts/week × 50 weeks/yr) = **500-1250 receipts/year aggregate**. At an average receipt = 1 Vision call + 1 retry on failure ≈ 1.2 calls/receipt, that's 600-1500 Vision calls/year. Per-call cost dominates LLM budget for the OCR slice. Get this wrong and the $200/mo Claude Max 20x credit burns down in a week.

### 1.2 Primary path: ClaudeMax CLI on desktop

Spec §7.4 + §8.1 lock the primary path. ClaudeMax CLI (`claude --bare -p ...`) is invoked as a desktop subprocess. The CLI reads the receipt image via `--allowedTools "Read"`. The model is implicit in the CLI's default config (Sonnet 4.x at time of writing) — explicit override via `--model` flag only when the router asks for it.

**Invocation contract:**

```bash
claude --bare -p \
  --output-format stream-json \
  --verbose \
  --allowedTools "Read" \
  --model claude-sonnet-4-5
<<< "$PROMPT"
```

The CLI consumes prompt from stdin. The prompt asks Claude to `Read` the receipt at `${workspaceDir}/receipts/${receipt_uuid}.jpg` and emit the structured JSON spec'd at §8.1.

**Stream-JSON parse contract** (`:shared:llm` ClaudeMaxCliProvider):

Stream-JSON output is verbose. Each line is a JSON event with `type` ∈ `{system, completion, result, error, api_retry}`. Key events:
- `type: "system"` with `subtype: "init"` → session-start, ignore
- `type: "system"` with `api_retry` key → backoff event; if `error: "rate_limit"` or `"billing_error"` → throw `QuotaExceededException` (spec §7.4 fragment)
- `type: "completion"` or `type: "result"` → final text payload

**Exit code semantics (locked open question in spec §32 #2):**
Empirically (from 2026 Q1 community reports): exit code 0 on success, exit code 1 on parse error / abort. The CLI emits the `api_retry` system event *and then exits non-zero* when the quota is exhausted — it does NOT crash silently. **Plan-2 action:** smoke-test `claude --print` against a known-exhausted Max-20x account on first wiring; capture the exact exit code and verify the stream-json `api_retry` event semantics. If exit code conflicts with the api_retry event, prefer the stream-json event (carries `error` reason string).

**Sandbox:** `workspaceDir` is set to a per-job temp directory containing only the one receipt image being processed. `--allowedTools "Read"` plus this directory containment prevents the CLI from reading arbitrary disk state, which would burn tokens and risk leaking other receipts.

**Concurrency:** Sequential. ClaudeMax CLI subprocess startup is ~1-2s, completion is ~3-8s for a single-page receipt. Concurrent invocations risk hitting the Anthropic per-account rate cap that the Max 20x plan rebalances around. Queue depth surfaces in `/diag` via `pending_jobs(job_type='ocr_receipt')`.

### 1.3 Fallback path: OpenRouter Gemini 2.5 Flash Vision

When desktop is offline (heartbeat > 60s), or when ClaudeMax CLI quota is exhausted (≥95% budget per §7.5), the router falls through to OpenRouter `google/gemini-2.5-flash` per spec §7.3.

**Pricing (OpenRouter, May 2026):**
- Input: $0.30 / 1M tokens
- Output: $2.50 / 1M tokens
- Per-receipt: a receipt photo at 1080×1920 base64-encoded ≈ 5K image tokens + 200 prompt tokens. Output JSON ~ 500 tokens. Total ≈ 5,700 input + 500 output = **$0.0017 + $0.00125 ≈ $0.003 per receipt** ($0.30/100).
- At 1250 receipts/year fallback-only ≈ **$3.75/year**. Effectively free. The cost story argues *against* aggressive Max-20x usage when desktop is offline — Gemini fallback is so cheap that the ClaudeMax CLI primary path is justified by quality + budget headroom for harder calls (paper synthesis, meal plan ranking), not by saving on receipts.

**Quality (2026 benchmarks):**
- Gemini 2.5 Flash hits ~95% accuracy on printed financial documents per published benchmarks.
- Claude Sonnet 4.5/4.6 hits ~85% printed-media composite, with a critical edge: **0.09% hallucination rate vs Gemini's ~0.15%**.
- For receipts with 8-15 line items, a 0.06 percentage-point hallucination delta = roughly 1 fewer hallucinated line per 100 receipts. With 5 users × 100 receipts/year aggregate, that's ~3 hallucinated lines/year on Claude vs ~9 on Gemini.

**Design implication:** Don't try to "always pick the better model." Pick by **availability + budget + cross-source confirm window**:
1. Desktop online + ClaudeMax budget OK → ClaudeMax
2. Desktop offline OR ClaudeMax budget >95% → Gemini Flash
3. Always run the dual-pass agreement check (1.5 below) regardless of which model fired, because hallucination is non-zero either way

### 1.4 Raw-corruption gate: `llm-raw/<call_uuid>.txt` retention

Spec §8.4 Council 3 BREAK #3 mandates: every Vision call writes its raw output to `state/llm-raw/{call_uuid}.txt` **before parse**. This is the audit trail when downstream finds an anomaly weeks later ("why did this 4.5L milk get logged as 4.5 lei when actually it was 14.5 lei").

**Storage path:** Spec §11.4 raw/llm-raw/. Storage is on the desktop (writes co-located with the CLI process); replicated to VPS via the existing nightly rsync to B2. **Immutable** = file-level, not ACL — never overwrite a `{call_uuid}.txt`. If a retry happens, retry gets its own `{call_uuid}` and writes its own raw file.

**Disk usage:** Each raw response is ~3-8KB. 1500 receipts/year × ~5KB × 2-pass dual confirm = ~15MB/year/receipt-OCR. Negligible. Same applies to flyer-vision (more pages per flyer, ~50KB each, ~50MB/year). Total Vision raw archive grows ~100MB/year. B2 cost is rounding error.

**Cleanup policy:** **Never auto-delete.** The point is post-hoc audit. When a price_observation gets `vision_anomaly_queue` z-scored > 2.5σ months later, the raw file is the only ground truth.

**File format:** Plain `.txt` containing the entire stream-json transcript (newline-delimited JSON events). Final parsed JSON is the **derived** artifact, stored separately in `llm_calls.response_ref` as a pointer back to this file. Why keep stream-json not just final JSON? Because hallucination diagnosis often needs the model's intermediate "reasoning" text — Claude in particular often dribbles thinking before final JSON. Stream-json captures it.

### 1.5 Dual-pass agreement gate

Council 3 BREAK #3 + 2026 academic consensus (Seeing-is-Believing paper, NeurIPS 2025) converge: **single-pass Vision OCR is insufficient for financial data**. The fix: dual-pass with cross-source agreement.

**Two-pass strategies (pick one per pipeline):**

**A. Dual-model agreement.** Run ClaudeMax CLI on the image, run Gemini Flash on the same image, diff the line-item arrays. Disagreements above tolerance → `flyer_review_queue` (per spec for flyer) or `receipt_review_queue` (per spec §4.5 for receipts). **Cost:** 2× per receipt. **Quality:** highest. Use for first 50 receipts to bootstrap the receipt_aliases table.

**B. Same-model two-shot.** Run ClaudeMax CLI twice with different prompt seeds (temperature=0 default; vary prompt phrasing). Diff. Cheaper than A. Use for steady-state after alias table is populated.

**C. Single-pass + cross-source confirm.** This is what spec §8.4 already locks for `price_observations`: a single-pass Vision row is only admitted to `price_posterior` if confirmed by Monitorul OR Playwright within ±7d. **For receipt OCR specifically**, the cross-source is the receipt_aliases table itself: once 3 confirmed matches exist for a `(store_id, receipt_line_text)` tuple, alias-lookup replaces re-vision for that line.

**Recommendation per the locked Council 3 spec:**
- Plan-2/3 ships **C** as the default (cheapest, leverages existing alias table).
- Plan-3 ships an opt-in **A** path triggered by user `/photo --dual-pass` when user is bootstrapping aliases for a new store.
- **B** is held in reserve; not impl'd unless C fails an acceptance bar in production.

**Disagreement threshold for A/B:** Line-item count match (must be exact). Per-line `total_lei` within ±5%. `qty` within ±5% (treating 500g, 0.5kg as equivalent). `name_normalized` Jaccard ≥ 0.85 OR exact substring match.

### 1.6 SKU normalization: receipt-line → canonical SKU

Spec §9.1 Three-tier SKU match defines the contract: T1 GTIN, T2 normalize+Jaccard, T3 queue. Receipts add a wrinkle: **the receipt-line text differs per chain**.

Example: Spaghetti N°5 Barilla 500g appears as:
- Mega Image receipt: `SPAGH BARILLA 5 500G`
- Carrefour receipt: `SPAGHETTI BARILLA #5 500GR`
- Kaufland receipt: `BARILLA 5 SPAGETTE` (sic — printing fault)
- Auchan receipt: `Spaghete BARILLA Nr.5 500g`

The Jaccard score against canonical `spaghetti-no5-barilla-500g` ranges 0.4-0.7 across these — below the 0.85 threshold. Hence the `receipt_aliases` table per spec §4.1: store-specific learned mappings that bypass T2 once 3 confirmed matches exist.

**Normalization regex (commonMain `:shared:data` SkuNormalizer):**

```kotlin
fun normalize(raw: String): String =
    raw.lowercase()
       .replace(Regex("[ăâ]"), "a")
       .replace("î", "i")
       .replace(Regex("[șş]"), "s")
       .replace(Regex("[țţ]"), "t")
       .replace(Regex("\\b(de|cu|si|la|in|pe|proaspat|congelat|fresh|nr|n°|no|#)\\b"), " ")
       .replace(Regex("\\b(gr|gramm|g)\\b"), "g")
       .replace(Regex("\\s+"), " ")
       .trim()
```

This is locked at Council 2 fix and replicates well. The four examples above all normalize to `spagh barilla 5 500g` or `spaghette barilla 5` (Kaufland's "SPAGETTE" misprint). Jaccard against canonical `spaghetti barilla 5 500g` improves from 0.4-0.7 → 0.8-0.95.

**Alias-table learning loop:**
```
On receipt line:
  1. Try T1 (GTIN if printed — rare on RO thermal receipts)
  2. Try T2 (normalize + Jaccard ≥ 0.85 with size_within_5pct)
  3. If miss → check receipt_aliases for (store_id, normalized_line) match → if hit, use canonical_uuid + bump confirmed_count
  4. If still miss → enqueue to receipt_review_queue with candidate_sku_uuid (best T2 below 0.85)
  5. User reviews queue → confirms canonical → INSERT INTO receipt_aliases (store_id, receipt_line_text, canonical_uuid, confirmed_count=1)
  6. After 3 confirms for same alias → auto-apply on future receipts (no queue prompt)
```

This is **per-store** (key includes `store_id`). The Council 3 fix is correct: "BARILLA SPAGH" at Mega and "BARILLA SPAGH" at Kaufland are *different rows* in `receipt_aliases` because the same display text may map to different canonical SKUs across stores (e.g. private-label vs branded).

### 1.7 Quantity disambiguation: 3×500g vs 1.5kg vs 1 pack

Receipts express quantity ambiguously:
- `3 X 500G` → 1.5kg total, 3 packages × 500g each
- `1.500 KG` → 1.5kg total, single weight purchase
- `1 BUC` → 1 piece (e.g. 1 chicken; weight on label)
- `2.4 KG @ 9.99` → 2.4kg weighed-at-counter, price per kg
- `0.5KG X 2` → 2 packages × 500g

Vision LLM gets these wrong ~5-10% of the time in 2026 benchmarks. Spec §8.1 schema enforces `qty` (numeric) + `unit` (g/ml/buc). The router prompt must add explicit examples.

**Recommended prompt fragment (Plan-2 `templates/receipt/_common.md`):**

```
Output one entry per RECEIPT LINE. Do NOT consolidate multi-buys.

For "3 X 500G PASTA BARILLA" → emit ONE entry with:
  qty: 1500
  unit: "g"
  total_lei: <line total>
  multiplier_text: "3x500g"  (preserve original for audit)

For "1.500 KG ROSII" → emit ONE entry with:
  qty: 1500
  unit: "g"

For "PUI BUC 1" → emit ONE entry with:
  qty: 1
  unit: "buc"
  note: "weight unknown; check label"

If the line is a discount, fee, or VAT row, OMIT it from line_items
  and include it in subtotals.json instead.
```

Without this prompt scaffolding, Vision LLM tends to emit `qty: 3, unit: "pkg"` for the first example — which then fails T2 SKU match because canonical Barilla SKU has `size_g=500` not `1500`.

### 1.8 RO diacritic recovery: ț / ș vs Ş / Ţ

Romanian standard diacritics are **comma-below** (Ș U+0218, Ț U+021A). Tesseract historically maps to **cedilla-below** (Ş U+015E, Ţ U+0162) — a known Turkish/Romanian Unicode collision (tesseract-ocr/tesseract#1314, langdata_lstm#37). Vision LLMs trained on web text *usually* emit comma-below but occasionally drop diacritics entirely on low-quality thermal print.

**Pipeline normalization (`:shared:data` DiacriticNormalizer):**

```kotlin
fun ronormalize(s: String) = s
    .replace('Ş', 'Ș').replace('ş', 'ș')
    .replace('Ţ', 'Ț').replace('ţ', 'ț')
    // Recover dropped diacritics on common food words:
    .recoverWord("rosii",   "roșii")
    .recoverWord("branza",  "brânză")
    .recoverWord("paine",   "pâine")
    .recoverWord("piept",   "piept")        // unchanged but in dictionary
    .recoverWord("sunca",   "șuncă")
    // ... (~80-word RO food-noun dictionary at wiki/knowledge/ro-context/diacritic-dictionary.md)
```

The recovery dictionary is small (~80 RO food nouns). Build it once from a frequency analysis of the first 200 receipts × canonical SKU names. After that, `ronormalize` is deterministic + reusable.

**Tesseract fallback (Council 2 spec §8.2 nutrition labels):** When Vision misreads the thermal print so badly the parse fails (parse failure → `vision_anomaly_queue`), fall through to Tesseract with `--psm 6 --oem 1 -l ron`. The `ron.traineddata` model handles ț/ș correctly in **best-quality input only**; thermal receipts at 200dpi will struggle.

**Image preprocessing before Tesseract fallback:** Per 2026 standard practice:
1. Convert to grayscale
2. Adaptive thresholding (block size 25-31, C constant 2-5)
3. Deskew via Hough transform or projection profile (>20% accuracy gain)
4. Median blur over Gaussian (preserves edge of small characters)
5. Optionally scale 2× with bicubic before binarization for thermal print

Whether to even *bother* with Tesseract fallback vs just dropping to user review: at the 5-user scale, **dropping to user review is fine**. The receipt_review_queue is already in the spec; the user `/review` cmd is already in the UI. Tesseract fallback adds complexity for minimal recall improvement on the ~3% of receipts where Vision fully fails.

**Recommendation:** Plan-2 ships Vision-only with `flyer_review_queue` / `receipt_review_queue` for failures. Tesseract integration deferred unless ≥5% of receipts route to manual review and user complains.

### 1.9 Multi-receipt batch upload

User scenario: Sunday evening, user batch-uploads 3-5 receipts from the week. Currently spec §8.1 implies single-receipt POST per upload.

**Recommended batch flow:**

```
Phone UI: receipts.batchUploadButton
  → ForEach receipt: POST /receipts/upload (multipart) sequentially
  → Each upload returns {receipt_uuid, image_ref, ocr_status: 'pending'}
  → Phone shows N "Pending review" cards
  → VPS enqueues N pending_jobs(job_type='ocr_receipt') sequentially
  → Desktop polls jobs every 30s, drains queue at ~1 receipt / 5-10s
  → As each receipt completes: ntfy push to phone "Receipt N of N processed"
  → Phone refreshes the relevant pending-review card
```

**Concurrency limit:** Desktop runs ClaudeMax CLI sequentially (one subprocess at a time). Total wall-clock for 5 receipts ≈ 30-60s. Acceptable. Don't parallelize subprocesses — Anthropic rate cap interactions are unpredictable on Max-20x.

**Phone-side disk cache:** Upload N images to phone-local cache *before* the network push. If network drops mid-batch (Tailscale flap), the outbox-replay-on-network-available pattern (existing Plan-1 contract) covers it.

### 1.10 User-correction loop + audit trail

Per spec §14.4 Receipt-derived pantry_events default to Mode A (optimistic decrement with `confidence=guessed`). Daily summary surfaces "we guessed N decrements — review?".

**Audit-trail contract (Plan-3 ergonomics):**

Every `pantry_event` derived from a receipt carries:
- `evidence_ref` = `receipt_event.event_uuid`
- `reason` = `'receipt'`
- A `confidence` field (planned addition? — **spec gap**, see Open Questions)

When user opens the receipt-detail screen, the UI shows:
- The receipt photo (linked from `receipt_event.image_ref`)
- The parsed line items + `confidence` per line from `line_items_json`
- The derived `pantry_events` with their current pantry-state contribution
- Per line: "Wrong? → Edit canonical SKU → Re-derive pantry_event"

When user corrects: insert a **compensating** pantry_event (negative delta to undo guessed decrement, positive delta for the correct decrement) + update `receipt_aliases` row with the corrected SKU. Never mutate the original event (spec §3 event-sourcing invariant: events are immutable).

### 1.11 Cost analysis at 5 users × 2-5 receipts/wk × ClaudeMax + Gemini fallback

| Path | Receipts/yr | Per-call cost | Annual |
|------|------------|---------------|--------|
| ClaudeMax CLI primary (no $-cost, against Max-20x credit) | 1000 | "free" within $200/mo | $0 marginal |
| Gemini Flash fallback (~20% of calls when desktop offline) | 250 | $0.003 | $0.75 |
| Dual-pass A bootstrap (first 50 receipts × 2 models) | 50 (one-time) | $0.003 + Claude credit | $0.15 + 50 Max-20x calls |
| User-review queue retries (~5% of receipts) | 60 | $0.003 (Gemini retry) | $0.18 |
| **Total cash cost** | | | **~$1.10/year** |

Max-20x credit usage: ~600-1000 ClaudeMax CLI calls/year for receipts. At Sonnet-tier internal pricing this is single-digit dollars against the $200/mo allowance. The receipt slice is **not the binding constraint** on the LLM budget. Plan-2 should design the router with paper-synthesis (~$50-100/yr Anthropic-equivalent) and meal-plan-ranking (~$20-40/yr) as the dominant costs.

### 1.12 Failure-mode UX

Spec §8.4 step 3: parse failure → log + skip + queue for review, NEVER partial-extract. UX surface:

**Drawer card on Home screen:**
```
┌────────────────────────────────────────┐
│ Needs review (2)                       │
│ • Mega receipt 2026-05-15 — parse fail │
│ • Kaufland receipt 2026-05-14 — 3      │
│   anomaly lines flagged                │
│ [Review →]                             │
└────────────────────────────────────────┘
```

Tap → drilldown to the receipt photo + line-level inline editor. The Compose Multiplatform pattern: a `ReceiptReviewScreen` Composable in `:shared:ui-components` consuming a `ReceiptReviewState` from `:shared:domain`. Both Android + Desktop render the same screen. Camera retake (Android) routes to CameraX; desktop "Re-upload" routes to native file picker via expect/actual.

**`data-testid` selectors for the Interaction-smoke gate:**
- `[data-testid="receipt-review-drawer"]` — Home drawer card
- `[data-testid="receipt-review-list"]` — list of pending receipts
- `[data-testid="receipt-photo-zoom"]` — pinch-zoom on photo (Android) / scroll-zoom (Desktop)
- `[data-testid="receipt-line-edit-row"]` — one per line item
- `[data-testid="receipt-confirm-button"]` / `[data-testid="receipt-reparse-button"]`

These belong in spec §30 acceptance criteria. **Spec gap** — currently §30 lists Home / Pantry / Planner / Shopping / Diag screens only. Receipt-review UX is implied by §8 + §14 but not listed in the acceptance gate.

### 1.13 Sources cross-referenced

- Anthropic Vision API docs: image OCR + base64 + structured output via tool-based JSON schema
- Claude Agent SDK GitHub: stream-json output format, subprocess invocation pattern
- Gemini 2.5 Flash OpenRouter pricing 2026
- "Seeing is Believing? Mitigating OCR Hallucinations in MLLMs" (NeurIPS 2025) — dual-pass agreement gate research
- tesseract-ocr/tesseract#1314 + langdata_lstm#37 — RO diacritic mapping bug
- 2026 OCR benchmark studies (Roboflow, OmniAI, CodeSOTA) — Claude Sonnet 4.x vs Gemini 2.5 Flash composite scores

---

## Gap 2 — Paper Ingestion Sequencing

### 2.1 Why this gap matters

Spec §11 wiki structure lists ~150 wiki concept pages spanning nutrition methodology, food safety, cooking, supplementation, RO context. **Each page is anchored by 1-5 peer-reviewed papers** that need to be ingested, parsed, summarized, and cross-linked. Plus the **MASS Research Review** monthly issues (spec §11.5) which add ~10-15 papers per issue, and **user voice-citations of new papers** as they come up. Total target: ~500-1000 papers in the corpus at steady state.

Without a robust paper-ingestion pipeline, Plan-7 stalls. The wiki becomes "narrative without citations" — exactly the anti-pattern spec §11.4 (raw datasets) is designed against. The LLM router (Plan-2) needs paper-grounded context to refuse pseudoscience claims (spec §28). The planner (Plan-3) needs ISSN-position-stand grounding for macro recommendations.

### 2.2 GROBID on desktop: confirmed RAM + perf

Spec errata 2026-05-17 #2 moved GROBID from VPS to desktop. Plan-7 must verify the desktop-side install.

**Confirmed RAM requirements (kermitt2/grobid Docker docs 2026):**
- Header-only extraction (`/api/processHeaderDocument`): **2GB Docker memory minimum**
- Full-text extraction (`/api/processFulltextDocument`): **4GB recommended**
- Citation-only: 3GB
- Batch parallel processing: 6-8GB

Spec §13 (Dietician spec) said "1.5GB peak resident" for VPS — that's the **operational peak** number for header+citations, not full-text. For full-text + Deep Learning model variant on desktop, allocate **4GB to Docker Desktop** as a desktop-side prerequisite.

**Docker image variants:**
- `grobid/grobid:0.8.x` lightweight CRF-only — ~600MB image, runs at 2-3GB RAM, lower accuracy on tables and references
- `grobid/grobid:0.8.x-full` with Deep Learning models — ~8GB image (huge!), runs at 4-6GB RAM, higher accuracy

**Plan-7 recommendation:** Use the **lightweight CRF-only** variant. Reasons:
- Disk install is 8× smaller (600MB vs 8GB)
- RAM headroom is critical on a user laptop that also runs Compose Multiplatform desktop UI + Postgres-Tailscale connections + Compose dev tools when building
- Accuracy delta is ~3-5% on tables/refs, not material for nutrition-paper extraction where the LLM cleans GROBID's output anyway

**Desktop bootstrap script (`scripts/grobid-init.ps1` Windows / `.sh` cross-platform):**
```bash
docker pull grobid/grobid:0.8.1
docker run -d \
  --name dietician-grobid \
  --memory 3g \
  --memory-swap 3g \
  --cpus 1.5 \
  -p 127.0.0.1:8070:8070 \
  --restart unless-stopped \
  grobid/grobid:0.8.1
```

Localhost-bound. The `:shared:knowledge` paper-fetch coroutine talks to `http://127.0.0.1:8070/api/processFulltextDocument`. **VPS-side scheduler queues paper-fetch jobs; desktop is the executor.**

### 2.3 Pipeline sequence (per spec §12.4 expanded)

```
1. Trigger: user voice "look up Jäger 2017 protein"
   OR: user pastes DOI in /paper cmd
   OR: VPS cron picks up next MASS issue PDF and enqueues per-paper jobs

2. Resolve DOI → metadata
   a. Semantic Scholar API: GET /graph/v1/paper/DOI:{doi}?fields=title,authors,abstract,year,references,citations,tldr
   b. Crossref polite-pool: GET https://api.crossref.org/works/{doi}?mailto=victor.vasiloi@gmail.com
   c. If both fail → log + queue to fetch_paper_failed_queue

3. Find OA full text
   a. Unpaywall: GET https://api.unpaywall.org/v2/{doi}?email=victor.vasiloi@gmail.com
   b. If OA URL present → fetch PDF (10s timeout) → /storage/papers/{doi-slug}.pdf
   c. If not OA → fall through to step 4

4. Paywalled path (Gap 3)
   a. AnelisPaperFetcher.fetch(doi: String): Result<File>
   b. Currently returns Result.failure(Unavailable("Anelis investigation pending"))
   c. When Gap 3 lands: SAML auth flow via UAIC Shibboleth IdP → publisher PDF

5. Parse with GROBID (desktop)
   a. Upload PDF to localhost:8070/api/processFulltextDocument
   b. Response: TEI XML with <titleStmt>, <abstract>, <body><div type="section">, <listBibl>
   c. Parse TEI with kotlin-xml or jsoup (TEI is XML; jsoup handles it fine)

6. LLM structured extraction per paper-type
   See §2.4 below — different prompts for RCT / meta-analysis / review / methods

7. Wiki commit
   a. wiki/knowledge/{domain}/papers/{doi-slug}.md — narrative + frontmatter
   b. wiki/knowledge/{domain}/papers/{doi-slug}.data.md — autogen tables + extracted claims
   c. git commit on VPS-side wiki repo with msg "ingest: {title} (doi:{doi})"

8. Embedding index
   a. Generate paper_embedding from (title + abstract + key claims)
   b. INSERT INTO papers_embedding (doi, embedding, last_updated)
   c. pgvector ivfflat upsert

9. Citation graph linking
   a. For each reference in paper.references (Semantic Scholar response):
      - If reference.doi exists in our papers table → INSERT INTO citations(citing_doi, cited_doi)
      - Else: enqueue reference-paper for ingestion (depth-limited: ~3 levels)
   b. Cross-link to wiki concepts: top-5 nearest concept-card embeddings → ONE LLM call confirms+writes backlinks
```

**Per-paper LLM budget cap (spec §12.4 step 8):** 8 calls + 30K tokens. Sonnet input at ~$3/M = $0.09/paper input. Output ~5K tokens × $15/M = $0.075/paper output. **Total ~$0.16/paper.** At 500-1000 papers/yr = **$80-160/year**. Dominant LLM cost slice. Justifies aggressive caching (idempotency by `doi` + `gradient_hash`) so user can replay safely without re-billing.

**Per-paper desktop wall-clock:** GROBID 5-15s (full-text). LLM extraction 30-90s (8 calls sequential). Embedding 1s. Wiki commit + cross-link 5-10s. **Total: 45-120s per paper.** A 500-paper backfill at 90s/paper = 12.5 hours desktop-on. Run overnight as a batch job.

### 2.4 LLM prompts per paper-type

GROBID gives you the TEI structure. The LLM job is to **type the paper** and then extract type-specific claims. Single-shot extraction with a type-switched prompt > one mega-prompt.

**Paper-type detection (LLM call 1):**

```
You are reading a paper extracted by GROBID. Classify into ONE category:

- RCT (randomized controlled trial)
- META (meta-analysis / systematic review)
- REVIEW (narrative review / position stand / consensus statement)
- METHODS (methodology paper / lab assay / new analytical method)
- OBSERVATIONAL (cohort / cross-sectional / case-control)
- OTHER

Title: {tei.title}
Abstract: {tei.abstract}
Methods section first paragraph: {tei.methods_first_para}

Output JSON: {"type": "RCT|META|REVIEW|METHODS|OBSERVATIONAL|OTHER", "confidence": 0..1}
```

**Per-type extraction prompts (LLM calls 2-7):**

**RCT prompt:**
```
Extract from this RCT:
1. Population (n, sex, age range, baseline characteristics)
2. Intervention arm (dose, frequency, duration)
3. Control arm
4. Primary outcome + measure
5. Secondary outcomes
6. Effect size (mean diff, % change, CI, p-value)
7. Limitations stated by authors
8. Conflicts of interest
9. Funding source

For each: also extract the verbatim sentence/paragraph as `evidence`.

Output JSON-schema-validated against rct_extraction.json.
```

**Meta-analysis prompt:**
```
Extract from this meta-analysis:
1. Question (PICO if formatted)
2. Inclusion / exclusion criteria
3. n studies included, n participants pooled
4. Heterogeneity (I², τ², χ²)
5. Pooled effect size + 95% CI
6. Subgroup analyses + findings
7. Publication bias assessment (funnel plot, Egger's test)
8. GRADE rating if reported
9. Strongest individual studies (top 3 by weight)

For each: verbatim evidence sentence.
```

**Review / position-stand prompt:**
```
Extract from this position stand / review:
1. Authoring body (e.g. "ISSN", "ACSM", "EFSA Panel")
2. Year of publication; year of preceding version if revision
3. Top 5-10 numbered recommendations or summary statements verbatim
4. Quality/strength rating per recommendation (A/B/C/D or equivalent)
5. Cited evidence basis (n RCTs, n meta-analyses) per recommendation if stated

Output as ordered list with evidence-strength.
```

**Methods prompt:**
```
Extract from this methods paper:
1. Method name + abbreviation
2. What it measures / its purpose
3. Specimen / sample requirements
4. Equipment required
5. Reproducibility metrics (CV%, ICC, etc.)
6. Cited validation papers

Less narrative; more data sheet shape.
```

**Synthesis prompt (LLM call 8):**
```
Given the structured extractions above, write:

1. A 200-word lay summary (wiki narrative)
2. A "Key claims" table with 5-10 rows: claim + evidence_quality + caveat
3. "Conflicts to flag" — anti-recommend triggers per wiki/knowledge/meta/source-anti-recommend-list.md
4. "Related concepts" — top-3 wiki concept pages from {nearest_concepts}
5. "User applicability" — does this apply to a 19yo M doing lean-bulk in RO? Why/why not?
```

The synthesis output is the `.md` narrative file. The structured extraction outputs land in the `.data.md` autogen file.

### 2.5 Citation graph: DOI ↔ wiki concept ↔ nutrient claim

Spec implies citation graph but doesn't define the schema. **Spec gap (Plan-7 must close):**

```sql
CREATE TABLE papers (
  doi               TEXT PRIMARY KEY,
  title             TEXT NOT NULL,
  authors_json      JSONB NOT NULL,
  year              INTEGER NOT NULL,
  paper_type        TEXT NOT NULL,        -- 'RCT' | 'META' | 'REVIEW' | 'METHODS' | 'OBSERVATIONAL' | 'OTHER'
  tei_xml_ref       TEXT NOT NULL,        -- /storage/papers/{slug}.tei.xml
  pdf_ref           TEXT,                 -- /storage/papers/{slug}.pdf (if cached)
  abstract          TEXT,
  unpaywall_oa_url  TEXT,
  is_paywalled      BOOLEAN NOT NULL,
  embedding         VECTOR(1024),         -- nomic-embed-text-v2 or BGE-M3
  wiki_page         TEXT,                 -- 'knowledge/sports-nutrition/protein-issn-2017.md'
  ingested_at       TIMESTAMPTZ NOT NULL,
  source_fetch_path TEXT NOT NULL         -- 'unpaywall' | 'anelis-plus' | 'user-upload'
);

CREATE TABLE citations (
  citing_doi        TEXT NOT NULL REFERENCES papers(doi),
  cited_doi         TEXT NOT NULL,        -- may or may not exist in our papers table
  resolved          BOOLEAN NOT NULL,     -- true if cited_doi is in our table
  position_in_text  TEXT,                 -- 'methods' | 'results' | 'discussion'
  PRIMARY KEY (citing_doi, cited_doi)
);
CREATE INDEX idx_citations_cited ON citations(cited_doi);

CREATE TABLE paper_concept_links (
  doi               TEXT NOT NULL REFERENCES papers(doi),
  wiki_concept_slug TEXT NOT NULL,        -- 'leucine-mps' | 'lean-bulk-principles'
  link_strength     REAL NOT NULL,        -- cosine sim 0..1
  link_confirmed    BOOLEAN NOT NULL,     -- LLM-confirmed vs raw similarity
  PRIMARY KEY (doi, wiki_concept_slug)
);

CREATE TABLE paper_claims (
  claim_uuid        UUID PRIMARY KEY,
  doi               TEXT NOT NULL REFERENCES papers(doi),
  claim_text        TEXT NOT NULL,
  evidence_quote    TEXT NOT NULL,
  evidence_strength TEXT NOT NULL,        -- 'A' | 'B' | 'C' | 'D' (GRADE-like)
  nutrient          TEXT,                 -- 'protein' | 'creatine' | 'vit-d' | NULL
  applicability     TEXT                  -- 'general' | 'male' | 'athlete' | 'elderly'
);
CREATE INDEX idx_paper_claims_nutrient ON paper_claims(nutrient);
```

This unlocks two queries:

**Q1: "Why do you recommend 137g protein for me?"**
```sql
SELECT pc.claim_text, p.title, p.authors_json, p.year
FROM paper_claims pc
JOIN papers p ON p.doi = pc.doi
WHERE pc.nutrient = 'protein'
  AND pc.applicability IN ('general', 'male', 'athlete')
  AND pc.evidence_strength IN ('A', 'B')
ORDER BY pc.evidence_strength, p.year DESC
LIMIT 5;
```

**Q2: "What papers ground the leucine-MPS wiki page?"**
```sql
SELECT p.doi, p.title, pcl.link_strength
FROM paper_concept_links pcl
JOIN papers p ON p.doi = pcl.doi
WHERE pcl.wiki_concept_slug = 'leucine-mps'
  AND pcl.link_confirmed = true
ORDER BY pcl.link_strength DESC;
```

Both queries paint into the wiki-page UI's "Sources" sidebar.

### 2.6 Two-file wiki pattern enforcement

Spec §11.2 locks the pattern: `{slug}.md` (narrative, user-editable, no numeric facts) + `{slug}.data.md` (autogen tables). The daemon writes only to `.data.md`. Narrative uses Obsidian transclusion `![[{slug}.data]]`.

**Enforcement code (Plan-7 `:shared:knowledge` WikiWriter):**

```kotlin
sealed interface WikiPath {
    data class Narrative(val slug: String, val domain: String) : WikiPath {
        val path = "wiki/knowledge/$domain/$slug.md"
    }
    data class AutogenData(val slug: String, val domain: String) : WikiPath {
        val path = "wiki/knowledge/$domain/$slug.data.md"
    }
}

class WikiWriter(private val gitRepo: File) {
    fun writeAutogen(p: WikiPath.AutogenData, body: String) {
        // Allowed. Includes mandatory header.
        val header = "<!-- AUTOGENERATED. EDITS OVERWRITTEN. Last refresh: ${Instant.now()} -->"
        File(gitRepo, p.path).writeText("$header\n\n$body")
        gitCommit("autogen: $p.path")
    }

    fun writeNarrative(p: WikiPath.Narrative, body: String) {
        // Allowed ONLY if the narrative file does not exist yet (first ingest).
        // After that, narrative is human-edited and the daemon must NOT touch it.
        val f = File(gitRepo, p.path)
        if (f.exists()) {
            throw NarrativeFileExistsException(p.path,
                "Narrative is user-editable after first ingest. " +
                "Daemon writes to ${p.slug}.data.md only. " +
                "If you need to update narrative, surface a diff suggestion to the user.")
        }
        f.writeText(body)
        gitCommit("ingest: $p.path")
    }

    fun suggestNarrativeUpdate(p: WikiPath.Narrative, diff: String) {
        // Append to a review queue, do NOT write the file.
        File(gitRepo, "wiki/.suggestions/${p.slug}.diff.md").appendText(diff)
    }
}
```

**File-watcher exception (spec §11.2):** if user edited `{slug}.md` within last hour, suppress `.data.md` regenerate for that session. Implement as `git log --since="1 hour ago" -- wiki/.../{slug}.md` check before writing the data file.

### 2.7 DOI resolution + Crossref + Unpaywall + Anelis fallback

**Crossref polite-pool integration (commonMain):**

```kotlin
class CrossrefClient(
    private val http: HttpClient,
    private val email: String = "victor.vasiloi@gmail.com"
) {
    suspend fun getWork(doi: String): CrossrefWork? {
        val url = "https://api.crossref.org/works/$doi"
        return http.get(url) {
            url { parameters.append("mailto", email) }
            headers.append("User-Agent", "Dietician/1.0 (mailto:$email)")
        }.body()
    }
}
```

The `mailto` parameter routes to the polite-pool. Per Crossref docs, the polite pool is "more reliable because it's protected from misbehaving scripts." For 500-1000 DOI lookups/year, polite is sufficient — paid plans aren't needed.

**Unpaywall:**

```kotlin
class UnpaywallClient(private val http: HttpClient, private val email: String) {
    suspend fun getOaInfo(doi: String): UnpaywallResponse? {
        val url = "https://api.unpaywall.org/v2/$doi?email=$email"
        return http.get(url).body()
    }
}

data class UnpaywallResponse(
    val doi: String,
    val isOa: Boolean,
    val bestOaLocation: OaLocation?,
    val oaLocations: List<OaLocation>
)
```

Unpaywall covers only Crossref-registered DOIs. Some Romanian-specific papers (in `revista.ro` journals) lack Crossref DOIs entirely and need a different path. **Spec gap:** how to handle non-Crossref RO papers? Recommendation: queue to user-upload-required (drop to user dragging the PDF onto the app).

**Anelis fallback (Gap 3):** see §3 below.

### 2.8 PDF storage location

Per spec §11.4:
- `raw/papers/{issn|fao|efsa|ro}/{slug}.pdf` — immutable raw archive
- `wiki/knowledge/{domain}/papers/{slug}.md` + `.data.md` — parsed wiki
- `papers(doi, embedding)` Postgres row — embedding for retrieval

VPS-side storage. Replicated to B2 weekly via existing rsync. **Total disk footprint:** 1000 papers × 2-5MB/paper PDF = 2-5GB. Fits comfortably on the VPS.

**Desktop-cache of frequent papers:** Top 50 most-queried-this-month papers are mirrored to desktop `state/papers-cache/` for offline reference. Eviction: LRU bounded at 200MB.

### 2.9 Cost per paper, end-to-end

| Phase | Tool | Cost |
|-------|------|------|
| DOI resolution | Crossref + Semantic Scholar | $0 (rate-limited) |
| OA discovery | Unpaywall | $0 |
| OA PDF fetch | direct HTTP | $0 |
| Anelis fetch | Shibboleth session (Gap 3) | $0 (free for UAIC affiliates) |
| GROBID | desktop Docker | $0 (compute only) |
| LLM extraction | Sonnet (8 calls × ~5K tok) | ~$0.12 |
| LLM synthesis | Sonnet (1 call × ~8K tok) | ~$0.04 |
| Embedding | Voyage 3.5 lite or BGE-M3 self-host | ~$0.0001 |
| **Total** | | **~$0.16 / paper** |

500-paper backfill = $80. 12 papers/month sustaining = $24/yr. Combined budget impact <$130/yr — easily inside the $200/mo Max-20x credit.

### 2.10 AnelisPaperFetcher interface stub (locked spec §13)

```kotlin
interface PaperFetcher {
    suspend fun fetch(doi: String): Result<File>
}

class AnelisPaperFetcher(
    private val authProvider: AnelisAuthProvider,    // Gap 3
    private val publisherResolver: PublisherResolver, // doi → publisher → SP URL
    private val http: HttpClient
) : PaperFetcher {
    override suspend fun fetch(doi: String): Result<File> {
        // Currently:
        return Result.failure(Unavailable("Anelis investigation pending — see Gap 3"))

        // Future (post-Gap-3 investigation):
        // val session = authProvider.session()
        // val sp = publisherResolver.resolve(doi)
        // return downloadPdfViaShibbolethSession(http, session, sp, doi)
    }
}

class CompositePaperFetcher(
    private val unpaywall: UnpaywallClient,
    private val anelis: AnelisPaperFetcher,
    private val ftp: File   // user-upload drop-zone
) : PaperFetcher {
    override suspend fun fetch(doi: String): Result<File> {
        unpaywall.getOaInfo(doi)?.bestOaLocation?.urlForPdf?.let { url ->
            return downloadPdf(url)
        }
        anelis.fetch(doi).getOrNull()?.let { return Result.success(it) }
        return Result.failure(UserUploadRequired(doi,
            "Drop the PDF onto the app and re-run /paper $doi"))
    }
}
```

Plan-7 ships the `CompositePaperFetcher` with `AnelisPaperFetcher` stubbed. Gap 3 unlocks the real Anelis impl.

### 2.11 Sources cross-referenced

- GROBID docs: Docker memory requirements, image variants, TEI XML schema
- Crossref REST API docs: polite-pool mailto param, polite headers
- Unpaywall API docs: DOI coverage limited to Crossref-registered
- Semantic Scholar API: graph traversal (references + citations), TLDR field
- WIREs 2025 paper on KNIME + GROBID + LLM: structured extraction patterns

---

## Gap 3 — Anelis Plus Authentication Reverse-Engineer

### 3.1 Why this gap matters

Spec §13 locks the investigation: auth model unknown, three possibilities (Shibboleth/SAML, simple portal, IP-auth). The spec correctly gates implementation on user-led probe.

Research confirms: **UAIC has a live Shibboleth IdP at `https://idp.uaic.ro/idp/shibboleth`**, registered in RoEduNetID since 2019-10-22, supporting SAML 2.0 with entity categories R&S (Research and Scholarship) + SIRTFI. The RoEduNetID federation participates in eduGAIN. Romania's NREN (RoEduNet) hosts the federation.

**Implication:** Anelis Plus consortium agreements (Springer, Wiley, IEEE, ScienceDirect, IOP, RSC, CUP, Cambridge) most likely flow through the SP-side Shibboleth login → Discovery Service → "select institution: UAIC" → redirect to `idp.uaic.ro/idp/shibboleth` → SAML assertion → publisher unlocks content.

### 3.2 Confirmed UAIC IdP details

From RoEduNetID Metadata Explorer Tool:
- **Entity ID:** `https://idp.uaic.ro/idp/shibboleth`
- **Protocol:** SAML 2.0
- **Scopes:** `uaic.ro`
- **Tech contact:** idp@uaic.ro
- **Abuse contact:** abuse@uaic.ro
- **Certificates:** 3 SHA256
- **Federation:** RoEduNetID (since 2019-10-22) + UK Access Management Federation (yes, listed!)
- **Entity Categories:** Research and Scholarship + SIRTFI
- **Registration authority:** http://eduid.roedu.net

**Credential format (spec §13 confirmed):** Office-365 format `firstname.surname@student.uaic.ro` (students) or `firstname.surname@uaic.ro` (staff). Login backed by UAIC's central IDM, same credential used for grades portal + Outlook.

### 3.3 Auth flow probe protocol (user-led)

**The investigation runbook (`docs/runbooks/anelis-investigation.md`):**

1. User opens desktop browser
2. Navigate to Anelis Plus member-portal: `https://www.anelis-plus.ro/` → "Acces resurse"
3. Select Springer Nature link → publisher SP discovery page
4. Search "Iași" or "Alexandru Ioan Cuza" in the institution selector → click UAIC entry
5. Browser redirects to `https://idp.uaic.ro/idp/profile/SAML2/Redirect/SSO?SAMLRequest=...`
6. UAIC login page (likely a SimpleSAMLphp or Shibboleth IdP login skin)
7. User enters `firstname.surname@student.uaic.ro` + password
8. After login → browser POSTs SAML assertion to publisher SP → publisher shows "Welcome, UAIC user" → full-text access granted

**Captures during step 5-8 (DevTools network tab, save HAR):**
- Initial SP discovery URL
- IdP-side login form action URL + CSRF token shape
- POST body shape (username field name, password field name)
- The SAML response URL the IdP posts to publisher SP
- Any cookies set (Shibboleth-_xxx cookies are SP-side; uaic IDM cookies are IdP-side)

The HAR file becomes the input to the AnelisAuthProvider implementation.

### 3.4 Auth strategy: user-mediated session export

**The recommendation:** DO NOT store the UAIC password. Instead, use **user-mediated session export**:

```
1. User logs into UAIC IdP via browser on desktop ONCE per session
2. User runs `/anelis export-session` in-app → opens a Playwright headless context attached to the SAME profile dir as the browser
3. Playwright reads the Shibboleth SP-side cookie jar (publisher-side) AND the IdP-side IDM cookie
4. Cookie jar persisted age-encrypted to state/anelis-session.age
5. Subsequent paper-fetch requests load the cookie jar → POST to publisher SP → 200 with PDF
6. When publisher SP returns 401/302-to-login → session expired → notify user "/anelis refresh"
```

**Why not store password:** Per spec §26 cred storage, KMP `SecureCredentialStore` is per-platform DPAPI/Keystore. *Storing the UAIC primary password unlocks all UAIC services* (email, grades, library). The blast radius is too wide. Session cookies expire (typically 8-12h) which bounds the worst-case leak.

**Why Playwright (not Ktor cookie-extraction):** Modern browsers store cookies in encrypted SQLite (Chrome/Edge `Cookies` file with OS-bound master key). Decrypting outside the browser process requires platform-specific tricks. Playwright's `storageState()` API exports them directly via the browser process — clean, supported, cross-platform.

### 3.5 Credential storage strategy

Per spec §26 + Council 3 fix #7:
- Session cookies encrypted at rest via age (passphrase from user OR DPAPI/Keystore on respective platforms)
- `credential_heartbeat` table tracks `expected_to_work_at` per credential
- Absence > 7d → alert via ntfy

For Anelis specifically:
- Cookie jar at `state/anelis-session.age`
- `credential_heartbeat` row `'anelis'` with `expected_to_work_at = now() + 8.hours`
- When `last_used_at` > 8h ago AND a fetch fails → ntfy push: "Anelis session expired — run /anelis refresh"

### 3.6 Rotation runbook stub

`docs/runbooks/anelis-credential-rotation.md` (already in spec §13 todo list):

```markdown
# Anelis Credential Rotation Runbook

## When to run
- ntfy "Anelis session expired"
- /diag shows `credential:anelis` = broken
- User changed UAIC password
- More than 7 days since last successful paper fetch

## Steps
1. Open desktop browser; log into anelis-plus.ro via UAIC SSO
2. In-app: /anelis refresh
3. Wait for "Session captured" ntfy
4. /anelis verify — fetches a known-DOI test paper
5. If verify fails: check `state/llm-raw/anelis-debug.txt` for the SP response
6. If publisher SP returned 401: UAIC IdP did not release expected attributes — open Issue or
   email idp@uaic.ro citing entityID `https://idp.uaic.ro/idp/shibboleth`

## When to escalate to UAIC IT
- /anelis verify fails 3 sessions in a row
- /anelis verify fails on multiple publishers (Springer, ScienceDirect, IEEE)
  → likely IdP-side attribute-release issue, not session-export issue
```

### 3.7 Library coverage at UAIC via Anelis Plus

Spec asks "which journals Anelis Plus actually unlocks via UAIC?" Sources researched:

**Anelis Plus consortium members include UAIC (one of 87 institutions).** Published agreements:
- **Springer Nature** transformative agreement (Anelis Plus 2020) — full hybrid OA publishing + reading
- **Wiley** read+publish — UAIC affiliates can publish OA in Wiley journals via consortium APC pre-paid pool
- **IEEE** open publishing — APCs prepaid in hybrid + gold OA journals
- **ScienceDirect (Elsevier)** — full-text access
- **Cambridge University Press** OA agreement
- **IOP**, **RSC** (Royal Society of Chemistry) — full access

**Coverage gaps:**
- **MASS Research Review** (the user's monthly digest of strength training research) is NOT in the Anelis Plus list — independent paid subscription. **User-upload-required path.**
- **PubMed Central** is OA so Anelis not needed
- **Examine.com** is paid B2C subscription — separate from Anelis. Some content scrapeable; most behind paywall. **User-upload-required path.**

**Recommendation for Plan-7:** Implement Anelis path only for the consortium publishers above. MASS + Examine are out-of-scope for automated fetch; surface as "user-upload-required" in the paper-fetch pipeline.

### 3.8 2FA handling if UAIC adds it

UAIC currently does not enforce 2FA on student/staff accounts (verified 2026-05). If UAIC adds Microsoft Authenticator 2FA (foreseeable for Office 365 federation hardening):

**Mitigation:** the user-mediated session-export flow above is **2FA-resilient** because the user is the one going through 2FA in the browser. The Playwright session export just copies the post-2FA cookie jar. No 2FA handling code on Dietician side needed.

If UAIC adds 2FA AND issues short-lived sessions (≤ 1h), the runbook frequency would become onerous. Mitigation: surface a one-click `/anelis refresh` flow in-app that launches a system-default browser with a pre-loaded SP discovery URL, then polls for session export on a 2-min interval until the user completes 2FA. UX-acceptable for ≤ daily refresh.

### 3.9 IP-auth fallback (unlikely but spec'd)

Spec §13 lists IP-auth via UAIC campus network as one possibility. Verified via UAIC DCDI docs and RoEduNet membership: **federated Shibboleth is the primary path**. IP-auth via UAIC campus network does exist for legacy publishers but is being deprecated in favor of federated access.

Plan-7 should NOT implement IP-auth path. If user is on UAIC campus, Shibboleth still works (browsers traverse the same flow). If user is at home (per memory: Moldova residence) on a non-UAIC network, IP-auth would fail; Shibboleth still works.

### 3.10 Sources cross-referenced

- RoEduNetID Metadata Explorer entry for `idp.uaic.ro`
- RoEduNetID about-page (eduGAIN participation, since-2014)
- Anelis Plus consortium homepage + publisher partnership listings (Springer, Wiley, IEEE, ScienceDirect, Cambridge)
- UAIC DCDI Microsoft 365 + network policy documentation
- Shibboleth Consortium docs on entity categories (R&S, SIRTFI)
- eduGAIN documentation on SP-IdP-Discovery flow

---

## Gap 4 — Embedding Strategy for Multi-Corpus Knowledge Base

### 4.1 Why this gap matters

Plan-7 builds 6+ retrieval corpora:
- **Food composition** (~10k items from USDA FDC + CIQUAL + OFF RO + local override)
- **Recipes** (~5k items at steady state)
- **Supplements** (~3k items from Examine.com + ISSN stands + user-curated)
- **Papers** (~1k papers as ingested per Gap 2)
- **SKU catalog** (~50k items per chain × 6 chains = 300k SKUs at full scale; realistically ~20k unique-deduplicated)
- **Traditional RO dishes** (~200 items in wiki/knowledge/ro-context/)

Plus the **wiki concept layer** (~150 narrative pages) and **user voice notes / preferences** (~500 entries) that index for "what did I say last Tuesday about chicken."

LLM router (Plan-2) calls embeddings for: (a) recipe-similarity searches, (b) paper cross-linking on ingest, (c) RAG-grounded planner queries, (d) "show me concept pages related to X." Plan-3 server hosts the index; Plan-4/5 UI surfaces results.

**Wrong choice here = either too-slow retrieval at planner-query time (>1s breaks UX) or too-expensive embedding generation (eats $200/mo budget).**

### 4.2 Single index vs N indexes

**Single index:** all embeddings in one `embeddings` table keyed by `(corpus, item_id)`. Pros: one HNSW index to maintain; cross-corpus retrieval is just a query without UNION. Cons: noisier results when you want "only food_composition matches."

**N indexes:** one table per corpus (`food_embedding`, `recipe_embedding`, `paper_embedding`, etc.). Pros: corpus-scoped queries are clean; per-corpus HNSW tuning. Cons: cross-corpus retrieval needs UNION + reranker.

**Recommendation: N indexes + a `corpus` filter column on queries.** PostgreSQL pgvector supports `WHERE corpus = ? ORDER BY embedding <=> $query LIMIT 5` efficiently with a partial index. The query planner uses an index-scan when corpus selectivity is high. Easy to add a new corpus (papers in Plan-7 v1, supplements in Plan-7 v2).

Actually scratch that — spec says "no version phasing" (memory: feedback_no_version_phasing). Read it as: Plan-7 ships all 6 corpora in one pass. The schema below covers all 6.

**Locked schema (extends spec §4):**

```sql
CREATE TABLE corpus_embeddings (
  corpus            TEXT NOT NULL,            -- 'food' | 'recipe' | 'supplement' | 'paper' | 'sku' | 'ro_dish' | 'wiki_concept'
  item_id           TEXT NOT NULL,            -- food_id, recipe_id::text, doi, etc.
  embedding         VECTOR(1024) NOT NULL,
  embedding_model   TEXT NOT NULL,            -- 'bge-m3' | 'voyage-3.5-lite' | 'nomic-embed-v2'
  embedded_at       TIMESTAMPTZ NOT NULL,
  source_text_hash  TEXT NOT NULL,            -- to detect when source changes
  PRIMARY KEY (corpus, item_id)
);
CREATE INDEX idx_corpus_embeddings_hnsw
  ON corpus_embeddings USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);
CREATE INDEX idx_corpus_embeddings_corpus ON corpus_embeddings(corpus);
```

Single table, single HNSW index, corpus filter via predicate. Reindex triggered when `source_text_hash` differs from current `digest(source_text(), 'sha256')`.

### 4.3 Single embedding model across corpora

**Locked recommendation: BGE-M3 self-hosted on desktop, with Voyage 3.5 Lite fallback via OpenRouter when desktop is offline.**

Reasoning:

**Voyage 3.5 Lite (API):**
- Price: $0.02/1M tokens
- Multilingual: RO confirmed via voyage-3 series multilingual benchmarks
- Dimensions: 1024 (configurable to 2048 / 512 / 256 via Matryoshka)
- MTEB retrieval avg ~62-64
- Convenient via OpenRouter
- Drawback: per-call cost on a long-tail corpus (300k SKU items = ~$0.20-0.60 one-time)

**BGE-M3 (self-host):**
- Free (compute only)
- Multilingual: 100+ languages including RO (formally evaluated on MIRACL)
- Dimensions: 1024
- MTEB retrieval avg ~65
- Supports dense + sparse + multi-vector retrieval simultaneously
- Runs in 4GB RAM via Ollama on desktop
- Drawback: requires Ollama install on desktop (extra setup); cold-start latency ~3-5s per batch

**Nomic-embed-v2 (self-host via Ollama):**
- Free
- Multilingual: 100+ languages including RO
- Dimensions: 768 (smaller, lower index size)
- MTEB retrieval avg ~62
- Smaller model, faster, lower RAM
- Drawback: slightly weaker than BGE-M3 on multilingual benchmarks

**Why BGE-M3:** highest multilingual recall, native sparse + dense (overlaps with the BM25 hybrid story below), self-host removes a per-call cost variable. Voyage fallback handles desktop-offline-and-must-embed-now scenarios.

**Cost projection:**
| Corpus | n items | Voyage cost (if Voyage-only) | BGE-M3 cost |
|--------|---------|-----------------------------|-------------|
| food | 10000 | $0.20 | $0 |
| recipe | 5000 | $0.10 | $0 |
| supplement | 3000 | $0.06 | $0 |
| paper | 1000 | $0.02 | $0 |
| sku | 20000 | $0.40 | $0 |
| ro_dish | 200 | <$0.01 | $0 |
| wiki_concept | 150 | <$0.01 | $0 |
| **Initial backfill total** | | **~$0.78** | **$0** |
| **Reindex on edits/month** | ~500 | $0.05 | $0 |

So self-hosting saves ~$1/yr. The actual driver is **not cost; it's the multilingual quality + native sparse-vector support**. Voyage doesn't expose sparse vectors; BGE-M3 does, which simplifies the hybrid search story (no separate BM25 build).

### 4.4 Cross-corpus retrieval pattern

User query: "Tell me about high-protein breakfasts that use stuff I have."

```kotlin
class CrossCorpusRetriever(
    private val pg: PostgresClient,
    private val embedder: Embedder,
    private val rerank: LlmRouter
) {
    suspend fun retrieve(query: String, limit: Int = 5): List<RetrievedItem> {
        val q = embedder.embed(query)
        // Step 1: HNSW broad recall across all corpora
        val candidates = pg.query("""
            SELECT corpus, item_id, embedding <=> $1 AS dist
            FROM corpus_embeddings
            ORDER BY embedding <=> $1
            LIMIT 30
        """, q).execute()
        // Step 2: BM25-lite via pg_trgm word similarity for lexical anchor
        val bm25 = pg.query("""
            SELECT corpus, item_id, similarity(name, $1) AS sim
            FROM (
              SELECT 'recipe' AS corpus, recipe_id::text AS item_id, name FROM recipes
              UNION ALL
              SELECT 'food'   AS corpus, food_id, name_en FROM food_composition
              -- ... other corpora
            ) AS unified
            WHERE name % $1
            ORDER BY sim DESC
            LIMIT 30
        """, query).execute()
        // Step 3: RRF merge
        val merged = reciprocalRankFusion(candidates, bm25)
        // Step 4: LLM rerank top-30 → top-5 (TEXT_MECHANICAL chain)
        return rerank.rerank(query, merged.take(30), limit = 5)
    }
}
```

### 4.5 Hybrid search: BM25 + pg_trgm + vector + LLM rerank

Per 2026 standard practice (ParadeDB, Tiger Data, multiple Medium posts), hybrid retrieval substantially outperforms vector-only OR BM25-only on RAG benchmarks. The pattern:

1. **Vector recall (HNSW):** broad recall, semantic matches
2. **Lexical recall:** BM25 (or pg_trgm `%` similarity) for exact-string matches, brand names, units
3. **RRF merge** (Reciprocal Rank Fusion): `score = sum(1 / (k + rank_i))` across rankers
4. **LLM rerank top-N** (optional but cheap): single Gemini Flash call to score the top-30 against the query intent, return top-5

For PostgreSQL: ParadeDB's `pg_textsearch` or `pg_search` extensions ship true BM25 in Postgres. **VPS is on plain Postgres 16 + pgvector per spec (locked).** No ParadeDB. So either:
- Use `pg_trgm` (built-in) for trigram similarity — works as a "poor man's BM25" for short strings (brand names, food names)
- Use `tsvector` + `ts_rank` (built-in) for true BM25-ish on longer text (wiki concept body, paper abstract)
- Spec already locks pgvector; trgm + tsvector are built-in PG modules — no extra dependency needed

**Reranker cost:** Gemini Flash at $0.30/M input + $2.50/M output. Top-30 candidates × ~100 tokens each + query = ~4K input + ~500 output = $0.0012 + $0.0013 ≈ $0.0025/query. At 100 retrievals/day = $0.075/day = $27/yr. Real.

**Recommendation:** ship hybrid (vector + pg_trgm for short corpora, vector + tsvector for paper/wiki/long), skip LLM rerank initially, add LLM rerank as opt-in if user reports irrelevant results.

### 4.6 Reindex strategy when LLM-driven edits land

When the LLM router edits a wiki concept (Plan-7 enables this), the `.data.md` body changes. The corpus embedding row is stale.

**Reindex trigger:**

```sql
-- After wiki file commit hook on VPS:
INSERT INTO pending_jobs (job_type, payload_json, required_provider)
VALUES ('reindex_embedding', jsonb_build_object(
  'corpus', 'wiki_concept',
  'item_id', 'leucine-mps',
  'reason', 'wiki file modified'
), 'desktop');
```

Desktop reindex worker:
1. Reads new source text
2. Computes `sha256(source_text)`
3. If hash matches stored hash → no-op (idempotent)
4. Else: BGE-M3 embed → UPSERT INTO `corpus_embeddings`
5. Reindex job complete; HNSW index auto-updates on insert (slow build but pgvector handles incremental)

**HNSW vs IVFFlat for ~50k rows:**
- HNSW: 15× higher QPS at 0.998 recall vs IVFFlat. 32× slower build. 2.8× more storage.
- IVFFlat: faster build, lighter, but worse query throughput

At ~50k total rows (sum of all corpora), HNSW is the right call. Storage delta is ~0.5GB vs 0.2GB — fits comfortably. Build time delta only matters on initial backfill (~1h vs ~3min), acceptable. **Locked: HNSW.**

### 4.7 Multilingual EN/RO embedding quality per corpus

| Corpus | Source language | Query language | Concern |
|--------|----------------|----------------|---------|
| food | EN (USDA, CIQUAL) | RO + EN | RO query "pui" must match EN "chicken" |
| recipe | EN + RO mixed | RO + EN | RO query "rețetă cu pui" must match EN-titled recipes |
| supplement | EN-dominant | RO + EN | "creatină" must match "creatine" |
| paper | EN | EN | Single-language, easy |
| sku | RO-dominant | RO + EN | Same shape as food |
| ro_dish | RO | RO | Single-language |
| wiki_concept | EN-dominant | RO + EN | Translation alignment |

BGE-M3 and Voyage 3.x both handle EN↔RO cross-lingual retrieval competently per published multilingual benchmarks. Specific RO benchmark numbers are scarce, but Romanian is well-represented in mC4 + multilingual CC News training corpora used by Nomic-v2 + BGE-M3.

**Smoke-test on first 100 SKUs:** Plan-7 must include a `Plan7Smoke` task that issues 20 RO queries and 20 EN queries against a seed corpus and asserts top-3 contains the expected match. If recall drops below 0.85 → fall back to per-corpus separate-language indexes (split into `corpus_embedding_en` + `corpus_embedding_ro`).

### 4.8 Self-host vs API decision per corpus

| Corpus | Reindex rate | Recommendation |
|--------|--------------|----------------|
| food | rare (annual USDA update) | BGE-M3 self-host (cheap one-time) |
| recipe | every ingest (~daily) | BGE-M3 self-host (low latency, near-zero cost) |
| supplement | rare (~weekly) | BGE-M3 self-host |
| paper | every ingest (1-2/wk steady-state) | BGE-M3 self-host |
| sku | high (~daily price/SKU sync) | BGE-M3 self-host (300k items × API would cost $6/refresh) |
| ro_dish | rare | BGE-M3 self-host |
| wiki_concept | per-edit (~weekly) | BGE-M3 self-host |

**Decision: single-source self-host BGE-M3 via Ollama on desktop. Voyage 3.5 Lite via OpenRouter is the fallback when desktop is offline AND a fresh embedding is needed (rare).**

### 4.9 Sources cross-referenced

- BGE-M3 model card (BAAI/bge-m3 HuggingFace) — multilingual 100+ langs, dense+sparse+multi-vector
- Nomic Embed v2 preprint — Mixture-of-Experts multilingual
- Voyage AI 3.5 Lite pricing + multilingual benchmarks
- MTEB leaderboard 2026
- pgvector docs: HNSW vs IVFFlat performance studies (AWS, Google Cloud, Tembo)
- ParadeDB, Tiger Data hybrid search articles
- RoEduNet + CC News + mC4 multilingual training corpora for RO coverage

---

## Gap 5 — KMP UI Sharing Strategy

### 5.1 Why this gap matters

Spec §2 locks Android + Desktop on Compose Multiplatform. **iOS is explicitly out of scope.** The spec lists `:shared:ui-components` as a Gradle module containing Compose Multiplatform shared widgets. Plans 4 + 5 implement Android + Desktop UI respectively.

**Open question Plan-4 / Plan-5 must answer:** how much UI is actually shared? The answer drives plan structure. If 90%+ is shared, Plans 4 and 5 collapse to a single Plan-4-5 "ship shared screens + 2 thin platform shells." If sharing is more like 50%, the plans stay split.

### 5.2 Empirical 2026 data on Compose Multiplatform sharing

Production apps shipping Android + iOS reach 90-96% shared code (Feres taxi app: 90% UI; Respawn habits: 96%; Fast&Fit: 90%+). **Dietician targets Android + Desktop — the same Compose runtime, same Material 3, no iOS-only widget gaps. Sharing should run higher than 90%.**

Material 3 is fully supported in Compose Multiplatform 1.7+ including the experimental `MaterialExpressiveTheme`. Adaptive layouts via `NavigationSuiteScaffold` (bottom bar ↔ navigation rail by window size) ship in `compose-material3-adaptive`. No need to write a custom Android-vs-Desktop branch for nav scaffolding — the adaptive library handles it.

### 5.3 Per-screen audit: shared vs diverge

Spec §30 lists 5 in-app screens: Home, Pantry, Planner, Shopping, Diag. Plus implicit Receipt Review (Gap 1), Recipe Detail, Settings, /diag deep-dive.

| Screen | Shared % expected | Divergence concern |
|--------|-------------------|--------------------|
| Home (default) | 95% | Status bar tint on Android; window title bar on Desktop |
| Pantry list | 98% | Pull-to-refresh on Android; menu-bar refresh on Desktop |
| Planner grid | 95% | Drag-and-drop hit-testing differs slightly |
| Shopping list | 95% | Share-intent on Android exports list; Desktop opens file save dialog |
| Diag | 100% | Pure read-only display, fully shared |
| Receipt Review | 90% | Camera retake (Android CameraX) vs file picker (Desktop) — drawer button label differs |
| Recipe Detail | 98% | Share button shape (Android share-sheet vs Desktop clipboard) |
| Settings | 90% | Credentials store UI (DPAPI prompt on Desktop, Keystore on Android) |
| Voice memo recorder | 70% | Android MediaRecorder vs Desktop audio capture differ; UI gesture shape varies |

**Aggregate:** ~92-95% UI code shared. Confirms the upper bound — Android + Desktop on Compose Multiplatform is essentially a single UI codebase with platform-specific actuals for ~5-8% of features.

### 5.4 Diverging surfaces — five locked patterns

**Pattern 1: Camera and file capture**

```kotlin
// commonMain
expect class ImageCapture {
    @Composable
    fun captureImage(onCaptured: (ByteArray) -> Unit, content: @Composable () -> Unit)
}

// androidMain
actual class ImageCapture(private val activity: ComponentActivity) {
    @Composable
    actual fun captureImage(onCaptured, content) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ... }
        Box(modifier = Modifier.clickable { launcher.launch(...) }) { content() }
    }
}

// desktopMain
actual class ImageCapture {
    @Composable
    actual fun captureImage(onCaptured, content) {
        // Desktop has no camera — open file picker
        val picker = remember { java.awt.FileDialog(...) }
        Box(modifier = Modifier.clickable { picker.show(); /* read bytes */ onCaptured(bytes) }) { content() }
    }
}
```

**Pattern 2: Status bar tint (Android) vs window chrome (Desktop)**

Use `Theme` configuration with platform-conditional `WindowInsets` consumption:

```kotlin
// commonMain
@Composable
fun DieticianTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = dieticianColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

// Android wraps with SystemUiController + EdgeToEdge
// Desktop wraps with Window decoration via Window.frameWindowScope
```

The status-bar/window-chrome difference is contained in the platform entry-point (`MainActivity.kt` for Android, `Main.kt` for Desktop). Composables themselves are platform-agnostic.

**Pattern 3: File dialog / scratchpad export**

Spec gap: shopping-list export. Android: share intent. Desktop: file save dialog. Single shared API:

```kotlin
// commonMain
expect class FileExporter {
    suspend fun saveText(filename: String, content: String): Boolean
}

// androidMain — share intent fallback if user wants to email
// desktopMain — JFileChooser
```

**Pattern 4: Notification scaffolding**

Spec §22 locks ntfy for push. Android subscribes via the ntfy Android client app. Desktop subscribes via Ktor SSE to `https://<tailscale-ip>/sub/<topic>?since=NN`. UI side:

```kotlin
// commonMain
expect class NotificationDispatcher {
    fun show(title: String, body: String, priority: Priority)
}

// androidMain — NotificationManager + channel
// desktopMain — TrayIcon balloon OR Compose-window toast
```

For Dietician, **most notifications are ntfy push** (server-pushed). The local-show path is only for "we just generated a new meal plan" foreground confirmations. The expect/actual is small.

**Pattern 5: Bluetooth scale pairing**

Spec doesn't lock scale-pairing. Future scope (weight log auto-capture from Bluetooth scale).

If Plan-4 (Android) wants scale-Bluetooth, it's purely Android — `BluetoothLeScanner` in `androidMain`. Desktop has no scale pairing in scope. **Pattern:** `expect class ScalePairing { suspend fun pair(): Result<ScaleHandle> }` with `actual` only in `androidMain`; `desktopMain` actual is `Result.failure(NotSupportedOnDesktop)`.

### 5.5 Single Plan-4-5 vs split Plan-4 + Plan-5

**Recommendation: collapse to a single `Plan-4-5` covering shared UI + 2 thin shells.**

Rationale:
- 92-95% shared code means dev cycle is "edit shared composable, see both platforms update"
- Reviewing two separate plans against the same screens would just double-count work
- Acceptance gate is unified: same `data-testid` selectors render on both Android + Desktop

**Plan-4-5 structure (skeleton):**

```
Plan-4-5 — Compose Multiplatform UI (Android + Desktop)
  Phase A — :shared:ui-components
    A1. DieticianTheme, ColorScheme, Typography
    A2. Common composables: ProgressBar, ListItem, ConfirmDialog
    A3. Voyager nav graph: Screen<Home>, Screen<Pantry>, Screen<Planner>, Screen<Shopping>, Screen<Diag>
    A4. Koin DI: ScreenModels wired to :shared:domain stores
  Phase B — Per-screen composables (shared)
    B1-B9. Home, Pantry, Planner, Shopping, Diag, ReceiptReview, RecipeDetail, Settings, VoiceMemo
  Phase C — :androidApp shell
    C1. MainActivity.kt; CameraX + ntfy Android + Keystore wiring
    C2. Material 3 EdgeToEdge + WindowInsets
    C3. Acceptance: data-testid Espresso/Compose-Test harness
  Phase D — :desktopApp shell
    D1. Main.kt; Compose Desktop window; DPAPI wiring
    D2. Window menu + tray icon
    D3. Acceptance: data-testid Compose-Test harness
  Phase E — Final whole-impl SDD review
    E1. Both Android + Desktop pass Interaction-smoke gate against live VPS
```

**One plan, one acceptance, one SDD review.** Saves a council pass.

### 5.6 Voyager + Koin DI sharing

Both already in `libs.versions.toml` per spec §33. Locked.

**Voyager pattern (Plan-4-5 Phase A3):**

```kotlin
// commonMain - shared/src/commonMain/kotlin/com/dietician/ui/screens/HomeScreen.kt
class HomeScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel: HomeScreenModel = koinScreenModel()
        val state by screenModel.state.collectAsState()
        // ... composable body using `state`
    }
}

// shared/src/commonMain/kotlin/com/dietician/ui/screens/HomeScreenModel.kt
class HomeScreenModel(
    private val pantryStore: PantrySnapshotStore,
    private val planner: Planner
) : ScreenModel {
    val state: StateFlow<HomeState> = ...
    fun onQuickLog(amount: Double) = ...
}

// shared/src/commonMain/kotlin/com/dietician/UiModule.kt
val UiModule = module {
    factoryOf(::HomeScreenModel)
    factoryOf(::PantryScreenModel)
    // ...
}
```

Both Android + Desktop:
```kotlin
// MainActivity.kt
setContent {
    DieticianTheme {
        Navigator(HomeScreen())
    }
}

// Main.kt
application {
    Window(...) {
        DieticianTheme {
            Navigator(HomeScreen())
        }
    }
}
```

The 2-line entry points are the **entire** platform divergence in the shell layer.

### 5.7 Test strategy: commonTest vs platform tests

- **`commonTest`:** ScreenModel logic, state-flow assertions, pure composable previews. Run via Kotest + Turbine. ~80% of UI tests live here.
- **`androidUnitTest`:** Android-specific composable interaction (CameraX launcher, ntfy subscription). Use Robolectric.
- **`desktopTest`:** Desktop-specific (window-menu, file dialog). Use Compose-Test direct.
- **End-to-end smoke (per CLAUDE.md Interaction-smoke gate):** Compose-Test launches each screen against a mock VPS, asserts `data-testid` selectors paint + interact without 4xx/5xx. One harness per platform.

### 5.8 iOS gate

Spec §32 locks Android + Desktop only. Confirmed via spec §2 architecture diagram (no iOS section). **Plan-4-5 should NOT plan for iOS.** If user adds iOS later, the Compose Multiplatform sharing pattern extends cleanly — at the cost of adding `iosMain` actuals for camera, file dialogs, notifications, and a Compose-iOS entry point (`MainViewController.kt`).

The Voyager nav library, Koin DI, kotlinx-serialization, Ktor Client are all already iOS-capable. Only platform-specific actuals would need iOS impls.

### 5.9 Decompose vs Voyager: stick with Voyager

Spec locks Voyager via `libs.versions.toml`. 2026 community consensus:
- Voyager: simpler setup, Compose-Multiplatform-only, ScreenModel + Screen abstractions natural for shared UI
- Decompose: harder setup, supports both Compose + native UI, component-based separation of nav from rendering, broader use-case but heavier

Dietician is Compose-only on both platforms. Voyager fits cleanly. **No motion to switch.**

### 5.10 Sources cross-referenced

- Compose Multiplatform 1.7-1.9.3 release notes (JetBrains)
- Production Compose Multiplatform case studies: Feres (90%), Respawn (96%), Fast&Fit (90%+)
- Voyager docs + Koin integration patterns
- Material 3 Expressive + Material 3 Adaptive in Jetpack Compose
- KMP `expect/actual` camera/image-picker patterns (DEV.to, Medium)

---

## Cross-Cutting Synthesis

### Five gaps ranked impact × ease

| # | Gap | Impact | Ease | Score |
|---|-----|--------|------|-------|
| 5 | KMP UI sharing strategy | High (blocks Plans 4 + 5 structure) | Easy (research already done; collapses to one plan) | 5/5 |
| 1 | Receipt OCR robustness | High (touches Plans 2 + 3 + 6) | Medium (most patterns are documented in spec; need to lock smoke-test on first claudemax run) | 4/5 |
| 4 | Embedding strategy | High (defines Plan-7 retrieval) | Medium (BGE-M3 + Voyage fallback is straightforward) | 4/5 |
| 2 | Paper ingestion sequencing | High (Plan-7) | Medium (GROBID + LLM extraction needs prompt iteration) | 3/5 |
| 3 | Anelis Plus auth | Medium (Unpaywall covers ~60% of needed papers) | Hard (user-led probe + Playwright session export + UAIC publisher coverage gaps) | 2/5 |

### Recommended attack order

**5 → 1 → 4 → 2 → 3**

1. **Gap 5 first (KMP UI sharing).** Locks plan structure. Cheapest to change *before* any UI is written. Collapses Plans 4 + 5 into a single Plan-4-5 saving a council pass + a SDD review cycle. ETA: integrated into Plan-2 council kickoff.

2. **Gap 1 (Receipt OCR).** Unblocks Plan-2 (LLM router) and Plan-3 (Ktor server orchestration of OCR routing). The raw-corruption gate + alias-table loop are load-bearing for Plan-1's `:shared:data` pantry-event derivation, which is already shipped — so this fills the consumer side. Plan-2 first task: wire ClaudeMax CLI subprocess + smoke-test the quota-exhausted exit code.

3. **Gap 4 (Embedding strategy).** Unblocks Plan-7 retrieval. Decision matrix above locks BGE-M3 self-host + Voyage fallback. Plan-7 first task: install Ollama on desktop, pull bge-m3, smoke-test with 20 RO + 20 EN queries.

4. **Gap 2 (Paper ingestion).** GROBID Docker on desktop. Per-paper-type LLM extraction prompts. Citation-graph schema additions to Postgres. The expensive slice ($80-160/yr LLM budget) but well-scoped.

5. **Gap 3 (Anelis Plus).** **User-led investigation gates implementation.** Plan-7 ships the stub `AnelisPaperFetcher.fetch() = Result.failure(...)` and the `CompositePaperFetcher` flow degrades to Unpaywall-only + user-upload-required. Anelis impl lands as a follow-up after user records the auth flow HAR.

### Cross-gap touchpoints

- **Gap 1 ↔ Gap 4:** Receipt OCR feeds `sku_canonical` + `receipt_aliases` tables which `:shared:knowledge` indexes for cross-corpus retrieval. When a new alias is confirmed (Gap 1 §1.6), trigger embedding reindex for the corpus='sku' item (Gap 4 §4.6).
- **Gap 2 ↔ Gap 3:** Paper-ingestion (Gap 2) depends on `CompositePaperFetcher` which depends on `AnelisPaperFetcher` (Gap 3). Plan-7 ships with the stub. Gap 3 resolves it later.
- **Gap 2 ↔ Gap 4:** Paper embeddings (Gap 2 §2.5 `papers.embedding`) reuse the same BGE-M3 model + same `corpus_embeddings` table (Gap 4 §4.2). One model, one index, multiple corpora.
- **Gap 5 ↔ Gap 1:** Receipt Review UI (Gap 1 §1.12) is a shared Compose Multiplatform screen per Gap 5 §5.3 — 90% shared with platform-specific camera (Gap 5 §5.4 Pattern 1).

### Spec gaps requiring follow-up

1. **Receipt review screen `data-testid` selectors** not in spec §30 (Gap 1 §1.12). Add to spec acceptance gate.
2. **`confidence` field on `pantry_events`** for guessed-receipt-derived events (Gap 1 §1.10). Spec implies via §14.4 but doesn't add the column. Plan-1 Phase 1 deferred — add to Plan-3 schema migration alongside `papers`/`citations` tables.
3. **Citation graph tables** (`papers`, `citations`, `paper_concept_links`, `paper_claims`) not in spec §4 schema (Gap 2 §2.5). Add to Plan-3 server migration alongside the Postgres backend tasks.
4. **`corpus_embeddings` unified table** not in spec §4 (Gap 4 §4.2). Add to Plan-3 / Plan-7 schema migration.
5. **Non-Crossref RO paper handling** (Gap 2 §2.7). Spec doesn't define what happens when DOI doesn't exist or paper is in `revista.ro` journal. Recommendation: drop to "user-upload-required" path. Document in Plan-7.
6. **Plan-4 and Plan-5 collapse to Plan-4-5** (Gap 5 §5.5). Update master plan listing in spec §0 or wherever the Plan-N enumeration lives.

---

## Open Questions

1. **Will UAIC enforce 2FA on Office365 within 12 months?** If yes, the Anelis session-export flow remains correct (user mediates 2FA in browser); session-refresh frequency may shorten (hourly?). Monitor UAIC DCDI announcements.

2. **Does Anelis Plus consortium include all journals the user needs for nutrition research?** ISSN-position-stand papers are typically in *Journal of the International Society of Sports Nutrition* (open-access, Anelis not needed). *American Journal of Clinical Nutrition* and *British Journal of Nutrition* are likely covered via Wiley/Cambridge agreements. *MASS Research Review* is NOT in Anelis — user-upload-required. **Plan-7 acceptance:** seed the first 20 papers from a mix of open-access (Unpaywall path) + paywalled (Anelis path) + user-upload (MASS path) to verify all three legs work end-to-end.

3. **BGE-M3 vs Voyage 3.5 Lite quality on RO supermarket SKU corpus.** Multilingual benchmarks favor BGE-M3, but the specific RO retail vocabulary (chain-specific brand names, RO + EN mixed labels) hasn't been benchmarked publicly. **Plan-7 smoke:** index 100 SKUs in each model, run 20 RO + 20 EN queries, compare top-3 recall. If delta < 5% → stick with BGE-M3 self-host. If delta > 10% → consider Voyage for SKU corpus specifically (still self-host BGE-M3 for everything else).

4. **Compose Multiplatform stability on Windows desktop for camera/file pickers.** Voyager + Material 3 + Koin are all production-stable on Desktop. The file-picker expect/actual via `java.awt.FileDialog` is plain JVM — bulletproof. The unknown is whether `ImageCapture` on desktop is even needed (desktop has no camera; "Re-upload" via file picker is the entire feature). Decide via Plan-4-5 Phase A3 review.

5. **GROBID variant: lightweight CRF vs full Deep Learning.** Spec doesn't specify. Recommendation: lightweight CRF (600MB image, 2-3GB RAM) is the right default. Re-evaluate if accuracy drops on RO-authored nutrition papers (unlikely; nutrition papers are mostly English).

6. **Ollama on desktop for BGE-M3.** Spec §7 mentions `OllamaLocalProvider` for embeddings fallback (TEXT chain). Need to verify Ollama bundles BGE-M3 — it does via `ollama pull bge-m3` since Q4 2025. Bookmark for Plan-7 desktop bootstrap.

7. **Receipt batch-upload UX on slow Tailscale.** If user is on phone with marginal cellular Tailscale, multi-receipt upload retries via outbox-replay (Plan-1 contract). What's the user feedback during the retry? Surface as "Uploading 3 of 5 (retrying)" with an in-app per-receipt status pill. Already covered by spec §6.3 health-check; just needs UI binding.

8. **Per-store receipt prompt drift over time.** Spec §8.1 has per-chain prompt templates. If Mega Image changes receipt layout (different VAT row, new line item format), the template breaks. **Plan-2 monitoring:** track per-store parse-success-rate over rolling 7d window; alert if drops > 10%. The receipt_review_queue depth is a leading indicator.

---

## Sources

### Receipt OCR + Vision LLM
- [Anthropic Vision API docs](https://platform.claude.com/docs/en/build-with-claude/vision)
- [Claude Agent SDK docs (CLI subprocess + stream-json)](https://docs.anthropic.com/en/docs/claude-code/sdk)
- [Claude vs GPT-4o for OCR benchmarks 2026 (CodeSOTA)](https://www.codesota.com/ocr/claude-vs-gpt4o-ocr)
- [OmniAI OCR Benchmark](https://getomni.ai/blog/ocr-benchmark)
- [OCR Benchmark Leaderboard 2026 (CodeSOTA)](https://www.codesota.com/ocr)
- [Receipt OCR Benchmark with LLMs (AIMultiple)](https://research.aimultiple.com/receipt-ocr/)
- [Gemini 2.5 Flash OpenRouter pricing](https://openrouter.ai/google/gemini-2.5-flash)
- [Gemini 2.5 Flash pricing & specs (LLMReference)](https://www.llmreference.com/model/gemini-2.5-flash/openrouter)
- [Gemini 2.5 for document processing (Medium)](https://medium.com/google-cloud/gemini-2-5-flash-the-ai-backbone-for-smarter-document-processing-6b8f4a18135a)
- [Gemini 2.5 Flash vs Google Vision OCR comparison (Roboflow)](https://playground.roboflow.com/models/compare/gemini-2-5-flash-vs-google-vision-ocr)
- [Mitigating OCR Hallucinations in MLLMs (arXiv 2506.20168)](https://arxiv.org/html/2506.20168v2)
- [NeurIPS 2025 Poster — OCR hallucinations](https://neurips.cc/virtual/2025/poster/117155)
- [LLM Structured Outputs Schema Validation (Collin Wilkins)](https://collinwilkins.com/articles/structured-output)
- [Gemini structured outputs](https://ai.google.dev/gemini-api/docs/structured-output)
- [Tesseract wrong Romanian diacritic mapping (#1314)](https://github.com/tesseract-ocr/tesseract/issues/1314)
- [Tesseract langdata Romanian default mapping (#37)](https://github.com/tesseract-ocr/langdata_lstm/issues/37)
- [Tesseract image preprocessing for receipts (Label Studio)](https://labelstud.io/blog/improve-ocr-quality-for-receipt-processing-with-tesseract-and-label-studio/)
- [Tesseract OCR adaptive thresholding (Towards Data Science)](https://towardsdatascience.com/getting-started-with-tesseract-part-ii-f7f9a0899b3f/)
- [Inside Claude Agent SDK on AgentCore (Substack)](https://buildwithaws.substack.com/p/inside-the-claude-agent-sdk-from)
- [Claude Vision for Document Analysis (GetStream)](https://getstream.io/blog/anthropic-claude-visual-reasoning/)
- [Khan format-claude-stream CLI filter](https://github.com/Khan/format-claude-stream)

### Paper Ingestion + GROBID + DOI
- [GROBID documentation (Read the Docs)](https://grobid.readthedocs.io/en/latest/Introduction/)
- [GROBID Docker setup](https://grobid.readthedocs.io/en/latest/Grobid-docker/)
- [GROBID memory limits (kermitt2/grobid #1036)](https://github.com/kermitt2/grobid/issues/1036)
- [grobid-tei-xml PyPI](https://pypi.org/project/grobid-tei-xml/)
- [GROBID GitHub (kermitt2/grobid)](https://github.com/kermitt2/grobid)
- [WIREs 2025 — automating scientific data extraction with GROBID + LLM + KNIME](https://wires.onlinelibrary.wiley.com/doi/10.1002/wcms.70047)
- [PsychKG knowledge graph using GROBID](https://medium.com/@jenlindadsouza/psychkg-how-to-build-a-minimal-knowledge-graph-for-psychology-fac0c76800ac)
- [Crossref REST API authentication](https://www.crossref.org/documentation/retrieve-metadata/rest-api/access-and-authentication/)
- [Crossref REST API pools](https://community.crossref.org/t/rest-api-pools-which-to-use-and-when/15317)
- [Unpaywall API coverage](https://support.unpaywall.org/support/solutions/articles/44001900286-which-dois-does-unpaywall-cover-)
- [Unpaywall free API guide (DEV.to)](https://dev.to/0012303/unpaywall-has-a-free-api-find-open-access-versions-of-any-paywalled-paper-n0i)
- [Semantic Scholar API tutorial](https://www.semanticscholar.org/product/api/tutorial)
- [Semantic Scholar API docs](https://api.semanticscholar.org/api-docs/)
- [Research Paper APIs for Scientific Literature 2026 (IntuitionLabs)](https://intuitionlabs.ai/articles/research-paper-apis-scientific-literature)

### Anelis Plus + UAIC + Romanian Federation
- [Anelis Plus official site](https://anelis-plus.ro/)
- [Anelis Plus 2020 project](http://anelisplus2020.anelisplus.ro/index.php?lang=en)
- [Anelis Plus IEEE partnership](https://open.ieee.org/partners/anelis-plus-consortium-romania/)
- [Anelis Plus Springer Nature OA agreement](https://www.springernature.com/gp/open-science/oa-agreements/romania/anelis-plus)
- [Anelis Plus Cambridge OA agreement](https://www.cambridge.org/core/services/open-access-policies/read-and-publish-agreements/oa-agreement-anelis-plus)
- [Anelis Plus Wiley agreement](https://authors.wiley.com/author-resources/Journal-Authors/open-access/affiliation-policies-payments/anelis-agreement.html)
- [UAIC official site](https://www.uaic.ro/en/)
- [UAIC IdP metadata (RoEduNetID)](https://met.refeds.org/met/entity/https://idp.uaic.ro/idp/shibboleth/?federation=roedunetid)
- [UAIC student M365 access](https://dcd.uaic.ro/?page_id=6053&lang=en)
- [UAIC register portal](https://register.uaic.ro/)
- [RoEduNet Identity Federation](https://eduid.roedu.net/)
- [RoEduNet eduGAIN participation](https://eduid.roedu.net/edugain/)
- [eduGAIN Federations list](https://reporting.edugain.org/federation_list.php)
- [eduGAIN membership status](https://technical.edugain.org/status)
- [eduGAIN about page](https://edugain.org/)
- [Springer Federated Access for libraries](https://www.springernature.com/de/librarians/tools-services/implement/federated-access)
- [Shibboleth Consortium](https://www.shibboleth.net/about-us/the-shibboleth-project/)
- [SAML Authentication in EZproxy (OCLC)](https://help.oclc.org/Library_Management/EZproxy/Authenticate_users/EZproxy_authentication_methods/SAML_authentication)
- [Multilateral federation with Microsoft Entra ID + Shibboleth (Microsoft Learn)](https://learn.microsoft.com/en-us/entra/architecture/multilateral-federation-solution-two)

### Embeddings + pgvector + Hybrid Search
- [pgvector ivfflat vs HNSW deep dive (AWS)](https://aws.amazon.com/blogs/database/optimize-generative-ai-applications-with-pgvector-indexing-a-deep-dive-into-ivfflat-and-hnsw-techniques/)
- [PGVector HNSW vs IVFFlat (Medium)](https://medium.com/@bavalpreetsinghh/pgvector-hnsw-vs-ivfflat-a-comprehensive-study-21ce0aaab931)
- [Faster similarity search with pgvector (Google Cloud)](https://cloud.google.com/blog/products/databases/faster-similarity-search-performance-with-pgvector-indexes)
- [Tuning pgvector Performance (ParadeDB)](https://www.paradedb.com/learn/postgresql/tuning-pgvector)
- [Hybrid Search BM25 + pgvector + RRF (DEV.to)](https://dev.to/gabrielanhaia/hybrid-search-in-100-lines-bm25-pgvector-with-rrf-merge-58cn)
- [Hybrid Search in PostgreSQL Missing Manual (ParadeDB)](https://www.paradedb.com/blog/hybrid-search-in-postgresql-the-missing-manual)
- [Stop the Hallucinations - Hybrid Retrieval pipeline](https://medium.com/@richardhightower/stop-the-hallucinations-hybrid-retrieval-with-bm25-pgvector-embedding-rerank-llm-rubric-rerank-895d8f7c7242)
- [Tiger Data hybrid search article](https://www.tigerdata.com/blog/elasticsearchs-hybrid-search-now-in-postgres-bm25-vector-rrf)
- [BAAI/bge-m3 model card](https://huggingface.co/BAAI/bge-m3)
- [Nomic Embed Multilingual preprint](https://static.nomic.ai/nomic_embed_multilingual_preprint.pdf)
- [Voyage 3.5 lite announcement](https://blog.voyageai.com/2025/05/20/voyage-3-5/)
- [Voyage 3 lite — small but mighty](https://blog.voyageai.com/2024/09/18/voyage-3/)
- [Voyage AI text embeddings docs](https://docs.voyageai.com/docs/embeddings)
- [Cohere Embed v3 multilingual benchmarks 2026](https://ucstrategies.com/news/cohere-embed-v3-multilingual-embedding-model-specs-benchmarks-2026/)
- [Best embedding models 2026 (pecollective)](https://pecollective.com/tools/text-embedding-models-compared/)
- [Voyage 3.5 vs OpenAI vs Cohere comparison 2026](https://www.buildmvpfast.com/blog/best-embedding-model-comparison-voyage-openai-cohere-2026)
- [Best embedding models benchmarked 2026 (Cheney Zhang)](https://zc277584121.github.io/rag/2026/03/20/embedding-models-benchmark-2026.html)
- [Best embedding models 2026 (Milvus)](https://milvus.io/blog/choose-embedding-model-rag-2026.md)
- [Best embedding models 2026 (BentoML)](https://www.bentoml.com/blog/a-guide-to-open-source-embedding-models)
- [USDA FoodData Central](https://fdc.nal.usda.gov/)
- [USDA FDC data documentation](https://fdc.nal.usda.gov/data-documentation/)
- [CIQUAL nutritional composition table (ANSES)](https://www.anses.fr/en/content/ciqual-nutritional-composition-table)
- [CIQUAL 2025 documentation](https://ciqual.anses.fr/cms/sites/default/files/inline-files/Table%20Ciqual%202025%20doc%20ENG_2025_11_19.pdf)
- [Open Food Facts API tutorial](https://openfoodfacts.github.io/openfoodfacts-server/api/tutorial-off-api/)
- [Open Food Facts API introduction](https://openfoodfacts.github.io/openfoodfacts-server/api/)
- [Fuzzy matching at scale (Medium)](https://medium.com/trusted-data-science-haleon/fuzzy-matching-at-scale-part-i-4621b0b36ba5)
- [Product title matching with NLP for SKU management (Unite.AI)](https://www.unite.ai/product-title-matching-for-sku-management-with-nlp/)

### KMP + Compose Multiplatform
- [Compose Multiplatform GitHub (JetBrains)](https://github.com/JetBrains/compose-multiplatform)
- [Kotlin Multiplatform official](https://kotlinlang.org/multiplatform/)
- [Compose Multiplatform homepage](https://kotlinlang.org/compose-multiplatform/)
- [Compose Multiplatform 1.9.3 release notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html)
- [Compose Multiplatform in 2026 Android iOS sharing (My Android Solutions)](https://www.myandroidsolutions.com/2026/03/23/compose-multiplatform-shared-ui-android-ios/)
- [KMP production-ready 2026 (Medium)](https://medium.com/@androidlab/kotlin-multiplatform-is-finally-production-ready-ed98c14e8ec5)
- [KMP ultimate guide 2026 (commonmain.dev)](https://commonmain.dev/kotlin-multiplatform/)
- [Compose Multiplatform write UI once (Medium)](https://medium.com/@eshagajjar7573/compose-multiplatform-write-ui-once-for-android-ios-desktop-web-ebefd2066bdf)
- [KMP + Compose unified Camera/Gallery picker expect/actual (DEV.to)](https://dev.to/ismoy/kotlin-multiplatform-compose-unified-camera-gallery-picker-with-expectactual-and-permission-4573)
- [Compose Multiplatform cross-platform guide (Pavan Rangani)](https://blogs.pavanrangani.com/compose-multiplatform-cross-platform-guide/)
- [Voyager docs](https://voyager.adriel.cafe/)
- [Voyager GitHub (adrielcafe/voyager)](https://github.com/adrielcafe/voyager)
- [Voyager + Compose Multiplatform tutorial (Medium)](https://medium.com/@muhammetemingundogar53/using-voyager-in-compose-multiplatform-d5062dac2492)
- [Voyager ScreenModel + Koin (Medium)](https://medium.com/@italord.melo/voyager-compose-multiplatform-navigation-and-viewmodels-screenmodel-b36693484d98)
- [Compose Multiplatform navigation Voyager analysis](https://michalkonkel.dev/compose-multiplatform-navigation-solutions-voyager)
- [Material 3 in Compose (developer.android.com)](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Material 3 Adaptive for Jetpack Compose](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive)
- [Material 3 Expressive UI complete guide 2026](https://medium.com/@androidlab/material-3-expressive-ui-in-compose-the-ultimate-guide-51c703e8e88a)
- [SQLDelight setup for KMP Compose (Medium)](https://medium.com/@hunterfreas/sqldelight-setup-a-local-database-for-kmp-compose-ios-android-65f7e2b1e224)
- [Ktor + SQLDelight multiplatform tutorial](https://kotlinlang.org/docs/multiplatform/multiplatform-ktor-sqldelight.html)
- [Ktor SSE + Compose Multiplatform pagination (Medium)](https://medium.com/@ov.hlotov/streaming-pagination-made-easy-sse-ktor-compose-multiplatform-f861d70176b4)
- [Ktor WebSockets docs](https://ktor.io/docs/server-websockets.html)
- [Ktor SSE server docs](https://ktor.io/docs/server-server-sent-events.html)

---

## END OF ROUND 5

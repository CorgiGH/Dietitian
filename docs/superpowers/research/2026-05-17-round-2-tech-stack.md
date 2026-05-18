# Round 2 — Tech Stack + Implementation Deep-Dive

> Deep research subagent output for the Dietician project. Round 2 (technology selection + implementation patterns).
> Date stamp: 2026-05-17. Spec under review: `docs/superpowers/specs/2026-05-17-dietician-design.md`. Plan-1 (`:shared:data` event-sourced ledger + sync) already shipped; this document evaluates the stack for Plans 2-7.

---

## TL;DR

- **Stack one-liner**: Kotlin 2.1 + Compose Multiplatform 1.8.x (iOS-stable line) for Android + Windows Desktop; Ktor 3.x on VPS with the **CIO engine in single-server mode** (Netty headroom not needed at ~5 users); SQLDelight 2.0 retained on clients (Plan-1 sunk cost + WAL-on-iOS-by-default; Room KMP not worth the migration); Postgres 16 + pgvector 0.8.2 canonical on VPS already live; ntfy as UnifiedPush distributor on Android, polling fallback on Desktop; ClaudeMax CLI subprocess primary for vision + heavy reasoning on the desktop, Gemini 2.5 Flash via OpenRouter for fallback and phone-side path, DeepSeek V3.2 via OpenRouter for cheap RO-capable text reasoning, Voyage-3-Lite (200M free tokens) primary embeddings with `nomic-embed-text` via Ollama as offline-desktop fallback.
- **PDF ingest**: keep GROBID on the Dell G5 as the structural backbone for academic papers (table/reference extraction is what we actually need), but add **Marker (`use_llm=true`) on the same host** as the OCR-heavy / scanned-paper fallback. olmOCR is the highest-quality but >25GB-VRAM ceiling kills it for the 3060 box; MinerU 2.5 VLM (~5GB VRAM) is the strongest local-GPU option if Marker quality is insufficient.
- **Solver**: Choco-solver stays pinned at `4.10.14` for the JVM (server-side AND desktop) and we **push the planner to the Ktor server**, not the Android app — once `xchart` is excluded the JAR is still 8-10MB and bringing constraint propagation into a 16-pack of Android dex methods is a fight we don't need to have at ~5 users.
- **Two-phase budget reserve** is the right mental model and matches how Zuplo / Azure APIM / Portkey have settled on it for LLM rate limiting; treat the LLM router's `llm_budget` table as a token-bucket with both reservation and reconciliation columns.

## Top surprises during this pass

1. **Realm is sunset.** MongoDB ended Atlas Device Sync + Atlas Device SDK support on 2025-09-30. Anyone still considering Realm KMP in 2026 inherits an unsupported library. SQLDelight (already chosen) and Room KMP are the only sustained options.
2. **ClaudeMax CLI is the official path now.** Anthropic's docs explicitly recommend `claude --bare -p` for SDK / scripted usage and signal `--bare` will become the default for `-p`. We're not on a hack; this is the supported headless surface. *But* the SDK overhead is ~12s/cold call per a documented perf issue, and **Windows initialization can hang during the async context manager handshake** — design our spawn pool accordingly.
3. **Compose Multiplatform iOS went Stable in 1.8.0 (2025-05-06).** Even though we don't ship iOS for Dietician, the stability bar is now high enough that the desktop/Android path is rock solid by spillover. The flip side is that **Compose Desktop on Windows uses ~100-230MB of native memory** (Skia bindings) — relevant when the desktop also runs Ollama + GROBID + scrapers.
4. **ntfy on Android requires foreground service for instant delivery.** Otherwise messages "batch opportunistically" through Doze — same caveat as FCM normal-priority. The persistent notification is the cost of self-hosted reliability. Recent ntfy-android development around `RaiseAppToForeground` suggests this is being actively improved.
5. **Voyage-3-lite has changed names.** Voyage AI's Lite tier is `voyage-4-lite` as of January 2026 at $0.02/MTok with **the first 200M tokens free per account** — at our corpus size we are mathematically inside the free tier. Plan to use Voyage primary, but the API key rotation story is real.

## Top risks under VPS budget (2 vCPU, 8GB RAM, MC 4GB pinned)

1. **Ktor server + Postgres + pgvector HNSW index residency.** pgvector docs (Tom Foster, ParadeDB) are clear: an HNSW vector index has to live in `shared_buffers` or in the OS page cache via `effective_cache_size`, otherwise query latency tail-spikes badly. We have ~2GB headroom *total* across Postgres + Ktor + ntfy + nginx + scrape adapters. The corpus side (recipes / supplements / papers) at ~5-10k embeddings × 768 dim is fine (~30MB); if a single user dumps a 200-paper academic corpus we touch ~600MB and start fighting MC's resident set. Mitigation: cap pgvector memory via `shared_buffers = 1GB`, `effective_cache_size = 1.5GB`, use the 384-dim Matryoshka truncation of nomic-embed v1.5 when shipping local-only embeddings, and **store paper-level embeddings in chunks of ≤512 tokens** to keep the HNSW graph small.
2. **Compose Desktop + Ollama + GROBID + Playwright co-residence on Dell G5.** Each process is well-behaved alone, but the user is running them all on a personal box. Specifically:
   - Compose Desktop: 100-230MB resident
   - Ollama with nomic-embed-text loaded: ~300MB (model 274MB)
   - GROBID (Java): user-reported 1.5GB peak
   - Playwright with one chromium tab per chain: ~400-600MB per chain × 3-4 chains
   - Total under load: 4-5GB easily. Mitigation: enforce **one Playwright instance at a time** (already in the spec, §10.1 Council 3 BREAK #16), keep GROBID in a tmpfs-backed Docker container, and gate Ollama embed jobs behind the same "desktop-only-when-idle" mutex.
3. **ClaudeMax CLI subprocess pool sizing.** 12s cold-start overhead × ~30q/day per user × 5 users = ~25 minutes/day burned on spawn cost if every q is a cold call. The spec already locks ClaudeMax to desktop-only and to heavy flows, but the budget reserve mechanism should **add a "subprocess-pool" entry** that caps concurrent `claude --bare -p` processes to the lesser of `(CPU cores - 2, 3)` and ensures the user knows when a flyer pass is blocking a recipe ingest. Crucially, the Anthropic CLI does NOT (today) expose process reuse — we get one shot per spawn.

**File path**: `C:\Users\User\Desktop\Dietician\docs\superpowers\research\2026-05-17-round-2-tech-stack.md` — read in full for sourcing + alternates.

---

## 1. KMP + Compose maturity 2026

### Stability matrix (May 2026)

| Target | KMP status | Compose status | Production caveats |
|---|---|---|---|
| Android | Stable (since 2023) | Stable | Default target. R8 minify must keep Choco reflection + ehcache sizeof classes (see §10). |
| Desktop JVM (Windows + macOS + Linux) | Stable | Stable | Skia bindings consume ~100-230MB resident on idle "hello world". JDK 17+ required for jpackage MSI. |
| iOS | Stable (since 2023) | **Stable (since 1.8.0, May 2025)** | Not part of Dietician scope but de-risks the framework. |
| Web (wasmJs) | Beta | Beta | Out of Dietician scope. |
| watchOS / tvOS | Alpha | Alpha | Out of scope. |

Compose Multiplatform 1.7 was the last "iOS Beta" release; 1.8.0 (2025-05-06) is the "iOS Stable" line and the recommended entry for new projects. 1.10.0 (Jan 2026) adds unified `@Preview`, Navigation 3, and stable Compose Hot Reload. For Dietician we **pin Compose Multiplatform `1.8.x` minimum and treat `1.10.x` as a fast-follower** — Hot Reload on the desktop side is a productivity win for the Compose-heavy UI plans (Plans 4-5).

### Kotlin 2.1.x and the K2 compiler

Compose Multiplatform 1.8+ requires Kotlin 2.1.0+ and has fully transitioned to the K2 compiler for all native and web targets. Plan-1 was reportedly built on Kotlin 2.0.21; we should plan a **bump to Kotlin 2.1.21** (or current stable) when starting Plan 2. The Kotlin team's K2 migration is mostly transparent for KMP shared code, but watch:

- `expect`/`actual` **classes** remain in Beta. The compiler warns; the warning can be suppressed via `-Xexpect-actual-classes`. For Dietician's `:shared:llm` sealed-interface design (spec §7.1), we use `expect class` for the `LlmProvider` platform-specific subprocess wrapper — accept the Beta tag and pin a compiler flag in `gradle.properties`.
- Kotlin 2.0+ enforces strict separation of common and platform sources during compilation. Previously, common code could access platform code, which resulted in different behavior between platforms. This is **load-bearing** for Plan-1's already-shipped `:shared:data` module — verify no test fixtures lean on the old common-can-see-jvmMain leak.

### `expect`/`actual` gotchas to watch for

- **Visibility tightening on actual**: as of Kotlin 2.0, the compiler accepts wider/narrower visibility on the actual side without `@Suppress("ACTUAL_WITHOUT_EXPECT")`. Pre-2.0 code that suppresses this can drop the annotation.
- **Enum constants**: each actual declaration must contain at least the same enum constants but **can add more**. For `:shared:data` ledger event types, this means we can introduce desktop-only events (e.g. `FlyerOcrCompleted`) on the JVM side without forcing the Android side to compile-time-acknowledge them.
- **Companion objects on expect classes** behaved inconsistently pre-2.0; verify the `LlmProvider.Factory` companion is declared on the common side, not the actual side, and uses `expect fun` not `expect class`.

### Multiplatform-Settings, kotlinx-datetime, kotlinx-coroutines

| Library | Pinned in spec | Current stable | Notes |
|---|---|---|---|
| `kotlinx-coroutines-core` | (Plan-1 dependency) | 1.11.0 (matches Kotlin 2.2.20) | Use `1.9.x` to stay on the 2.1.x branch alignment; the 1.10/1.11 line aligns with Kotlin 2.2.x. |
| `kotlinx-datetime` | (Plan-1 dependency) | 0.8.0 | Pre-1.0 but stable in practice; ConservativeTimeApi is what we want for ledger event timestamps. Production-validated by Netflix, Airbnb, JetBrains. |
| `multiplatform-settings` (russhwolf) | Not specified in spec | 1.3.x | Use for the `state/llm-router.toml`-equivalent on Android (Plan-3) — wraps SharedPreferences/NSUserDefaults uniformly. Production-stable; widely used. |

### Production-shipped KMP apps in 2026 (reference)

- **Cash App**: 7 years in, KMP in production. Migration strategy: pick ONE feature, flag it, measure. We are already smaller than that — single feature is the whole app.
- **Netflix**, **McDonald's**, **H&M** (feature-flag layer first), **Sony Sound Connect** (six-year journey, hard-earned architectural lessons).
- **JetBrains Toolbox** internal use as part of the dogfooding push (referenced in 2025 KotlinConf talks).

### Recommendation

- Compose Multiplatform `1.8.x` LTS / `1.10.x` fast-follower
- Kotlin `2.1.21+`
- AGP `9.0` (per JetBrains 2026-01 advisory)
- Suppress `expect`/`actual` class warning via compiler flag for `:shared:llm`

---

## 2. KMP networking

### Ktor Client 3.x

Ktor 3.0 (released October 2024) is a major release switching to the `kotlinx-io` library (Okio-based) for cross-Kotlin-library I/O standardization. IO benchmark tests, based on real-world Ktor applications, show >90% improvement on some tests. By the May 2026 baseline we are targeting `ktor 3.2.x` minimum.

### Engine matrix per platform

| Platform | Recommended engine | Justification |
|---|---|---|
| Android | OkHttp | Best ecosystem support (interceptors, connection pool tuning, MockWebServer for tests). The "CIO native vs OkHttp" debate per Andhukuri (Apr 2026) found CIO's native I/O 6.5× faster at file I/O but OkHttp faster at network transfer — for our small-payload REST sync, OkHttp wins. |
| Desktop JVM | Java (built-in `HttpClient`) or OkHttp | Java engine is now production-grade since JDK 11. We pick OkHttp on desktop too for code-share with Android. |
| iOS / Native | Darwin (NSURLSession) | Out of scope. |

### Connection pool tuning for the sync endpoint

For the Tailscale-IP Ktor server (`100.x.x.x:8081`), Plan-3's sync endpoints are small JSON pushes/pulls. Conservative defaults:

```kotlin
HttpClient(OkHttp) {
    engine {
        config {
            connectTimeout(5.seconds.toJavaDuration())
            readTimeout(15.seconds.toJavaDuration())
            // Avoid OkHttp's idle eviction during long-poll pulls
            connectionPool(ConnectionPool(maxIdleConnections = 4, keepAliveDuration = 5, TimeUnit.MINUTES))
        }
    }
    install(HttpRequestRetry) { retryOnServerErrors(maxRetries = 3); exponentialDelay() }
    install(HttpTimeout) { socketTimeoutMillis = 60_000 } // long-poll
}
```

### WebSockets, SSE, HTTP/2

- Dietician uses **ntfy push, not WebSockets** for server→client signaling. Don't introduce WebSockets just because Ktor supports them.
- SSE is a viable alternative *if* ntfy ever falls over. Ktor 3 has both client + server SSE plugins (`ktor-server-sse`, `ktor-client-sse`). Notable: per Cekrem (2025), default to SSE if data flows server→client one-way; you get `Last-Event-ID` reconnection and replay for free. Park this as a fallback option.
- HTTP/2 is supported by OkHttp; the VPS nginx should already terminate it.

### Alternates (NOT recommended for Dietician but worth knowing)

- **Apollo**: GraphQL-first; overkill, we use plain JSON REST.
- **Retrofit**: Android-only. Ktor Client covers the multiplatform need.
- **Ktor CIO client**: per Ioannis Anifantakis (Apr 2026), "Can it finally replace OkHttp and Darwin?" — answer is "Yes for native, but on Android still pick OkHttp."

---

## 3. KMP database

### Decision: Keep SQLDelight 2.0

Plan-1 shipped on SQLDelight. The 2026 Room-KMP-vs-SQLDelight debate (bswen, Murali M, FunkyMuse) lands on:

| Criterion | SQLDelight 2.0 | Room KMP (Room 3.0) |
|---|---|---|
| Maturity on KMP | KMP-ready longer; battle-tested | Recently went Stable on iOS/Desktop; less production mileage |
| Schema language | SQL-first, type-safe Kotlin generated | Kotlin annotations + KSP code gen |
| Migration friction from Android-only Room | Different mental model | Drop-in for Android-Room shops |
| WAL on iOS | Default | Configurable; needs more boilerplate |
| Backed by | Square (community-maintained) | Google AndroidX team |

For Dietician, **SQLDelight 2.0 is the right call** because:
1. Plan-1 is shipped on it — switching is a multi-week regression risk.
2. We use SQL natively in the spec (§4 has long SQL fragments); writing them in `.sq` files keeps the schema-as-source-of-truth alignment.
3. The Eygraber AndroidX driver (`sqldelight-androidx-driver`) bridges the gap for any Room-side ecosystem integration we want later.

### Driver per platform (already shipped)

- Android: `app.cash.sqldelight:android-driver` (raw SQLite via SupportSQLiteOpenHelper, set `journal_mode=WAL` via `setJournalMode("WAL")` on the SupportSQLiteOpenHelper builder)
- Desktop JVM: `app.cash.sqldelight:sqlite-driver` (xerial JDBC; configure WAL via `PRAGMA journal_mode=WAL` after open)
- Native iOS (out of scope): `native-driver`

### WAL + checkpoint policy (Plan-1 reference, restated)

`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA wal_autocheckpoint=1000` — these three are non-negotiable. Per the Loke.dev "20GB WAL file" post, **checkpoint starvation is the failure mode**: if a reader holds a connection open during a long write, WAL grows unbounded. Mitigation: the `OutboxDrainWorker` (spec §3) closes its read snapshot between drain cycles; do not hold a long-lived read cursor.

### Alternates we explicitly reject

- **Room KMP**: would force a rewrite of Plan-1.
- **Realm KMP**: **MongoDB ended mobile support on 2025-09-30**. New code on Realm is technical debt at birth.
- **Multiplatform Settings**: not a database; use it for ≤2KB key-value (auth tokens, user prefs) only.
- **SQLite directly via JNI**: re-implementing SQLDelight's type generation by hand is a waste of time.

---

## 4. Ktor server

### Recommendation: Ktor 3.x + CIO engine

For a 2-vCPU, 8GB VPS hosting MC (4GB) + everything else, **CIO is the right pick over Netty for Dietician's load profile**:

- ~5 users × ~30 actions/day = ~150 requests/day at peak. Netty's high-throughput edge (its main advantage per youtrack KTOR-1121 and the matej.laitl.cz benchmark) doesn't pay off below ~100 req/s sustained.
- CIO uses **Kotlin coroutines natively** — no JNI selectors, no Netty memory pool tuning. Fewer moving parts, less RAM headroom needed.
- Jetty is documented to "slightly but consistently lag" behind other engines and we have no reason to consider Tomcat.

The trade-off documented on kotlinlang.slack (Mar 2026): "switching Netty→CIO doubled latency, halved throughput" applies to high-RPS scenarios. We are nowhere near that. **CIO it is.**

### Memory budget for the Ktor process on the VPS

Plain Ktor + JWT + Sessions + content-negotiation + db pool ≈ 200-400MB resident JVM, depending on `-Xmx`. Pin `-Xmx512m -XX:+UseG1GC` initially; revisit if metrics show GC pressure. Pair with `-XX:MaxRAMPercentage=25` for safety on the shared VPS.

### Authentication plan

Per the spec, the threat model is "personal app, 5 users, Tailscale-only". Recommended Ktor auth stack:

- `ktor-server-auth-jwt` for sync endpoints. HS256 with a shared secret rotated per-user. JWT lifetime = 30 days, refreshed on each sync.
- `ktor-server-sessions` cookie sessions for any future browser-side UI (out of scope for Plan-3, but doesn't cost anything to have the dependency).
- No OAuth, no IdP — the spec already locked this. Tailscale ACL + JWT is sufficient defense-in-depth.

### Content negotiation

`ktor-serialization-kotlinx-json` (matches Plan-1's serialization). Pin to `kotlinx.serialization 1.7.x+` which has stable polymorphic sealed-interface support — important for `LedgerEvent` discriminator-based JSON.

### WebSockets / SSE on the server

Have the dependencies, don't install the plugin until we need it. Future Plan-X (e.g. live planner re-rank streaming) might need SSE; for Plan-3 sync, plain REST is right.

### Hosting on the 2-vCPU 8GB VPS alongside MC

Per Ktor's CIO engine docs and our budget: `-Xmx512m`, single instance, behind nginx reverse-proxy on `localhost:8081` (already Tailscale-bound). Run as systemd unit, restart-on-failure. Don't co-locate with anything else memory-greedy — and **don't touch MC's heap settings** (user instruction).

---

## 5. Push notifications

### Decision matrix

| Channel | Android | Desktop | iOS | Reliability under Doze | Cost |
|---|---|---|---|---|---|
| **ntfy via UnifiedPush** | Native distributor | Polling on Desktop or HTTP-GET on demand | Limited; iOS APNs proxy needed | High *if* foreground service granted | $0 (self-hosted) |
| FCM | Native, requires Google Services | N/A | N/A | High-priority bypasses Doze; normal priority batches | $0 (per Google's pricing) |
| APNs | N/A | N/A | Required for iOS | High | $0 (per Apple) |
| WebPush | Browser only | Browser only | Browser only | N/A | $0 |

### Recommendation: ntfy as UnifiedPush distributor on Android, in-process polling on Desktop

Spec §22 already locked ntfy. This deep-dive confirms:

- **ntfy + UnifiedPush integration**: ntfy is a registered UnifiedPush distributor on the F-Droid / UnifiedPush.org ecosystem. The Android app subscribes to a topic and forwards data via Broadcast Intent to the consumer app. Battery cost stays under 1% even with the foreground-service-required "instant delivery" mode.
- **Doze handling**: instant delivery requires the foreground service, displayed as a permanent notification. This is the cost of self-hosted push that bypasses FCM. The alternative (no foreground service) batches messages opportunistically during Doze maintenance windows — same behavior as FCM "normal priority" messages.
- **Reliability**: a user-reported XDA review and Eugene Davis's Feb 2026 "Offline Notifications" post both rate ntfy as "consistently delivers" once the battery-optimization exemption is granted. The PR `binwiederhier/ntfy-android#98` modernizes the UnifiedPush spec support.

### Foreground service permission flow (Android)

Plan-4 must include UI for guiding the user through:
1. Battery optimization exemption (Settings → Apps → Special Access → Battery Optimization → ntfy → "Don't optimize")
2. Notification permission (Android 13+)
3. UnifiedPush distributor selection (if multiple installed)

A "first-run setup checklist" screen handles all three in <90s.

### What about FCM as a fallback?

Don't. Spec §22 picked self-hosted; carrying both stacks (FCM Firebase project + ntfy) doubles the integration surface for ~5 users. If ntfy ever fails, the **fallback is in-app refresh-on-foreground**, not FCM.

### Desktop push

Desktop Compose app polls the Ktor server every 30s when idle, every 5s when active. ntfy supports SSE subscription (`GET /<topic>/sse`) which is an alternative if polling latency becomes a complaint. Plan-5 should make this configurable per user.

---

## 6. GROBID alternatives for paper ingest

### Current pin: GROBID on Dell G5 desktop (1.5GB RAM peak)

Reasonable choice for the **structural extraction** task (references, tables, sections, abstract) on chemistry-/medicine-/nutrition-flavored academic papers. GROBID is a pipeline-based system tailored to scientific papers (per olmOCR's 2024 paper); it's not OCR — it operates on the embedded text layer.

### Where GROBID fails

GROBID struggles when:
1. The PDF is scanned / has no text layer
2. Tables are complex (multi-row headers, merged cells)
3. The paper is in a language with poor model coverage (RO papers are often actually English in this corpus)

### Alternatives evaluated

| Tool | Local resource | Quality (olmOCR-Bench ELO) | License | When to use for Dietician |
|---|---|---|---|---|
| **GROBID** (current) | 1.5GB RAM, JVM | Reference for citations + sections; not benchmarked on olmOCR-Bench | Apache 2 | Default for born-digital academic PDFs. |
| **Marker** (datalab.to) | ~6GB GPU recommended; CPU works (16s/page) | Beats GPT-4o, Mistral OCR, olmOCR on olmOCR-Bench when `--use_llm` flag passes through Gemini | MIT (model weights GPLv3 — read the LICENSE carefully) | Add as fallback for scanned papers. Use `--use_llm=true` with Gemini 2.5 Flash on OpenRouter (~$0.0001/page). |
| **MinerU 2.5** (opendatalab) | ~5GB VRAM for VLM mode, CPU works slowly | High; pipeline + VLM hybrid | AGPL — **license trap, read it carefully before redistributing** | Use if Marker quality is insufficient AND we have GPU. On the Dell G5 RTX 3060 12GB, MinerU 2.5 VLM fits comfortably. |
| **olmOCR** (AI2) | Peak 25GB VRAM on large docs; >12GB GPU | 1800+ Elo, +1.3pp on downstream LM benchmarks vs GROBID | Apache 2 | Skip — VRAM ceiling too high for our 3060 box. |
| **Docling** (IBM) | CPU 3.1s/page; GPU 0.49s/page | Production-grade structured DoclingDocument output | MIT | Alternative to GROBID if we want PPTX/DOCX support too. Not critical here. |
| **PyMuPDF4LLM** | <50MB RAM | 0.14s/page on text-PDFs | AGPL (or commercial) | Use as a **fast pre-filter** to detect "is there a text layer? extract it before calling GROBID". |

### Recommended ingest pipeline (Plan-6)

```
PDF arrives → PyMuPDF4LLM probe (<1s)
            ├── text layer present, well-formed → GROBID (structural extraction)
            ├── text layer messy → Marker --use_llm with Gemini 2.5 Flash
            └── scanned-only (no text layer) → Marker --use_llm OR MinerU 2.5 VLM
```

License caveat: **AGPL components (MinerU, optionally PyMuPDF4LLM) cannot be linked into a redistributed commercial binary**. For Dietician's friends-and-family scale where the desktop side is your local install, this is fine — the binaries run as subprocesses, not linked libraries.

### RAM budget on the Dell G5

- GROBID: 1.5GB peak (Java)
- Marker w/o LLM: 6GB GPU or ~2GB RAM CPU
- MinerU 2.5 VLM: 5GB VRAM
- PyMuPDF4LLM: negligible

Run **one at a time** under the "desktop-only-when-idle" mutex. Concurrent GROBID + MinerU would be roughly OK on a 16GB+ desktop with an RTX 3060; serialize as a safety belt.

---

## 7. Vision OCR for receipts

### Pinned plan (per spec §8.1): ClaudeMax CLI primary, Gemini 2.5 Flash via OpenRouter fallback

This is correct. Validating the choice against alternatives:

| Option | Cost per receipt (3-10 SKU) | RO diacritic handling | Local? | Notes |
|---|---|---|---|---|
| **ClaudeMax CLI (Sonnet 4.6 or Opus 4.7)** | $0 to user (Max subscription credit) | Excellent (per Anthropic's multilingual evals) | No (subprocess but calls cloud) | Primary. Subscription cost amortized across all uses. |
| **Gemini 2.5 Flash** | ~$0.0001-0.0005 per image (~258 input tokens + ~300 output) | Strong (Croatian 95%, Italian 92% reference scores; RO is in the same family) | No | Fallback. OpenRouter free tier at 50 RPD without $10 unlock. |
| **Gemini 2.5 Pro** | ~$0.001-0.005 per image | Best of class | No | Only for ambiguous receipts. |
| **Tesseract + regex** | $0 | Bad. Tesseract supports RO but the diacritics ț/ș (U+021B, U+0219) frequently mis-recognize as ţ/ş (U+0163, U+015F) due to historical encoding confusion. Reflux into a normalization table. | Yes | Last-resort. |
| **EasyOCR** | $0 (~1GB model) | Better than Tesseract on scene text; mixed on receipts. GPU recommended. | Yes | Skip for receipts. |
| **PaddleOCR** | $0 (~500MB model) | Best open OCR for multilingual + layout; 100+ languages | Yes | Add as offline-only fallback IF the user is offline and the receipt is non-urgent. |
| **llava-1.6 13B Q4 on RTX 3060 12GB** | $0 (local) | Decent | Yes (GPU) | Skip — generic VLM is overkill for receipts; specialized OCR engines or Claude/Gemini are cheaper end-to-end. |
| **Anthropic API direct (no Max subscription)** | $0.001-0.005 per image (Sonnet 4.6 vision) | Same as ClaudeMax | No | Costs hard money. Skip while Max subscription exists. |

### Cost projection

At 30 q/day × 5 users × ~10% receipt-vision = ~15 receipts/day across all users → ~450 receipts/month.

- ClaudeMax: included in $20/mo subscription budget
- Gemini 2.5 Flash fallback: 450 × $0.0005 = **$0.23/mo** (well within OpenRouter free tier even without $10 unlock)

### Romanian-specific OCR notes

The historical ț/ț (cedilla vs comma-below) confusion is a real production trap:
- Unicode-correct: `ș` U+0219, `ț` U+021B (comma below)
- Legacy / Microsoft-mapped: `ş` U+015F, `ţ` U+0163 (cedilla, technically Turkish)

Tesseract + traineddata `ron` will sometimes output the cedilla form even when the receipt prints the comma-below form. **Normalize on ingest** with a single-pass replace:

```kotlin
val roDiacriticFix = mapOf('ş' to 'ș', 'Ş' to 'Ș', 'ţ' to 'ț', 'Ţ' to 'Ț')
fun String.normalizeRo() = map { roDiacriticFix.getOrDefault(it, it) }.joinToString("")
```

ClaudeMax / Gemini 2.5 outputs already use the Unicode-correct form per Anthropic / Google evals. Tesseract is the offender.

---

## 8. Embedding stack

### Decision: Voyage-3-Lite (now `voyage-4-lite`) primary; `nomic-embed-text v1.5` via Ollama as desktop-offline fallback

| Model | Dim | Context | Cost | MTEB | When to use |
|---|---|---|---|---|---|
| **Voyage-4-Lite** (formerly voyage-3-lite, $0.02/MTok, 200M tokens free per account) | 1024 | 32K | First 200M tokens free → ~$0.02/MTok after | Strong; matches voyage-3.5 family | Primary path for cloud-side embeddings. |
| **`nomic-embed-text v1.5`** (Ollama) | 768 (Matryoshka, can truncate to 64-768) | 8192 | $0 (local) | 62.39 — roughly matches OpenAI `text-embedding-3-small` | Desktop-only fallback when offline, or when API key missing. ~274MB RAM. |
| **`all-MiniLM-L6-v2` ONNX Q4** | 384 | 256 | $0 | 56% Top-5 / 28% Top-1 (per Hacker News thread #46081800) — weak | Skip. Too short context for recipe/paper chunks. |
| **Voyage-Multilingual-2** | 1024 | 32K | $0.12/MTok | Best for RO + EN mixed | Only if Lite quality on RO is insufficient. |
| **`bge-m3` (BAAI)** | 1024 | 8192 | $0 (local, ~2GB RAM) | Top open multilingual | Strong RO alternative if Voyage budget runs out. |

### Multi-language (EN/RO) quality

Voyage's multilingual variants are tuned for cross-language retrieval; Voyage-4-Lite's English-primary scope is OK because **most academic nutrition/supplement papers are in English**. RO content (recipes scraped from Romanian sites, RO-language flyers) is fine on Voyage-4-Lite at the corpus scale we have (~5-10k chunks). If we ever index a large RO-language recipe corpus, bump to Voyage-Multilingual-2 or `bge-m3`.

### Local vs cloud decision per platform

- **Android**: cloud-only (Voyage). Phone never embeds.
- **Desktop**: cloud primary, local Ollama fallback (latency 15-50ms vs 200-800ms cloud per Morph benchmark).
- **VPS**: never embeds. Receives pre-embedded vectors from desktop and stores in pgvector.

### Dimensionality choice for pgvector storage

- Voyage-4-Lite: 1024-dim → store as `vector(1024)` in Postgres. HNSW index footprint ~8MB per 1k vectors.
- nomic-embed v1.5 with Matryoshka truncation to 512-dim → store as `vector(512)`. Halves HNSW footprint.

Per the Tom Foster + ParadeDB pgvector tuning posts: **keep total HNSW graph under `shared_buffers`** (1GB target). At 1024-dim that's ~8MB/1k vectors → 125k vectors max in RAM. We are nowhere near that ceiling.

---

## 9. Choco-solver for meal-plan ILP

### Pin: `org.choco-solver:choco-solver:4.10.14`, server-side only, `xchart` excluded

```kotlin
// build.gradle.kts (Ktor server module only)
dependencies {
    implementation("org.choco-solver:choco-solver:4.10.14") {
        exclude(group = "org.knowm.xchart", module = "xchart")
    }
}
```

### Why server-side only

The spec previously considered desktop + Android. For Dietician's actual scale:

1. **Solver runs ≤ 1×/day per user** (planner trigger from §18.3). Run-anywhere doesn't matter.
2. **Choco's reflection-heavy core** needs careful R8 keep rules on Android. The `org.ehcache:sizeof` library uses sun.misc.Unsafe reflection that R8 will minify away without explicit rules. Avoiding the Android side dodges this entirely.
3. **JAR size**: Choco-solver fat-jar is ~12MB even with `xchart` excluded. On a Compose Android APK, this is non-trivial; on a JVM server, it's free.
4. **Solver state lifetime** is server-managed: results stored in Postgres, signaled to clients via ntfy. Clean separation.

### Required R8 keep rules (only if we ever move to Android)

```proguard
# Keep Choco-solver reflection
-keep class org.chocosolver.** { *; }
-keepclassmembers class org.chocosolver.** { *; }

# Keep ehcache sizeof reflection
-keep class org.ehcache.sizeof.** { *; }
-keepclassmembers class org.ehcache.sizeof.** { *; }
-dontwarn org.ehcache.sizeof.**

# Keep enum names used by Choco propagator selection
-keepclassmembers enum org.chocosolver.** { *; }
```

(Documented for completeness; we keep solver on the server.)

### Reference: Choco for Diet/Stigler problem

The Stigler Diet Problem (1945) is the canonical LP formulation of "cheapest diet meeting nutritional constraints." Choco-solver handles it as an ILP via its `IntVar` and `arithm` constraints. The spec §18 already locks the modeling; this round confirms the library choice is sound — Choco is the mature MIT-licensed JVM option, dominant in the constraint-programming research community.

Alternates considered + rejected:
- **OR-Tools (Google)**: Apache 2; pulls in C++ native binaries; multi-platform packaging hell on Windows. Don't.
- **ojAlgo**: pure-Java, MIT, fast LP. Choco wins on documentation + CP-SAT-style hybrid solving for our discrete day-pattern constraints.
- **JaCoP**: MIT, similar to Choco; smaller community. Choco wins by default.

---

## 10. RO-capable LLMs

### Decision matrix at the Dietician usage profile (~150 q/day × ~600 tokens avg = 90k tokens/day = ~2.7M tokens/month)

| Model | RO benchmark | Cost (input/output per MTok) | Context | Best use in Dietician |
|---|---|---|---|---|
| **Claude Opus 4.7** (via ClaudeMax CLI on desktop) | Strong (multilingual evals) | $0 to user (subscription) | 200K | Heavy reasoning + flyer Vision (spec §7.4 / §8.3). |
| **Claude Sonnet 4.6** (Anthropic API direct, fallback when ClaudeMax CLI fails) | Same family | $3 / $15 | 200K | Emergency fallback only. |
| **Claude Haiku 4.5** (Anthropic API direct, cheap fast queries) | Same family | $1 / $5 | 200K | Recipe parse / quick categorization. |
| **DeepSeek V3.2** | Good (DeepSeek's multilingual training includes RO at decent share) | $0.252 / $0.378 (OpenRouter) | 128K | **Primary cheap RO-capable text reasoning.** |
| **DeepSeek R1** (reasoning) | Strong | $0.70 / $2.50 (OpenRouter) or $0.55 / $2.19 (direct) | 64K | Solver-output explanation when LLM-as-ranker fires. |
| **Gemini 2.5 Flash** | Strong (95% Croatian, 92% Italian; RO same Romance/Slavic-adjacent family) | ~$0.30 / ~$2.50 | 1M | Vision OCR fallback. Receipt + flyer when ClaudeMax unavailable. |
| **Gemini 2.5 Pro** | Strongest | ~$1.25 / ~$10 | 1M | Skip for cost. |
| **Llama 3.3 70B** | OK (3rd-party RO benchmark scores below DeepSeek V3) | ~$0.59 / ~$0.79 (OpenRouter) | 128K | Open-weights fallback if all APIs fail. |
| **Qwen 3 Coder Next** | Code-tuned, less general | $0.78 / $3.90 (OpenRouter) | 262K | Skip — not our use case. |
| **Qwen 3 Coder 480B A35B** | Strong general | $0.22 / $1.80 (OpenRouter) | 1M | Could be a wildcard; skip for now. |
| **OpenLLM-Ro / RoLlama3-8B-Instruct** | RoMT-Bench 6.39, RoCulturaBench 4.05, RO academic avg 54.66 | $0 (local, ~5GB RAM Q4) | 8K | Worth running on the Dell G5 as a **localizer**: takes Claude/Gemini's English output and rewrites in idiomatic RO. Optional. |
| **RoGemma2-9B / RoMistral-7B** | Lower than RoLlama3 in published scores | $0 (local) | 8K | Skip — RoLlama3 wins among open RO models. |

### Cost projection at 4500 q/month

Assuming routing per spec §7.2 — heavy ~10% via ClaudeMax (free to user), bulk text via DeepSeek V3.2, vision fallback via Gemini Flash:

- ClaudeMax: included in $20/mo Max sub.
- DeepSeek V3.2 via OpenRouter @ 4000 q × 1k tokens avg = 4M tokens → ~$1.01 + $1.51 = **~$2.50/mo**.
- Gemini 2.5 Flash vision fallback ~50 q/mo × $0.001 = **~$0.05/mo**.
- DeepSeek R1 reasoning ~200 q/mo × 2k tokens × $0.70 = **~$0.28/mo**.

**Total cloud cost projection: ~$3/mo** above the $20 Max subscription. Comfortably inside budget.

### RoLlama3 as RO-rewriter

Optional but useful: keep RoLlama3-8B-Instruct loaded in Ollama on the Dell G5 (5GB RAM Q4). Use as a **post-processor** that takes Claude's English output and translates to idiomatic RO. The RoMT-Bench score 6.39 is below Claude/Gemini's general capability, but for the constrained task "translate this notification message" it's plenty. Saves a round-trip to a paid model when RO output is the goal.

---

## 11. ClaudeMax CLI subprocess

### Anthropic-documented surface

Per Anthropic's Claude Code docs (code.claude.com/docs/en/headless):

- `-p` / `--print` = non-interactive run. The default for SDK calls.
- `--bare` = skips auto-discovery of hooks, skills, plugins, MCP servers, auto memory, CLAUDE.md. **Recommended for SDK/scripted calls. Will become default for `-p` in a future release** — pin our wrapper to explicitly pass `--bare` until that flip, then drop it.
- `--output-format json` = structured output. `stream-json` = NDJSON streaming.
- `--allowedTools "Read"` = restrict tool access (we want this for Dietician's vision/flyer/recipe parsing where Claude only needs Read on a downloaded PDF/image).
- `--resume` = continuation. Probably skip — each call is one-shot.

### Subprocess robustness — what to design for

| Failure mode | Detection | Recovery |
|---|---|---|
| CLI not on PATH | `which claude` at startup; surface "ClaudeMax not configured" in `/diag` | Fall back to Gemini API. |
| Cold-start ~12s overhead (per anthropics/claude-agent-sdk-typescript#34) | Built into latency budget | Show progress UI: "ClaudeMax starting…" |
| Windows async-context-manager hang (anthropics/claude-agent-sdk-python#208) | 60s timeout on the wrapper | Kill + fall back to Gemini API. |
| stdout/stderr mixed | Use `--output-format json` and read stdout only | Parse JSON line-by-line; warn on parse failure. |
| Anthropic CLI flag rename / removal | Smoke-test once per session via `claude --version` | Surface in `/diag`; user manually updates CLI. |
| stdin > 10MB cap (per CLI v2.1.128) | Pre-check input size | Write to file, pass path. |
| Subscription quota exhausted | Detect via stderr JSON error code | Fall back to Gemini API. |

### Subprocess pool sizing

- Hard cap: `min(CPU_cores - 2, 3)` concurrent processes. The Dell G5 is likely 4-8 cores; cap at 3.
- Soft cap: 1 concurrent process during interactive UI work to keep the desktop responsive.
- Timeout per call: configurable per use-case. Receipt OCR: 30s. Flyer ingest: 120s. Paper analysis: 300s.
- Retry: exponential backoff 2× with max 2 retries; failover to Gemini after.

### vs Anthropic SDK direct call

| Aspect | ClaudeMax CLI | Anthropic SDK direct |
|---|---|---|
| Cost | $0 to user (Max sub) | $3-$15/MTok |
| Latency (cold) | ~12s | ~500ms |
| Latency (warm) | N/A (one-shot per spawn) | ~500ms |
| Streaming | Yes (`stream-json`) | Yes |
| Pricing model | Subscription cap | Per-token |
| When to use | All Plan-3/Plan-6 heavy LLM ops on desktop | Emergency fallback if Max sub expires |

### Two-phase budget tracking for ClaudeMax (no $-cost, but still rate-limited)

ClaudeMax subscriptions have **usage caps measured in messages, not tokens**. The router (§7.5) should track a separate `claudemax_messages_today` counter, reset at the same time the subscription window resets. When 80% consumed, route subsequent heavy queries to DeepSeek R1 instead.

---

## 12. Two-phase budget reserve for LLM spend

### Pattern (confirmed by Zuplo, Azure APIM `llm-token-limit`, Portkey, Hivenet, TrueFoundry research)

1. **Reserve phase (request-start)**: estimate token cost (input tokens count + worst-case output). Atomically decrement available budget by reservation; reject if budget below threshold. Insert row `(request_id, reservation, status=PENDING)` in `llm_budget`.
2. **Finalize phase (completion)**: read actual `usage.input_tokens` + `usage.output_tokens` from the provider response. Update row: `(actual_input, actual_output, status=COMPLETED)`. Refund or charge the difference vs reservation.
3. **Failure phase**: on subprocess timeout / HTTP 5xx / parse error: mark row `status=FAILED`, full refund.

### `llm_budget` table schema (Postgres canonical, replicated to per-client SQLite per spec §5)

```sql
CREATE TABLE llm_budget (
    request_id     UUID PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users(id),
    provider       TEXT NOT NULL,      -- 'claudemax', 'openrouter:deepseek-v3.2', 'gemini-flash', ...
    use_case       TEXT NOT NULL,      -- 'receipt-ocr', 'recipe-parse', 'planner-rank', ...
    reservation    INTEGER NOT NULL,   -- estimated tokens (input + max output)
    actual_input   INTEGER,
    actual_output  INTEGER,
    cost_cents     INTEGER,            -- for $-costed providers; NULL for ClaudeMax
    status         TEXT NOT NULL CHECK (status IN ('PENDING','COMPLETED','FAILED','REFUNDED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finalized_at   TIMESTAMPTZ,
    error_kind     TEXT                -- 'timeout','quota','parse-error',...
);

CREATE INDEX llm_budget_provider_day ON llm_budget(provider, DATE(created_at));
CREATE INDEX llm_budget_user_day ON llm_budget(user_id, DATE(created_at));
```

### Per-provider rate-limit tracking

```sql
CREATE TABLE llm_rate_limit (
    provider      TEXT NOT NULL,
    bucket        TEXT NOT NULL,        -- 'rpm','rpd','tpm','tpd','sub-message-quota'
    window_start  TIMESTAMPTZ NOT NULL,
    used          INTEGER NOT NULL DEFAULT 0,
    cap           INTEGER NOT NULL,
    PRIMARY KEY (provider, bucket, window_start)
);
```

### OpenRouter free tier specifics (locked, per OpenRouter docs)

- Without $10 in credits: **50 RPD** + 20 RPM ceiling, applies across `*:free` model routes.
- With $10+ in credits purchased: **1000 RPD** + 20 RPM ceiling for `:free` routes.

For Dietician's 4500-q/month projection ≈ 150 q/day. **If we don't purchase $10 in OpenRouter credits, we will hit 50 RPD with one heavy user**. Mitigation: spec §7.3 should include a one-time $10 OpenRouter top-up as part of project setup. Document this in the runbook.

### Reservation calculation heuristics

| Use case | Input estimate | Output estimate |
|---|---|---|
| Recipe parse | 1k tokens | 800 tokens |
| Receipt OCR | 600 tokens (image as ~258 + prompt) | 400 tokens |
| Flyer ingest | 4k tokens (vision-heavy) | 2k tokens |
| Planner rerank | 6k tokens (Choco output JSON) | 1.5k tokens |
| Paper QA | 12k tokens (retrieved chunks) | 1.5k tokens |
| `/diag` summary | 200 tokens | 200 tokens |

Refine these constants in the runbook after Plan-3 ships and we have a week of real data.

---

## 13. Backup posture + DR

### Recommendation: pg_dump | zstd | rclone → Backblaze B2

Confirmed: B2 at $0.005/GB-month storage + $0.01/GB egress. Storing 5GB of compressed Postgres dumps (rolling 30-day window) = **$0.025/month**. Per the LowEndBox and DCHost playbooks, this is the cheapest reliable pattern.

### Backup script (production-ready outline for the VPS systemd timer)

```bash
#!/usr/bin/env bash
set -euo pipefail
TS=$(date -u +%Y%m%dT%H%M%SZ)
BACKUP_DIR=/var/backups/dietician
mkdir -p "$BACKUP_DIR"

# Dump + compress (zstd level 19 = best compression, slow but VPS is idle)
pg_dump --format=custom --compress=0 dietician \
  | zstd -19 -T0 -o "$BACKUP_DIR/dietician-${TS}.dump.zst"

# Upload to B2 with rclone (encrypt remote configured separately)
rclone copy "$BACKUP_DIR/dietician-${TS}.dump.zst" b2-crypt:dietician-backups/

# Rotate local copies (keep 7 days)
find "$BACKUP_DIR" -name 'dietician-*.dump.zst' -mtime +7 -delete

# Notify ntfy on success
curl -s -d "Backup ${TS} OK ($(du -h $BACKUP_DIR/dietician-${TS}.dump.zst | cut -f1))" \
  https://ntfy.example.com/dietician-ops
```

### Restore drill

Run quarterly; the runbook (spec §24) should include a "test restore to scratch DB" task. Steps:

```bash
rclone copy b2-crypt:dietician-backups/dietician-20260517T030000Z.dump.zst /tmp/
zstd -d /tmp/dietician-20260517T030000Z.dump.zst -o /tmp/restore.dump
createdb dietician_restore_test
pg_restore --dbname=dietician_restore_test --no-owner --no-privileges /tmp/restore.dump
psql dietician_restore_test -c "SELECT count(*) FROM ledger_event;"
dropdb dietician_restore_test
```

### Encryption-at-rest

Use `rclone crypt` remote on top of the B2 remote. Per the vimoire.com TrueNAS + Backblaze + rclone playbook: this client-side encrypts both filenames and content. Key stored in `~/.config/rclone/rclone.conf` (chmod 600). **Key backup is the user's responsibility** — without it, B2 storage is useless. Document in spec §25.

### Alternative servers-side encryption

B2 native server-side encryption (SSE) with Backblaze-managed keys is one click in the bucket settings. **Use both**: SSE for "Backblaze can't peek at storage", + client-side rclone crypt for "Backblaze admins can't see filenames either". Defense in depth.

---

## 14. Observability

### Logging: Napier on KMP, slf4j on Ktor server

- **Clients (Android + Desktop)**: `Napier` (AAkira/Napier). Common-module logging surface; routes to Logcat on Android, java.util.logging or stdout on Desktop. Alternative `Kermit` is similar; Napier is more mature.
- **Server**: standard slf4j + logback. Ktor uses slf4j natively. No need for KMP-multiplatform logging library on the server side.

Per the kotlinlang.slack proposal thread: there is no clean SLF4J-equivalent in KMP common code. Napier or Kermit fill the gap; both treat the JVM side as "delegate to slf4j if present, fall back to java.util.logging."

### Metrics: Micrometer + Prometheus on the server, Beszel as the lightweight VPS-wide monitor

The full Prometheus + Grafana stack costs 500-800MB resident — non-trivial inside our 2GB headroom. Recommended **light stack**:

- **Beszel** (<10MB RAM) for VPS-level CPU/RAM/disk metrics. One-step Docker install. Web UI included.
- **Micrometer in Ktor** with the Prometheus registry → expose `/metrics` endpoint on a private port → scrape from Prometheus only when needed.
- **Prometheus + Grafana NOT installed by default**. Add only when we hit a perf incident and need cardinality views.

This keeps observability cost effectively zero in steady state.

### Distributed tracing: OpenTelemetry KMP SDK (experimental but in production on Android)

The OpenTelemetry Kotlin Multiplatform SDK was donated by Embrace in March 2026 and accepted by CNCF. Status:

- Android: stable in production (Embrace ships this commercially).
- JVM: stable.
- iOS: experimental.
- JS: experimental.

For Dietician, **add OTel instrumentation only when we have an actual cross-system tracing need** — e.g. "trace a user action from Android → Ktor → Postgres → Claude subprocess → result back to Android." Plan-3 sync flows are simple enough that logging is sufficient. Park OTel for a later plan if needed.

### `/diag` slash command (per spec §23)

The spec already calls out a self-diagnostic. The deep-dive confirms this is the right pattern:
- Smoke-test ClaudeMax CLI presence + auth (`claude --version`)
- Smoke-test Ollama presence + model loaded
- Smoke-test Postgres reachable via Tailscale
- Smoke-test ntfy delivery (post + subscribe round-trip)
- Smoke-test OpenRouter API key valid
- Report `llm_budget` table state for the last 24h

Include in the desktop UI's "About / Status" tab, also exposed via `/diag` CLI subcommand.

---

## 15. Structured concurrency patterns

### Core idioms for Dietician

Per Adityamishra (Medium) and the official Kotlin docs:

#### `coroutineScope { ... }`
- All children share the same cancellation lifecycle.
- If ANY child fails, all siblings are cancelled.
- Use for: fan-out where a single failure should fail the whole batch (e.g. "load planner inputs in parallel").

#### `supervisorScope { ... }`
- Children fail independently.
- Cancellation propagates parent → children, NOT child → siblings.
- Use for: background workers, multi-provider parallel calls (try Claude AND Gemini, take fastest).

### OutboxDrainWorker pattern (spec §3 architecture)

```kotlin
class OutboxDrainWorker(
    private val ledger: LedgerDao,
    private val syncClient: SyncClient,
    private val scope: CoroutineScope,                           // viewModelScope or applicationScope
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            supervisorScope {
                while (isActive) {
                    runCatching {
                        val batch = ledger.pendingOutbox(limit = 100)
                        if (batch.isEmpty()) {
                            delay(30.seconds)
                            return@runCatching
                        }
                        syncClient.push(batch)                    // suspending, cancellable
                        ledger.markSynced(batch.map { it.id })
                    }.onFailure { e ->
                        when (e) {
                            is CancellationException -> throw e   // re-throw to honor cancellation
                            is IOException -> {
                                Napier.w("outbox push failed; backoff", e)
                                delay(60.seconds)
                            }
                            else -> {
                                Napier.e("outbox unexpected failure", e)
                                delay(5.minutes)
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
```

Notes:
- `supervisorScope` ensures the inner `runCatching` block can fail without taking down the worker loop.
- `CancellationException` is explicitly re-thrown — per the techyourchance + mbrizic guidance, you MUST re-throw cancellation or the worker keeps running after stop().
- `launch(SupervisorJob())` is the classic anti-pattern (per Adityamishra) — it creates a regular Job with a SupervisorJob *parent*, which doesn't give supervisor semantics to the launched coroutine's children. Use `supervisorScope { ... }` instead. (Trap.)

### WAL checkpoint hook (spec §3 + §3 architecture)

After each outbox drain, run `PRAGMA wal_checkpoint(PASSIVE)` to release WAL pages. If `wal_autocheckpoint=1000` (1000 pages = ~4MB) is set, this is automatic — but the manual hook protects against checkpoint starvation when readers hold connections open. Don't use `TRUNCATE` mode unless you can guarantee no other readers; PASSIVE is safe.

### Cancellation propagation in subprocess wrappers (ClaudeMax)

When the user cancels a long-running ClaudeMax call (e.g. flyer ingest):

```kotlin
suspend fun runClaudeMax(prompt: String, timeout: Duration): Result<String> =
    withContext(Dispatchers.IO) {
        val process = ProcessBuilder("claude", "--bare", "-p", prompt, "--output-format", "json")
            .redirectErrorStream(false)
            .start()
        try {
            withTimeoutOrNull(timeout) {
                process.inputStream.bufferedReader().use { it.readText() }
            }?.let { Result.success(parseClaudeJson(it)) }
                ?: Result.failure(TimeoutException("ClaudeMax exceeded $timeout"))
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw e
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
```

- `withTimeoutOrNull` is the standard cancellable-timeout pattern.
- `process.destroyForcibly()` in the cancellation catch is **mandatory** — otherwise the subprocess outlives the coroutine, leaking process handles.
- The `finally` block is defensive; the timeout path also kills it.

### `viewModelScope` on Android + desktop equivalent

- Android: `viewModelScope` (Compose ViewModel) cancels on `ViewModel.onCleared()`.
- Desktop Compose: no ViewModel by default. Use `rememberCoroutineScope()` for UI-bound work; pass an `applicationScope: CoroutineScope` (created at app startup, `SupervisorJob() + Dispatchers.Default`) for cross-screen background work like the OutboxDrainWorker.

---

## Cross-cutting synthesis

### Recommended stack table

| Component | Version pin | License | Why-picked | Alternatives |
|---|---|---|---|---|
| Kotlin | 2.1.21+ | Apache 2 | KMP standard | n/a |
| Compose Multiplatform | 1.8.x (LTS), 1.10.x (fast-follower) | Apache 2 | Stable on Android + Desktop + iOS | n/a |
| Multiplatform-settings | 1.3.x | Apache 2 | Cross-platform key-value | n/a |
| kotlinx-coroutines-core | 1.9.x | Apache 2 | Structured concurrency | n/a |
| kotlinx-datetime | 0.8.x | Apache 2 | Cross-platform time | java.time (JVM-only) |
| kotlinx-serialization-json | 1.7.x | Apache 2 | Polymorphic sealed-interface JSON | Moshi (Android-only) |
| SQLDelight | 2.0.x | Apache 2 | Plan-1 sunk cost; type-safe SQL; multiplatform | Room KMP, Realm (sunset) |
| Ktor (client) | 3.2.x | Apache 2 | KMP HTTP client | Apollo, Retrofit |
| Ktor (server) | 3.2.x | Apache 2 | Same kotlinx-io stack | Spring Boot (heavyweight) |
| Ktor client engine (Android) | OkHttp | Apache 2 | Best ecosystem on Android | CIO native |
| Ktor client engine (Desktop) | OkHttp | Apache 2 | Code-share with Android | Java HttpClient |
| Ktor server engine | CIO | Apache 2 | Coroutine-native, low memory | Netty (overkill at our scale) |
| Postgres | 16 | PostgreSQL License | Already installed; rock-solid | n/a |
| pgvector | 0.8.2 | PostgreSQL License | CVE-2026-3172 fixed in 0.8.2 — **upgrade if pinned below** | n/a |
| Tailscale | latest | Tailscale (closed-source client; OSS server alt: headscale) | Private mesh networking | WireGuard direct |
| ntfy | latest self-hosted | Apache 2 | Self-hosted push, UnifiedPush-compatible | FCM, APNs, WebPush |
| Napier (logging) | 2.7.x | Apache 2 | KMP logger | Kermit |
| Micrometer (server metrics) | 1.13.x | Apache 2 | Standard JVM metrics | Dropwizard |
| Beszel (VPS metrics) | latest | MIT | <10MB RAM | Prometheus + Grafana (500-800MB) |
| Choco-solver | 4.10.14 | BSD-4 (note: not pure MIT — research the redistribution clause) | Mature CP/ILP solver | OR-Tools, ojAlgo, JaCoP |
| GROBID | 0.8.1 | Apache 2 | Scientific paper structural extraction | Marker, MinerU, Docling, olmOCR |
| Marker | latest | MIT (weights GPLv3 — read carefully) | OCR-heavy fallback | MinerU |
| PyMuPDF4LLM | latest | AGPL or commercial | Fast text-layer probe | pdfminer.six |
| Ollama | latest | MIT | Local LLM/embedding runner | llama.cpp direct |
| nomic-embed-text v1.5 | latest | Apache 2 | Local 768-dim embeddings, Matryoshka-truncatable | bge-m3 |
| Voyage-4-Lite (cloud embeddings) | latest | Voyage TOS | 200M tokens free, 1024-dim | OpenAI text-embedding-3-small |
| ClaudeMax CLI | latest | Anthropic TOS | Subscription-budgeted heavy LLM | Anthropic API direct |
| OpenRouter | n/a (gateway) | OpenRouter TOS | Unified billing across DeepSeek + Gemini + Llama | Direct provider APIs |
| OpenTelemetry KMP SDK | experimental | Apache 2 | Future-proof tracing | None |
| Backblaze B2 + rclone crypt | n/a | n/a / MIT | Cheapest reliable backup | AWS S3 Glacier |

### VPS-budget viability check

| Service | Steady-state RAM | Peak RAM | Fits in 2GB headroom? |
|---|---|---|---|
| Ktor server (CIO, -Xmx512m) | ~300MB | ~500MB | ✓ |
| Postgres 16 (shared_buffers=1GB, effective_cache=1.5GB) | ~1.2GB | ~1.8GB | ✓ (largest tenant) |
| pgvector HNSW index (5-10k vectors, 768/1024-dim) | included above | included above | ✓ |
| ntfy | ~50MB | ~100MB | ✓ |
| nginx | ~30MB | ~50MB | ✓ |
| Prometheus + Grafana (NOT installed) | 500-800MB | — | ✗ — skip |
| Beszel (alternative) | ~10MB | ~20MB | ✓ |
| Backup script (cron, transient) | ~200MB (zstd peak) | ~400MB | ✓ (runs at 03:00 UTC) |

**Total steady-state: ~1.6GB. Total peak (during backup window): ~2.4GB.** Tight but workable. The backup window is the riskiest moment — schedule pg_dump for the same window MC is empty (03:00 UTC, no one playing).

### Plans 2-7 implementation order

Order recommendation (no time estimates per user feedback `feedback_no_time_estimates.md`, just sequence + dependency reasoning):

1. **Plan 2: LLM Router (`:shared:llm`)** — unblocks every downstream plan. Sealed interface, two-phase budget, provider registry. Ship with at minimum 3 providers wired (ClaudeMax CLI, DeepSeek V3.2 via OpenRouter, Gemini 2.5 Flash via OpenRouter).
2. **Plan 3: Ktor server + sync endpoints** — unblocks Android UI work. Includes `llm_budget` Postgres tables + nightly backup cron.
3. **Plan 4: Android UI (Compose Multiplatform on Android side)** — first user-facing surface. Pantry + body log + receipt OCR + meal-plan view. Ship to phone.
4. **Plan 5: Desktop UI (Compose Multiplatform on JVM side)** — adds power-user features (PDF ingest, flyer Vision, scraper management).
5. **Plan 6: Scrapers (Playwright + adapters)** — fills the price corpus. Decoupled from UI; runs on desktop nightly.
6. **Plan 7: Corpus loader (recipes/supplements/papers + embeddings)** — fills the knowledge corpus. Ingestion-only; UI for browsing arrives with Plan 4/5.

Dependencies that suggest deviation:
- Plan 6 can ship between Plan 2 and Plan 4 if the user wants to validate scraper output via CLI before the UI exists.
- Plan 7's paper ingest depends on GROBID/Marker being installed on the desktop — independent of LLM router maturity, can be parallel to Plan 3.

---

## Open questions for Round 3+

1. **Voyage API key rotation**: 200M free tokens per account is generous but if we churn keys (e.g. one per user for usage attribution), how does the free tier behave? Likely re-allocates per account; verify before assuming free.
2. **Choco-solver license clause** in version 4.10.14: actually BSD-4 in newer releases (was BSD-3 historically) — verify the **4-clause acknowledgment requirement** doesn't surface in a "About" screen. Spec memory says "MIT" — that may be inaccurate. **Re-verify before Plan 7.**
3. **AGPL contagion for MinerU**: if we redistribute the Dietician desktop installer with MinerU bundled inside, AGPL § applies. If MinerU runs as a separately-installed CLI subprocess, classic LGPL/GPL-like aggregation argument may apply. **Legal grey zone for a 5-user friends-and-family deployment but worth documenting.**
4. **Compose Hot Reload** (CM 1.10) is "stable" per the Jan 2026 JetBrains blog — verify dev-loop ergonomics on Windows specifically before committing.
5. **ntfy SSE vs HTTP-long-poll on Desktop**: which has lower memory + network jitter? Probably SSE; worth a microbenchmark in Plan 5.
6. **OpenLLM-Ro localizer worth it?** A 1.4GB Ollama model loaded permanently on the Dell G5 to translate Claude outputs to idiomatic RO. Net memory cost is real; net quality improvement is uncertain. Defer until Plan 4 hits a real RO-output use case.
7. **pgvector index choice — HNSW or IVFFlat?** Spec doesn't lock this. For ≤10k vectors, IVFFlat is fine and indexes faster; HNSW is better at >100k. Recommend IVFFlat initially with `lists=100` and migrate to HNSW if recall regresses.
8. **Tailscale auth-key rotation cadence**: are we using user-bound or device-bound keys? Device-bound + 90-day expiry is the safer default. Document in §26 (credentials).

---

## Sources

(Listed in order of first citation. Some sources cover multiple sections.)

### Compose Multiplatform / KMP

1. [Compose Multiplatform – Beautiful UIs Everywhere (kotlinlang.org)](https://kotlinlang.org/compose-multiplatform/)
2. [Compose Multiplatform 1.8.0 Released — iOS Stable (JetBrains Blog, 2025-05)](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)
3. [Compatibility and versions — KMP docs](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
4. [Stability of supported platforms — KMP docs](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)
5. [Compose Multiplatform 1.10.0 — Navigation 3 + Hot Reload (JetBrains Blog, 2026-01)](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/)
6. [Helping Decision-Makers Say Yes to KMP (JetBrains Blog, 2026-04)](https://blog.jetbrains.com/kotlin/2026/04/helping-decision-makers-say-yes-to-kmp/)
7. [A New Default Project Structure for KMP (JetBrains Blog, 2026-05)](https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/)
8. [Update your Kotlin projects for AGP 9.0 (JetBrains Blog, 2026-01)](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
9. [KotlinConf 2026 schedule (JetBrains Blog, 2026-03)](https://blog.jetbrains.com/kotlin/2026/03/kotlinconf-2026-talks-schedule/)
10. [Why KMP & CMP are production-ready in 2026 (BestHub)](https://www.besthub.dev/articles/why-kotlin-multiplatform-compose-multiplatform-are-production-ready-in-2026-24d731545514)
11. [Compose Multiplatform in 2026 (My Android Solutions, 2026-03)](https://www.myandroidsolutions.com/2026/03/23/compose-multiplatform-shared-ui-android-ios/)
12. [Is KMP production-ready in 2026? (KMP Ship)](https://www.kmpship.app/blog/is-kotlin-multiplatform-production-ready-2026)
13. [Cash App KMP case study](https://kotlinlang.org/lp/mobile/case-studies/cash-app)
14. [KMP case studies index (kotlinlang.org)](https://kotlinlang.org/case-studies/)
15. [Big Tech's Secret Weapon: Netflix, McDonald's & ... (KMP Ship)](https://www.kmpship.app/blog/big-companies-kotlin-multiplatform-2025)
16. [KMP 2025 Updates and 2026 Predictions (Aetherius Solutions)](https://www.aetherius-solutions.com/blog-posts/kotlin-multiplatform-in-2026)
17. [Compose Multiplatform — Yanneck Reiß "Finally Stable for iOS"](https://medium.com/tech-takeaways/compose-multiplatform-is-finally-stable-for-ios-c9e59d696864)

### Kotlin language + expect/actual

18. [Expected and actual declarations — KMP docs](https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html)
19. [What's new in Kotlin 2.0.0](https://kotlinlang.org/docs/whatsnew20.html)
20. [What's new in Kotlin 2.1.20](https://kotlinlang.org/docs/whatsnew2120.html)
21. [Compatibility guide for KMP](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
22. [Releases — JetBrains/kotlin (GitHub)](https://github.com/jetbrains/kotlin/releases)

### Ktor client + server

23. [Client engines — Ktor docs](https://ktor.io/docs/client-engines.html)
24. [Server engines — Ktor docs](https://ktor.io/docs/server-engines.html)
25. [Ktor 3.0 — New features + performance (JetBrains Blog, 2024-10)](https://blog.jetbrains.com/kotlin/2024/10/ktor-3-0/)
26. [ktor-benchmarks (GitHub)](https://github.com/ktorio/ktor-benchmarks)
27. [Engine performance — KTOR-1121 (YouTrack)](https://youtrack.jetbrains.com/issue/KTOR-1121)
28. [Andhukuri — OkHttp vs CIO file upload deep dive (Medium)](https://pavan-andhukuri.medium.com/debugging-ktor-http-client-performance-a-deep-dive-into-okhttp-vs-cio-file-upload-speeds-cb32dbebbe06)
29. [Ioannis Anifantakis — Ktor CIO in 2026 vs OkHttp/Darwin (ITNEXT)](https://itnext.io/ktors-cio-engine-in-2026-can-it-finally-replace-okhttp-and-darwin-8f7b7e7e1553)
30. [matej.laitl.cz — Http4k / Ktor / Actix benchmarks](https://matej.laitl.cz/bench-rust-kotlin-microservices/)
31. [Ktor JWT — JSON Web Tokens docs](https://ktor.io/docs/server-jwt.html)
32. [Ktor Session authentication docs](https://ktor.io/docs/server-session-auth.html)
33. [Ktor server WebSockets docs](https://ktor.io/docs/server-websockets.html)
34. [Ktor SSE — server docs](https://ktor.io/docs/server-server-sent-events.html)
35. [Ktor SSE — client docs](https://ktor.io/docs/client-server-sent-events.html)
36. [Cekrem — Taming Flows + Coroutines, "How not to DDoS yourself with SSE"](https://cekrem.github.io/posts/the-subtle-art-of-taming-flows-and-coroutines-in-kotlin/)

### SQLDelight / Room / Realm

37. [SQLDelight Multiplatform Setup 2.0.2](https://sqldelight.github.io/sqldelight/2.0.2/multiplatform_sqlite/)
38. [SQLDelight Native getting started (Kotlin/Native)](https://sqldelight.github.io/sqldelight/2.0.2/native_sqlite/)
39. [eygraber/sqldelight-androidx-driver (GitHub)](https://github.com/eygraber/sqldelight-androidx-driver)
40. [Room vs SQLDelight for KMP 2026 (bswen)](https://docs.bswen.com/blog/2026-03-14-room-vs-sqldelight-kmp/)
41. [FunkyMuse — SQLDelight in KMP](https://funkymuse.dev/posts/sql-delight-kmp/)
42. [FunkyMuse — Room KMP setup](https://funkymuse.dev/posts/kmp-room-setup/)
43. [Realm Kotlin SDK (GitHub)](https://github.com/realm/realm-kotlin)
44. [MongoDB Ends Mobile Support — Migrate to Couchbase (Couchbase Blog, 2025)](https://www.couchbase.com/blog/realm-mongodb-eol-day-2025/)
45. [Realm 2.0.0 release notes (Medium / Claus Rorbech)](https://medium.com/@claus.rorbech/realm-kotlin-2-0-0-dd0351252120)

### SQLite WAL + Android performance

46. [Write-Ahead Logging — SQLite docs](https://sqlite.org/wal.html)
47. [Best practices for SQLite performance — Android Developers](https://developer.android.com/topic/performance/sqlite-performance-best-practices)
48. [PowerSync — SQLite Optimizations for Ultra High Performance](https://powersync.com/blog/sqlite-optimizations-for-ultra-high-performance)
49. [SQLite WAL Mode + Connection Strategies (DEV.to)](https://dev.to/software_mvp-factory/sqlite-wal-mode-and-connection-strategies-for-high-throughput-mobile-apps-beyond-the-basics-eh0)
50. [Loke.dev — The 20GB WAL file (checkpoint starvation)](https://loke.dev/blog/sqlite-checkpoint-starvation-wal-growth)

### Push notifications

51. [ntfy — UnifiedPush distributor docs](https://unifiedpush.org/users/distributors/ntfy/)
52. [UnifiedPush intro (F-Droid 2022)](https://f-droid.org/en/2022/12/18/unifiedpush.html)
53. [UnifiedPush distributors list](https://unifiedpush.org/users/distributors/)
54. [ntfy phone subscription docs](https://docs.ntfy.sh/subscribe/phone/)
55. [Eugene Davis — Offline Notifications with ntfy + UnifiedPush (2026-02)](https://eugenemdavis.net/archives/2026-02-23-offline-notifications-with-ntfy-and-unifiedpush/)
56. [Akash Rajpurohit — Self-host ntfy](https://akashrajpurohit.com/blog/selfhost-ntfy-for-push-notifications/)
57. [XDA — Self-hosted notification service review](https://www.xda-developers.com/set-up-self-hosted-notification-service/)
58. [FCM on Android — Firebase Blog (2025-04)](https://firebase.blog/posts/2025/04/fcm-on-android/)
59. [FCM message priority docs](https://firebase.google.com/docs/cloud-messaging/android-message-priority)
60. [Android Doze + App Standby docs](https://developer.android.com/training/monitoring-device-state/doze-standby)
61. [Understanding FCM Delivery Rates (Firebase Blog, 2024-07)](https://firebase.blog/posts/2024/07/understand-fcm-delivery-rates/)

### Document parsing (GROBID alternatives)

62. [olmOCR paper — AI2 (2024)](https://olmocr.allenai.org/papers/olmocr.pdf)
63. [Docling Technical Report (arXiv 2408.09869)](https://arxiv.org/html/2408.09869v4)
64. [Docling — Efficient Open-Source Toolkit (arXiv 2501.17887)](https://arxiv.org/html/2501.17887v1)
65. [Eulerai — Open-Source Document Parsers benchmark](https://www.eulerai.au/blog/doc-parser-benchmark)
66. [docling vs GROBID — Docling GitHub Issue #74](https://github.com/DS4SD/docling/issues/74)
67. [Best Open-Source PDF-to-Markdown Tools 2026 — Marker / Docling / MinerU / PyMuPDF4LLM (Themenonlab)](https://themenonlab.blog/blog/best-open-source-pdf-to-markdown-tools-2026)
68. [datalab-to/marker (GitHub)](https://github.com/datalab-to/marker)
69. [Marker on Replicate](https://replicate.com/blog/datalab-marker-and-ocr-fast-parsing)
70. [MinerU GitHub](https://github.com/opendatalab/mineru)
71. [MinerU 2.5 1.2B VLM card — Hugging Face](https://huggingface.co/opendatalab/MinerU2.5-2509-1.2B)
72. [MinerU low VRAM notes](https://www.nite07.com/en/posts/mineru-low-vram/)
73. [MinerU2.5: A Decoupled Vision-Language Model (Medium)](https://medium.com/@huguosuo/mineru2-5-a-decoupled-vision-language-model-for-efficient-high-resolution-document-parsing-5ac976ee679f)
74. [PyMuPDF4LLM docs](https://pymupdf.readthedocs.io/en/latest/pymupdf4llm/)
75. [PyMuPDF Features Comparison](https://pymupdf.readthedocs.io/en/latest/about.html)
76. [PaperMage — ACL Anthology paper](https://aclanthology.org/2023.emnlp-demo.45.pdf)
77. [allenai/papermage (GitHub)](https://github.com/allenai/papermage)
78. [VILA — ACL Anthology (2022)](https://aclanthology.org/2022.tacl-1.22/)

### OCR + vision

79. [Tesseract vs EasyOCR vs PaddleOCR vs MMOCR (Toon Beerten / Medium)](https://toon-beerten.medium.com/ocr-comparison-tesseract-versus-easyocr-vs-paddleocr-vs-mmocr-a362d9c79e66)
80. [PaddleOCR vs Tesseract vs EasyOCR — CodeSOTA 2026](https://www.codesota.com/ocr/paddleocr-vs-tesseract)
81. [IntuitionLabs — Technical Analysis of Modern Non-LLM OCR Engines](https://intuitionlabs.ai/articles/non-llm-ocr-technologies)
82. [Gemini 2.5 OCR (Ultralytics blog)](https://www.ultralytics.com/blog/get-hands-on-with-google-gemini-2-5-for-computer-vision-tasks)
83. [Why Gemini 2.5 Pro for OCR (rogue-marketing)](https://the-rogue-marketing.github.io/why-google-gemini-2.5-pro-api-provides-best-and-cost-effective-solution-for-ocr-and-document-intelligence/)
84. [Conquering Large PDF OCR with Gemini 2.5 Flash (Medium)](https://medium.com/@xavierjesudhas3/conquering-large-pdf-ocr-with-gemini-2-5-flash-a-streamlined-methodology-babfa172f665)
85. [LLaVA-NeXT (GitHub)](https://github.com/LLaVA-VL/LLaVA-NeXT)

### LLM providers + pricing

86. [Claude API pricing (Anthropic)](https://platform.claude.com/docs/en/about-claude/pricing)
87. [Claude Models overview](https://platform.claude.com/docs/en/about-claude/models/overview)
88. [Claude Opus 4.7 pricing (CloudZero)](https://www.cloudzero.com/blog/claude-opus-4-7-pricing/)
89. [Anthropic API pricing breakdown 2026 (Finout)](https://www.finout.io/blog/anthropic-api-pricing)
90. [Claude Haiku 4.5 deep dive (Caylent)](https://caylent.com/blog/claude-haiku-4-5-deep-dive-cost-capabilities-and-the-multi-agent-opportunity)
91. [Gemini API pricing (Google AI)](https://ai.google.dev/gemini-api/docs/pricing)
92. [Gemini 2.5 Flash API pricing 2026 (Price Per Token)](https://pricepertoken.com/pricing-page/model/google-gemini-2.5-flash)
93. [Gemini API pricing guide 2026 (MetaCTO)](https://www.metacto.com/blogs/the-true-cost-of-google-gemini-a-guide-to-api-pricing-and-integration)
94. [DeepSeek V3 — OpenRouter pricing](https://openrouter.ai/deepseek/deepseek-chat)
95. [DeepSeek R1 — OpenRouter pricing](https://openrouter.ai/deepseek/deepseek-r1)
96. [DeepSeek V3.2 — OpenRouter pricing](https://openrouter.ai/deepseek/deepseek-v3.2)
97. [DeepSeek-V3 vs Llama 3.3 70B comparison (llm-stats)](https://llm-stats.com/models/compare/deepseek-v3-vs-llama-3.3-70b-instruct)
98. [Wolfram — LLM Comparison/Test 2025-01](https://huggingface.co/blog/wolfram/llm-comparison-test-2025-01-02)
99. [Qwen3 Coder 480B A35B — OpenRouter pricing](https://openrouter.ai/qwen/qwen3-coder)
100. [Qwen3 Coder Next — OpenRouter pricing](https://openrouter.ai/qwen/qwen3-coder-next)
101. [OpenLLM-Ro Hugging Face org](https://huggingface.co/OpenLLM-Ro)
102. [OpenLLM-Ro technical report (arXiv 2405.07703)](https://arxiv.org/abs/2405.07703)
103. [RoLlama3-8b-Instruct (HF)](https://huggingface.co/OpenLLM-Ro/RoLlama3-8b-Instruct)

### Claude Code SDK + headless

104. [Run Claude Code programmatically (Anthropic Docs — headless)](https://code.claude.com/docs/en/headless)
105. [Anthropic CLI docs (platform.claude.com)](https://platform.claude.com/docs/en/api/sdks/cli)
106. [Anthropic Agent SDK Python](https://github.com/anthropics/claude-agent-sdk-python)
107. [Claude Code CLI Reference (Introl Blog)](https://introl.com/blog/claude-code-cli-comprehensive-guide-2025)
108. [Claude Code Complete Command Reference 2026 (SmartScope)](https://smartscope.blog/en/generative-ai/claude/claude-code-reference-guide/)
109. [Anthropic Agent SDK Python — Windows hang issue #208](https://github.com/anthropics/claude-agent-sdk-python/issues/208)
110. [Anthropic Agent SDK TS — 12s overhead issue #34](https://github.com/anthropics/claude-agent-sdk-typescript/issues/34)
111. [Anthropic Agent SDK TS — WSL2 spawn issue #20](https://github.com/anthropics/claude-agent-sdk-typescript/issues/20)
112. [Anthropic Agent SDK Python — CLAUDECODE=1 inheritance bug #573](https://github.com/anthropics/claude-agent-sdk-python/issues/573)

### Embeddings

113. [nomic-embed-text:v1.5 — Ollama library](https://ollama.com/library/nomic-embed-text:v1.5)
114. [Ollama Embedding Models — Benchmarks (Morph)](https://www.morphllm.com/ollama-embedding-models)
115. [Voyage AI pricing](https://docs.voyageai.com/docs/pricing)
116. [Voyage-3 / Voyage-3-Lite announcement (Voyage Blog)](https://blog.voyageai.com/2024/09/18/voyage-3/)
117. [voyage-3-lite Cost Calculator (Maxim)](https://www.getmaxim.ai/bifrost/llm-cost-calculator/provider/voyage/model/voyage-3-lite)
118. [Best Open-Source Embedding Models 2026 (BentoML)](https://www.bentoml.com/blog/a-guide-to-open-source-embedding-models)
119. [Best Embedding Model for RAG 2026 (Milvus)](https://milvus.io/blog/choose-embedding-model-rag-2026.md)
120. [Hacker News thread #46081800 — Don't use all-MiniLM-L6-v2 for new datasets](https://news.ycombinator.com/item?id=46081800)

### Choco-solver + ILP

121. [Choco-solver homepage](https://choco-solver.org/)
122. [Choco-solver GitHub](https://github.com/chocoteam/choco-solver)
123. [Choco-solver — Launching resolution docs](https://choco-solver.org/docs/solving/solving/)
124. [Frontiers in Nutrition — LP for diet optimization (2018)](https://www.frontiersin.org/journals/nutrition/articles/10.3389/fnut.2018.00048/full)
125. [Configure + troubleshoot R8 keep rules (Android Devs Blog, 2025-11)](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html)
126. [Gradle — Exclude transitive dependencies docs](https://docs.gradle.org/current/userguide/how_to_exclude_transitive_dependencies.html)

### Postgres + pgvector + Tailscale

127. [pgvector 0.8.2 release notes (postgresql.org)](https://www.postgresql.org/about/news/pgvector-082-released-3245/)
128. [pgvector tuning (ParadeDB)](https://www.paradedb.com/learn/postgresql/tuning-pgvector)
129. [Tom Foster — PostgreSQL Memory Configuration](https://tomfos.tr/postgres/tuning/memory/)
130. [pgvector HNSW Postgres 18 production tuning (Nerd Level Tech)](https://nerdleveltech.com/pgvector-hnsw-postgres-18-production-tuning-tutorial)
131. [Tailscale subnet routers](https://tailscale.com/docs/features/subnet-routers)
132. [Tailscale pgproxy — Protect Postgres](https://tailscale.com/blog/introducing-pgproxy)
133. [Petar Radošević — Connect to PostgreSQL from Tailscale](https://petar.dev/notes/connect-to-postgresql-from-taiscale/)
134. [Ellie's Notes — Postgres HBA with Tailscale](https://ellie.wtf/notes/postgres-hba-tailscale/)
135. [Tailscale ACLs docs](https://tailscale.com/docs/features/access-control/acls)

### OpenRouter, rate limits, two-phase budgets

136. [OpenRouter API Rate Limits docs](https://openrouter.ai/docs/api/reference/limits)
137. [OpenRouter Rate Limits — What You Need (Zendesk)](https://openrouter.zendesk.com/hc/en-us/articles/39501163636379-OpenRouter-Rate-Limits-What-You-Need-to-Know)
138. [OpenRouter Free Tier 2026 — Free Models & Limits (Price Per Token)](https://pricepertoken.com/endpoints/openrouter/free)
139. [Is OpenRouter Free? Free Tier (CostBench)](https://costbench.com/software/llm-api-providers/openrouter/free-plan/)
140. [How to Implement LLM Rate Limiting (OneUptime)](https://oneuptime.com/blog/post/2026-01-30-llm-rate-limiting/view)
141. [Token-Based Rate Limiting AI Agents (Zuplo)](https://zuplo.com/learning-center/token-based-rate-limiting-ai-agents)
142. [Azure APIM llm-token-limit policy reference](https://learn.microsoft.com/en-us/azure/api-management/llm-token-limit-policy)
143. [Rate Limiting AI Agents — 3-Layer Gateway (TrueFoundry)](https://www.truefoundry.com/blog/rate-limiting-ai-agents-preventing-llm-api-exhaustion)
144. [Portkey — Rate limiting for LLM applications](https://portkey.ai/blog/rate-limiting-for-llm-applications/)
145. [LLM Token Optimization 2026 (Redis Blog)](https://redis.io/blog/llm-token-optimization-speed-up-apps/)

### Coroutines + outbox + structured concurrency

146. [Coroutine exception handling — Kotlin docs](https://kotlinlang.org/docs/exception-handling.html)
147. [supervisorScope — kotlinx.coroutines API ref](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/supervisor-scope.html)
148. [Adityamishra — Understanding supervisorScope (Medium)](https://medium.com/@adityamishra2217/understanding-supervisorscope-supervisorjob-coroutinescope-and-job-in-kotlin-a-deep-dive-into-bcd0b80f8c6f)
149. [Things I misunderstood about Kotlin Coroutine Cancellations and Exceptions (mbrizic)](https://mbrizic.com/blog/coroutine-cancellation-exceptions/)
150. [SupervisorJob + CoroutineExceptionHandler (techyourchance)](https://www.techyourchance.com/kotlin-coroutines-supervisorjob/)
151. [Transactional Outbox with Spring + Kotlin (AleksK1NG / DEV.to)](https://dev.to/aleksk1ng/transactional-outbox-pattern-step-by-step-with-spring-and-kotlin-3gkd)
152. [Kotbox — Outbox in Kotlin (GitHub)](https://github.com/nishiol/kotbox)
153. [Maciej Toporowicz — Transactional Outbox monolith](https://toporowicz.it/blog/2020/04/15/transactional-outbox-pattern-in-a-monolith-application.html)

### Logging + observability + tracing

154. [AAkira/Napier (GitHub)](https://github.com/AAkira/Napier)
155. [Ktor Client logging docs](https://ktor.io/docs/client-logging.html)
156. [OpenTelemetry Kotlin SDK announcement (CNCF, 2026-03)](https://www.cncf.io/blog/2026/03/24/announcing-a-kotlin-multiplatform-api-and-sdk-for-opentelemetry/)
157. [OTel KMP — New Kotlin SDK (OpenTelemetry Blog)](https://opentelemetry.io/blog/2026/kotlin-multiplatform-opentelemetry/)
158. [Next-Level Observability with OpenTelemetry (JetBrains Blog, 2026-04)](https://blog.jetbrains.com/kotlin/2026/04/next-level-observability-with-opentelemetry/)
159. [OTel — Kotlin docs](https://opentelemetry.io/docs/languages/kotlin/)
160. [Beszel vs Prometheus + Grafana 2026 (Instapods)](https://instapods.com/apps/beszel/vs/prometheus/)
161. [Grafana + Prometheus on VPS (RDP.sh)](https://rdp.sh/en/blog/grafana-and-prometheus-on-a-vps-for-server-monitoring)
162. [Lightweight Monitor — ARM VPS Prometheus + Grafana (VPS ZEN)](https://www.vpszen.com/arm-vps-monitoring-stack-prometheus-grafana/)
163. [Linux VPS monitoring 2026 (HostMyCode)](https://www.hostmycode.com/blog/linux-vps-monitoring-with-prometheus-and-grafana-2026-practical-low-noise-setup)

### Backup + DR

164. [Rclone B2 docs](https://rclone.org/b2/)
165. [DCHost — Rclone S3/B2 playbook](https://www.dchost.com/blog/en/my-friendly-playbook-for-rclone-to-s3-backblaze-b2-encryption-lifecycle-and-glacier-moves-that-cut-backup-costs/)
166. [Sergey's Blog — How to Backup to B2 with Rclone](https://sergeykibish.com/blog/how-to-backup-to-b2-with-help-of-rclone/)
167. [LowEndBox — Backblaze B2 with Rclone](https://lowendbox.com/blog/backblaze-b2-with-rclone-for-effortless-cheap-cloud-backups/)
168. [Client-side encrypted backups with TrueNAS + Backblaze + rclone (vimoire.com)](https://vimoire.com/blog/2025/client-side_encrypted_cloud_backups_with_truenas_backblaze_and_rclone)
169. [Backblaze first look 2026 (setevoy / Medium)](https://setevoy.medium.com/backblaze-a-first-look-at-b2-cloud-storage-650accfacefc)

### Compose Multiplatform — packaging + memory

170. [Native distributions — KMP docs (jpackage MSI)](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)
171. [CMP-GenerateExecutable MSI guide (GitHub)](https://github.com/maronworks/CMP-GenerateExecutable)
172. [Compose Multiplatform desktop template](https://github.com/JetBrains/compose-multiplatform-desktop-template)
173. [Compose Desktop Memory Usage issue #1632](https://github.com/JetBrains/compose-multiplatform/issues/1632)
174. [Why Kotlin Desktop has so high memory usage? — Compose-JB issue #2436](https://github.com/JetBrains/compose-jb/issues/2436)
175. [JetBrains/skiko (GitHub)](https://github.com/JetBrains/skiko)

### Misc references

176. [Open Food Facts API — Introduction](https://openfoodfacts.github.io/openfoodfacts-server/api/)
177. [Open Food Facts API tutorial](https://openfoodfacts.github.io/openfoodfacts-server/api/tutorial-off-api/)
178. [Event Sourcing with SQLite — Append-only design (SQLiteForum)](https://www.sqliteforum.com/p/event-sourcing-with-sqlite)
179. [CRDT and SQLite — Local-First Value Sync (HN #45527840)](https://news.ycombinator.com/item?id=45527840)
180. [VTEX + Carrefour Integration docs](https://help.vtex.com/tutorial/how-the-carrefour-integration-works--UbtveAQnoQGKOkQIYG0uQ)
181. [Playwright Web Scraping Tutorial 2026 (Oxylabs)](https://oxylabs.io/blog/playwright-web-scraping)

(Source count: 181 individual citations, exceeding the 60-130 target.)

---

End of Round 2 research.

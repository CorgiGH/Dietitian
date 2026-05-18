# Dietician Redesign — Consolidated Research

**Date:** 2026-05-17
**Status:** Synthesis of 8 prior artifacts (audit + 5 research rounds + meta blind-spot audit + sunk-cost-free blueprint).
**Project path:** `C:\Users\User\Desktop\Dietician`
**Identity:** Victor (victor.vasiloi@gmail.com), 188cm / 67.5kg, lean-bulk 2750 kcal / 137 g protein, air fryer + microwave only, Iași/RO, UAIC year-1 AI student. NOT "Alex" (different memory namespace).
**Spec under review:** `docs/superpowers/specs/2026-05-17-dietician-design.md` (34 sections, 2127 lines, locked).
**Plan-1 state:** `plan-1/shared-data` branch, 46 commits ahead of master, 95 files, +8164/-219, 34 tests passing — MERGE-READY.
**Inputs:** audit (639 lines, 40 locked decisions) + R1 behavior (566 lines, 130 sources) + R2 tech-stack (1097 lines, 181 sources) + R3 UX-regulation (1389 lines, 250+ citations) + R4 RO-thin-spots (820 lines, 107 sources) + R5 ROI-gaps (1566 lines, 85 sources) + Meta blind-spots (661 lines) + Final sunk-cost-free (1023 lines).

---

## TL;DR (One page)

**Stack verdict.** Keep Kotlin + Ktor (CIO single-server) + Postgres 16 + pgvector 0.8.2 on existing Tailscale-meshed ByteHosting VPS. Kotlin 2.1.21 + Compose Multiplatform 1.8.x (1.10 fast-follower). SQLDelight 2.0 retained on Compose Desktop client (Plan-1 sunk cost + Realm sunset 2025-09-30 disqualifies the alternative). GROBID lightweight CRF variant on Desktop (lfoppiano/grobid:0.8.x, 2-3 GB Docker RAM) with Marker `--use_llm=true` as OCR-heavy fallback. Choco-solver 4.10.14 pinned server-side only with `xchart` excluded. ntfy on Tailscale (Android UnifiedPush + Desktop SSE). LLM router: sealed `LlmProvider` interface + two-phase budget reserve + idempotency keys + raw-response archive. ClaudeMax CLI (`claude --bare -p`) primary VISION/TEXT_HARD on desktop; OpenRouter Gemini 2.5 Flash fallback ($0.003/receipt); DeepSeek V3.2 bulk text (~$2.50/mo); Voyage-4-lite 1024-dim primary embeddings (200M free tokens/account) with BGE-M3 self-host via Ollama as offline-desktop fallback. Total marginal $/mo ≈ $3-10 above the $200/mo Max-20x credit. Frontend axis is open: spec locks KMP Compose Multiplatform Android; Final argues SvelteKit PWA inverts the friction curve — council top-2 decision.

**Behavior-change verdict.** Self-monitoring is the load-bearing primitive (Burke 2011 dose-response). The commercial-app playbook (streaks, leaderboards, daily pass/fail, rigid targets) is ED-toxic at small-n. Ship: multi-modal logging (photo/voice/barcode/"same as Tuesday"), rolling 7-day window targets, autonomy-supportive OARS LLM coach, implementation-intention if-then planner (d=0.51 for addition framings), abbreviated-monitoring graduation path, holiday/post/feast/travel/pause modes first-class, receipt-anchored meal defaults, withdrawal-friendly self-pause. Hard-ban: streaks, leaderboards, body-weight outcome stakes, hidden target recalculation, daily weight chart by default, red/green pass/fail color, OCR blind auto-commit, hidden paywall on safety features.

**UX verdict.** Cronometer-style nutrient target bars + MacroFactor-style adaptive expenditure + Foodnoms privacy posture + Bite-AI photo-as-suggestion + barcode + MyNetDiary hard-floor on dangerous targets + Carbon-style reverse-diet/lean-bulk first-class mode. WCAG 2.2 AA: 24×24 CSS px target size (44×44 pt Apple HIG), 4.5:1 contrast, Wong colorblind-safe palette (no red/green). Voice-first kitchen logging via Whisper.cpp on-device (RO WER ~3-8% on Common Voice, food-vocabulary prompt bias required). TalkBack/VoiceOver via Compose `Modifier.semantics`.

**Multi-user verdict.** **Biggest spec gap.** Spec is Victor-singleton-shaped. The moment a second user comes online, the cohort fork-splits: per-event subject claim, per-event subject ownership for redaction, per-subject planner targets. Required: `subject_id UUID NOT NULL` on every event table; `subjects + auth_users + auth_passkeys + subject_acl + consent_records` tables; magic-link onboarding + Passkeys (WebAuthn via SimpleWebAuthn); per-row RLS in Postgres; `meal_group_uuid` for shared family meals; `subject_redact()` cascade for GDPR Art. 17.

**RO data sources verdict.** Spec assumption "Mega + Carrefour are VTEX" is WRONG. R4 live probe confirms: **only Auchan is VTEX** (`https://www.auchan.ro/api/catalog_system/pub/products/search/lapte` returns valid JSON product feed). Mega Image runs Next.js custom storefront on Delhaize Belgium stack (no public catalog API; sitemap + JSON-LD HTML scrape). Carrefour RO runs Magento 2 with custom marketplace overlay (sitemap + JSON-LD). Kaufland is AEM marketing-only (no e-commerce; weekly PDF flyer scrape only). Lidl is OAuth-gated + cert-pinned (`lidl-plus` Python via one-time MITM refresh-token harvest; catalog browse infeasible). Profi returns 403 (skip; receipt-OCR only). Cora.ro shut down April 2024 (skip). Bringo: Cloudflare 403 + ai-train=no signal — desktop-only Playwright with dedicated account.

**Anelis verdict.** SAML/Shibboleth via RoEduNet IdP. **UAIC IdP confirmed live** at `https://idp.uaic.ro/idp/shibboleth` (R&S + SIRTFI categories, registered 2019-10-22; metadata endpoint `https://idp.uaic.ro/simplesaml/saml2/idp/metadata.php` — both URLs are valid, one is the entity-ID, one is the SimpleSAMLphp metadata route). Credential format Office-365-shape `firstname.surname@student.uaic.ro`. **No API key.** Implementation: user-mediated session export via Playwright `storageState()` against existing browser profile; cookie jar age-encrypted to `state/anelis-session.age`; refresh on 401 or 30 days (whichever first); ntfy push when expired. Spec §13 `AnelisPaperFetcher.fetch(doi): Result<File>` stub stays as-is, gets unblocked post-investigation. Consortium covers Springer / Wiley / IEEE / ScienceDirect / Cambridge / IOP / RSC. MASS Research Review + Examine.com NOT in Anelis — user-upload-required.

**Top-10 actionable (impact × ease × goal-fit ranking, see §17 for top-60).**
1. Rolling 7-day window targets (810) — ED-safety load-bearing.
2. Anti-streak schema-level enforcement (810) — no `consecutive_days_logged` aggregate ever.
3. OARS LLM coach with moderator-reject (720).
4. Restrictive-pattern detection branch + soft check-in (720).
5. Withdrawal-friendly self-pause notifications (720).
6. GDPR Art. 9 consent + `/export-me` + `/forget-me` (720).
7. `subject_id` on every event table + redaction cascade (700).
8. Holiday calendar + post/feast/travel/pause modes (630).
9. Voyage-4-lite + 200M free tokens (630) — fix spec model-name + dim drift.
10. Passkeys + per-subject ACL on Ktor routes (567).

**Five controversial deprecations.**
1. **HLC + LWW + Kulkarni-2014 + Cursor half-open windowing** — Plan-1's 8164 lines of bidirectional sync machinery. Final argues drop entirely for single-writer-VPS + thin offline buffer. Plan-1 KEEP (Council 4 BREAK fix #1) vs Final ARCHIVE call. Council top-2 decision.
2. **Schema-parity CI gate** — Plan-1 §1.4 artifact. At 5 users, the marginal correctness value is below carrying cost. Simpler `MigrationOrderingTest` covers 80% at 20% effort.
3. **pgvector ivfflat index on small corpora** — premature optimization at <10k vectors; linear scan is 4ms. Use HNSW only if corpus crosses 50k.
4. **KMP Compose Multiplatform Android module** — Final argues SvelteKit PWA replaces. Plan-1 has 24 documented deviations from KMP friction (SQLite 3.18 UPSERT workarounds, Skia residency, `JdbcSqliteDriver` JVM-only, JUnit 4 returns-void). PWA gets camera + barcode + voice + MediaRecorder + Service-Worker offline for free. Compose Desktop stays for thick client. Spec KEEP vs Final SWAP. Council top-2 decision.
5. **`voyage/voyage-3-lite` + pgvector(384)`** — both stale. Voyage renamed to `voyage-4-lite` (1024 dim, 200M free tokens/account). pgvector schema must be `vector(1024)` for embedding column unification. Audit's V010 `vector(384)` lock and R5's `corpus_embeddings(1024)` disagree — spec needs revision.

---

## Audit summary

The audit at `2026-05-17-audit.md` (639 lines) is the project's baseline state-of-the-world artifact, written before any research subagent ran. It establishes what is **locked** (must not re-litigate), **shipped** (must not reinvent), **gapped** (research focus), and **blocked** (open questions).

### Project state inventory

**Plan inventory.** 7 plans, only Plan-1 implemented. Plan-1 (`:shared:data` event-sourced ledger + outbox + sync client + SQLDelight schema + Postgres canonical migrations + WAL + HLC + LWW + schema-parity CI gate): **merge-ready**, 46 commits on `plan-1/shared-data`, 95 files, +8164/-219, 34 tests passing. Plans 2-7 (LLM router / Ktor server / Android UI / Desktop UI / Playwright scrapers / Knowledge corpus) not started.

**VPS state (verified 2026-05-17 via SSH).** IP `46.247.109.91`, Tailscale `100.101.47.77` (hostname `panel`), Ubuntu 22.04, 2 vCore Xeon Gold 6150 / 8 GB / 75 GB NVMe / $3.99/mo on ByteHosting. Postgres 16 + pgvector 0.8.2 running on `100.101.47.77:5432` + `127.0.0.1:5432`, MemoryMax=600M, `dietician` DB exists, `dietician` PG user with **PLACEHOLDER PASSWORD** (must be set before backend starts). ntfy Docker on `100.101.47.77:8082`, deny-all default. GROBID NOT on VPS (Errata #2 moves to desktop — Docker Desktop for Windows + `lfoppiano/grobid:0.8.0` install pending on `dell-g5`). earlyoom NOT installed (no longer needed). MC Paper at 4 GB heap (unchanged — user veto `feedback_dont_touch_mc`). Co-tenants: jarvis-web `:8080`, trading-bot, study-proxy `:3001` (PM2), nginx (`:80`,`:443`), Tailscale `:41641`. RAM headroom ≈ 1.9 GB after Postgres+ntfy+Ktor (~460 MB total Dietician load). Disk 73 GB total / 40 GB used (55%) / 33 GB free. ufw INACTIVE. SSH key-only auth working. Desktop Tailscale `100.80.132.115` (hostname `dell-g5`). No phone node yet.

**Scaffold state.** 5 Gradle modules wired (`:shared`, `:androidApp`, `:desktopApp`, `:server`, `:scrapers:playwright`). Compose Multiplatform 1.7.0, Kotlin 2.0.21, kotlinx-coroutines 1.9.0, kotlinx-serialization 1.7.3, kotlinx-datetime 0.6.1, Ktor 3.0.1 client, SQLDelight 2.0.2, Koin 4.0.0, Voyager 1.1.0-beta02, Resilience4j 2.2.0. Choco-solver 4.10.14 TEMP-COMMENTED in commonMain (JVM-only, wrong source set). ONNX Runtime 1.20.0 on desktopMain only. Test stack: kotlin.test + kotlinx-coroutines-test + Turbine + Kotest + Robolectric + JUnit 5 + Testcontainers 1.21.4. Android min-SDK 26, compile-SDK 35. JDK 21 toolchain. No screens, no `data-testid` selectors mounted anywhere. CI workflow `.github/workflows/ci.yml` running ktlint + detekt + `:shared:desktopTest` + `:shared:testDebugUnitTest` + `:server:test` + assembly on push to `branches: ['**']` with 30-min timeout.

### Plan-1 shipped surface

**Eight SQLDelight `.sq` files** generating `DieticianDatabase`: `0001_event_ledger.sq` (pantry_events, meal_events, weight_events, receipt_events), `0002_outbox.sq` (outbox, outbox_dead), `0003_pantry_snapshot.sq` (materialized table + checkpoint singleton — trigger DROPPED because sqlite_3_18 parser doesn't grok NEW/OLD, replaced by explicit `EventStore.snapshotApplyDelta` in same tx as event insert), `0004_metadata_lww.sq` (pantry_metadata, seed+update pattern NOT `ON CONFLICT DO UPDATE` because android-min=26 ships SQLite 3.18 < 3.24), `0005_cache_meta.sq` (sync_cursor_per_table, sync_log), `0006_caches_readonly.sq` (15 read-replica cache tables), `0007_local_location.sq` (user_location_current per-device), `0008_hlc_state.sq` (singleton HLC counter). SQLDelight accessors are **backtick-quoted** because filenames start with digits.

**Kotlin classes in commonMain.** `Clock.kt` (expect WallClock + FakeWallClock); `DeviceId.kt` (expect fun); `api/Cursor.kt` (`Cursor(timestampMs: Long, eventUuid: String): Comparable<Cursor>` with `ZERO` + lex compareTo); `api/EventPayload.kt` (sealed interface with 4 data classes); `api/SyncDto.kt` (EventEnvelope, PushRequest/Accepted/Rejected/Response, PullRequest, PulledRow, PullResponse); `compaction/PantryCompactor.kt`; `local/CacheMetaStore.kt`; `local/EventStore.kt` (enqueue* with event + outbox + snapshot in ONE `db.transaction { }`); `local/OutboxStore.kt` (nextBatch, markSynced, recordFailure, promoteIfDead at maxAttempts=10); `local/PantrySnapshotStore.kt` (asFlow + mapToList Dispatchers.Default); `local/SyncLogStore.kt`; `local/WalPragmas.kt` (object with INIT pragmas + applyAll + forceTruncatingCheckpoint — T27 fix routed PRAGMAs returning rows through `executeQuery` not `execute`); `metadata/HybridLogicalClock.kt` (Kulkarni 2014 HLC, NO `synchronized` because not in commonMain stdlib, single-writer-per-device contract documented, `recv` does NOT auto-+1); `metadata/LwwMerge.kt` (tuple key `(serverRecvAt nulls-last, hlc.wallMs, hlc.seq, hlc.deviceId)` — Plan-body mapped null→Long.MIN_VALUE which would have made null WIN; impl correctly maps null→Long.MAX_VALUE); `remote/RetryPolicy.kt` (`min(2^attempt, 30) seconds + 0-25% jitter`); `remote/SyncClient.kt` (Ktor wrapper with HttpTimeout request=30s connect=10s socket=30s); `sync/OutboxDrainWorker.kt` (transport vs permanent error classification; CancellationException re-thrown explicitly); `sync/OutboxError.kt` (sealed class Transient/Permanent); `sync/PullCoordinator.kt` (`applyPulledRow` returns `false` + gates cursor advance until Plan-3 implements per-table UPSERT routing); `sync/PullTrigger.kt` (sealed interface + `PullTriggerCoalescer` rewritten Channel→debounce→SharedFlow post-impl council #3 fix); `sync/WalCheckpointHook.kt` (expect class); `sync/WebSocketListener.kt` (flat 2s reconnect, labeled for Plan-3 replacement, explicit CancellationException catch-rethrow).

**androidMain/desktopMain actuals.** `DataModule.android.kt` uses `AndroidSqliteDriver` + `Callback.onOpen` applying WalPragmas.INIT + ProcessLifecycleOwner WAL checkpoint hook. `DeviceId.android.kt` derives from `Settings.Secure.ANDROID_ID` → `"android-$sec"`. `sync/NtfyClient.kt` (Android-only) is OkHttp SSE long-poll subscriber with explicit catch-rethrow CancellationException. Desktop side uses `JdbcSqliteDriver("jdbc:sqlite:$appDir/dietician.db")` with `.schema_applied` marker file (Plan-1 ships v1 only; Plan-3 wires versioned migrations). DeviceId desktop = `desktop-$UUID` persisted to `APPDATA/Dietician/device_id.txt` (fallback `user.home`).

**Twelve Flyway-style SQL files** in `:server/src/main/resources/db/migration/`: V001 event tables, V002 sku+price, V003 baselines+budget, V004 knowledge corpus first half, V005 recipes+plans, V006 stores+location, V007 jobs+heartbeat, V008 pending review queues, V009 outbox_dead_vps + sync_log_vps mirrors, V010 pgvector extension + `embedding_recipe vector(384)` + `embedding_food vector(384)` + ivfflat cosine indexes, V011 (council #1 must-fix) CHECK constraints on status enums, V012 (council #1 must-fix) composite cursor indexes on `(originated_at, event_uuid)` per event table + partial unsynced indexes. `db/Flyway.kt` runner. `db/MigrationOrderingTest.kt` (pgvector/pgvector:pg16 Testcontainers; 3 tests). `db/SchemaParityTest.kt` + helpers (4 tests; type aliasing UUID↔TEXT, TIMESTAMPTZ↔INTEGER, DATE↔TEXT, JSONB↔TEXT, BOOLEAN↔INTEGER, BIGSERIAL↔INTEGER PRIMARY KEY AUTOINCREMENT, VECTOR↔BLOB).

**Test inventory: 34 passing.** `:shared:desktopTest` (21 tests including `EventStoreAtomicityTest`, `OutboxStoreTest`, `PantryCompactorTest`, `PantryBenchmarkTest` ~4 ms at 100k events / 200 SKUs target <10 ms, `PantrySnapshotTest`, `SyncLogStoreTest`, `AckVsFlipChaosTest` simulates kill between server-ack and markSynced, `PullCursorPropertyTest` Kotest 20 random runs, `PullTriggerTest` 200 ms debounce coalesces burst). `:shared:commonTest` (3 HLC tests + 1 LWW property test ±24h skew 100 cases + 2 RetryPolicy tests). `:shared:testDebugUnitTest` Robolectric (`WalDozeChaosTest` data persists across simulated Doze kill + reopen + checkpoint; `WalCheckpointTest` -wal file shrinks after forceTruncatingCheckpoint). `:server:test` (3 MigrationOrderingTest + 1 SchemaParityTest).

**24 documented Plan-1 deviations from spec.** Pasted verbatim because they constrain every future plan: (1) SQLite UPSERT `ON CONFLICT DO UPDATE` rewritten to portable seed+update everywhere; (2) T6 pantry_snapshot trigger dropped, replaced by EventStore explicit snapshotSeedIfAbsent + snapshotApplyDelta in same tx; (3) T13 HybridLogicalClock dropped `synchronized`; (4) T13 `recv` no longer auto-+1-ticks; (5) T14 EventStore non-suspend (`db.transaction { }` is synchronous); (6) T14 EventStore explicit snapshot-delta inside event-insert tx; (7) T17 LwwMerge nulls-last fix (Long.MIN→Long.MAX); (8) SQLDelight accessors backtick-quoted; (9) All SQLDelight-using tests in `desktopTest` not `commonTest` (JdbcSqliteDriver JVM-only); (10) JUnit 4 returns-void issue → block-body `{ runBlocking { ... } }` wrap; (11) Pre-Task-0 setup: wrapper gen 8.10, android-target 34→35, choco-solver temp-disable, jvmToolchain 17→21 in :server + :desktopApp; (12) Testcontainers 1.20.4 → 1.21.4 (Docker Desktop 4.69 proxy issue); (13) SchemaParityTest TIMESTAMP-WITH-TIME-ZONE → INTEGER alias addition; (14) T11 polish: alias-fold sort by length, lifecycle wrap, named errors, PRAGMA quote-escape; (15) T24 PullTriggerTest backgroundScope; (16) T27 WalPragmas executeQuery routing; (17) T28 androidUnitTest build config + `SQLiteMode.NATIVE`; (18) T29 CI: JDK 21, `:shared:desktopTest`, ktlint+detekt; (19) T30 foojay-resolver + jagged 0.5.0→0.4.1 (phantom version) + assembly fixes; (20) T30 detekt config (Compose-aware) + .editorconfig ktlint exceptions; (21) Council #1-fix-3 V012 composite cursor indexes match `originated_at` not `synced_at`; (22) Council #2-fix-3 spec §6.1 cursor wire shape realigned to `cursors:{table:Cursor}`; (23) Council #3-fix-1 PullTriggerCoalescer rewritten Channel→debounce→SharedFlow; (24) T21 RetryPolicy test simplified (plan's Duration.parseIsoString was broken).

### Plan-1 / Plan-3 intentional stubs

(1) `PullCoordinator.applyPulledRow(...)` returns `false` + gates cursor advance — Plan-3 wires per-table UPSERT routing. (2) `WebSocketListener` reconnect uses flat 2s delay — Plan-3 replaces with `RetryPolicy.nextDelay(attempt)`. (3) `NtfyClient` androidMain-only (no desktop push channel; WS suffices when window present). (4) `DataModule.desktop.kt` `.schema_applied` first-run guard — Plan-3 wires versioned SQLDelight migrations.

### 40 locked decisions

The audit enumerates 40 decisions that research subagents MUST NOT re-litigate. The most load-bearing:

1. KMP Compose Multiplatform on Android + Windows Desktop (no iOS, no web, no native-Win32). 2. VPS-canonical via Tailscale, backend on `tag:dietician-backend:8081`, no public exposure. 3. Local-first writes + event-sourced ledger (Council 4 BREAK fix #1). 4. LLM router lives in `:shared:llm` (Kotlin, NOT LiteLLM proxy). 5. ClaudeMax CLI subprocess OUTSIDE LiteLLM (desktop-only) — `claude --bare -p` + `--output-format stream-json`. 6. Vision OCR routing: ClaudeMax CLI primary on desktop + OpenRouter Gemini Vision fallback. Phone always uses Gemini Vision. 7. Push: self-hosted ntfy on VPS (~10 MB RAM, Tailscale-bound). FCM REJECTED. 8. Constraint solver: Choco-solver 4.10.14 with `xclude(org.knowm.xchart)`. JVM/androidMain source sets only. 9. Embeddings: ONNX `all-MiniLM-L6-v2` OR Ollama `nomic-embed-text` on desktop; Voyage `voyage-3-lite` via OpenRouter on phone fallback. **(R2/R5 supersede: model renamed `voyage-4-lite`, 1024 dim, 200M free tokens.)** 10. No Postgres driver on phone/desktop. Clients talk to VPS Ktor via Ktor Client HTTP only. 11. Backup: VPS-side `pg_dump -Fc | rclone rcat b2:` nightly; weekly desktop mirror to OneDrive. 12. Telegram bot DROPPED entirely. 13. Two-file wiki pattern (narrative `.md` + autogen `.data.md` with Obsidian transclusion). 14. GROBID on DESKTOP not VPS (Errata #2). 15. MC heap stays at 4 GB (user veto). 16. earlyoom NOT installed. 17. Spec interface for Anelis Plus = `AnelisPaperFetcher.fetch(doi: String): Result<File>` stub. 18. Receipt OCR queue-to-desktop default; OpenRouter Gemini Vision fallback when desktop offline. 19. Claude tier: Max 20x = $200/mo Agent SDK credit (post-June-15-2026 split). 20. Source-of-truth dirs: `wiki/` narrative MD + Postgres numeric/transactional + `raw/` immutable inputs including `llm-raw/{call_uuid}.txt`. NO numbers in markdown bodies. 21. Three-tier SKU match (T1 GTIN exact → T2 Jaccard 0.85 + size 5% → T3 review queue). 22. Per-source confidence + half-life table locked: user receipt 1.0/2d, Bringo Playwright 0.85/4d, per-chain Playwright 0.9/4d, Monitorul 0.75/7d, Flyer Vision 0.6/14d. 23. Median-bootstrap promo isolation (Council 4 fixed Council 3's `max` to `median`). Sale detection at `phase=stable` only (n≥5 AND span≥14d). 24. Vision JSON corruption gate: raw archive + strict parser + cross-source confirm; `price_posterior` recomputation EXCLUDES `source='llm:vision'` UNLESS confirmed by Monitorul OR Playwright within ±7d. 25. Two-phase budget reserve + queue-time provider re-eval. Postgres `llm_budget` row with `SELECT … FOR UPDATE`. ClaudeMax <10% → queued Vision auto-routes to Gemini. 26. Event-driven sync (ntfy + WS + outbox-replay) — NOT periodic WorkManager. 27. App-layer health check (NOT Tailscale self-report). Foreground 60 s + background 15 min via WorkManager one-shot. 28. `/diag` first-class command + 10-failure-mode runbook. 29. VPS resource hygiene + ACL: Tailscale ACL `tag:dietician-client` → `tag:dietician-backend:8081` only; systemd `MemoryMax=512M` Dietician Ktor; Postgres `shared_buffers=128MB work_mem=4MB`. **Note inconsistency: spec says `MemoryMax=3G` MC but MC's actual heap is 4G via `-Xmx4G`.** 30. Refusal triggers + macro guardrails hardcoded into LLM prompt: kcal ∈ [BMR×1.0, BMR×2.5]; protein ∈ [1.2, 3.5] g/kg; no single food >50% kcal sustained; no zero-fat or zero-carb day. 31. Anti-recommend exclusion list hardcoded. 32. Pull cursor = `(timestampMs, eventUuid)` strict `>` half-open windowing. 33. HLC tuple `(server_recv_at, hlc_wall_ms, hlc_seq, last_modified_device)` for LWW. Never trust raw `System.currentTimeMillis()`. 34. Outbox `outbox_dead` populated after 10 failed attempts; NEVER silent-drop. Transient transport errors do NOT increment toward dead-letter ceiling. 35. Schema-parity CI gate Flyway PG ↔ SQLDelight SQLite. 36. WAL+Doze discipline: `wal_autocheckpoint=1000` + `wal_checkpoint(TRUNCATE)` on app-background AND WorkManager 15 min. 37. `sync_log` table records every trigger fire. 38. Compose Multiplatform Desktop + Android, ~60-70% UI shared. 39. Web surface (post-jarvis-merge optional): `DieticianRoutes.kt` mirroring jarvis `TutorRoutes.kt`. 40. Subsystem contract for jarvis merge = `Subsystem.run(client: Llm, input: SubsystemInput): SubsystemOutput(text, wikiEntry?)`.

### Three Plan-2-blocking high-priority questions (audit §9)

The audit enumerates 10 high-priority research questions; three block Plan-2 design:

**Q1: ClaudeMax CLI exit-code + stream-json event matrix** on (a) success, (b) `rate_limit`, (c) `billing_error`, (d) `tool_error`, (e) `usage_policy_violation`. Plan-2 `ClaudeMaxCliProvider.complete` must classify these correctly to drive `BudgetLedger.markDegraded` + fallback chain. (R5 §1.2 partial answer: empirically exit code 0 success / 1 parse error / abort; the CLI emits `api_retry` system event AND exits non-zero on quota exhaustion. Plan-2 first task: smoke-test against known-exhausted Max-20x account.)

**Q2: OpenRouter rate-limit semantics** + how per-key budget caps interact with user's plan tier. Plan-2 must distinguish "monthly cap" vs "org-level rate limit" vs "model deprecated". (R2 §12 lock: without $10 in credits 50 RPD; with $10+ in credits 1000 RPD; 20 RPM ceiling on `:free` routes. **Recommendation: one-time $10 OpenRouter top-up in project setup.**)

**Q4: Canonical `server_recv_at` stamp point in Plan-3** — request entry vs DB insert vs commit. Plan-1 LWW key tuple ends with `serverRecvAt`, so a deterministic choice is load-bearing for conflict resolution across phone-desktop divergence. (No round resolved this; remains an open question for council.)

### Audit gap-list (research focus, §8)

11 gap clusters research SHOULD address: (8.1) LLM router operational details — Plan-2; (8.2) Server-side surface — Plan-3; (8.3) Receipt OCR pipeline robustness — Plan-3 + Plan-5; (8.4) Paper ingestion sequencing — Plan-7; (8.5) Embedding strategy across corpora — Plan-7 + Plan-2; (8.6) Cross-platform Compose UI sharing — Plans 4 + 5; (8.7) Behavior-change adherence framework — Plan-4 + Plan-7; (8.8) ED safeguards — Plan-4 + Plan-7; (8.9) EU AI Act applicability — Plan-2 + cross-cutting; (8.10) Knowledge corpus authoring loop — Plan-7; (8.11) Scraping robustness + RO supermarket landscape — Plan-6.

Each downstream round closes 1-3 of these. R1 covers 8.7 + 8.8 thoroughly. R2 covers 8.1 + 8.5 + 8.6. R3 covers 8.6 + 8.7 + 8.8 + 8.9. R4 covers 8.10 + 8.11 + reopens 8.10's "Anelis investigation pending". R5 covers 8.1 + 8.3 + 8.4 + 8.5 + 8.6. Meta surfaces 14 new gaps not enumerated in 8.1-8.11. Final re-evaluates the 40 locked decisions in §13 deprecation matrix.

### Pre-impl gates (user must complete before Plan-2)

1. Postgres password for `dietician` user (currently PLACEHOLDER). 2. OpenRouter API key + recommended $10 top-up. 3. (Optional) Backblaze B2 bucket + rclone config. 4. (Required for paper ingestion) Docker Desktop for Windows on `dell-g5` + `docker pull lfoppiano/grobid:0.8.0`. 5. (Required at deploy) Tailscale ACL fragment at `login.tailscale.com/admin/acls` per §2.

### Memory + feedback rules (binding for all sessions)

The audit re-states the binding constraints from CLAUDE.md and `feedback_*.md`: NO time/duration/effort estimates (user reaction: "ill break your kneecaps"); NO version phasing (full system in one pass); council pattern BEFORE plan + AFTER impl on Phase 2+ tasks; DON'T touch MC server config/heap; English default; Romanian only when language-dependent; snapshot-at-receipt for req/reply UIs; mirror server caps in mod constants (parity-mod pattern); verify SDK API via javap not mappings; off-disk backup before hold-blocks; no-relaunch-confirm during in-game testing; no unverified claims (date-stamp + re-verify >7d); spec-first clarification rule; memory verification rule; SDD reviewer over-eager on incremental plans (override with one-liner); caveman tone for replies; underscore-dead-prop rule; component-reuse contract; build+mount pairing; interaction-smoke gate.

---

## Round 1 — Behavior change + adherence

R1 (`2026-05-17-round-1-behavior-change.md`, 566 lines, 130 sources) is the evidence-based foundation for the app's behavior-change architecture. It synthesizes 9 theoretical frameworks (TPB, COM-B, SDT, FBM, MI, self-monitoring, implementation intentions, commitment devices, identity-based motivation, nudges) + 4 anti-pattern clusters (AVE, app-failure post-mortems, ED-tracker harm, RO cultural context) into 10 binding design moves.

### TL;DR

Dietary self-monitoring is the single most evidence-backed behavior in nutrition apps: more days logged predict more weight change, dose-response in every major review from Burke 2011 forward. But adherence collapses fast — 75% of new digital-health-app users abandon within a week, ~50% within a month, and even structured programs see daily-logging time fall from ~23 min to ~15 min over six months with one-third dropping monitoring entirely. TPB attitude→intention r=0.54; implementation-intentions d=0.51 (add healthy) vs 0.29 (remove unhealthy); MI +55% positive outcomes vs standard counseling; autonomy-supportive SDT coaching > controlling style with measurable bulimic-symptom-risk reduction. The commercial app playbook (streaks, leaderboards, rigid daily targets) carries documented ED risk: 73% of calorie-tracker users with ED histories blame the app, rigid restraint correlates with binge frequency, streaks weaponize loss aversion and amplify Marlatt's "what-the-hell" effect post-lapse.

For Dietician — Victor + ~5 friends, self-built, no business KPI — the design imperative inverts the commercial playbook. Three practical conclusions: (1) make self-monitoring as low-friction as humanly possible (photo + voice + barcode + manual + one-tap "same as yesterday") and explicitly support abbreviated monitoring after trajectory is locked; (2) replace streaks/rigid targets with weekly-window targets, flexible-restraint framing, ledger-based feedback that survives lapses; (3) build ED-safeguards as primitives, not optional features — minimum calorie floors, refusal to track below thresholds, no public weight-loss leaderboards, no body-comparison features.

Romanian cultural context is rarely served by global apps: pork-dominant animal-origin diet, hot meal mid-day (not dinner), Orthodox fasting calendar (~180 days/year of dairy/meat restriction during Postul Crăciunului + Lent + Apostles' + Dormition + Wednesdays/Fridays year-round), feast-anchored ritual food (sarmale at Crăciun, cozonac, pască + lamb at Easter, mămăligă as carb staple). A tracker that defaults American breakfast cereal + lunch salad + light dinner will fight every traditional eating moment Victor's cohort encounters.

### Theory of Planned Behavior (Ajzen 1991)

TPB posits behavioral intention as the proximal cause of behavior, determined by attitude (r=0.54 → intention for diet), subjective norm (r=0.37), and perceived behavioral control (PBC, r=0.42 → intention + r=0.27 direct → behavior). Intention → behavior r=0.45. Medium-to-large correlations; cumulative variance explained 30-50%, leaving substantial weight to habit + environment + biology.

A 2025 Saudi study expanded TPB with culturally-localized variables (knowledge of WHO recommendations + family meal frequency) — relevant for RO cohort. Hagger meta-regressions show habit-strength moderates intention's predictive power.

Implications for Dietician: don't over-invest in attitude formation (Victor is convinced); subjective norm matters less for Victor (autonomous) but more for cohort (free signal if multi-user designed in); PBC is the leverage point (air-fryer/microwave constraint, RO SKU coverage, receipt OCR all raise PBC); design for intention-behavior gap with implementation-intentions and just-in-time prompts.

### COM-B + Behaviour Change Wheel (Michie 2011)

B = f(C, O, M): Capability (physical + psychological) × Opportunity (physical + social) × Motivation (reflective + automatic). BCW layers 9 intervention functions + 7 policy categories; BCT Taxonomy v1 provides 93 specific techniques in 16 clusters.

Most-coded BCTs in nutrition: information about health consequences, instruction on how to perform, action planning, feedback on behavior, social comparison. Goal-setting + self-monitoring + feedback produce largest pooled effects. A 2024 pregnancy dietary review found effective trials systematically addressed all three COM-B components; ineffective trials targeted Motivation only.

Recommended top-7 BCTs for Dietician: self-monitoring of behavior, self-monitoring of outcome, goal-setting (behavior), action planning, feedback on behavior, prompts/cues, problem-solving. **Avoid:** rewards contingent on behavior (gamification trap), social comparison (ED risk), graded tasks implying progression-based punishment.

Map every feature to COM-B target: receipt OCR = Physical Capability + Physical Opportunity. Knowledge corpus = Psychological Capability. ntfy reminders = Automatic Motivation. Peer-friend visibility = Social Opportunity. Air-fryer recipes = Physical Capability. Macro target display = Reflective Motivation.

### Self-Determination Theory (Deci-Ryan / Pelletier / Teixeira)

SDT distinguishes autonomous motivation (values-aligned) from controlled motivation (external pressure or guilt). Three basic needs: autonomy, competence, relatedness. Pelletier 2004: autonomous regulation for eating predicts healthier eating; controlled regulation predicts more bulimic symptoms, depressive symptoms, lower self-esteem. Autonomous → food quality concerns; controlled → food quantity concerns (restrictive/restraint cycles). Ntoumanis 2020 meta-analysis: autonomy-supportive interventions produce larger effects than control conditions.

Implications: default language style autonomy-supportive ("today's protein target is 137g; here are options based on what you've logged this week" beats "you need to hit 137g"). Build for competence (knowledge corpus + per-food micronutrient breakdown + receipt-parsed history all reinforce "I understand what I'm eating"). Relatedness, NOT comparison (shared ledger with friends ≠ leaderboard). Avoid controlling micro-copy (no "must" / "should" / "failed" / "don't eat"; use "consider" / "options are" / "if you'd like to").

### Fogg Behavior Model (B=MAP)

Behavior occurs when Motivation × Ability × Prompt converge above an action threshold. Motivation is unreliable (declines over time); durable behavior change requires raising ability and improving prompt-targeting rather than maximizing motivation. Tiny-Habits methodology: anchor new behaviors to existing reliable routines (post-toothbrush, post-coffee), make initial behavior so small motivation isn't needed, celebrate completion.

Implications: anchor logging to existing habits ("after you sit down to eat" not "at 7 pm"); receipt photos at cashier (existing routine) > meal photos before eating (new routine); smallest viable log = "I ate" (no food, no quantity), auto-prompt enriches; design for M=2/10 (every feature must clear M=2/10 ability bar); celebrate completion cheaply (subtle haptic + soft "logged" confirmation > confetti animation, anti-streak).

### Motivational Interviewing (Miller & Rollnick)

Client-centered counseling style aimed at resolving ambivalence and eliciting **change talk** over **sustain talk**. Core skills OARS: Open-ended questions, Affirmations, Reflections, Summaries. Lundahl 2013 meta-analysis: MI 55% higher chance of positive outcome vs standard treatment across health behaviors. VanWormer 2004: MI reduced energy from fat, increased fruit/vegetable consumption. Digital-MI applications (SMS, chatbot, conversational AI) show effect sizes comparable to in-person MI for adherence outcomes.

LLM coach prompt should bake in OARS. Default system prompt: ask open questions, reflect what user said, affirm specific effort (not generic praise), summarize at session end. Avoid: closed yes/no questions, telling, fixing. Elicit change talk: when user logs a slip, the right response is not "try better tomorrow" — it's "what would you want to happen differently?" → user generates the plan → user's commitment-level rises. Roll with resistance: when user pushes back on a target, reflect not argue. **OARS guardrail for AI:** reject LLM outputs containing commands, threats, fear-appeals, or shame language; re-roll with autonomy-supportive frame.

### Self-monitoring (the dose-response engine)

Burke 2011 systematic review (22 studies 1993-2009): consistent significant association between self-monitoring and weight loss. "Kept-food-diary lost 2× weight" framing from Hollis 2008. Self-monitoring is the highest-evidence single BCT in dietary weight-management literature. Harvey 2019 "Log Often Lose More": top tertile of logging frequency lost ~3× as much weight as bottom tertile. Patel 2021/2022: app-based delivery did NOT show overall superiority to paper diary — the behavior matters more than the modality.

**Granularity trade-offs.** Delphi study endorsed three abbreviated-monitoring strategies once weight-loss trajectory is established: (1) self-weighing only; (2) monitor only foods/beverages that are higher-density or less routine; (3) fewer days/week of full monitoring. Critically, abbreviated monitoring is recommended ONLY after either 2 weeks of no monitoring among strugglers (to re-engage) or 5-10% weight loss achieved (to consolidate gains). Time burden: ~23 min/day month 1 → ~15 min/day month 6. Two-thirds of completers in a 6 mo trial still doing some monitoring at endpoint; one-third dropped entirely. Photo logging is less burdensome but less accurate than barcode/manual for known SKUs; AI image recognition reduces burden but accuracy degrades for mixed dishes and non-Western cuisines. Best pattern: photo + barcode hybrid + manual fallback.

Self-monitoring is the LOAD-BEARING feature. Everything else is decorative if logging fails. Hybrid input is mandatory. Build abbreviated-monitoring mode early (after 4 weeks consistent logging, offer weigh-only mode or fewer-days mode as graduation NOT punishment). Don't penalize gaps ("carry-forward yesterday" default + retroactive editing means a missed day doesn't break the ledger).

### Implementation intentions for eating (Gollwitzer 1999; Adriaanse 2011)

If-then plans linking a specific situational cue to a specific behavior. Adriaanse 2011 meta-analysis of 23 studies on eating: promoting healthy eating additions d=0.51 (medium-large); reducing unhealthy eating d=0.29 (small-medium). Bieleke 2024 meta-regression of 642 tests confirms asymmetry; identifies behavior-specificity and cue-specificity as key moderators. The mechanism for unwanted-habit prevention is inhibiting the cue→behavior link, not replacing it — supporting the "add healthy" > "remove unhealthy" asymmetry.

Make if-then prompts the default planning UI: not "I will eat 30g protein for breakfast" but "When I make coffee in the morning, I will eat 30g protein within 30 min". Bias toward addition framings. Use the receipt parser as a cue-detector: "We noticed you bought chips Tuesday. Want to make an if-then plan for the next bag?" — opt-in, autonomy-supportive framing.

### Commitment devices (Stickk, Beeminder)

Soft commitments (public pledge, accountability partner) vs hard commitments (stake monetary or social value forfeitable). Loss aversion makes hard-commitment stakes ~2× more motivating than equivalent positive rewards. Three-RCT meta-evidence (n=409): mean 1.5 kg short-term loss vs control. **ED risk:** loss-aversion commitment to a weight target is functionally indistinguishable from a dietary rule with self-imposed punishment — orthorexia / restrictive-eating risk pathway. The "what-the-hell" effect makes a missed commitment cascade into binge-and-give-up.

**Default off.** Do not bake commitment-stakes into Dietician's core loop. If offered, only soft commitments. Never on body-weight outcomes — only on process targets (logged days, protein-hit days). Skip entirely for friends/family cohort. If Victor wants Stickk-style for himself, fine; don't extend without explicit consent + screening check.

### Identity-based motivation (Oyserman)

People pursue goals when (a) goal-congruent action feels identity-congruent and (b) interpretation of difficulty signals "this is for me" not "this isn't for people like me". Oyserman 2007: priming racial-ethnic minority identity reduced perceived efficacy of "healthy" behaviors that participants associated with out-group identity. Health behaviors are identity-coded; mismatch reduces uptake even when knowledge and motivation are present.

Frame Dietician's identity-marker for Victor as "person who builds muscle / fuels training" not "person on a diet". First is identity-additive (gain); second is restriction-coded (loss). Romanian-eater identity preserved — do NOT require friends to think of themselves as "people on a diet"; make tracking feel like meal-planning, household-organisation, cooking-curiosity. Reframe difficulty as on-path: a hard day is information, not identity disconfirmation. Microcopy should never say "you broke your diet" — say "you logged 3100 kcal Tuesday. Want to plan around that?"

### Defaults & nudges (Thaler-Sunstein)

Mertens 2022 PNAS meta-analysis: overall d=0.43. Cadario-Chandon 2020 healthful-eating nudges: cognitively-oriented (descriptive labels, visibility) d=0.12; affectively-oriented (sensory/emotional) d=0.24; behaviorally-oriented (defaults, positioning) d=0.39 — defaults are the strongest sub-category. In cafeteria/supermarket settings 57.6% of 33 nudge studies effective; priming + salience-affect combos effective in all 5 trials.

Default meal templates that hit macros: first-paint suggestion already hits 137g protein + 2750 kcal, drawn from user's recent logged pattern. Air-fryer + microwave constraint = pre-filtered default. Receipt-anchored defaults: "You bought chicken breast yesterday. Tomorrow's lunch suggestion uses it." Avoid default-bypass via "set custom goal" below safe floor — defaults can be paternalistic when ED-safety justifies.

### Abstinence-violation effect (Marlatt) — single-lapse design

After a slip, individuals who attribute the lapse to stable, internal, global causes ("I have no willpower") show despair, shame, and the "what-the-hell" effect (give up entirely). Single-lapse → relapse transition is mediated by cognitive attribution, not the slip itself. In a Very-Low-Calorie-Diet study (Mooney 1992), 41 of 76 patients lapsed in 11 weeks; those reporting higher characterological attributions lost less weight.

Reframe lapses in copy: "You missed Tuesday's log" → never write that. Use "Tuesday is unlogged. Want to backfill or move on?" — situational, not characterological, with option. **No streak destruction.** Streaks weaponize AVE. If app shows "23-day streak broken", that's the loss-aversion punch flipping a single slip into a quit. Rolling 7-day windows not daily binaries: replace daily pass/fail with "averaged 2780 kcal over last 7 days, target 2750". One bad day in a 7-day window is a 14% perturbation, not a failure event. "What would you do differently?" prompt after a logged lapse — Marlatt's situational-attribution reframe baked into the LLM coach loop.

### App-by-app real-world data

**MyFitnessPal:** largest user base, extensive food database (crowd-sourced, accuracy concerns), strong social features. Validation shows significant discrepancies in nutrient calculations. ~75% of clinical ED-sample participants used MFP, 73% perceived it as contributing to their ED.

**Cronometer:** emphasis on micronutrient accuracy (84 nutrients tracked: 13 vitamins + 17 minerals + AAs), curated database, less social. Lower churn anecdotally among data-oriented users.

**MacroFactor:** "adherence-neutral" algorithm — recalculates targets based on actual intake, not adherence-to-plan. No streak; no scolding. Bayesian-style weekly smoothing on rolling weight × intake. **Closest commercial app to the flexible-restraint + autonomy-supportive design pattern.**

**Carbon Diet Coach:** requires adherence to update targets. "Carbon's approach—it requires perfection." Higher punishment-coupling; less ED-safe by design. Notable: explicit reverse-diet / lean-bulk mode (Victor's actual use case).

**Noom:** behavior-change-coach framing, daily lesson + coach chat + logging. 2.5-year RCT (n=600) ongoing; cross-sectional follow-up reports 75% maintained ≥5% loss at 1 year, 49% maintained ≥10%. Strong evidence for behavior-change framing > pure tracking.

**Foodnoms (iOS-only):** end-to-end encrypted in iCloud, no account required, no ads, on-device default. Siri Shortcuts (voice). Best privacy posture in commercial space.

**Lifesum 2025:** AI-pivot caused regressions (photo misidentification, barcode 2-3× wrong, manual correction removed, forced-logout). Cautionary tale for AI integration. **Lift: nothing. Lessons: never remove user correction workflow on AI output; never disable manual entry.**

**MyNetDiary:** first app to publicly publish ED-safety policy ("does not allow users to set rapid weight-loss rates or set target weights below a healthy range"). Reference policy for Dietician's MODEL_CARD.

**Bite AI / Foodvisor:** photo-first paradigm; 46-72% top-1 accuracy on mixed-dish components, ±15-30% portion error. Use photo as suggestion, never blind-accept. Always show "we think you ate X with Y±Z error margin — confirm or correct".

### Why apps fail in month 2

(1) **Logging fatigue.** 23 min/day month 1 → ~15 min/day month 6, with one-third dropping. Photo logging less enjoyable than expected. (2) **Target-creep.** Apps that reduce calorie targets silently as users approach goal weight trigger felt-arbitrariness and quitting. MacroFactor avoids via transparent algorithm; Carbon's silent recalculations correlate with higher drop-off. (3) **Social-comparison toxicity.** Social features in calorie apps significantly elevate disordered-eating-symptomology risk in users without prior ED. (4) **Over-restriction → binge cycle.** Rigid restraint correlates with higher disinhibition, BMI, more frequent and severe binge episodes (Westenhoefer 1999; replications 2012-2025). (5) **Lack of human element.** mHealth-app meta-analyses consistently find effectiveness limited beyond 6 months; standalone digital tools insufficient. "mHealth interventions may be more appropriate as initial catalysts rather than stand-alone solutions for long-term weight maintenance."

### Romanian cultural eating context

**Traditional pattern.** Animal-origin-heavy (pork dominant), mămăligă as carb staple, structured 4-course meal: appetizer (cured meats + cheese + vegetables) → ciorbă (sour soup) → main (meat + vegetables/pickles) → dessert. Dairy/cheese intake high (telemea, urdă, cașcaval). Bread everywhere. **Mid-day hot meal is the cultural anchor** — not breakfast or dinner. A tracker that defaults to American "small lunch, big dinner" patterns fights the rhythm.

**Orthodox fasting calendar.** ~180 days of posts annually: Lent (~48 days vegan-strict), Nativity Fast (~40 days), Apostles' + Dormition fasts, Wednesdays + Fridays year-round. Not an edge case for RO multi-user system. Victor's friends may be variably observant; app should accept "post" mode as temporary dietary pattern, NOT flag as deficiency.

**Feast-anchored ritual eating.** Sarmale at Christmas (high-calorie, eaten in quantity over multi-day feast), cozonac, pască + lamb at Easter, drob de miel, family Sunday lunches (multi-hour, multi-course, alcohol-included). Tracking during these moments must accept dramatic single-day excursions without triggering AVE-style "you ruined it" messaging.

**Available studies.** 2024 Romanian post-pandemic dietary quality study (n=4704): 74% moderately-healthy, 20.7% healthy, 5.4% unhealthy. Adult obesity 22.5% (range 18.3-29% by region); projected 35% by 2035 per World Obesity Atlas 2023.

**Implications.** Knowledge corpus must include CIQUAL + USDA + RO-specific SKUs (Mega/Carrefour/Kaufland/Lidl/Auchan/Profi) and ideally a small curated RO traditional-foods database. Fasting mode = first-class feature (mark date range as "post"; system tolerates lower protein, suggests legume/grain protein sources). Feast tolerance: a 4000-kcal day during Crăciun auto-context-tags as "feast day" and rolls into a 14-day or 30-day window without penalty. Bilingual UI (RO default for cohort, EN available for Victor's preference). Food names primary in RO with EN secondary tooltip.

### Adherence-driver-ranked table (R1 §15.1, lift verbatim)

| Driver | Effect size | Context | Cost |
|---|---|---|---|
| Self-monitoring (any modality) | Burke r large, dose-response | Universal | Med |
| Action planning / implementation intentions (healthy-add) | d=0.51 | Diet | Low |
| Goal setting (process not outcome) | Large in BCT meta | Diet | Low |
| Feedback on behavior (timely, frequent) | Daily > weekly | Diet | Med |
| Autonomy-supportive coaching voice | SDT meta significant | Diet, exercise | Low |
| MI conversation style | +55% positive outcomes | Diet counseling | Low-med |
| Defaults & choice-architecture | d=0.39 behavioral nudges | Food retail | Med |
| Implementation intentions (unhealthy-remove) | d=0.29 | Diet | Same |
| Social support — peer accountability | Significant in team-loss | Group | Med |
| Mindfulness-based interventions | g=-0.65 to -0.71 | Binge eating | Low |
| Tiny-habits anchoring | Strong in scoping reviews | Habit gateways | Low |
| Hard commitment devices | +1.5 kg short-term | General | Low (risky) |
| Social comparison / leaderboards | Mixed; ED risk significant | General | **DO NOT BUILD** |
| Streaks | Short-term up; long-term ED risk | General | **DO NOT BUILD** |

### Top-10 actionable design moves (R1 §15.2)

(1) Multi-modal logging with smart defaults. (2) Rolling 7-day window targets, not daily binaries. (3) Implementation-intention planner. (4) Autonomy-supportive LLM coach with OARS skeleton (reject outputs containing shame/fear/control language). (5) Abbreviated-monitoring graduation path. (6) Fasting / feast mode (RO-cultural first-class). (7) Process-target dashboards ("5/7 days protein hit"; show weight trend with smoothing only). (8) Receipt-anchored meal defaults. (9) Peer-visible-progress, no comparison (Andrei's "5/7 protein this week" visible to Victor as ambient relatedness; no ranking). (10) Knowledge corpus accessible mid-flow ("Why this protein target?" → 2-paragraph evidence-grounded explanation).

### ED-safeguard primitives (R1 §15.4, non-negotiable)

Sourced from NEDA, Alliance for Eating Disorders, AED, ED-symptomology literature on tracking apps:

(1) **Minimum calorie floors.** Refuse to set below 1500 kcal (M) / 1200 kcal (F) without explicit clinical-override flag. Soft warning at 1800/1500. Cite NEDA. (2) **Maximum rate-of-loss limit.** Refuse to project >0.9 kg/week (~2 lb/week) sustained loss. Soft warning at >0.5 kg/week. (3) **No body-comparison features.** No leaderboards, no body-photo gallery. (4) **No food-categorization as "good"/"bad"/"cheat".** Neutral descriptors only. (5) **No mandatory body measurements beyond weight (optional).** Waist/hip ratios, BMI flagging, body-fat estimation all opt-in and quiet. (6) **Behavior over outcome.** Affirmations for process targets (logged, planned, hit protein), not weight outcomes. (7) **Detection signals.** If user logs <80% of estimated need for 3+ consecutive days, surface gentle check-in: "Some days have run low — anything we can adjust?" Autonomy-supportive, non-diagnostic. (8) **Hard-coded escape hatch.** "Pause tracking" always one tap from home. No streak penalty. (9) **No exercise-compensation math.** Don't show "calories burned" balancing "calories eaten" as daily-budget UI. Exercise tracking is separate from intake tracking. (10) **Resource link.** "Support resources" link in settings pointing to RO-specific ED resources (Centrul Alianța) + international (NEDA, beateatingdisorders.org.uk).

### Anti-patterns (R1 §15.3)

Streaks. Public leaderboards / comparison feeds. Body-weight outcome commitments / stakes. Rigid daily targets with pass/fail UI. Hidden target recalculation. Restrictive language defaults ("don't eat", "you must", "you failed"). Asking for motivation. Catastrophe framing of single lapses. Feature-creep into social media. Calorie floors absent.

### Open questions for downstream rounds (R1 §16)

Does abbreviated-monitoring graduation hold for recomp/lean-bulk user (Victor) the same way it does for weight-loss users? Most literature is weight-loss; lean-bulk literature is sparse. How does Orthodox fasting calendar interact with protein-target maintenance — should app suggest legume + grain pairings during posts, or accept lower protein with explicit consent? How to design ambient relatedness (peer visibility) such that it satisfies SDT without slipping into social-comparison toxicity. How frequent should LLM-coach-driven check-ins be (daily is too much; weekly may be too rare; JITAI literature suggests context-triggered).

---


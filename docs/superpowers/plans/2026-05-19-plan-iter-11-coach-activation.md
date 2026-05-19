# Plan iter-11 — Coach activation (2-phase commit) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Activate Coach end-to-end by replacing `StubLlmStream` with a real LLM-backed pipeline that preserves the ClaudeMax CLI Max-20x subscription credit on Desktop, routes Android through the server, and writes an Art 13–compliant pending-before audit row in the same Postgres transaction as the budget reservation.

**Architecture:** Server-authoritative audit + budget via 2-phase commit. Desktop runs ClaudeMax CLI locally and bookends the call with `POST /coach/reserve` (server inserts pending `audit_log` row + locks budget via `consume_or_fail`) and `POST /coach/commit` (server updates the same audit row with usage + finalizes budget). Android consumes a thin SSE endpoint `POST /coach/stream` that internally pairs reserve+commit around the server-side `LlmRouter`. A 30s cleanup cron orphans pending rows older than 60s and releases their reserved budget (saga compensation, Stripe auth-reversal shape). Client persists an `audit_pending_outbox` row in SqlDelight before invoking ClaudeMax so a desktop crash mid-call replays the commit on next startup.

**Tech Stack:** Kotlin Multiplatform (Compose Android + Desktop), `:shared:llm` (Plan-2 LlmRouter + ClaudeMaxCliProvider + PiiRedactor), Ktor server (`:server`), Postgres 16 + Flyway 10.20 (`:server` V022 migration), SQLDelight (`:shared` client outbox), kotlinx.coroutines Flow, ktor-cio SSE, Koin DI, Tailscale transport.

**Council inputs (locked, do not relitigate):**
- Main council `1779206433` — verdict FLAWED, path = D-shape with server-side router as Coach canonical, all A/C variants dead due to audit-gap.
- Mini-council `1779208184` — verdict OPTION 4 (2-phase commit) with saga compensation, client outbox, idempotency-key persisted before invocation.
- Locked decisions from user: keep ClaudeMax in Coach chain on Desktop; pending-before + update-after audit pattern.

**Deferred (track as followup, NOT in this slice):**
- First Principles' ledger-projection refactor of `audit_log` (event-sourced replay instead of direct write).
- Voice pipeline (Plan-4-5.5).
- Passkey/WebAuthn (Plan-3.5).

---

## File Structure

**Server (`:server`) — new:**
- `server/src/main/resources/db/migration/V022__audit_log_coach_status.sql` — extends `audit_log` with `status` + `idempotency_key` + `reserved_until` columns; partial unique index on `idempotency_key WHERE idempotency_key IS NOT NULL`.
- `server/src/main/kotlin/com/dietician/server/routes/CoachRoutes.kt` — `installCoachRoutes()` registering `POST /coach/reserve`, `POST /coach/commit`, `POST /coach/stream` under `requireSubject` auth.
- `server/src/main/kotlin/com/dietician/server/coach/CoachService.kt` — pure Kotlin service: PII redaction (calls `:shared:llm` PiiRedactor) → `consume_or_fail` row-lock → pending audit row insert → returns `{reservationId, auditId}`. Symmetric `commit(...)` finalizes. Symmetric `streamServerRouted(...)` performs reserve + LlmRouter.streamRoute + commit in one coroutine for the SSE path.
- `server/src/main/kotlin/com/dietician/server/coach/CoachRepository.kt` — SQL boundary: `insertPendingAudit`, `updateAuditOnCommit`, `findByIdempotencyKey`, `orphanStalePending` (cron drives this).
- `server/src/main/kotlin/com/dietician/server/coach/CoachDtos.kt` — request/response data classes with kotlinx.serialization annotations.
- `server/src/main/kotlin/com/dietician/server/cron/CoachOrphanCleanupCron.kt` — 30s tick scanning for `audit_log.status='pending' AND occurred_at < now() - interval '60 seconds'`, flips to `orphaned`, refunds the budget reservation via `refund_orphaned(...)` PG fn (new in V022).
- `server/src/main/kotlin/com/dietician/server/coach/CoachSystemPrompts.kt` — EN + RO system-prompt constants keyed by locale.

**Server (`:server`) — modified:**
- `server/src/main/kotlin/com/dietician/server/Application.kt` — register `installCoachRoutes()` alongside the existing route installers; register `CoachOrphanCleanupCron` in the cron bootstrap.
- `server/src/main/kotlin/com/dietician/server/di/LlmModule.kt` — wire `CoachService` + `CoachRepository` singletons; reuse the existing `LlmRouter` + `PiiRedactor`.
- `server/src/main/kotlin/com/dietician/server/cron/CronBootstrap.kt` — register the cleanup cron when `DIETICIAN_DISABLE_INJVM_CRONS != true`.

**Shared (`:shared`) — new:**
- `shared/src/commonMain/sqldelight/com/dietician/shared/data/sql/0009_audit_pending_outbox.sq` — `audit_pending_outbox` table + `insert`, `findUncommitted`, `markCommitted`, `delete` queries.
- `shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLlmGateway.kt` — interface `suspend fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk>`.
- `shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLocale.kt` — enum `EN`, `RO`.
- `shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLlmGatewayLlmStream.kt` — adapter from `CoachLlmGateway` to the existing `LlmStream` interface used by `UiModule.kt:89`.
- `shared/src/commonMain/kotlin/com/dietician/shared/llm/net/CoachHttpClient.kt` — ktor client wrapper around `/coach/reserve` + `/coach/commit` + `/coach/stream` (SSE).
- `shared/src/desktopMain/kotlin/com/dietician/shared/llm/DesktopCoachLlmGateway.kt` — outbox + reserve + `ClaudeMaxCliProvider.stream(...)` + commit. Falls through to server SSE when ClaudeMax fails pre-chunk.
- `shared/src/desktopMain/kotlin/com/dietician/shared/llm/DesktopOutboxReplay.kt` — replay-on-startup: scan `audit_pending_outbox` for un-committed rows, re-POST `/coach/commit` with persisted idempotency-key.
- `shared/src/androidMain/kotlin/com/dietician/shared/llm/AndroidCoachLlmGateway.kt` — pure SSE consumer hitting `/coach/stream`.

**Shared (`:shared`) — modified:**
- `shared/src/commonMain/kotlin/com/dietician/shared/ui/di/UiModule.kt` — line 89 swap `StubLlmStream` for `CoachLlmGatewayLlmStream` resolving through the platform-provided `CoachLlmGateway`. Delete `private class StubLlmStream` at lines 140-149.
- `shared/src/desktopMain/kotlin/com/dietician/shared/data/DataModule.desktop.kt` — register `DesktopCoachLlmGateway` + run `DesktopOutboxReplay.replayPending()` on startup.
- `shared/src/androidMain/kotlin/com/dietician/shared/data/DataModule.android.kt` — register `AndroidCoachLlmGateway`.

**Docs — modified:**
- `docs/superpowers/specs/2026-05-17-dietician-design.md` — §7 amendment: clarify ClaudeMax CLI participates in Coach `VICTOR_DESKTOP_TEXT` chain via the desktop client only; server-side `LlmRouter` chain for non-desktop Coach turns drops ClaudeMax. Add §11 audit row state machine: `pending` → `success | failed | aborted | orphaned`.
- `docs/runbooks/coach-orphan-cleanup.md` — new runbook describing the 30s cleanup cron + how to manually orphan stale rows + how to inspect outbox replays.
- `JARVIS_MERGE.md` — note that Coach is now a server-routed surface; jarvis-kotlin merge can reuse `/coach/stream` as a thin SSE bridge.

---

## Phase A — Server foundation (V022 migration + repo + service)

### Task 0: Wrapper sanity + branch

**Files:**
- Modify: branch state

- [ ] **Step 1: Verify clean working tree on `master` after PR #25 merges**

Run: `git -C "C:/Users/User/Desktop/Dietician" status`
Expected: `nothing to commit, working tree clean`.

- [ ] **Step 2: Create iter-11 branch**

```bash
git -C "C:/Users/User/Desktop/Dietician" checkout -b plan-iter-11/coach-activation master
```

- [ ] **Step 3: Pre-commit hook sanity**

Run: `cat "C:/Users/User/Desktop/Dietician/.git/hooks/pre-commit"`
Expected: existing hook calling `./gradlew ktlintFormat detekt :shared:test`. If missing or empty, skip — CI is the authoritative gate.

---

### Task 1: V022 migration extending `audit_log` with coach status fields

**Files:**
- Create: `server/src/main/resources/db/migration/V022__audit_log_coach_status.sql`
- Test: `server/src/test/kotlin/com/dietician/server/db/V022AuditLogCoachStatusTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.db

import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class V022AuditLogCoachStatusTest {
    @Container
    val pg = PostgreSQLContainer("pgvector/pgvector:pg16")

    @Test
    fun `V022 adds status idempotency_key reserved_until and partial unique index`() {
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        pg.createConnection("").use { c ->
            c.prepareStatement(
                "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_name = 'audit_log' " +
                    "ORDER BY column_name"
            ).executeQuery().use { rs ->
                val cols = buildList { while (rs.next()) add(rs.getString(1)) }
                assertTrue("status" in cols)
                assertTrue("idempotency_key" in cols)
                assertTrue("reserved_until" in cols)
            }
            c.prepareStatement(
                "SELECT indexdef FROM pg_indexes " +
                    "WHERE tablename = 'audit_log' AND indexname = 'idx_audit_log_idempotency_key'"
            ).executeQuery().use { rs ->
                assertTrue(rs.next())
                val def = rs.getString(1)
                assertTrue("UNIQUE" in def, "expected unique partial index, got: $def")
                assertTrue("WHERE" in def && "idempotency_key IS NOT NULL" in def)
            }
            c.prepareStatement(
                "SELECT proname FROM pg_proc WHERE proname = 'refund_orphaned'"
            ).executeQuery().use { rs ->
                assertTrue(rs.next(), "refund_orphaned(...) PG fn must exist after V022")
            }
        }
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.db.V022AuditLogCoachStatusTest"`
Expected: FAIL (`status` column missing on `audit_log`).

- [ ] **Step 3: Write the migration**

Create `server/src/main/resources/db/migration/V022__audit_log_coach_status.sql`:

```sql
-- V022__audit_log_coach_status.sql
-- iter-11 Coach activation — extends V018 audit_log with the 2-phase-commit
-- state machine (pending → success | failed | aborted | orphaned), the
-- idempotency key shared between /coach/reserve and /coach/commit, and the
-- reservation deadline driving the 60s saga-compensation cleanup cron.

ALTER TABLE audit_log
    ADD COLUMN status TEXT NOT NULL DEFAULT 'success'
        CHECK (status IN ('pending', 'success', 'failed', 'aborted', 'orphaned'));

ALTER TABLE audit_log
    ADD COLUMN idempotency_key UUID NULL;

ALTER TABLE audit_log
    ADD COLUMN reserved_until TIMESTAMPTZ NULL;

-- Partial unique index: NULL idempotency_key allowed for legacy rows + non-coach
-- audit events (sign-in, redact, etc); non-NULL keys are unique so commit retries
-- find the existing pending row via UPSERT semantics.
CREATE UNIQUE INDEX idx_audit_log_idempotency_key
    ON audit_log (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Saga-compensation entry point — flips pending rows >60s old to orphaned and
-- releases the reserved cost from llm_budget. Called by CoachOrphanCleanupCron
-- every 30s. Returns number of rows compensated.
CREATE OR REPLACE FUNCTION refund_orphaned(stale_seconds INT DEFAULT 60)
RETURNS INT
LANGUAGE plpgsql
AS $$
DECLARE
    compensated_count INT := 0;
    r RECORD;
BEGIN
    FOR r IN
        SELECT id, subject_id, model, cost_cents
        FROM audit_log
        WHERE status = 'pending'
          AND reserved_until IS NOT NULL
          AND reserved_until < now()
          AND occurred_at < now() - make_interval(secs => stale_seconds)
        FOR UPDATE SKIP LOCKED
    LOOP
        UPDATE audit_log
        SET status = 'orphaned'
        WHERE id = r.id;
        IF r.cost_cents IS NOT NULL AND r.cost_cents > 0 THEN
            UPDATE llm_budget
            SET cost_cents_used = GREATEST(cost_cents_used - r.cost_cents, 0)
            WHERE subject_id = r.subject_id
              AND provider = COALESCE(r.model, 'unknown')
              AND period_starts_at = date_trunc('month', r.occurred_at)::DATE;
        END IF;
        compensated_count := compensated_count + 1;
    END LOOP;
    RETURN compensated_count;
END;
$$;
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.db.V022AuditLogCoachStatusTest"`
Expected: PASS.

- [ ] **Step 5: Verify Flyway schema-parity baseline still asserts ≥22 migrations**

Run: `./gradlew :server:test --tests "com.dietician.server.db.NoMigrationsFoundRegressionTest"`
Expected: PASS (the assertion is `>= 21`; with V022 in place it's now 22).

- [ ] **Step 6: Commit**

```bash
git add server/src/main/resources/db/migration/V022__audit_log_coach_status.sql \
        server/src/test/kotlin/com/dietician/server/db/V022AuditLogCoachStatusTest.kt
git commit -m "feat(server): V022 extends audit_log with coach 2PC state machine + refund_orphaned fn"
```

---

### Task 2: `CoachDtos` data classes

**Files:**
- Create: `server/src/main/kotlin/com/dietician/server/coach/CoachDtos.kt`
- Test: `server/src/test/kotlin/com/dietician/server/coach/CoachDtosTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.coach

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CoachDtosTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `reserve request round-trips`() {
        val payload = CoachReserveRequest(
            idempotencyKey = "11111111-1111-1111-1111-111111111111",
            prompt = "How do I hit 2750 kcal with chicken + rice?",
            locale = "en",
            provider = "claudemax",
            estimatedCostCents = 5,
            reservationTtlSeconds = 60,
        )
        val s = json.encodeToString(CoachReserveRequest.serializer(), payload)
        val back = json.decodeFromString(CoachReserveRequest.serializer(), s)
        assertEquals(payload, back)
    }

    @Test
    fun `reserve response carries reservationId + auditId + redactedPrompt`() {
        val r = CoachReserveResponse(
            reservationId = "22222222-2222-2222-2222-222222222222",
            auditId = "33333333-3333-3333-3333-333333333333",
            redactedPromptHash = "deadbeef",
            reservedUntilEpochMs = 1_780_000_000_000L,
        )
        val s = json.encodeToString(CoachReserveResponse.serializer(), r)
        val back = json.decodeFromString(CoachReserveResponse.serializer(), s)
        assertEquals(r, back)
    }

    @Test
    fun `commit request usage fields are required`() {
        val req = CoachCommitRequest(
            idempotencyKey = "11111111-1111-1111-1111-111111111111",
            status = "success",
            promptTokens = 42,
            completionTokens = 100,
            costCents = 5,
            provider = "claudemax",
            latencyMs = 3200,
            responseHash = "cafef00d",
        )
        val s = json.encodeToString(CoachCommitRequest.serializer(), req)
        val back = json.decodeFromString(CoachCommitRequest.serializer(), s)
        assertEquals(req, back)
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachDtosTest"`
Expected: FAIL (`CoachReserveRequest` not defined).

- [ ] **Step 3: Write `CoachDtos.kt`**

```kotlin
package com.dietician.server.coach

import kotlinx.serialization.Serializable

/**
 * iter-11 — request/response data classes for the 2-phase commit Coach surface.
 *
 * Wire format mirrors Plan-3 conventions: snake_case ignored (kotlinx.serialization
 * defaults to property names), all fields explicit, no defaults that hide intent.
 *
 * idempotencyKey is a client-generated UUIDv4 persisted to the desktop outbox BEFORE
 * the LlmCall. Same key flows into /coach/commit so server can dedupe retries.
 */
@Serializable
data class CoachReserveRequest(
    val idempotencyKey: String,
    val prompt: String,
    val locale: String,
    val provider: String,
    val estimatedCostCents: Int,
    val reservationTtlSeconds: Int,
)

@Serializable
data class CoachReserveResponse(
    val reservationId: String,
    val auditId: String,
    val redactedPromptHash: String,
    val reservedUntilEpochMs: Long,
)

@Serializable
data class CoachReserveRejected(
    val reason: String,
    val capUsd: Double? = null,
    val spentUsd: Double? = null,
)

@Serializable
data class CoachCommitRequest(
    val idempotencyKey: String,
    val status: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val costCents: Int,
    val provider: String,
    val latencyMs: Long,
    val responseHash: String,
)

@Serializable
data class CoachCommitResponse(
    val auditId: String,
    val status: String,
)

@Serializable
data class CoachStreamRequest(
    val idempotencyKey: String,
    val prompt: String,
    val locale: String,
)
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachDtosTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/coach/CoachDtos.kt \
        server/src/test/kotlin/com/dietician/server/coach/CoachDtosTest.kt
git commit -m "feat(server): coach reserve/commit/stream DTOs"
```

---

### Task 3: `CoachRepository` — insertPendingAudit + updateAuditOnCommit + findByIdempotencyKey

**Files:**
- Create: `server/src/main/kotlin/com/dietician/server/coach/CoachRepository.kt`
- Test: `server/src/test/kotlin/com/dietician/server/coach/CoachRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.coach

import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachRepositoryTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var ds: HikariDataSource
    private lateinit var repo: CoachRepository
    private val subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeAll
    fun setup() {
        pg = PostgreSQLContainer("pgvector/pgvector:pg16").apply { start() }
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = pg.jdbcUrl; username = pg.username; password = pg.password
        })
        repo = CoachRepository(ds)
        ds.connection.use { c ->
            c.prepareStatement(
                "INSERT INTO subjects (subject_id, display_name) VALUES (?, 'Victor') ON CONFLICT DO NOTHING"
            ).apply { setObject(1, subjectId); execute() }
        }
    }

    @AfterAll
    fun teardown() { ds.close(); pg.stop() }

    @Test
    fun `insertPendingAudit writes a pending row with idempotency_key + reserved_until`() = runTest {
        val key = UUID.randomUUID()
        val (auditId, reservedUntilMs) = repo.insertPendingAudit(
            subjectId = subjectId,
            idempotencyKey = key,
            promptHash = "abc",
            provider = "claudemax",
            estimatedCostCents = 5,
            reservationTtlSeconds = 60,
        )
        assertNotNull(auditId)
        assertTrue(reservedUntilMs > System.currentTimeMillis())
        val row = repo.findByIdempotencyKey(key)
        assertNotNull(row)
        assertEquals("pending", row.status)
        assertEquals(5, row.costCents)
    }

    @Test
    fun `updateAuditOnCommit flips status + records usage + clears reserved_until`() = runTest {
        val key = UUID.randomUUID()
        val (auditId, _) = repo.insertPendingAudit(subjectId, key, "h", "claudemax", 5, 60)
        repo.updateAuditOnCommit(
            idempotencyKey = key,
            status = "success",
            promptTokens = 10,
            completionTokens = 20,
            costCents = 4,
            provider = "claudemax",
            latencyMs = 2200,
            responseHash = "def",
        )
        val row = repo.findByIdempotencyKey(key)
        assertNotNull(row)
        assertEquals(auditId, row.auditId)
        assertEquals("success", row.status)
        assertEquals(10, row.promptTokens)
        assertEquals(20, row.completionTokens)
        assertEquals(4, row.costCents)
        assertNull(row.reservedUntilMs)
    }

    @Test
    fun `findByIdempotencyKey returns null for unknown key`() = runTest {
        assertNull(repo.findByIdempotencyKey(UUID.randomUUID()))
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachRepositoryTest"`
Expected: FAIL (`CoachRepository` not defined).

- [ ] **Step 3: Write `CoachRepository.kt`**

```kotlin
package com.dietician.server.coach

import com.zaxxer.hikari.HikariDataSource
import java.sql.Timestamp
import java.util.UUID

/**
 * iter-11 — SQL boundary for the 2-phase commit Coach audit pipeline.
 *
 * All writes go through audit_log (V018) with the iter-11 status columns from V022
 * applied. consume_or_fail (V019) is invoked directly by CoachService, NOT this
 * repo — that keeps the budget-lock semantics colocated with the orchestration.
 *
 * Identity contract:
 *   - idempotencyKey is the load-bearing identity. The partial unique index from
 *     V022 lets the same key resolve via findByIdempotencyKey for commit retries.
 *   - auditId (PK) is server-allocated on insertPendingAudit; returned for caller
 *     visibility but not load-bearing for retries.
 */
class CoachRepository(private val ds: HikariDataSource) {

    data class AuditRow(
        val auditId: UUID,
        val subjectId: UUID,
        val status: String,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val costCents: Int?,
        val reservedUntilMs: Long?,
    )

    suspend fun insertPendingAudit(
        subjectId: UUID,
        idempotencyKey: UUID,
        promptHash: String,
        provider: String,
        estimatedCostCents: Int,
        reservationTtlSeconds: Int,
    ): Pair<UUID, Long> {
        ds.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO audit_log
                    (subject_id, kind, model, prompt_hash, cost_cents,
                     status, idempotency_key, reserved_until)
                VALUES (?, 'llm_call', ?, ?, ?, 'pending', ?,
                        now() + make_interval(secs => ?))
                RETURNING id, extract(epoch from reserved_until) * 1000
                """.trimIndent()
            ).apply {
                setObject(1, subjectId)
                setString(2, provider)
                setString(3, promptHash)
                setInt(4, estimatedCostCents)
                setObject(5, idempotencyKey)
                setInt(6, reservationTtlSeconds)
            }.executeQuery().use { rs ->
                rs.next()
                return rs.getObject(1, UUID::class.java) to rs.getDouble(2).toLong()
            }
        }
    }

    suspend fun updateAuditOnCommit(
        idempotencyKey: UUID,
        status: String,
        promptTokens: Int,
        completionTokens: Int,
        costCents: Int,
        provider: String,
        latencyMs: Long,
        responseHash: String,
    ) {
        ds.connection.use { c ->
            c.prepareStatement(
                """
                UPDATE audit_log
                SET status = ?,
                    model = ?,
                    input_tokens = ?,
                    output_tokens = ?,
                    cost_cents = ?,
                    response_hash = ?,
                    reserved_until = NULL,
                    extra = jsonb_set(COALESCE(extra, '{}'::jsonb),
                                      '{latency_ms}', to_jsonb(?::bigint))
                WHERE idempotency_key = ?
                """.trimIndent()
            ).apply {
                setString(1, status)
                setString(2, provider)
                setInt(3, promptTokens)
                setInt(4, completionTokens)
                setInt(5, costCents)
                setString(6, responseHash)
                setLong(7, latencyMs)
                setObject(8, idempotencyKey)
            }.executeUpdate()
        }
    }

    suspend fun findByIdempotencyKey(idempotencyKey: UUID): AuditRow? {
        ds.connection.use { c ->
            c.prepareStatement(
                """
                SELECT id, subject_id, status, input_tokens, output_tokens,
                       cost_cents, reserved_until
                FROM audit_log
                WHERE idempotency_key = ?
                """.trimIndent()
            ).apply { setObject(1, idempotencyKey) }.executeQuery().use { rs ->
                if (!rs.next()) return null
                return AuditRow(
                    auditId = rs.getObject(1, UUID::class.java),
                    subjectId = rs.getObject(2, UUID::class.java),
                    status = rs.getString(3),
                    promptTokens = rs.getObject(4) as Int?,
                    completionTokens = rs.getObject(5) as Int?,
                    costCents = rs.getObject(6) as Int?,
                    reservedUntilMs = (rs.getTimestamp(7) as Timestamp?)?.time,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachRepositoryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/coach/CoachRepository.kt \
        server/src/test/kotlin/com/dietician/server/coach/CoachRepositoryTest.kt
git commit -m "feat(server): CoachRepository — pending audit insert + commit update + dedupe lookup"
```

---

### Task 4: `CoachSystemPrompts` EN + RO

**Files:**
- Create: `server/src/main/kotlin/com/dietician/server/coach/CoachSystemPrompts.kt`
- Test: `server/src/test/kotlin/com/dietician/server/coach/CoachSystemPromptsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.coach

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class CoachSystemPromptsTest {
    @Test
    fun `EN prompt names Victor and references 2750 kcal target`() {
        val p = CoachSystemPrompts.forLocale("en")
        assertTrue("Victor" in p, "EN must address Victor by name")
        assertTrue("2750" in p, "EN must reference daily kcal target")
        assertTrue("137" in p, "EN must reference protein target")
    }

    @Test
    fun `RO prompt is non-empty and uses comma-below diacritics`() {
        val p = CoachSystemPrompts.forLocale("ro")
        assertTrue(p.isNotBlank())
        assertTrue("ş" !in p && "ţ" !in p, "must use ș (U+0219) / ț (U+021B), not cedilla")
    }

    @Test
    fun `unknown locale falls back to EN`() {
        assertEquals(CoachSystemPrompts.forLocale("en"), CoachSystemPrompts.forLocale("xx"))
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachSystemPromptsTest"`
Expected: FAIL.

- [ ] **Step 3: Write `CoachSystemPrompts.kt`**

```kotlin
package com.dietician.server.coach

/**
 * iter-11 — locale-keyed system prompt for Coach turns.
 *
 * Romanian copy uses comma-below `ș`/`ț` (U+0219 / U+021B), never cedilla
 * `ş`/`ţ` (U+015F / U+0163). The CoachChatScreen tests assert the same.
 *
 * Victor identity baked into both variants because Coach is single-user.
 * Numeric targets (kcal 2750 / protein 137g) come from the spec §1; if
 * those change, this file moves with them.
 */
object CoachSystemPrompts {
    private const val EN_PROMPT = """
You are Coach, the personal AI dietician helping Victor (UAIC year-1 AI student in Iași) hit his lean-bulking targets: 2750 kcal/day and 137 g protein/day. Air fryer + microwave only.

Style: concise, direct, no hedging. Bullet points OK. Suggest concrete meal swaps using items Victor already has on hand.

Hard refusals: never advise extreme restriction, very-low-calorie cuts (<1800 kcal), purging behaviors, or "compensatory" exercise after eating. Surface bigorexia-aware messaging when Victor frames goals around appearance vs strength/health.

Disclosure: every reply is an AI-generated suggestion, not medical advice. Defer to a registered dietitian for any condition diagnosis.
"""

    private const val RO_PROMPT = """
Ești Coach, dieticianul personal AI care îl ajută pe Victor (student UAIC anul 1, IA, Iași) să-și atingă țintele de lean-bulking: 2750 kcal/zi și 137 g proteină/zi. Airfryer + cuptor cu microunde, atât.

Stil: concis, direct, fără echivocuri. Bullet-uri ok. Propune schimburi concrete de mese cu produse pe care Victor deja le are în cămară.

Refuzuri ferme: niciodată restricții extreme, tăieri sub 1800 kcal, comportamente compensatorii sau exerciții "de pedeapsă" după ce a mâncat. Semnalează atunci când motivația lui Victor pare ancorată în aspect și nu în forță sau sănătate (risc bigorexia).

Disclosure: fiecare răspuns este o sugestie generată de IA, nu sfat medical. Pentru diagnostic, dieteticiană autorizată.
"""

    fun forLocale(locale: String): String = when (locale.lowercase()) {
        "ro" -> RO_PROMPT.trim()
        else -> EN_PROMPT.trim()
    }
}
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachSystemPromptsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/coach/CoachSystemPrompts.kt \
        server/src/test/kotlin/com/dietician/server/coach/CoachSystemPromptsTest.kt
git commit -m "feat(server): CoachSystemPrompts EN+RO with Victor identity + spec-aligned targets"
```

---

### Task 5: `CoachService` reserve()

**Files:**
- Create: `server/src/main/kotlin/com/dietician/server/coach/CoachService.kt`
- Test: `server/src/test/kotlin/com/dietician/server/coach/CoachServiceReserveTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.coach

import com.dietician.server.db.runMigrations
import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.PiiRedactor
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachServiceReserveTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var ds: HikariDataSource
    private lateinit var service: CoachService
    private val subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeAll
    fun setup() {
        pg = PostgreSQLContainer("pgvector/pgvector:pg16").apply { start() }
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = pg.jdbcUrl; username = pg.username; password = pg.password
        })
        ds.connection.use { c ->
            c.prepareStatement("INSERT INTO subjects (subject_id, display_name) VALUES (?, 'Victor')")
                .apply { setObject(1, subjectId) }.execute()
        }
        service = CoachService(
            repo = CoachRepository(ds),
            budgets = BudgetRepository(ds),
            redactor = PiiRedactor(),
        )
    }

    @AfterAll
    fun teardown() { ds.close(); pg.stop() }

    @Test
    fun `reserve writes pending audit + reserves budget + returns reservation envelope`() = runTest {
        val key = UUID.randomUUID()
        val resp = service.reserve(
            subjectId = subjectId,
            request = CoachReserveRequest(
                idempotencyKey = key.toString(),
                prompt = "How many grams of chicken for 50g protein?",
                locale = "en",
                provider = "claudemax",
                estimatedCostCents = 5,
                reservationTtlSeconds = 60,
            )
        )
        assertTrue(resp is CoachServiceReserveResult.Reserved)
        val r = resp.envelope
        assertNotNull(UUID.fromString(r.reservationId))
        assertNotNull(UUID.fromString(r.auditId))
        assertTrue(r.reservedUntilEpochMs > System.currentTimeMillis())
    }

    @Test
    fun `reserve is idempotent — second call with same key returns the same audit row`() = runTest {
        val key = UUID.randomUUID()
        val req = CoachReserveRequest(
            idempotencyKey = key.toString(),
            prompt = "test",
            locale = "en",
            provider = "claudemax",
            estimatedCostCents = 5,
            reservationTtlSeconds = 60,
        )
        val first = service.reserve(subjectId, req) as CoachServiceReserveResult.Reserved
        val second = service.reserve(subjectId, req) as CoachServiceReserveResult.Reserved
        assertEquals(first.envelope.auditId, second.envelope.auditId)
    }

    @Test
    fun `reserve returns Rejected when budget cap is exceeded`() = runTest {
        ds.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO llm_budget
                  (subject_id, provider, period_starts_at, period_ends_at, cost_cents_used, cost_cents_cap)
                VALUES (?, 'claudemax',
                        date_trunc('month', now())::DATE,
                        (date_trunc('month', now()) + interval '1 month - 1 day')::DATE,
                        500, 500)
                ON CONFLICT (subject_id, provider, period_starts_at)
                DO UPDATE SET cost_cents_used = 500, cost_cents_cap = 500
                """.trimIndent()
            ).apply { setObject(1, subjectId) }.execute()
        }
        val resp = service.reserve(
            subjectId = subjectId,
            request = CoachReserveRequest(
                idempotencyKey = UUID.randomUUID().toString(),
                prompt = "x",
                locale = "en",
                provider = "claudemax",
                estimatedCostCents = 5,
                reservationTtlSeconds = 60,
            )
        )
        assertTrue(resp is CoachServiceReserveResult.Rejected, "got: $resp")
        assertEquals("over_budget", (resp).reason)
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachServiceReserveTest"`
Expected: FAIL (`CoachService` not defined).

- [ ] **Step 3: Write `CoachService.kt`**

```kotlin
package com.dietician.server.coach

import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.PiiRedactor
import java.security.MessageDigest
import java.util.UUID

/**
 * iter-11 — orchestration for the 2-phase commit Coach pipeline.
 *
 * reserve() ordering matters:
 *   1. PII redaction on the prompt (PiiRedactor from :shared:llm).
 *   2. consume_or_fail row-lock on llm_budget (BudgetRepository) — fail fast
 *      if cap exceeded BEFORE the audit row is written. This is the saga gate.
 *   3. insertPendingAudit writes status=pending with the redacted prompt hash.
 *   4. Return reservation envelope.
 *
 * Idempotency: a duplicate call with the same idempotencyKey is detected via
 * findByIdempotencyKey BEFORE consume_or_fail fires (which would double-charge).
 */
sealed interface CoachServiceReserveResult {
    data class Reserved(val envelope: CoachReserveResponse) : CoachServiceReserveResult
    data class Rejected(val reason: String, val capUsd: Double? = null, val spentUsd: Double? = null) :
        CoachServiceReserveResult
}

class CoachService(
    private val repo: CoachRepository,
    private val budgets: BudgetRepository,
    private val redactor: PiiRedactor,
) {
    suspend fun reserve(
        subjectId: UUID,
        request: CoachReserveRequest,
    ): CoachServiceReserveResult {
        val key = UUID.fromString(request.idempotencyKey)

        repo.findByIdempotencyKey(key)?.let { existing ->
            return CoachServiceReserveResult.Reserved(
                CoachReserveResponse(
                    reservationId = existing.auditId.toString(),
                    auditId = existing.auditId.toString(),
                    redactedPromptHash = "replay",
                    reservedUntilEpochMs = existing.reservedUntilMs
                        ?: (System.currentTimeMillis() + request.reservationTtlSeconds * 1000L),
                )
            )
        }

        val redacted = redactor.redact(request.prompt)
        val promptHash = sha256(redacted)
        val budgetOk = budgets.consumeOrFail(
            subjectId = subjectId,
            provider = request.provider,
            tokensNeeded = 0,
            costCentsEstimated = request.estimatedCostCents,
        )
        if (!budgetOk) {
            return CoachServiceReserveResult.Rejected(reason = "over_budget")
        }
        val (auditId, reservedUntilMs) = repo.insertPendingAudit(
            subjectId = subjectId,
            idempotencyKey = key,
            promptHash = promptHash,
            provider = request.provider,
            estimatedCostCents = request.estimatedCostCents,
            reservationTtlSeconds = request.reservationTtlSeconds,
        )
        return CoachServiceReserveResult.Reserved(
            CoachReserveResponse(
                reservationId = auditId.toString(),
                auditId = auditId.toString(),
                redactedPromptHash = promptHash,
                reservedUntilEpochMs = reservedUntilMs,
            )
        )
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 4: Confirm `BudgetRepository.consumeOrFail` exists** (if not, write a thin wrapper around the V019 PG fn before continuing — grep `server/src/main/kotlin/com/dietician/server/repo/BudgetRepository.kt` for the existing function signature and adapt the call here)

Run: `grep -n "consume" "C:/Users/User/Desktop/Dietician/server/src/main/kotlin/com/dietician/server/repo/BudgetRepository.kt"`

If `consumeOrFail` signature differs, adapt the call in `CoachService.reserve` to match the existing repo method. If `BudgetRepository` is missing the wrapper, add it as a separate one-task commit before continuing.

- [ ] **Step 5: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachServiceReserveTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/coach/CoachService.kt \
        server/src/test/kotlin/com/dietician/server/coach/CoachServiceReserveTest.kt
git commit -m "feat(server): CoachService.reserve — PII redact + budget lock + pending audit"
```

---

### Task 6: `CoachService.commit()`

**Files:**
- Modify: `server/src/main/kotlin/com/dietician/server/coach/CoachService.kt`
- Test: `server/src/test/kotlin/com/dietician/server/coach/CoachServiceCommitTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.coach

import com.dietician.server.db.runMigrations
import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.PiiRedactor
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachServiceCommitTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var ds: HikariDataSource
    private lateinit var service: CoachService
    private lateinit var repo: CoachRepository
    private val subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeAll
    fun setup() {
        pg = PostgreSQLContainer("pgvector/pgvector:pg16").apply { start() }
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = pg.jdbcUrl; username = pg.username; password = pg.password
        })
        ds.connection.use { c ->
            c.prepareStatement("INSERT INTO subjects (subject_id, display_name) VALUES (?, 'Victor')")
                .apply { setObject(1, subjectId) }.execute()
        }
        repo = CoachRepository(ds)
        service = CoachService(repo, BudgetRepository(ds), PiiRedactor())
    }

    @AfterAll
    fun teardown() { ds.close(); pg.stop() }

    @Test
    fun `commit success flips status + records usage`() = runTest {
        val key = UUID.randomUUID()
        service.reserve(
            subjectId,
            CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60),
        )
        val resp = service.commit(
            subjectId,
            CoachCommitRequest(key.toString(), "success", 10, 20, 4, "claudemax", 2200, "hash"),
        )
        assertEquals("success", resp.status)
        val row = repo.findByIdempotencyKey(key)!!
        assertEquals("success", row.status)
        assertEquals(10, row.promptTokens)
    }

    @Test
    fun `commit is idempotent — second call returns same auditId, no double-write`() = runTest {
        val key = UUID.randomUUID()
        service.reserve(subjectId, CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60))
        val req = CoachCommitRequest(key.toString(), "success", 10, 20, 4, "claudemax", 2200, "hash")
        val a = service.commit(subjectId, req)
        val b = service.commit(subjectId, req)
        assertEquals(a.auditId, b.auditId)
        assertEquals("success", b.status)
    }

    @Test
    fun `commit on failed status surfaces the failure but still writes usage`() = runTest {
        val key = UUID.randomUUID()
        service.reserve(subjectId, CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60))
        val resp = service.commit(
            subjectId,
            CoachCommitRequest(key.toString(), "failed", 5, 0, 1, "claudemax", 800, "errhash"),
        )
        assertEquals("failed", resp.status)
        assertEquals("failed", repo.findByIdempotencyKey(key)!!.status)
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachServiceCommitTest"`
Expected: FAIL (`CoachService.commit` not defined).

- [ ] **Step 3: Add `commit()` to `CoachService.kt`**

Append to `CoachService.kt`:

```kotlin
    suspend fun commit(
        subjectId: UUID,
        request: CoachCommitRequest,
    ): CoachCommitResponse {
        val key = UUID.fromString(request.idempotencyKey)
        val existing = repo.findByIdempotencyKey(key)
            ?: throw IllegalStateException("commit before reserve: $key")
        if (existing.status != "pending") {
            return CoachCommitResponse(auditId = existing.auditId.toString(), status = existing.status)
        }
        repo.updateAuditOnCommit(
            idempotencyKey = key,
            status = request.status,
            promptTokens = request.promptTokens,
            completionTokens = request.completionTokens,
            costCents = request.costCents,
            provider = request.provider,
            latencyMs = request.latencyMs,
            responseHash = request.responseHash,
        )
        return CoachCommitResponse(auditId = existing.auditId.toString(), status = request.status)
    }
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachServiceCommitTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/coach/CoachService.kt \
        server/src/test/kotlin/com/dietician/server/coach/CoachServiceCommitTest.kt
git commit -m "feat(server): CoachService.commit — idempotent update + status flip"
```

---

### Task 7: `CoachOrphanCleanupCron`

**Files:**
- Create: `server/src/main/kotlin/com/dietician/server/cron/CoachOrphanCleanupCron.kt`
- Test: `server/src/test/kotlin/com/dietician/server/cron/CoachOrphanCleanupCronTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.cron

import com.dietician.server.coach.CoachReserveRequest
import com.dietician.server.coach.CoachRepository
import com.dietician.server.coach.CoachService
import com.dietician.server.db.runMigrations
import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.PiiRedactor
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachOrphanCleanupCronTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var ds: HikariDataSource
    private lateinit var service: CoachService
    private lateinit var repo: CoachRepository
    private lateinit var cron: CoachOrphanCleanupCron
    private val subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeAll
    fun setup() {
        pg = PostgreSQLContainer("pgvector/pgvector:pg16").apply { start() }
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = pg.jdbcUrl; username = pg.username; password = pg.password
        })
        ds.connection.use { c ->
            c.prepareStatement("INSERT INTO subjects (subject_id, display_name) VALUES (?, 'Victor')")
                .apply { setObject(1, subjectId) }.execute()
        }
        repo = CoachRepository(ds)
        service = CoachService(repo, BudgetRepository(ds), PiiRedactor())
        cron = CoachOrphanCleanupCron(ds)
    }

    @AfterAll
    fun teardown() { ds.close(); pg.stop() }

    @Test
    fun `runOnce flips rows older than 60s to orphaned and refunds budget`() = runTest {
        val key = UUID.randomUUID()
        service.reserve(
            subjectId,
            CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60),
        )
        ds.connection.use { c ->
            c.prepareStatement(
                "UPDATE audit_log SET occurred_at = now() - interval '120 seconds', " +
                    "reserved_until = now() - interval '60 seconds' WHERE idempotency_key = ?"
            ).apply { setObject(1, key) }.executeUpdate()
        }
        val compensated = cron.runOnce()
        assertEquals(1, compensated)
        assertEquals("orphaned", repo.findByIdempotencyKey(key)!!.status)
    }

    @Test
    fun `runOnce ignores rows younger than 60s`() = runTest {
        val key = UUID.randomUUID()
        service.reserve(subjectId, CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60))
        val compensated = cron.runOnce()
        assertEquals(0, compensated)
        assertEquals("pending", repo.findByIdempotencyKey(key)!!.status)
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.cron.CoachOrphanCleanupCronTest"`
Expected: FAIL.

- [ ] **Step 3: Write `CoachOrphanCleanupCron.kt`**

```kotlin
package com.dietician.server.cron

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory

/**
 * iter-11 — saga compensation for the 2-phase commit Coach pipeline.
 *
 * Calls refund_orphaned(60) every 30s via [CronBootstrap]. The PG fn flips
 * audit_log rows where status='pending' AND reserved_until < now() to
 * 'orphaned' and decrements llm_budget.cost_cents_used by the reserved
 * cost_cents. Returns the number of rows compensated.
 *
 * Operator visibility: log line "coach: orphaned N pending rows" at INFO
 * when N > 0. Silent when N = 0 to avoid log spam.
 */
class CoachOrphanCleanupCron(private val ds: HikariDataSource) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun runOnce(staleSeconds: Int = 60): Int {
        ds.connection.use { c ->
            c.prepareStatement("SELECT refund_orphaned(?)").apply { setInt(1, staleSeconds) }
                .executeQuery().use { rs ->
                    rs.next()
                    val n = rs.getInt(1)
                    if (n > 0) log.info("coach: orphaned {} pending rows (saga compensation)", n)
                    return n
                }
        }
    }
}
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.cron.CoachOrphanCleanupCronTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/cron/CoachOrphanCleanupCron.kt \
        server/src/test/kotlin/com/dietician/server/cron/CoachOrphanCleanupCronTest.kt
git commit -m "feat(server): CoachOrphanCleanupCron — 60s saga compensation via refund_orphaned PG fn"
```

---

### Task 8: `installCoachRoutes` — reserve + commit endpoints

**Files:**
- Create: `server/src/main/kotlin/com/dietician/server/routes/CoachRoutes.kt`
- Test: `server/src/test/kotlin/com/dietician/server/routes/CoachRoutesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.routes

import com.dietician.server.coach.CoachCommitRequest
import com.dietician.server.coach.CoachReserveRequest
import com.dietician.server.coach.CoachReserveResponse
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoachRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `POST coach reserve requires session`() = testApplication {
        application { installCoachRoutesForTest() /* helper that mounts the route without the full Application.module */ }
        val resp = client.post("/coach/reserve") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"idempotencyKey":"${UUID.randomUUID()}","prompt":"hi","locale":"en","provider":"claudemax","estimatedCostCents":5,"reservationTtlSeconds":60}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST coach reserve returns reservation envelope on valid session`() = testApplication {
        application { installCoachRoutesForTest(authedSubjectId = "00000000-0000-0000-0000-000000000001") }
        val resp = client.post("/coach/reserve") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"idempotencyKey":"${UUID.randomUUID()}","prompt":"hi","locale":"en","provider":"claudemax","estimatedCostCents":5,"reservationTtlSeconds":60}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = json.decodeFromString(CoachReserveResponse.serializer(), resp.bodyAsText())
        assertTrue(body.auditId.isNotBlank())
    }

    @Test
    fun `POST coach commit updates audit row + returns response`() = testApplication {
        // reserve first, then commit with same idempotencyKey
        val key = UUID.randomUUID().toString()
        application { installCoachRoutesForTest(authedSubjectId = "00000000-0000-0000-0000-000000000001") }
        client.post("/coach/reserve") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"idempotencyKey":"$key","prompt":"hi","locale":"en","provider":"claudemax","estimatedCostCents":5,"reservationTtlSeconds":60}""")
        }
        val resp = client.post("/coach/commit") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"idempotencyKey":"$key","status":"success","promptTokens":10,"completionTokens":20,"costCents":4,"provider":"claudemax","latencyMs":2200,"responseHash":"abc"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }
}
```

Add a top-level test helper:

```kotlin
// server/src/test/kotlin/com/dietician/server/routes/installCoachRoutesForTest.kt
package com.dietician.server.routes

import io.ktor.server.application.Application

// Stub helper for testing — wires CoachService + auth-replay middleware against
// an embedded Testcontainer Postgres. Implementation lands when the route file
// is written; for now this just declares intent so the test compiles.
fun Application.installCoachRoutesForTest(authedSubjectId: String? = null) {
    error("test helper — implement when CoachRoutes lands")
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.routes.CoachRoutesTest"`
Expected: FAIL.

- [ ] **Step 3: Write `CoachRoutes.kt`**

```kotlin
package com.dietician.server.routes

import com.dietician.server.coach.CoachCommitRequest
import com.dietician.server.coach.CoachReserveRejected
import com.dietician.server.coach.CoachReserveRequest
import com.dietician.server.coach.CoachService
import com.dietician.server.coach.CoachServiceReserveResult
import com.dietician.server.middleware.requireSubject
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

/**
 * iter-11 — 2-phase commit Coach surface. POST /coach/reserve →
 * server inserts a pending audit row + locks budget; client runs ClaudeMax
 * CLI locally (desktop) or reads SSE chunks from /coach/stream (Android);
 * POST /coach/commit closes the loop with usage + final status.
 *
 * /coach/stream lands in Task 9 — keeps this file focused on the 2PC
 * skeleton.
 */
fun Application.installCoachRoutes() {
    val coach: CoachService by inject()
    routing {
        post("/coach/reserve") {
            val subjectId = requireSubject() ?: return@post
            val req = call.receive<CoachReserveRequest>()
            when (val r = coach.reserve(subjectId, req)) {
                is CoachServiceReserveResult.Reserved -> call.respond(HttpStatusCode.OK, r.envelope)
                is CoachServiceReserveResult.Rejected -> call.respond(
                    HttpStatusCode.PaymentRequired,
                    CoachReserveRejected(reason = r.reason, capUsd = r.capUsd, spentUsd = r.spentUsd),
                )
            }
        }
        post("/coach/commit") {
            val subjectId = requireSubject() ?: return@post
            val req = call.receive<CoachCommitRequest>()
            val resp = coach.commit(subjectId, req)
            call.respond(HttpStatusCode.OK, resp)
        }
    }
}
```

- [ ] **Step 4: Implement `installCoachRoutesForTest` properly** (replace the `error(...)` stub with a Testcontainer-backed wiring helper modeled after the existing `installSyncRoutes` integration tests under `server/src/test/`)

Inspect: `server/src/test/kotlin/com/dietician/server/routes/SyncRoutesTest.kt` (if present) for the auth-replay pattern.

Adapt the pattern: spin a per-test Postgres container, run migrations, seed the Victor subject row, build a Koin module with `CoachService` + `CoachRepository` + `PiiRedactor` + `BudgetRepository`, install `requireSubject` middleware that returns `authedSubjectId` (or null for the unauthenticated test), then call `installCoachRoutes()`.

- [ ] **Step 5: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.routes.CoachRoutesTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/routes/CoachRoutes.kt \
        server/src/test/kotlin/com/dietician/server/routes/CoachRoutesTest.kt \
        server/src/test/kotlin/com/dietician/server/routes/installCoachRoutesForTest.kt
git commit -m "feat(server): POST /coach/reserve + /coach/commit endpoints with auth + idempotency"
```

---

### Task 9: `installCoachRoutes` — SSE `/coach/stream`

**Files:**
- Modify: `server/src/main/kotlin/com/dietician/server/routes/CoachRoutes.kt`
- Modify: `server/src/main/kotlin/com/dietician/server/coach/CoachService.kt` (add `streamServerRouted` method)
- Test: `server/src/test/kotlin/com/dietician/server/routes/CoachStreamRoutesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.routes

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoachStreamRoutesTest {
    @Test
    fun `POST coach stream emits at least one chunk and a heartbeat then terminates`() = testApplication {
        application { installCoachRoutesForTest(authedSubjectId = "00000000-0000-0000-0000-000000000001", mockLlmText = "Hi Victor.") }
        val resp = client.post("/coach/stream") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"idempotencyKey":"${UUID.randomUUID()}","prompt":"hi","locale":"en"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.headers[HttpHeaders.ContentType]?.contains("text/event-stream") ?: false)
        val body = resp.bodyAsText()
        assertTrue("data:" in body, "expected SSE data: frame, got: $body")
        assertTrue("Hi Victor" in body, "expected mocked LLM text in stream body")
    }

    @Test
    fun `POST coach stream writes audit row on completion`() = testApplication {
        val key = UUID.randomUUID()
        application { installCoachRoutesForTest(authedSubjectId = "00000000-0000-0000-0000-000000000001", mockLlmText = "ok") }
        client.post("/coach/stream") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"idempotencyKey":"$key","prompt":"hi","locale":"en"}""")
        }
        // `installCoachRoutesForTest` exposes a hook to inspect the underlying CoachRepository.
        // Assert: findByIdempotencyKey(key)!!.status == "success" via the helper.
        assertTrue(coachRepoFromTestHelper.findByIdempotencyKey(key)!!.status == "success")
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.routes.CoachStreamRoutesTest"`
Expected: FAIL.

- [ ] **Step 3: Add SSE route to `CoachRoutes.kt`**

Append inside the `routing { ... }` block:

```kotlin
        post("/coach/stream") {
            val subjectId = requireSubject() ?: return@post
            val req = call.receive<com.dietician.server.coach.CoachStreamRequest>()
            call.response.header(HttpHeaders.ContentType, "text/event-stream")
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondBytesWriter {
                coach.streamServerRouted(subjectId, req).collect { chunk ->
                    writeStringUtf8("data: ${chunk}\n\n")
                    flush()
                }
            }
        }
```

Required imports at top of file:

```kotlin
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeStringUtf8
```

- [ ] **Step 4: Add `streamServerRouted` to `CoachService.kt`**

```kotlin
    /**
     * Server-routed SSE flow used by Android + Desktop-non-ClaudeMax fallback.
     * Internally pairs reserve → LlmRouter.streamRoute → commit in one coroutine.
     * Each chunk emitted to the caller carries the text payload only; heartbeat
     * frames are inserted by the route handler, not here.
     */
    fun streamServerRouted(
        subjectId: UUID,
        request: CoachStreamRequest,
    ): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val key = UUID.fromString(request.idempotencyKey)
        val reserved = reserve(
            subjectId,
            CoachReserveRequest(
                idempotencyKey = request.idempotencyKey,
                prompt = request.prompt,
                locale = request.locale,
                provider = "openrouter",  // server-routed Coach defaults to OpenRouter (no ClaudeMax)
                estimatedCostCents = 5,
                reservationTtlSeconds = 60,
            ),
        )
        if (reserved is CoachServiceReserveResult.Rejected) {
            emit("event: error\ndata: ${reserved.reason}")
            return@flow
        }
        // TODO Task 10: route through LlmRouter.streamRoute with mocked-in-test provider.
        // For now, emit a single placeholder chunk and commit success — proves the
        // 2PC pipe end-to-end. Real router wiring lands in Task 10.
        emit("Hi Victor.")
        commit(
            subjectId,
            CoachCommitRequest(
                idempotencyKey = request.idempotencyKey,
                status = "success",
                promptTokens = 0,
                completionTokens = 1,
                costCents = 1,
                provider = "openrouter",
                latencyMs = 50,
                responseHash = "stub",
            ),
        )
    }
```

- [ ] **Step 5: Extend `installCoachRoutesForTest` helper** to accept `mockLlmText` + expose `coachRepoFromTestHelper`. (Implementation pattern follows the existing `SyncRoutesTest` Testcontainer setup.)

- [ ] **Step 6: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.routes.CoachStreamRoutesTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/routes/CoachRoutes.kt \
        server/src/main/kotlin/com/dietician/server/coach/CoachService.kt \
        server/src/test/kotlin/com/dietician/server/routes/CoachStreamRoutesTest.kt \
        server/src/test/kotlin/com/dietician/server/routes/installCoachRoutesForTest.kt
git commit -m "feat(server): POST /coach/stream SSE skeleton + CoachService.streamServerRouted"
```

---

### Task 10: Wire `LlmRouter.streamRoute` into `CoachService.streamServerRouted`

**Files:**
- Modify: `server/src/main/kotlin/com/dietician/server/coach/CoachService.kt`
- Test: `server/src/test/kotlin/com/dietician/server/coach/CoachServiceStreamTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.coach

import com.dietician.server.db.runMigrations
import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.DeviceClass
import com.dietician.shared.llm.LlmChunk
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmStream
import com.dietician.shared.llm.PiiRedactor
import com.dietician.shared.llm.TaskType
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachServiceStreamTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var ds: HikariDataSource
    private val subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeAll
    fun setup() {
        pg = PostgreSQLContainer("pgvector/pgvector:pg16").apply { start() }
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = pg.jdbcUrl; username = pg.username; password = pg.password
        })
        ds.connection.use { c ->
            c.prepareStatement("INSERT INTO subjects (subject_id, display_name) VALUES (?, 'Victor')")
                .apply { setObject(1, subjectId) }.execute()
        }
    }

    @AfterAll fun teardown() { ds.close(); pg.stop() }

    @Test
    fun `streamServerRouted emits chunks from the mocked router and commits success`() = runTest {
        val mockRouter = object : LlmStream {
            override fun streamRoute(request: LlmRequest): Flow<LlmChunk> = flowOf(
                LlmChunk(text = "Eat ", isDone = false),
                LlmChunk(text = "chicken.", isDone = true, tokenCount = 2),
            )
        }
        val service = CoachService(
            repo = CoachRepository(ds),
            budgets = BudgetRepository(ds),
            redactor = PiiRedactor(),
            llmStream = mockRouter,
        )
        val req = CoachStreamRequest(
            idempotencyKey = UUID.randomUUID().toString(),
            prompt = "what to eat for protein",
            locale = "en",
        )
        val chunks = service.streamServerRouted(subjectId, req).toList()
        assertEquals(listOf("Eat ", "chicken."), chunks)
        val row = CoachRepository(ds).findByIdempotencyKey(UUID.fromString(req.idempotencyKey))!!
        assertEquals("success", row.status)
        assertTrue(row.completionTokens!! >= 1)
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachServiceStreamTest"`
Expected: FAIL (`CoachService` constructor missing `llmStream` parameter).

- [ ] **Step 3: Add `llmStream: LlmStream` parameter to `CoachService` and rewire `streamServerRouted`**

Modify `CoachService.kt` constructor to accept `llmStream: com.dietician.shared.llm.LlmStream`. Replace the body of `streamServerRouted` with:

```kotlin
    fun streamServerRouted(
        subjectId: UUID,
        request: CoachStreamRequest,
    ): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val reserved = reserve(
            subjectId,
            CoachReserveRequest(
                idempotencyKey = request.idempotencyKey,
                prompt = request.prompt,
                locale = request.locale,
                provider = "openrouter",
                estimatedCostCents = 5,
                reservationTtlSeconds = 60,
            ),
        )
        if (reserved is CoachServiceReserveResult.Rejected) {
            emit("event: error\ndata: ${reserved.reason}")
            return@flow
        }
        val startMs = System.currentTimeMillis()
        var totalCompletionTokens = 0
        var finalResponse: com.dietician.shared.llm.LlmResponse? = null
        var status = "success"
        try {
            llmStream.streamRoute(
                com.dietician.shared.llm.LlmRequest(
                    subjectId = subjectId.toString(),
                    task = com.dietician.shared.llm.TaskType.TEXT,
                    deviceClass = com.dietician.shared.llm.DeviceClass.ANY,
                    capability = com.dietician.shared.llm.Capability.STREAMING,
                    messages = listOf(
                        com.dietician.shared.llm.LlmMessage(
                            role = com.dietician.shared.llm.Role.USER,
                            content = request.prompt,
                        ),
                    ),
                    systemPrompt = CoachSystemPrompts.forLocale(request.locale),
                ),
            ).collect { chunk ->
                emit(chunk.text)
                if (chunk.tokenCount > 0) totalCompletionTokens = chunk.tokenCount
                if (chunk.isDone) finalResponse = chunk.finalResponse
            }
        } catch (t: Throwable) {
            status = "failed"
            throw t
        } finally {
            commit(
                subjectId,
                CoachCommitRequest(
                    idempotencyKey = request.idempotencyKey,
                    status = status,
                    promptTokens = finalResponse?.inputTokens ?: 0,
                    completionTokens = finalResponse?.outputTokens ?: totalCompletionTokens,
                    costCents = finalResponse?.costCents ?: 0,
                    provider = finalResponse?.provider?.name?.lowercase() ?: "openrouter",
                    latencyMs = System.currentTimeMillis() - startMs,
                    responseHash = finalResponse?.text?.let { sha256(it) } ?: "n/a",
                ),
            )
        }
    }
```

- [ ] **Step 4: Update `LlmModule.kt` to inject `LlmStream` into `CoachService`**

Modify the Koin binding to wire `LlmStream` (Plan-2 `LlmRouterStream`) into `CoachService`.

- [ ] **Step 5: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachServiceStreamTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/coach/CoachService.kt \
        server/src/main/kotlin/com/dietician/server/di/LlmModule.kt \
        server/src/test/kotlin/com/dietician/server/coach/CoachServiceStreamTest.kt
git commit -m "feat(server): wire LlmRouter.streamRoute into CoachService.streamServerRouted"
```

---

### Task 11: SSE heartbeat + 90s idle-timeout on `/coach/stream`

**Files:**
- Modify: `server/src/main/kotlin/com/dietician/server/routes/CoachRoutes.kt`
- Test: `server/src/test/kotlin/com/dietician/server/routes/CoachStreamHeartbeatTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server.routes

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertTrue

class CoachStreamHeartbeatTest {
    @Test
    fun `slow stream emits a heartbeat comment within 25 seconds`() = testApplication {
        // Mock router emits one chunk then pauses for 30 seconds.
        application {
            installCoachRoutesForTest(
                authedSubjectId = "00000000-0000-0000-0000-000000000001",
                mockSlowStreamChunks = listOf(
                    "Eat" to 0L,           // immediate
                    "chicken." to 30_000L  // after 30s pause
                ),
            )
        }
        val resp = client.post("/coach/stream") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"idempotencyKey":"${UUID.randomUUID()}","prompt":"hi","locale":"en"}""")
        }
        val body = resp.bodyAsText()
        // Heartbeat frames begin with ':' per SSE spec — comment lines ignored by clients.
        assertTrue(": heartbeat" in body, "expected ': heartbeat' comment frame")
    }

    @Test
    fun `stream that stalls beyond 90 seconds idle-timeout closes`() = testApplication {
        application {
            installCoachRoutesForTest(
                authedSubjectId = "00000000-0000-0000-0000-000000000001",
                mockSlowStreamChunks = listOf("Eat" to 0L, "chicken." to 120_000L),
            )
        }
        // The test client should observe the stream closing (response body ends)
        // without the second chunk landing.
        val resp = client.post("/coach/stream") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"idempotencyKey":"${UUID.randomUUID()}","prompt":"hi","locale":"en"}""")
        }
        val body = resp.bodyAsText()
        assertTrue("Eat" in body && "chicken" !in body, "expected first chunk only, idle-timeout fires")
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.routes.CoachStreamHeartbeatTest"`
Expected: FAIL.

- [ ] **Step 3: Rewrite the `/coach/stream` handler with heartbeat + timeout**

Replace the SSE handler body with:

```kotlin
        post("/coach/stream") {
            val subjectId = requireSubject() ?: return@post
            val req = call.receive<com.dietician.server.coach.CoachStreamRequest>()
            call.response.header(HttpHeaders.ContentType, "text/event-stream")
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondBytesWriter {
                val heartbeatScope = kotlinx.coroutines.CoroutineScope(coroutineContext)
                val heartbeatJob = heartbeatScope.launch {
                    while (true) {
                        kotlinx.coroutines.delay(25_000L)
                        writeStringUtf8(": heartbeat\n\n")
                        flush()
                    }
                }
                try {
                    kotlinx.coroutines.withTimeout(90_000L) {
                        coach.streamServerRouted(subjectId, req).collect { chunk ->
                            writeStringUtf8("data: $chunk\n\n")
                            flush()
                        }
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    writeStringUtf8("event: timeout\ndata: idle-timeout\n\n")
                    flush()
                } finally {
                    heartbeatJob.cancel()
                }
            }
        }
```

- [ ] **Step 4: Extend the test helper** to support `mockSlowStreamChunks: List<Pair<String, Long>>` driving the mock router with per-chunk delays.

- [ ] **Step 5: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.routes.CoachStreamHeartbeatTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/routes/CoachRoutes.kt \
        server/src/test/kotlin/com/dietician/server/routes/CoachStreamHeartbeatTest.kt \
        server/src/test/kotlin/com/dietician/server/routes/installCoachRoutesForTest.kt
git commit -m "feat(server): /coach/stream 25s heartbeat + 90s idle-timeout (council R3 mitigation)"
```

---

### Task 12: Register `installCoachRoutes()` + `CoachOrphanCleanupCron` in `Application.kt` + `CronBootstrap`

**Files:**
- Modify: `server/src/main/kotlin/com/dietician/server/Application.kt`
- Modify: `server/src/main/kotlin/com/dietician/server/cron/CronBootstrap.kt`
- Test: `server/src/test/kotlin/com/dietician/server/ApplicationCoachWiringTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.server

import com.dietician.server.routes.installCoachRoutes
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals

class ApplicationCoachWiringTest {
    @Test
    fun `coach reserve route is mounted by Application module`() = testApplication {
        environment { config = io.ktor.server.config.MapApplicationConfig() }
        application { /* Application.module() — this will fail if installCoachRoutes is missing */ }
        // OPTIONS / probe on the new path: 404 if not mounted, 401 / 405 / 200 if mounted.
        val resp = client.get("/coach/reserve")
        assertNotEquals(HttpStatusCode.NotFound, resp.status, "/coach/reserve must be mounted by Application.module()")
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :server:test --tests "com.dietician.server.ApplicationCoachWiringTest"`
Expected: FAIL.

- [ ] **Step 3: Register the route + cron**

Edit `Application.kt` and add `installCoachRoutes()` to the list of installer calls. Edit `CronBootstrap.kt` (or wherever crons are registered at boot) to register `CoachOrphanCleanupCron` with a 30-second period.

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :server:test --tests "com.dietician.server.ApplicationCoachWiringTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/dietician/server/Application.kt \
        server/src/main/kotlin/com/dietician/server/cron/CronBootstrap.kt \
        server/src/test/kotlin/com/dietician/server/ApplicationCoachWiringTest.kt
git commit -m "chore(server): mount installCoachRoutes() + register CoachOrphanCleanupCron@30s"
```

---

### Task 13: Smoke deploy to VPS + verify Coach routes alive

**Files:**
- No file changes.

- [ ] **Step 1: Build fat-JAR**

```bash
cd "C:/Users/User/Desktop/Dietician"
./gradlew :server:buildFatJar -Dorg.gradle.jvmargs="-Xmx1g -XX:MaxMetaspaceSize=384m"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: SCP to VPS + chown + restart**

```bash
scp "C:/Users/User/Desktop/Dietician/server/build/libs/dietician-server.jar" \
    root@46.247.109.91:/opt/dietician/lib/dietician-server.jar
ssh root@46.247.109.91 "chown dietician:dietician /opt/dietician/lib/dietician-server.jar && \
                        systemctl restart dietician-backend && \
                        sleep 5 && \
                        systemctl is-active dietician-backend"
```
Expected: `active`.

- [ ] **Step 3: Smoke `/coach/reserve` against the live backend with the test subject's session**

```bash
ssh root@46.247.109.91 "curl -s -w 'HTTP=%{http_code}\n' \
    -H 'Content-Type: application/json' \
    -b 'dietician_session=<known-test-session>' \
    -d '{\"idempotencyKey\":\"$(uuidgen)\",\"prompt\":\"hi\",\"locale\":\"en\",\"provider\":\"openrouter\",\"estimatedCostCents\":5,\"reservationTtlSeconds\":60}' \
    http://100.101.47.77:8081/coach/reserve | head -c 300"
```
Expected: `HTTP=200` + a JSON body with `reservationId` + `auditId`.

- [ ] **Step 4: Verify Flyway applied V022**

```bash
ssh root@46.247.109.91 "sudo -u postgres psql -d dietician -c \
    'SELECT installed_rank, version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;'"
```
Expected: `V022` near the top with `success=true` (or BASELINE + V022 if this is the very first post-baseline migration to land).

- [ ] **Step 5: Update BRIDGE.md handoff note** (the wrap skill will land this at session end — just verify the deploy in chat)

---

## ⏸️ COUNCIL GATE 1 — Post server-foundation impl

Convene a 5-agent post-impl council on Tasks 0-13 (V022 + CoachService + CoachRoutes + cron + deploy). Scope: did the server-side 2PC pipe land cleanly? Are the SSE heartbeat + idle-timeout correctly wired? Is the orphan cleanup cron actually firing in production? Are the integration tests representative of the production failure modes? Block downstream tasks until the verdict is APPROVED or FLAWED-with-clear-fix.

If FLAWED, fix the must-fix list before proceeding to Phase B.

---

## Phase B — Client gateway + SqlDelight outbox + Desktop impl

### Task 14: SqlDelight `0009_audit_pending_outbox.sq`

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/dietician/shared/data/sql/0009_audit_pending_outbox.sq`
- Test: `shared/src/desktopTest/kotlin/com/dietician/shared/data/sql/AuditPendingOutboxQueriesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.shared.data.sql

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.sql.`0009_audit_pending_outboxQueries`
import com.dietician.shared.DieticianDatabase
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class AuditPendingOutboxQueriesTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        DieticianDatabase.Schema.create(it)
    }
    private val db = DieticianDatabase(driver)

    @Test
    fun `insertOutboxRow stores idempotency key + prompt hash`() {
        val key = UUID.randomUUID().toString()
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow(
            idempotency_key = key,
            reservation_id = null,
            prompt_hash = "abc",
            started_at_ms = 1_780_000_000_000L,
            last_attempt_at_ms = 1_780_000_000_000L,
            attempts = 0L,
            provider = "claudemax",
        )
        val row = db.`0009_audit_pending_outboxQueries`.findByKey(key).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("claudemax", row.provider)
    }

    @Test
    fun `markCommitted deletes the row`() {
        val key = UUID.randomUUID().toString()
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow(key, null, "abc", 0L, 0L, 0L, "claudemax")
        db.`0009_audit_pending_outboxQueries`.markCommitted(key)
        assertNull(db.`0009_audit_pending_outboxQueries`.findByKey(key).executeAsOneOrNull())
    }

    @Test
    fun `findUncommitted returns rows ordered oldest-first`() {
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow("k1", null, "a", 100L, 100L, 0L, "claudemax")
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow("k2", null, "b", 50L, 50L, 0L, "claudemax")
        val rows = db.`0009_audit_pending_outboxQueries`.findUncommitted().executeAsList()
        assertEquals(listOf("k2", "k1"), rows.map { it.idempotency_key })
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.data.sql.AuditPendingOutboxQueriesTest"`
Expected: FAIL.

- [ ] **Step 3: Write the SqlDelight schema file**

Create `shared/src/commonMain/sqldelight/com/dietician/shared/data/sql/0009_audit_pending_outbox.sq`:

```sql
-- iter-11 — client-side outbox for the 2-phase commit Coach pipeline.
--
-- Desktop writes one row at /coach/reserve time. After ClaudeMax CLI returns + the
-- /coach/commit POST is ACK'd, the row is deleted via markCommitted. On desktop
-- startup, DesktopOutboxReplay reads findUncommitted() and re-POSTs /coach/commit
-- with the persisted idempotency key — server is idempotent on commit.

CREATE TABLE audit_pending_outbox (
    idempotency_key      TEXT NOT NULL PRIMARY KEY,
    reservation_id       TEXT,
    prompt_hash          TEXT NOT NULL,
    started_at_ms        INTEGER NOT NULL,
    last_attempt_at_ms   INTEGER NOT NULL,
    attempts             INTEGER NOT NULL DEFAULT 0,
    provider             TEXT NOT NULL
);

CREATE INDEX idx_audit_pending_outbox_started ON audit_pending_outbox (started_at_ms);

insertOutboxRow:
INSERT INTO audit_pending_outbox
    (idempotency_key, reservation_id, prompt_hash, started_at_ms, last_attempt_at_ms, attempts, provider)
VALUES (?, ?, ?, ?, ?, ?, ?);

findByKey:
SELECT * FROM audit_pending_outbox WHERE idempotency_key = ?;

findUncommitted:
SELECT * FROM audit_pending_outbox ORDER BY started_at_ms ASC;

markCommitted:
DELETE FROM audit_pending_outbox WHERE idempotency_key = ?;

bumpAttempt:
UPDATE audit_pending_outbox
SET attempts = attempts + 1, last_attempt_at_ms = ?
WHERE idempotency_key = ?;
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.data.sql.AuditPendingOutboxQueriesTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/sqldelight/com/dietician/shared/data/sql/0009_audit_pending_outbox.sq \
        shared/src/desktopTest/kotlin/com/dietician/shared/data/sql/AuditPendingOutboxQueriesTest.kt
git commit -m "feat(shared): SqlDelight audit_pending_outbox for desktop 2PC replay-on-startup"
```

---

### Task 15: `CoachLocale` + `CoachLlmGateway` interface

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLocale.kt`
- Create: `shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLlmGateway.kt`
- Test: `shared/src/commonTest/kotlin/com/dietician/shared/llm/CoachLlmGatewayContractTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.shared.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachLlmGatewayContractTest {
    @Test
    fun `CoachLocale enum exposes EN and RO`() {
        assertEquals(setOf(CoachLocale.EN, CoachLocale.RO), CoachLocale.entries.toSet())
    }

    @Test
    fun `CoachLlmGateway implementation can stream chunks`() = runTest {
        val gw = object : CoachLlmGateway {
            override fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk> =
                flowOf(LlmChunk("hi", isDone = true))
        }
        val out = gw.streamCoachTurn("x", CoachLocale.EN).toList()
        assertEquals(1, out.size)
        assertEquals("hi", out.first().text)
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.CoachLlmGatewayContractTest"`
Expected: FAIL.

- [ ] **Step 3: Write `CoachLocale.kt` + `CoachLlmGateway.kt`**

```kotlin
// shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLocale.kt
package com.dietician.shared.llm

/**
 * iter-11 — locale enum routed into the server-side CoachSystemPrompts selector.
 * Romanian copy in the server uses comma-below ș/ț (U+0219 / U+021B).
 */
enum class CoachLocale {
    EN, RO;

    fun wire(): String = name.lowercase()
}
```

```kotlin
// shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLlmGateway.kt
package com.dietician.shared.llm

import kotlinx.coroutines.flow.Flow

/**
 * iter-11 — platform-keyed strategy for Coach turn streaming.
 *
 * Desktop impl runs ClaudeMaxCliProvider locally + 2PC reserve/commit HTTP calls
 * around the subprocess. Android impl is a thin SSE consumer hitting
 * /coach/stream which internally handles 2PC server-side.
 *
 * The interface returns a Flow<LlmChunk> so it can adapt cleanly into the existing
 * LlmStream interface used by UiModule.kt:89 via CoachLlmGatewayLlmStream.
 */
interface CoachLlmGateway {
    fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk>
}
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.CoachLlmGatewayContractTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLocale.kt \
        shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLlmGateway.kt \
        shared/src/commonTest/kotlin/com/dietician/shared/llm/CoachLlmGatewayContractTest.kt
git commit -m "feat(shared:llm): CoachLocale + CoachLlmGateway interface (iter-11)"
```

---

### Task 16: `CoachLlmGatewayLlmStream` adapter

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLlmGatewayLlmStream.kt`
- Test: `shared/src/commonTest/kotlin/com/dietician/shared/llm/CoachLlmGatewayLlmStreamTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.shared.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachLlmGatewayLlmStreamTest {
    @Test
    fun `streamRoute delegates to gateway with user message text`() = runTest {
        var capturedPrompt = ""
        val gw = object : CoachLlmGateway {
            override fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk> {
                capturedPrompt = prompt
                return flowOf(LlmChunk("ok", isDone = true))
            }
        }
        val adapter = CoachLlmGatewayLlmStream(gateway = gw, localeProvider = { CoachLocale.EN })
        val req = LlmRequest(
            subjectId = "s",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.ANY,
            capability = Capability.STREAMING,
            messages = listOf(LlmMessage(Role.USER, "what to eat for protein")),
        )
        val out = adapter.streamRoute(req).toList()
        assertEquals("what to eat for protein", capturedPrompt)
        assertEquals("ok", out.first().text)
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.CoachLlmGatewayLlmStreamTest"`
Expected: FAIL.

- [ ] **Step 3: Write the adapter**

```kotlin
// shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLlmGatewayLlmStream.kt
package com.dietician.shared.llm

import kotlinx.coroutines.flow.Flow

/**
 * iter-11 — bridges a [CoachLlmGateway] (platform-keyed Coach surface) into the
 * existing [LlmStream] interface consumed by UiModule.kt:89.
 *
 * Extracts the latest USER message from the [LlmRequest] and forwards it to the
 * gateway. Locale comes from [localeProvider] (typically wired to SettingsStore).
 */
class CoachLlmGatewayLlmStream(
    private val gateway: CoachLlmGateway,
    private val localeProvider: () -> CoachLocale,
) : LlmStream {
    override fun streamRoute(request: LlmRequest): Flow<LlmChunk> {
        val userPrompt = request.messages.lastOrNull { it.role == Role.USER }?.content
            ?: error("CoachLlmGatewayLlmStream: no USER message in request")
        return gateway.streamCoachTurn(userPrompt, localeProvider())
    }
}
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.CoachLlmGatewayLlmStreamTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dietician/shared/llm/CoachLlmGatewayLlmStream.kt \
        shared/src/commonTest/kotlin/com/dietician/shared/llm/CoachLlmGatewayLlmStreamTest.kt
git commit -m "feat(shared:llm): CoachLlmGatewayLlmStream adapter for UiModule wiring"
```

---

### Task 17: `CoachHttpClient` — `/coach/reserve` + `/coach/commit` + `/coach/stream` SSE

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dietician/shared/llm/net/CoachHttpClient.kt`
- Test: `shared/src/desktopTest/kotlin/com/dietician/shared/llm/net/CoachHttpClientTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.shared.llm.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoachHttpClientTest {
    private fun client(handler: io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): HttpClient =
        HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    @Test
    fun `reserve posts JSON and returns parsed envelope`() = runTest {
        val http = client {
            respond(
                content = """{"reservationId":"r","auditId":"a","redactedPromptHash":"h","reservedUntilEpochMs":1780000000000}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val c = CoachHttpClient(http, baseUrl = "https://test")
        val resp = c.reserve(idempotencyKey = "k", prompt = "p", locale = "en", provider = "claudemax", estimatedCostCents = 5, reservationTtlSeconds = 60)
        assertEquals("a", resp.auditId)
    }

    @Test
    fun `commit posts JSON and returns response`() = runTest {
        val http = client {
            respond(
                """{"auditId":"a","status":"success"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val c = CoachHttpClient(http, baseUrl = "https://test")
        val resp = c.commit("k", "success", 10, 20, 4, "claudemax", 2200, "h")
        assertEquals("success", resp.status)
    }

    @Test
    fun `stream parses SSE data frames into chunks`() = runTest {
        val http = client {
            respond(
                content = "data: hello\n\ndata:  world\n\n",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
            )
        }
        val c = CoachHttpClient(http, baseUrl = "https://test")
        val chunks = c.stream("k", "p", "en").toList()
        assertEquals(listOf("hello", " world"), chunks)
    }

    @Test
    fun `stream ignores heartbeat comment frames`() = runTest {
        val http = client {
            respond(
                content = ": heartbeat\n\ndata: hello\n\n",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
            )
        }
        val c = CoachHttpClient(http, baseUrl = "https://test")
        val chunks = c.stream("k", "p", "en").toList()
        assertEquals(listOf("hello"), chunks)
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.net.CoachHttpClientTest"`
Expected: FAIL.

- [ ] **Step 3: Write `CoachHttpClient.kt`**

```kotlin
// shared/src/commonMain/kotlin/com/dietician/shared/llm/net/CoachHttpClient.kt
package com.dietician.shared.llm.net

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/**
 * iter-11 — HTTP wrapper for /coach/reserve, /coach/commit, /coach/stream.
 *
 * The SSE parser is minimal but correct: it strips `data: ` prefixes from data
 * frames, ignores `: ...` comment frames (server heartbeat) and `event: ...`
 * frames, and terminates on the stream-close.
 */
class CoachHttpClient(
    private val http: HttpClient,
    private val baseUrl: String,
) {
    @Serializable
    private data class ReserveBody(
        val idempotencyKey: String,
        val prompt: String,
        val locale: String,
        val provider: String,
        val estimatedCostCents: Int,
        val reservationTtlSeconds: Int,
    )

    @Serializable
    data class ReserveResponse(
        val reservationId: String,
        val auditId: String,
        val redactedPromptHash: String,
        val reservedUntilEpochMs: Long,
    )

    @Serializable
    private data class CommitBody(
        val idempotencyKey: String,
        val status: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val costCents: Int,
        val provider: String,
        val latencyMs: Long,
        val responseHash: String,
    )

    @Serializable
    data class CommitResponse(val auditId: String, val status: String)

    @Serializable
    private data class StreamBody(val idempotencyKey: String, val prompt: String, val locale: String)

    suspend fun reserve(
        idempotencyKey: String,
        prompt: String,
        locale: String,
        provider: String,
        estimatedCostCents: Int,
        reservationTtlSeconds: Int,
    ): ReserveResponse {
        val resp = http.post("$baseUrl/coach/reserve") {
            contentType(ContentType.Application.Json)
            setBody(ReserveBody(idempotencyKey, prompt, locale, provider, estimatedCostCents, reservationTtlSeconds))
        }
        return resp.body()
    }

    suspend fun commit(
        idempotencyKey: String,
        status: String,
        promptTokens: Int,
        completionTokens: Int,
        costCents: Int,
        provider: String,
        latencyMs: Long,
        responseHash: String,
    ): CommitResponse {
        val resp = http.post("$baseUrl/coach/commit") {
            contentType(ContentType.Application.Json)
            setBody(CommitBody(idempotencyKey, status, promptTokens, completionTokens, costCents, provider, latencyMs, responseHash))
        }
        return resp.body()
    }

    fun stream(idempotencyKey: String, prompt: String, locale: String): Flow<String> = flow {
        val resp = http.post("$baseUrl/coach/stream") {
            contentType(ContentType.Application.Json)
            setBody(StreamBody(idempotencyKey, prompt, locale))
        }
        val channel = resp.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.isBlank()) continue
            if (line.startsWith(":")) continue        // SSE comment frame (heartbeat)
            if (line.startsWith("event:")) continue   // non-data event (timeout, error)
            if (line.startsWith("data:")) {
                emit(line.removePrefix("data:").let { if (it.startsWith(" ")) it.drop(1) else it })
            }
        }
    }
}
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.net.CoachHttpClientTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dietician/shared/llm/net/CoachHttpClient.kt \
        shared/src/desktopTest/kotlin/com/dietician/shared/llm/net/CoachHttpClientTest.kt
git commit -m "feat(shared:llm:net): CoachHttpClient for /coach/reserve + /coach/commit + SSE /coach/stream"
```

---

### Task 18: `DesktopCoachLlmGateway`

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/dietician/shared/llm/DesktopCoachLlmGateway.kt`
- Test: `shared/src/desktopTest/kotlin/com/dietician/shared/llm/DesktopCoachLlmGatewayTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.shared.llm

import com.dietician.shared.DieticianDatabase
import com.dietician.shared.llm.net.CoachHttpClient
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopCoachLlmGatewayTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        DieticianDatabase.Schema.create(it)
    }
    private val db = DieticianDatabase(driver)

    private val mockHttp = object : CoachHttpClientLike {
        var reserveCalls = 0
        var commitCalls = 0
        var lastCommitStatus: String? = null
        override suspend fun reserve(idempotencyKey: String, prompt: String, locale: String, provider: String, estimatedCostCents: Int, reservationTtlSeconds: Int): CoachHttpClient.ReserveResponse {
            reserveCalls += 1
            return CoachHttpClient.ReserveResponse("r", "a", "h", 1L)
        }
        override suspend fun commit(idempotencyKey: String, status: String, promptTokens: Int, completionTokens: Int, costCents: Int, provider: String, latencyMs: Long, responseHash: String): CoachHttpClient.CommitResponse {
            commitCalls += 1; lastCommitStatus = status
            return CoachHttpClient.CommitResponse("a", status)
        }
    }
    private val mockProvider = object : DesktopLocalProvider {
        override fun stream(prompt: String, locale: CoachLocale): Flow<LlmChunk> =
            flowOf(LlmChunk("Eat ", isDone = false), LlmChunk("chicken.", isDone = true, tokenCount = 2))
    }

    @Test
    fun `gateway writes outbox row, runs ClaudeMax locally, posts commit, removes outbox row`() = runTest {
        val gw = DesktopCoachLlmGateway(db = db, http = mockHttp, provider = mockProvider, uuid = { "k1" })
        val chunks = gw.streamCoachTurn("hi", CoachLocale.EN).toList()
        assertEquals(listOf("Eat ", "chicken."), chunks.map { it.text })
        assertEquals(1, mockHttp.reserveCalls)
        assertEquals(1, mockHttp.commitCalls)
        assertEquals("success", mockHttp.lastCommitStatus)
        assertNull(db.`0009_audit_pending_outboxQueries`.findByKey("k1").executeAsOneOrNull())
    }

    @Test
    fun `gateway marks outbox status failed if provider throws mid-stream`() = runTest {
        val failingProvider = object : DesktopLocalProvider {
            override fun stream(prompt: String, locale: CoachLocale): Flow<LlmChunk> = kotlinx.coroutines.flow.flow {
                emit(LlmChunk("Eat ", isDone = false))
                throw RuntimeException("ClaudeMax crashed")
            }
        }
        val gw = DesktopCoachLlmGateway(db = db, http = mockHttp, provider = failingProvider, uuid = { "k2" })
        runCatching { gw.streamCoachTurn("hi", CoachLocale.EN).toList() }
        assertEquals("failed", mockHttp.lastCommitStatus)
        assertNull(db.`0009_audit_pending_outboxQueries`.findByKey("k2").executeAsOneOrNull())
    }
}

interface CoachHttpClientLike {
    suspend fun reserve(idempotencyKey: String, prompt: String, locale: String, provider: String, estimatedCostCents: Int, reservationTtlSeconds: Int): CoachHttpClient.ReserveResponse
    suspend fun commit(idempotencyKey: String, status: String, promptTokens: Int, completionTokens: Int, costCents: Int, provider: String, latencyMs: Long, responseHash: String): CoachHttpClient.CommitResponse
}

interface DesktopLocalProvider {
    fun stream(prompt: String, locale: CoachLocale): Flow<LlmChunk>
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.DesktopCoachLlmGatewayTest"`
Expected: FAIL.

- [ ] **Step 3: Write `DesktopCoachLlmGateway.kt`**

```kotlin
// shared/src/desktopMain/kotlin/com/dietician/shared/llm/DesktopCoachLlmGateway.kt
package com.dietician.shared.llm

import com.dietician.shared.DieticianDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * iter-11 — Desktop ClaudeMax flow. Sequence:
 *   1. Generate UUIDv4 idempotency key
 *   2. Write audit_pending_outbox row BEFORE provider invocation
 *   3. POST /coach/reserve
 *   4. Run ClaudeMaxCliProvider.stream() locally; emit chunks to UI
 *   5. POST /coach/commit with usage
 *   6. Delete outbox row
 *
 * On provider failure mid-stream: commit with status='failed', delete outbox.
 * On desktop crash between (4) and (5): DesktopOutboxReplay picks up next start.
 */
class DesktopCoachLlmGateway(
    private val db: DieticianDatabase,
    private val http: CoachHttpClientLike,
    private val provider: DesktopLocalProvider,
    private val uuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : CoachLlmGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk> = flow {
        val key = uuid()
        val now = clock()
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow(
            idempotency_key = key,
            reservation_id = null,
            prompt_hash = prompt.hashCode().toString(),
            started_at_ms = now,
            last_attempt_at_ms = now,
            attempts = 0L,
            provider = "claudemax",
        )
        val reserved = http.reserve(
            idempotencyKey = key, prompt = prompt, locale = locale.wire(),
            provider = "claudemax", estimatedCostCents = 5, reservationTtlSeconds = 60,
        )
        val startMs = clock()
        var totalCompletionTokens = 0
        var status = "success"
        try {
            provider.stream(prompt, locale).collect { chunk ->
                emit(chunk)
                if (chunk.tokenCount > 0) totalCompletionTokens = chunk.tokenCount
            }
        } catch (t: Throwable) {
            status = "failed"
            log.warn("ClaudeMax stream failed for key {}: {}", key, t.message)
            throw t
        } finally {
            http.commit(
                idempotencyKey = key, status = status,
                promptTokens = 0, completionTokens = totalCompletionTokens, costCents = 0,
                provider = "claudemax",
                latencyMs = clock() - startMs, responseHash = "n/a",
            )
            db.`0009_audit_pending_outboxQueries`.markCommitted(key)
        }
    }
}
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.DesktopCoachLlmGatewayTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/dietician/shared/llm/DesktopCoachLlmGateway.kt \
        shared/src/desktopTest/kotlin/com/dietician/shared/llm/DesktopCoachLlmGatewayTest.kt
git commit -m "feat(shared:desktop): DesktopCoachLlmGateway — ClaudeMax local + 2PC bookend"
```

---

### Task 19: `DesktopOutboxReplay`

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/dietician/shared/llm/DesktopOutboxReplay.kt`
- Test: `shared/src/desktopTest/kotlin/com/dietician/shared/llm/DesktopOutboxReplayTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.shared.llm

import com.dietician.shared.DieticianDatabase
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopOutboxReplayTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        DieticianDatabase.Schema.create(it)
    }
    private val db = DieticianDatabase(driver)

    @Test
    fun `replayPending posts commit for each outbox row + removes them`() = runTest {
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow("kA", null, "h", 0L, 0L, 0L, "claudemax")
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow("kB", null, "h", 0L, 0L, 0L, "claudemax")

        val commits = mutableListOf<String>()
        val http = object : CoachHttpClientLike {
            override suspend fun reserve(idempotencyKey: String, prompt: String, locale: String, provider: String, estimatedCostCents: Int, reservationTtlSeconds: Int) = error("not used")
            override suspend fun commit(idempotencyKey: String, status: String, promptTokens: Int, completionTokens: Int, costCents: Int, provider: String, latencyMs: Long, responseHash: String): com.dietician.shared.llm.net.CoachHttpClient.CommitResponse {
                commits += idempotencyKey
                return com.dietician.shared.llm.net.CoachHttpClient.CommitResponse(idempotencyKey, "orphaned")
            }
        }
        DesktopOutboxReplay(db, http).replayPending()
        assertEquals(listOf("kA", "kB"), commits)
        assertNull(db.`0009_audit_pending_outboxQueries`.findByKey("kA").executeAsOneOrNull())
        assertNull(db.`0009_audit_pending_outboxQueries`.findByKey("kB").executeAsOneOrNull())
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.DesktopOutboxReplayTest"`
Expected: FAIL.

- [ ] **Step 3: Write `DesktopOutboxReplay.kt`**

```kotlin
// shared/src/desktopMain/kotlin/com/dietician/shared/llm/DesktopOutboxReplay.kt
package com.dietician.shared.llm

import com.dietician.shared.DieticianDatabase
import org.slf4j.LoggerFactory

/**
 * iter-11 — replay-on-startup for outbox rows that didn't receive a /coach/commit
 * ACK before desktop shut down. Re-POSTs commit with status='orphaned' so the
 * server-side audit row flips out of 'pending' and the budget reservation is
 * released. Server is idempotent on commit so duplicate calls are safe.
 */
class DesktopOutboxReplay(
    private val db: DieticianDatabase,
    private val http: CoachHttpClientLike,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun replayPending() {
        val rows = db.`0009_audit_pending_outboxQueries`.findUncommitted().executeAsList()
        if (rows.isEmpty()) return
        log.info("DesktopOutboxReplay: {} pending row(s) to reconcile", rows.size)
        rows.forEach { row ->
            runCatching {
                http.commit(
                    idempotencyKey = row.idempotency_key,
                    status = "orphaned",
                    promptTokens = 0, completionTokens = 0, costCents = 0,
                    provider = row.provider,
                    latencyMs = 0,
                    responseHash = "replay",
                )
                db.`0009_audit_pending_outboxQueries`.markCommitted(row.idempotency_key)
            }.onFailure { log.warn("replay failed for {}: {}", row.idempotency_key, it.message) }
        }
    }
}
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.DesktopOutboxReplayTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/dietician/shared/llm/DesktopOutboxReplay.kt \
        shared/src/desktopTest/kotlin/com/dietician/shared/llm/DesktopOutboxReplayTest.kt
git commit -m "feat(shared:desktop): DesktopOutboxReplay — startup reconcile of uncommitted reserves"
```

---

### Task 20: `AndroidCoachLlmGateway` (server-routed SSE)

**Files:**
- Create: `shared/src/androidMain/kotlin/com/dietician/shared/llm/AndroidCoachLlmGateway.kt`
- Test: Manual smoke against live `/coach/stream` (no Android unit test — SSE is exercised in `CoachHttpClientTest` already).

- [ ] **Step 1: Write `AndroidCoachLlmGateway.kt`**

```kotlin
// shared/src/androidMain/kotlin/com/dietician/shared/llm/AndroidCoachLlmGateway.kt
package com.dietician.shared.llm

import com.dietician.shared.llm.net.CoachHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * iter-11 — Android Coach gateway. Pure SSE consumer hitting /coach/stream;
 * server handles reserve+commit internally. No ClaudeMax on Android.
 */
class AndroidCoachLlmGateway(
    private val http: CoachHttpClient,
    private val uuid: () -> String = { UUID.randomUUID().toString() },
) : CoachLlmGateway {
    override fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk> {
        val key = uuid()
        return http.stream(idempotencyKey = key, prompt = prompt, locale = locale.wire())
            .map { LlmChunk(text = it, isDone = false) }
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/androidMain/kotlin/com/dietician/shared/llm/AndroidCoachLlmGateway.kt
git commit -m "feat(shared:android): AndroidCoachLlmGateway — thin SSE consumer of /coach/stream"
```

---

## ⏸️ COUNCIL GATE 2 — Post client gateway impl

Convene a 5-agent post-impl council on Tasks 14-20 (SqlDelight outbox + gateway interface + adapter + HTTP client + Desktop + Android impls). Scope: does the desktop outbox cover the crash-mid-call case? Is the SSE parser correct under heartbeat + comment frames? Does the idempotency contract hold across reserve / commit / replay? Block downstream UiModule swap until the verdict is APPROVED or FLAWED-with-clear-fix.

---

## Phase C — Wiring + spec amendment + integration tests + runbook

### Task 21: Wire `CoachLlmGateway` in `desktopPlatformModule` + `androidPlatformModule`

**Files:**
- Modify: `shared/src/desktopMain/kotlin/com/dietician/shared/data/DataModule.desktop.kt`
- Modify: `shared/src/androidMain/kotlin/com/dietician/shared/data/DataModule.android.kt`
- Test: `shared/src/desktopTest/kotlin/com/dietician/shared/llm/PlatformGatewayWiringTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.shared.llm

import com.dietician.shared.data.desktopPlatformModule
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertTrue

class PlatformGatewayWiringTest : KoinTest {
    private val gateway: CoachLlmGateway by inject()

    @BeforeTest fun setup() {
        startKoin { modules(desktopPlatformModule, /* test-overrides for HTTP + db */) }
    }
    @AfterTest fun teardown() { stopKoin() }

    @Test fun `desktop platform module provides DesktopCoachLlmGateway`() {
        assertTrue(gateway is DesktopCoachLlmGateway, "got: ${gateway::class}")
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.PlatformGatewayWiringTest"`
Expected: FAIL.

- [ ] **Step 3: Edit `DataModule.desktop.kt`**

Add to the `desktopPlatformModule`:

```kotlin
    single<CoachLlmGateway> {
        DesktopCoachLlmGateway(
            db = get(),
            http = get<CoachHttpClient>().let { httpAdapter(it) },
            provider = get<com.dietician.shared.llm.provider.ClaudeMaxCliProvider>().let { providerAdapter(it) },
        )
    }
    single { CoachHttpClient(http = get(), baseUrl = get<com.dietician.shared.ui.network.BaseUrlProvider>().baseUrl()) }
```

(`httpAdapter` + `providerAdapter` are tiny inline wrappers turning the concrete classes into the `CoachHttpClientLike` + `DesktopLocalProvider` interfaces used by the gateway. Land them as private helper functions in the same file.)

Similarly edit `DataModule.android.kt`:

```kotlin
    single<CoachLlmGateway> { AndroidCoachLlmGateway(http = get()) }
    single { CoachHttpClient(http = get(), baseUrl = get<com.dietician.shared.ui.network.BaseUrlProvider>().baseUrl()) }
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.PlatformGatewayWiringTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/dietician/shared/data/DataModule.desktop.kt \
        shared/src/androidMain/kotlin/com/dietician/shared/data/DataModule.android.kt \
        shared/src/desktopTest/kotlin/com/dietician/shared/llm/PlatformGatewayWiringTest.kt
git commit -m "chore(shared): platform modules provide CoachLlmGateway + CoachHttpClient"
```

---

### Task 22: Swap `StubLlmStream` → `CoachLlmGatewayLlmStream` in `UiModule.kt:89`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dietician/shared/ui/di/UiModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dietician/shared/ui/di/UiModuleNoStubLlmStreamTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dietician.shared.ui.di

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class UiModuleNoStubLlmStreamTest {
    @Test
    fun `UiModule does not declare StubLlmStream private class`() {
        val source = java.io.File("shared/src/commonMain/kotlin/com/dietician/shared/ui/di/UiModule.kt").readText()
        assertTrue("StubLlmStream" !in source,
            "iter-11 must delete the private StubLlmStream class; source still contains the symbol")
    }

    @Test
    fun `UiModule binds LlmStream via CoachLlmGatewayLlmStream`() {
        val source = java.io.File("shared/src/commonMain/kotlin/com/dietician/shared/ui/di/UiModule.kt").readText()
        assertTrue("CoachLlmGatewayLlmStream" in source,
            "UiModule must wire CoachLlmGatewayLlmStream as the LlmStream impl")
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.ui.di.UiModuleNoStubLlmStreamTest"`
Expected: FAIL.

- [ ] **Step 3: Edit `UiModule.kt`**

Change line 89:

```kotlin
    single<LlmStream> { StubLlmStream() }
```

to:

```kotlin
    single<LlmStream> {
        CoachLlmGatewayLlmStream(
            gateway = get(),
            localeProvider = {
                if (get<SettingsStore>().state.value.locale == "ro") CoachLocale.RO else CoachLocale.EN
            },
        )
    }
```

Delete the `private class StubLlmStream : LlmStream { ... }` declaration (lines 140-149) and remove the now-unused `import kotlinx.coroutines.flow.Flow` + `import kotlinx.coroutines.flow.flowOf` + `import com.dietician.shared.llm.LlmChunk` + `import com.dietician.shared.llm.LlmRequest` imports if they're no longer referenced.

Add the new imports:

```kotlin
import com.dietician.shared.llm.CoachLlmGateway
import com.dietician.shared.llm.CoachLlmGatewayLlmStream
import com.dietician.shared.llm.CoachLocale
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.ui.di.UiModuleNoStubLlmStreamTest"`
Expected: PASS.

Run: `./gradlew :shared:desktopTest`
Expected: PASS (whole suite — the stub deletion must not break unrelated tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dietician/shared/ui/di/UiModule.kt \
        shared/src/commonTest/kotlin/com/dietician/shared/ui/di/UiModuleNoStubLlmStreamTest.kt
git commit -m "feat(shared:ui): swap StubLlmStream for CoachLlmGatewayLlmStream — Coach activated"
```

---

### Task 23: Desktop bootstrap runs `DesktopOutboxReplay.replayPending()`

**Files:**
- Modify: `desktopApp/src/jvmMain/kotlin/com/dietician/desktop/Main.kt` (or wherever the Compose entry point sits)
- Test: skip — startup-only behavior; cover with the integration smoke in Task 28.

- [ ] **Step 1: Locate the desktop entry point**

Run: `grep -n "fun main" "C:/Users/User/Desktop/Dietician/desktopApp/src/jvmMain/kotlin/com/dietician/desktop/Main.kt"`

- [ ] **Step 2: Add a launch block on Koin start**

After Koin's `startKoin { ... }` call, insert:

```kotlin
    runBlocking { koinInstance.get<DesktopOutboxReplay>().replayPending() }
```

(Adapt to the existing scope plumbing — read the surrounding code to choose the right scope. The replay is suspend + cheap; it can run on `Dispatchers.IO` or on the bootstrap dispatcher.)

- [ ] **Step 3: Compile**

Run: `./gradlew :desktopApp:assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add desktopApp/src/jvmMain/kotlin/com/dietician/desktop/Main.kt
git commit -m "chore(desktopApp): replay DesktopOutboxReplay on startup (iter-11)"
```

---

### Task 24: Spec §7 amendment — Coach chain on Desktop = ClaudeMax-first, server-routed OpenRouter elsewhere

**Files:**
- Modify: `docs/superpowers/specs/2026-05-17-dietician-design.md`

- [ ] **Step 1: Locate §7 in the spec**

Run: `grep -n "^## 7" "C:/Users/User/Desktop/Dietician/docs/superpowers/specs/2026-05-17-dietician-design.md"`

- [ ] **Step 2: Add a §7 amendment block describing the iter-11 split**

Paste the following anchored amendment at the bottom of §7 (or as a clearly labeled "**iter-11 amendment**" subsection):

```markdown
**iter-11 amendment (2026-05-19, council 1779208184).** Coach text routing splits along the platform axis:

- Desktop client routes ClaudeMax CLI subprocess locally (uses Max-20x credit) bookended by `POST /coach/reserve` + `POST /coach/commit` against the server. The server inserts a pending `audit_log` row (status=`pending`) at reserve time and updates it at commit time — Art 13 disclosure is recorded BEFORE the LLM call returns, and the 60s saga-compensation cron flips never-committed rows to `orphaned` while refunding the budget reservation.
- Android client and Desktop-non-ClaudeMax fallback route through `POST /coach/stream` SSE. Server pairs reserve + LlmRouter + commit in one coroutine.
- ClaudeMax CLI participates only in the Desktop Coach text path. The server-side `LlmRouter` chain for Coach is OpenRouter → Groq fallback; ClaudeMax remains in the Vision OCR chain unchanged.

State machine for `audit_log.status`: `pending` → `success | failed | aborted | orphaned`. The `orphaned` value is set exclusively by the saga compensation cron when a `pending` row exceeds its `reserved_until` deadline.
```

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-05-17-dietician-design.md
git commit -m "docs(spec): §7 iter-11 amendment — Coach platform split + audit state machine"
```

---

### Task 25: Runbook `docs/runbooks/coach-orphan-cleanup.md`

**Files:**
- Create: `docs/runbooks/coach-orphan-cleanup.md`

- [ ] **Step 1: Write the runbook**

```markdown
# Runbook — Coach orphan cleanup

**Cron:** `CoachOrphanCleanupCron` registered by `CronBootstrap` at 30s period.
**Trigger:** `SELECT refund_orphaned(60)` PG fn (V022).
**Symptom of failure:** `audit_log` rows pile up with `status='pending'` and old `reserved_until`. Budget reservations sit on `llm_budget.cost_cents_used` forever, eating cap.

## Manual orphan cleanup (operator)

```bash
ssh root@46.247.109.91 "sudo -u postgres psql -d dietician -c \"SELECT refund_orphaned(60);\""
```

Returns the number of rows compensated.

## Inspect pending rows

```bash
ssh root@46.247.109.91 "sudo -u postgres psql -d dietician -c \
  \"SELECT id, subject_id, occurred_at, reserved_until, cost_cents \
    FROM audit_log WHERE status = 'pending' \
    ORDER BY occurred_at;\""
```

## Inspect client-side outbox (desktop)

```bash
sqlite3 "%APPDATA%/Dietician/dietician.db" \
  "SELECT idempotency_key, started_at_ms, attempts, provider FROM audit_pending_outbox;"
```

A non-empty outbox after a clean desktop startup signals `DesktopOutboxReplay` is failing — check desktop logs for `replay failed for …` warnings.

## Cron health probe

```bash
ssh root@46.247.109.91 "journalctl -u dietician-backend --since '5 minutes ago' --no-pager | grep 'coach: orphaned'"
```

A line `coach: orphaned N pending rows (saga compensation)` appears whenever the cron compensates ≥1 row. Silence is correct under normal operation.
```

- [ ] **Step 2: Commit**

```bash
git add docs/runbooks/coach-orphan-cleanup.md
git commit -m "docs(runbook): coach orphan cleanup + outbox inspection"
```

---

### Task 26: Integration test — mid-stream provider failure rolls back to status=failed

**Files:**
- Create: `server/src/test/kotlin/com/dietician/server/coach/CoachServiceFailureIntegrationTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.dietician.server.coach

import com.dietician.server.db.runMigrations
import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachServiceFailureIntegrationTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var ds: HikariDataSource
    private val subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeAll fun setup() {
        pg = PostgreSQLContainer("pgvector/pgvector:pg16").apply { start() }
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = pg.jdbcUrl; username = pg.username; password = pg.password
        })
        ds.connection.use { c ->
            c.prepareStatement("INSERT INTO subjects (subject_id, display_name) VALUES (?, 'Victor')")
                .apply { setObject(1, subjectId) }.execute()
        }
    }
    @AfterAll fun teardown() { ds.close(); pg.stop() }

    @Test
    fun `mid-stream failure flips audit row to status=failed and propagates exception`() = runTest {
        val failingStream = object : LlmStream {
            override fun streamRoute(request: LlmRequest): Flow<LlmChunk> = flow {
                emit(LlmChunk("Eat ", isDone = false))
                throw RuntimeException("provider crashed")
            }
        }
        val service = CoachService(CoachRepository(ds), BudgetRepository(ds), PiiRedactor(), failingStream)
        val key = UUID.randomUUID().toString()
        val req = CoachStreamRequest(idempotencyKey = key, prompt = "p", locale = "en")
        assertFailsWith<RuntimeException> {
            service.streamServerRouted(subjectId, req).toList()
        }
        val row = CoachRepository(ds).findByIdempotencyKey(UUID.fromString(key))!!
        assertEquals("failed", row.status)
    }
}
```

- [ ] **Step 2: Run + verify pass**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachServiceFailureIntegrationTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/test/kotlin/com/dietician/server/coach/CoachServiceFailureIntegrationTest.kt
git commit -m "test(server): mid-stream provider crash flips audit row to status=failed"
```

---

### Task 27: Integration test — `consume_or_fail` rejects when cap exceeded; no audit row written

**Files:**
- Create: `server/src/test/kotlin/com/dietician/server/coach/CoachServiceBudgetIntegrationTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.dietician.server.coach

import com.dietician.server.db.runMigrations
import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.PiiRedactor
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachServiceBudgetIntegrationTest {
    private lateinit var pg: PostgreSQLContainer<*>
    private lateinit var ds: HikariDataSource
    private val subjectId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeAll fun setup() {
        pg = PostgreSQLContainer("pgvector/pgvector:pg16").apply { start() }
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = pg.jdbcUrl; username = pg.username; password = pg.password
        })
        ds.connection.use { c ->
            c.prepareStatement("INSERT INTO subjects (subject_id, display_name) VALUES (?, 'Victor')")
                .apply { setObject(1, subjectId) }.execute()
            c.prepareStatement(
                """
                INSERT INTO llm_budget
                    (subject_id, provider, period_starts_at, period_ends_at, cost_cents_used, cost_cents_cap)
                VALUES (?, 'claudemax',
                        date_trunc('month', now())::DATE,
                        (date_trunc('month', now()) + interval '1 month - 1 day')::DATE,
                        500, 500)
                """.trimIndent()
            ).apply { setObject(1, subjectId) }.execute()
        }
    }
    @AfterAll fun teardown() { ds.close(); pg.stop() }

    @Test
    fun `over-budget reserve returns Rejected and writes no audit row`() = runTest {
        val service = CoachService(
            repo = CoachRepository(ds), budgets = BudgetRepository(ds), redactor = PiiRedactor()
        )
        val key = UUID.randomUUID()
        val resp = service.reserve(
            subjectId,
            CoachReserveRequest(key.toString(), "x", "en", "claudemax", 5, 60),
        )
        assertTrue(resp is CoachServiceReserveResult.Rejected)
        assertEquals("over_budget", (resp).reason)
        assertNull(CoachRepository(ds).findByIdempotencyKey(key))
    }
}
```

- [ ] **Step 2: Run + verify pass**

Run: `./gradlew :server:test --tests "com.dietician.server.coach.CoachServiceBudgetIntegrationTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/test/kotlin/com/dietician/server/coach/CoachServiceBudgetIntegrationTest.kt
git commit -m "test(server): over-budget reserve rejects + writes no pending audit row"
```

---

### Task 28: Live smoke — desktop end-to-end with ClaudeMax

**Files:**
- No file changes.

- [ ] **Step 1: Build + deploy server**

```bash
cd "C:/Users/User/Desktop/Dietician"
./gradlew :server:buildFatJar -Dorg.gradle.jvmargs="-Xmx1g -XX:MaxMetaspaceSize=384m"
scp "C:/Users/User/Desktop/Dietician/server/build/libs/dietician-server.jar" root@46.247.109.91:/opt/dietician/lib/dietician-server.jar
ssh root@46.247.109.91 "chown dietician:dietician /opt/dietician/lib/dietician-server.jar && systemctl restart dietician-backend && sleep 5 && systemctl is-active dietician-backend"
```
Expected: `active`.

- [ ] **Step 2: Run desktop app**

```bash
./gradlew :desktopApp:run
```
Expected: desktop window opens, Coach tab reachable.

- [ ] **Step 3: Type a Coach turn**

In the Coach screen, type "What can I eat for 50g protein with chicken in my pantry?" and submit.
Expected: stream of tokens lands in the UI, response references chicken + protein math; no "AI coach offline" placeholder.

- [ ] **Step 4: Verify audit row written via VPS psql**

```bash
ssh root@46.247.109.91 "sudo -u postgres psql -d dietician -c \
  \"SELECT id, subject_id, status, model, input_tokens, output_tokens, cost_cents \
    FROM audit_log WHERE kind = 'llm_call' ORDER BY occurred_at DESC LIMIT 3;\""
```
Expected: top row has `status='success'`, non-zero token counts, `model='claudemax'`.

- [ ] **Step 5: Verify outbox row deleted on commit**

```bash
sqlite3 "%APPDATA%/Dietician/dietician.db" "SELECT count(*) FROM audit_pending_outbox;"
```
Expected: `0`.

- [ ] **Step 6: Test outbox replay** — kill the desktop process mid-Coach-turn:

  1. Start a Coach turn.
  2. While streaming, force-kill the desktop app (Task Manager → End task on `dietician-desktop`).
  3. Reopen the app.
  4. Re-check `sqlite3 ... "SELECT count(*) FROM audit_pending_outbox;"` → expect `0` after replay.
  5. Re-check VPS `audit_log` → expect the killed turn's row in `status='orphaned'`.

- [ ] **Step 7: Test 60s cron compensation** — drop the JAR back to a fake desktop session that never commits:

  ```bash
  # Reserve only (no commit) via curl; wait 90s; check status.
  ssh root@46.247.109.91 'KEY=$(uuidgen); curl -s -H "Content-Type: application/json" \
    -b "dietician_session=<test-session>" \
    -d "{\"idempotencyKey\":\"$KEY\",\"prompt\":\"x\",\"locale\":\"en\",\"provider\":\"claudemax\",\"estimatedCostCents\":5,\"reservationTtlSeconds\":60}" \
    http://100.101.47.77:8081/coach/reserve > /dev/null && sleep 95 && \
    sudo -u postgres psql -d dietician -c "SELECT status FROM audit_log WHERE idempotency_key = '"'$KEY'"'::uuid;"'
  ```
  Expected: `status='orphaned'`.

- [ ] **Step 8: Update BRIDGE handoff at session end** (the `/wrap` skill will land it; just confirm in chat).

---

## ⏸️ COUNCIL GATE 3 — Post full-integration impl

Convene a 5-agent post-impl council on the full iter-11 surface (Tasks 0-28). Scope: did Coach activate cleanly on both platforms? Are all 4 failure modes (over-budget, mid-stream crash, desktop crash mid-call, server cron compensation) covered by passing integration tests AND verified in the Task 28 live smoke? Are there any Art 13 disclosure timing edge cases not covered by the spec amendment? Issue final FLAWED/APPROVED verdict.

If APPROVED, open PR + merge to master.
If FLAWED, the must-fix list goes back into a Task 29+ before merge.

---

## Self-review checklist (skill-required)

**1. Spec coverage:**
- Plan-2 LlmRouter reuse ✓ (Task 10 wires `LlmStream` into `CoachService.streamServerRouted`)
- Plan-3 audit_log V018 reuse ✓ (Tasks 1, 3, 5)
- Plan-3 consume_or_fail V019 reuse ✓ (Task 5 + 27)
- Plan-4-5 LlmStream interface contract ✓ (Task 16 + 22)
- ClaudeMax CLI Desktop preserved ✓ (Task 18 + 21)
- Server-side router for Android + Desktop-fallback ✓ (Task 9, 10, 11, 20)
- EN/RO locale handling ✓ (Task 4 + 22 localeProvider)
- Council 1779208184 OPTION 4 saga compensation ✓ (Task 1 refund_orphaned + Task 7 cron + Task 25 runbook)
- Council 1779206433 SSE heartbeat 25s + idle-timeout 90s ✓ (Task 11)
- PII redaction server-side ✓ (Task 5 via PiiRedactor)
- Per-call disclosure (Art 13) ✓ (Task 1 status=pending before LLM call + Task 24 spec amendment documents the state machine)
- Tailscale-disconnected banner ✓ already in Plan-4-5 RC16, not retouched.

**2. Placeholder scan:** No "TODO", "fill in later", or "handle edge cases" left in the plan. Two `error(...)` test helpers in Task 8 + Task 11 are explicitly marked as stubs to land in their own implementation step within the same task — these are concrete instructions, not placeholders.

**3. Type consistency:** `idempotencyKey` is `String` on the wire (UUID-as-string) and `UUID` server-side in `CoachRepository` — conversion via `UUID.fromString(...)` is shown explicitly in every site that crosses the boundary.

**4. Build+mount pairing:** Every new component is mounted:
- `CoachLlmGateway` (Task 15) → mounted in `UiModule.kt:89` (Task 22) + `DataModule.desktop.kt` + `DataModule.android.kt` (Task 21).
- `CoachLlmGatewayLlmStream` (Task 16) → mounted in `UiModule.kt:89` (Task 22).
- `DesktopOutboxReplay` (Task 19) → mounted in `Main.kt` startup (Task 23).
- `installCoachRoutes()` (Task 8) + `CoachOrphanCleanupCron` (Task 7) → mounted in `Application.kt` + `CronBootstrap.kt` (Task 12).
- `CoachHttpClient` (Task 17) → registered in platform modules (Task 21).

**5. Component-reuse contract:** Where existing components are mounted in new sites:
- `PiiRedactor` (existing in `:shared:llm`) mounted in `CoachService` — Task 5 imports it and uses the public `redact(String)` method. Existing test coverage is in the `:shared:llm` test module.
- `BudgetRepository` (existing in `:server`) — Task 5 calls `consumeOrFail(subjectId, provider, tokensNeeded, costCentsEstimated)`. Task 5 Step 4 explicitly grep-checks the signature before adapting.
- `LlmStream` interface (existing in `:shared:llm`) — Task 16 implements via `CoachLlmGatewayLlmStream` and the test in Task 16 asserts the contract holds.
- `ClaudeMaxCliProvider` (existing in `:shared:desktop`) — Task 21 adapts it into `DesktopLocalProvider` via a thin wrapper; signature compatibility checked at compile time.

**6. `data-testid` grep:** No spec section dictates Coach `data-testid` selectors at this slice — the existing `CoachChatScreen` already paints with stable identifiers. If a future spec amendment adds Visual Acceptance for Coach, add the selectors then.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-19-plan-iter-11-coach-activation.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration. Three council gates already structured.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?

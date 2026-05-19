package com.dietician.shared.llm

import com.dietician.shared.data.sql.DieticianDatabase
import com.dietician.shared.llm.net.CoachHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * iter-11 — Desktop ClaudeMax flow. Sequence:
 *   1. Generate UUIDv4 idempotency key.
 *   2. Persist `audit_pending_outbox` row BEFORE provider invocation (council
 *      1779208184 saga-recovery contract — local crash mid-call replays on
 *      next desktop startup via [DesktopOutboxReplay]).
 *   3. POST `/coach/reserve` → server inserts pending audit row + locks budget.
 *   4. Run [LocalCoachProvider.run] locally (production = ClaudeMax CLI
 *      subprocess); emit chunks to UI.
 *   5. POST `/coach/commit` with usage + status.
 *   6. Delete outbox row on commit ACK.
 *
 * On provider failure mid-flight: commit with status='failed', then rethrow.
 * On desktop crash between (4) and (5): outbox row survives; replay-on-
 * startup re-POSTs `/coach/commit` (server idempotent).
 */
class DesktopCoachLlmGateway(
    private val db: DieticianDatabase,
    private val http: CoachHttpClient,
    private val provider: LocalCoachProvider,
    private val uuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : CoachLlmGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk> =
        flow {
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
            http.reserve(
                idempotencyKey = key,
                prompt = prompt,
                locale = locale.wire(),
                provider = "claudemax",
                estimatedCostCents = ESTIMATE_COST_CENTS,
                reservationTtlSeconds = RESERVATION_TTL_SECONDS,
            )
            val startMs = clock()
            var lastResponse: LlmResponse? = null
            var totalCompletionTokens = 0
            var status = "success"
            @Suppress("TooGenericExceptionCaught")
            try {
                provider.run(prompt, locale).collect { chunk ->
                    emit(chunk)
                    if (chunk.tokenCount > 0) totalCompletionTokens = chunk.tokenCount
                    if (chunk.isDone) lastResponse = chunk.finalResponse
                }
            } catch (t: Throwable) {
                status = "failed"
                log.warn("ClaudeMax stream failed for key {}: {}", key, t.message)
                throw t
            } finally {
                http.commit(
                    idempotencyKey = key,
                    status = status,
                    promptTokens = lastResponse?.inputTokens ?: 0,
                    completionTokens = lastResponse?.outputTokens ?: totalCompletionTokens,
                    costCents = lastResponse?.costCents ?: 0,
                    provider = "claudemax",
                    latencyMs = clock() - startMs,
                    responseHash = lastResponse?.text?.hashCode()?.toString() ?: "n/a",
                )
                db.`0009_audit_pending_outboxQueries`.markCommitted(key)
            }
        }

    private companion object {
        const val ESTIMATE_COST_CENTS = 0 // ClaudeMax uses Max-20x subscription; OpenRouter passthrough cost = 0.
        const val RESERVATION_TTL_SECONDS = 120
    }
}

/**
 * Abstraction over a local Coach provider — production wraps ClaudeMax CLI
 * subprocess. Tests inject a mock for deterministic flows + crash injection.
 */
interface LocalCoachProvider {
    fun run(prompt: String, locale: CoachLocale): Flow<LlmChunk>
}

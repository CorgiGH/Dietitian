package com.dietician.shared.llm

import kotlinx.datetime.Clock

/**
 * Plan-2 LLM Router — Task 19 (happy path) + Task 20 (edge paths).
 *
 * Dispatch model (Batch C): the Router holds a `Map<ProviderId, ProviderCallable>` rather
 * than a polymorphic `LlmProvider` interface — Batch B providers each expose their own
 * `call(request, model)` directly and are wrapped into [ProviderCallable] functional
 * references at construction. This matches the Batch B precedent (concrete per-class
 * providers, no shared `LlmProvider.call`) and keeps each provider independently testable
 * with MockEngine.
 *
 * Call sequence per route():
 *   1. [RoutingRules.selectChain] → ordered [LlmProvider] failover chain.
 *   2. Compute idempotency key (subjectId + promptHash + first-chain-entry model). N
 *      concurrent identical calls collapse to ONE upstream dispatch via [IdempotencyCache]
 *      (RC7).
 *   3. For each provider in chain:
 *      a. Estimate input tokens (char-count / 4) + cost via [ModelPriceLookup].
 *      b. [BudgetLedger.reserve] — throw-tolerant: if BudgetExhausted, record + skip.
 *      c. Resolve [ProviderCallable] from dispatch table. Unknown providerId → skip.
 *      d. Invoke callable. Wrap non-[LlmError] exceptions as [LlmError.TransientFailure].
 *      e. On success: [BudgetLedger.finalize] with realized tokens + cost, write
 *         `llm_call` audit row, return response.
 *      f. On [LlmError.PermanentFailure] or [LlmError.ContentFiltered]: release
 *         reservation, write `llm_call_failed_permanent`, throw immediately — DO NOT
 *         fall through to the rest of the chain.
 *      g. On transient/timeout/rate-limit/provider-unavailable: release reservation, add
 *         to errors list, continue chain.
 *   4. Chain exhausted → write `llm_call_all_failed`, throw [LlmError.ProviderUnavailable]
 *      naming the first chain entry (per Plan-2 §A14 convention).
 *
 * Audit-row contract:
 *   - kind="llm_call" on success: model = "<providerId>/<model>", input_tokens,
 *     output_tokens, cost_cents, extra.device_class, extra.task.
 *   - kind="llm_call_failed_permanent" on permanent/content-filter error: extra.provider,
 *     extra.error.
 *   - kind="llm_call_failed_transient" on transient/timeout/rate-limit (per attempt) —
 *     emitted by Task 20 audit semantics so the chain trace is reconstructable.
 *   - kind="llm_call_all_failed" on chain exhaustion: extra.errors_count.
 *
 * Concurrency: the IdempotencyCache serializes identical concurrent dispatches; the
 * Router itself is otherwise stateless and safe for arbitrary parallel route() callers.
 */
class LlmRouter(
    private val config: RouterConfig,
    private val providers: Map<ProviderId, ProviderCallable>,
    private val cache: IdempotencyCache,
    private val budget: BudgetLedger,
    val auditLog: AuditLogSink,
    private val antiRecommend: AntiRecommendExclusion = AntiRecommendExclusion(AntiRecommendExclusionConfig.EMPTY),
    @Suppress("unused") private val clock: Clock = Clock.System,
) {
    suspend fun route(request: LlmRequest): LlmResponse {
        val rawChain = RoutingRules.selectChain(config, request)
        require(rawChain.isNotEmpty()) {
            "Empty chain for ${request.deviceClass}/${request.task}"
        }
        // Task 27 — strip excluded provider/model tuples for this subject (or global *).
        val chain = antiRecommend.filter(rawChain, request.subjectId)
        if (chain.isEmpty()) {
            auditLog.write(
                AuditEntry(
                    subjectId = request.subjectId,
                    kind = "llm_call_all_excluded",
                    extra = mapOf(
                        "device_class" to request.deviceClass.name,
                        "task" to request.task.name,
                        "raw_chain_size" to rawChain.size.toString(),
                    ),
                ),
            )
            throw LlmError.ProviderUnavailable(rawChain.first().id)
        }

        val firstProvider = chain.first()
        val cacheKey = IdempotencyCache.Key(
            subjectId = request.subjectId,
            promptHash = hashPrompt(request),
            model = firstProvider.model,
        )
        return cache.dedup(cacheKey) {
            tryChain(chain, request)
        }
    }

    private suspend fun tryChain(chain: List<LlmProvider>, request: LlmRequest): LlmResponse {
        val errors = mutableListOf<Pair<ProviderId, LlmError>>()
        for (provider in chain) {
            val callable = providers[provider.id]
            if (callable == null) {
                errors += provider.id to LlmError.ProviderUnavailable(provider.id)
                writeTransientFailureRow(request, provider, "callable_missing")
                continue
            }

            val estimateTokens = estimateInputTokens(request)
            val estimateCostCents = ModelPriceLookup.lookup(provider.id, provider.model)
                ?.computeCostCents(estimateTokens, request.maxOutputTokens)
                ?: 0

            val reservation = try {
                budget.reserve(
                    subjectId = request.subjectId,
                    provider = provider.id,
                    estimateTokens = estimateTokens,
                    estimateCostCents = estimateCostCents,
                )
            } catch (e: LlmError.BudgetExhausted) {
                errors += provider.id to e
                writeTransientFailureRow(request, provider, "budget_exhausted")
                continue
            }

            try {
                val response = callable.call(request, provider.model)
                budget.finalize(
                    reservation = reservation,
                    actualTokens = response.inputTokens + response.outputTokens,
                    actualCostCents = response.costCents,
                )
                auditLog.write(
                    AuditEntry(
                        subjectId = request.subjectId,
                        kind = "llm_call",
                        model = "${provider.id.raw}/${provider.model}",
                        inputTokens = response.inputTokens,
                        outputTokens = response.outputTokens,
                        costCents = response.costCents,
                        extra = mapOf(
                            "device_class" to request.deviceClass.name,
                            "task" to request.task.name,
                            "cache_read_tokens" to response.cacheReadTokens.toString(),
                            "cache_write_tokens" to response.cacheWriteTokens.toString(),
                        ),
                    ),
                )
                return response
            } catch (e: LlmError) {
                budget.release(reservation)
                errors += provider.id to e
                when (e) {
                    is LlmError.PermanentFailure, is LlmError.ContentFiltered -> {
                        auditLog.write(
                            AuditEntry(
                                subjectId = request.subjectId,
                                kind = "llm_call_failed_permanent",
                                extra = mapOf(
                                    "provider" to provider.id.raw,
                                    "error" to (e.message ?: "unknown"),
                                    "error_kind" to e::class.simpleName.orEmpty(),
                                ),
                            ),
                        )
                        throw e
                    }
                    else -> {
                        // Transient / RateLimit / Timeout / ProviderUnavailable / BudgetExhausted.
                        writeTransientFailureRow(
                            request,
                            provider,
                            errorKind = e::class.simpleName.orEmpty(),
                            errorMessage = e.message,
                        )
                    }
                }
            } catch (t: Throwable) {
                // Non-LlmError → wrap as TransientFailure + continue chain (Task 20 edge case).
                budget.release(reservation)
                val wrapped = LlmError.TransientFailure(t)
                errors += provider.id to wrapped
                writeTransientFailureRow(
                    request,
                    provider,
                    errorKind = "TransientFailure",
                    errorMessage = t.message,
                )
            }
        }
        auditLog.write(
            AuditEntry(
                subjectId = request.subjectId,
                kind = "llm_call_all_failed",
                extra = mapOf(
                    "errors_count" to errors.size.toString(),
                    "device_class" to request.deviceClass.name,
                    "task" to request.task.name,
                ),
            ),
        )
        throw LlmError.ProviderUnavailable(chain.first().id)
    }

    private suspend fun writeTransientFailureRow(
        request: LlmRequest,
        provider: LlmProvider,
        errorKind: String,
        errorMessage: String? = null,
    ) {
        auditLog.write(
            AuditEntry(
                subjectId = request.subjectId,
                kind = "llm_call_failed_transient",
                extra = buildMap {
                    put("provider", provider.id.raw)
                    put("model", provider.model)
                    put("error_kind", errorKind)
                    errorMessage?.let { put("error", it) }
                },
            ),
        )
    }

    /**
     * Stable per-request hash for [IdempotencyCache.Key].
     *
     * Inputs that affect the upstream response MUST be hashed: system prompt, every
     * message (role + content in order), maxOutputTokens, temperature, cacheControl. The
     * model name is also folded in even though the Key carries it separately — paranoia
     * against future refactors that reuse promptHash without model context.
     *
     * Hash is a deterministic FNV-1a-like accumulation over the canonical concatenation —
     * commonMain has no `MessageDigest`, but for our purposes (dedup keying within a 5s
     * TTL window) a strong-enough non-cryptographic hash suffices.
     */
    internal fun hashPrompt(req: LlmRequest): String {
        val sb = StringBuilder()
        sb.append("sys=").append(req.systemPrompt ?: "").append('\n')
        req.messages.forEach { m ->
            sb.append(m.role.name).append(':').append(m.content).append('\n')
        }
        sb.append("max=").append(req.maxOutputTokens).append(';')
        sb.append("t=").append(req.temperature).append(';')
        sb.append("cc=").append(req.cacheControl.name).append(';')
        sb.append("att=").append(req.attachments.size).append(';')
        return fnv1aHex(sb.toString())
    }

    private fun fnv1aHex(s: String): String {
        var hash = 0xcbf29ce484222325uL
        for (c in s) {
            hash = hash xor (c.code.toULong() and 0xffuL)
            hash *= 0x100000001b3uL
        }
        return hash.toString(16).padStart(16, '0')
    }

    /**
     * Simple input-token estimate used for budget reservation pre-call. Real token count
     * comes back in [LlmResponse.inputTokens] and is reconciled via
     * [BudgetLedger.finalize]. char/4 is a well-known rough heuristic for English/Romanian
     * tokenization.
     */
    internal fun estimateInputTokens(req: LlmRequest): Int {
        var chars = 0
        chars += req.systemPrompt?.length ?: 0
        req.messages.forEach { chars += it.content.length }
        return (chars / 4).coerceAtLeast(1)
    }
}

/**
 * Functional interface wrapping a provider's per-class `call(request, model)`.
 *
 * Each Batch B provider (OpenRouterProvider, AnthropicProvider, etc.) is wrapped into a
 * ProviderCallable instance + registered against its [ProviderId] in the Router's
 * dispatch table. This keeps the Router decoupled from provider-specific construction
 * (api keys, ktor clients) while still letting RC8 routing-rules drive the chain by
 * sealed [LlmProvider] subtype.
 */
fun interface ProviderCallable {
    suspend fun call(request: LlmRequest, model: String): LlmResponse
}

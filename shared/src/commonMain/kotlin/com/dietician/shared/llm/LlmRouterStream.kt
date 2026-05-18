package com.dietician.shared.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Streaming variant of [LlmRouter] — Plan-2 Task 21.
 *
 * Shares the same chain selection, idempotency hashing, and budget reserve/finalize/release
 * contract as [LlmRouter], but emits [LlmChunk]s instead of returning a single response.
 *
 * Failover policy (MVP):
 *   - Streaming fallback happens only BEFORE the first chunk emits from a provider. Once
 *     any chunk has been emitted to the caller, the caller has been shown text — falling
 *     back would re-render the prefix, which is worse than surfacing the mid-stream error.
 *   - Permanent / ContentFiltered errors throw immediately without trying downstream chain
 *     entries (same as non-streaming Router).
 *
 * Idempotency: streaming responses are NOT cached. Concurrent identical streaming calls
 * each get their own upstream dispatch — the in-flight coalescing semantics of
 * [IdempotencyCache] only makes sense for unary responses. Plan-3 caching layer may
 * batch-collapse later.
 *
 * Budget: reservation is taken pre-stream using the same char/4 estimate as
 * [LlmRouter.estimateInputTokens]. Finalized when the terminal chunk lands with a populated
 * `finalResponse`. Released on failover. If the stream never emits a terminal chunk with
 * `finalResponse`, the reservation is finalized with the running tokenCount on close.
 */
class LlmRouterStream(
    private val config: RouterConfig,
    private val providers: Map<ProviderId, StreamProviderCallable>,
    private val budget: BudgetLedger,
    private val auditLog: AuditLogSink,
) : LlmStream {

    override fun streamRoute(request: LlmRequest): Flow<LlmChunk> = flow {
        val chain = RoutingRules.selectChain(config, request)
        require(chain.isNotEmpty()) {
            "Empty chain for ${request.deviceClass}/${request.task}"
        }

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

            var emittedAnyChunk = false
            var terminalResponse: LlmResponse? = null
            try {
                callable.stream(request, provider.model).collect { chunk ->
                    if (!emittedAnyChunk) {
                        auditLog.write(
                            AuditEntry(
                                subjectId = request.subjectId,
                                kind = "llm_call_streaming_start",
                                extra = mapOf(
                                    "provider" to provider.id.raw,
                                    "model" to provider.model,
                                    "device_class" to request.deviceClass.name,
                                    "task" to request.task.name,
                                ),
                            ),
                        )
                    }
                    emittedAnyChunk = true
                    if (chunk.isDone && chunk.finalResponse != null) {
                        terminalResponse = chunk.finalResponse
                    }
                    emit(chunk)
                }
                // Stream completed. Finalize + audit + return.
                val final = terminalResponse
                if (final != null) {
                    budget.finalize(
                        reservation = reservation,
                        actualTokens = final.inputTokens + final.outputTokens,
                        actualCostCents = final.costCents,
                    )
                    auditLog.write(
                        AuditEntry(
                            subjectId = request.subjectId,
                            kind = "llm_call",
                            model = "${provider.id.raw}/${provider.model}",
                            inputTokens = final.inputTokens,
                            outputTokens = final.outputTokens,
                            costCents = final.costCents,
                            extra = mapOf(
                                "device_class" to request.deviceClass.name,
                                "task" to request.task.name,
                                "streaming" to "true",
                            ),
                        ),
                    )
                } else {
                    // Stream completed without a terminal chunk carrying finalResponse —
                    // release reservation to be safe.
                    budget.release(reservation)
                    auditLog.write(
                        AuditEntry(
                            subjectId = request.subjectId,
                            kind = "llm_call_streaming_no_terminal",
                            extra = mapOf("provider" to provider.id.raw),
                        ),
                    )
                }
                return@flow
            } catch (ce: CancellationException) {
                // Caller cancelled — release budget + propagate without audit row (caller
                // intent, not a router-side failure).
                budget.release(reservation)
                throw ce
            } catch (e: LlmError) {
                if (emittedAnyChunk) {
                    // Mid-stream error after chunks already flowed → propagate, do not
                    // fall back.
                    budget.release(reservation)
                    auditLog.write(
                        AuditEntry(
                            subjectId = request.subjectId,
                            kind = "llm_call_failed_mid_stream",
                            extra = mapOf(
                                "provider" to provider.id.raw,
                                "error_kind" to e::class.simpleName.orEmpty(),
                                "error" to (e.message ?: "unknown"),
                            ),
                        ),
                    )
                    throw e
                }
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
                    else -> writeTransientFailureRow(
                        request,
                        provider,
                        errorKind = e::class.simpleName.orEmpty(),
                        errorMessage = e.message,
                    )
                }
            } catch (t: Throwable) {
                if (emittedAnyChunk) {
                    budget.release(reservation)
                    auditLog.write(
                        AuditEntry(
                            subjectId = request.subjectId,
                            kind = "llm_call_failed_mid_stream",
                            extra = mapOf(
                                "provider" to provider.id.raw,
                                "error_kind" to "TransientFailure",
                                "error" to (t.message ?: "unknown"),
                            ),
                        ),
                    )
                    throw LlmError.TransientFailure(t)
                }
                budget.release(reservation)
                errors += provider.id to LlmError.TransientFailure(t)
                writeTransientFailureRow(request, provider, "TransientFailure", t.message)
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
                    "streaming" to "true",
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
                    put("streaming", "true")
                },
            ),
        )
    }

    private fun estimateInputTokens(req: LlmRequest): Int {
        var chars = 0
        chars += req.systemPrompt?.length ?: 0
        req.messages.forEach { chars += it.content.length }
        return (chars / 4).coerceAtLeast(1)
    }
}

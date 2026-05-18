package com.dietician.shared.llm

/**
 * Static price table indexed by upstream model id. Prices are stored as integer
 * `cents per million tokens` (cents are the canonical money unit across the codebase
 * — V019 `llm_budget.cap_cents`, audit_log.cost_cents).
 *
 * Used by the Router to compute realized cost from provider-reported token counts
 * (Batch B response parsers) so the audit row + budget finalize use a deterministic
 * value rather than provider-side dollar amounts which differ in rounding + currency.
 *
 * Update cadence: provider price changes are tracked manually here. The Batch B
 * `:server` integration WILL also reject responses whose `provider.priceCents`
 * deviates from this table by >10% (TBD — fraud-monitoring is post-batch).
 *
 * NOTE: numbers below are placeholders matching the Plan-2 brief — Batch B will
 * confirm against live provider pricing pages and add a `pricing-source` comment
 * per row.
 */
object ModelPriceLookup {
    private val prices: Map<String, Price> = mapOf(
        "anthropic/claude-sonnet-4.5" to Price(inputPerMTok = 300, outputPerMTok = 1500),
        "anthropic/claude-3.5-haiku" to Price(inputPerMTok = 80, outputPerMTok = 400),
        "google/gemini-2.5-pro" to Price(inputPerMTok = 125, outputPerMTok = 500),
        "google/gemini-2.5-flash" to Price(inputPerMTok = 30, outputPerMTok = 250),
        "openai/gpt-4o" to Price(inputPerMTok = 250, outputPerMTok = 1000),
        "meta-llama/llama-3.3-70b-instruct" to Price(inputPerMTok = 90, outputPerMTok = 90),
        "groq/llama-3.3-70b-versatile" to Price(inputPerMTok = 59, outputPerMTok = 79),
        // Direct-Groq-API name (chain entry uses bare model id; price duplicate kept in sync).
        "llama-3.3-70b-versatile" to Price(inputPerMTok = 59, outputPerMTok = 79),
        "voyage/voyage-4-lite" to Price(inputPerMTok = 2, outputPerMTok = 0),
        // Direct OpenRouter -> bare "claude-3-5-sonnet-latest" — ClaudeMaxCli uses CLI bundle pricing (zero per-call).
        "claude-3-5-sonnet-latest" to Price(inputPerMTok = 300, outputPerMTok = 1500),
    )

    /** @return Price entry for [model], or null if Batch B has not added pricing yet. */
    fun lookup(provider: ProviderId, model: String): Price? = prices[model]

    /** Read-only view of the full table — Batch B HTTP-status route exposes this for ops. */
    fun all(): Map<String, Price> = prices
}

/**
 * Cents-per-million-tokens schedule.
 *
 * [computeCostCents] uses integer division (truncating) — this is intentional so that the
 * V019 `consume_or_fail` integer subtraction matches what the Router decides. Sub-cent
 * losses are accumulated into provider margin (acceptable; reviewed in monthly audit).
 *
 * Plan-2 Task 25/26: [computeCostCentsWithCache] applies Anthropic-style prompt caching
 * discount. Anthropic's posted rates as of 2026-05:
 *   - cache_read_input_tokens: 10% of base input rate (90% discount)
 *   - cache_creation_input_tokens: 125% of base input rate (25% premium for the
 *     creation hit). Net: a 5-prompt session in a cached window pays ~24% of the
 *     non-cached cost.
 * Non-cache providers pass 0 for both cached fields → math degrades to the legacy
 * [computeCostCents].
 */
data class Price(val inputPerMTok: Int, val outputPerMTok: Int) {
    fun computeCostCents(inputTokens: Int, outputTokens: Int): Int {
        require(inputTokens >= 0) { "inputTokens must be non-negative: $inputTokens" }
        require(outputTokens >= 0) { "outputTokens must be non-negative: $outputTokens" }
        val inputCost = inputTokens.toLong() * inputPerMTok / 1_000_000L
        val outputCost = outputTokens.toLong() * outputPerMTok / 1_000_000L
        return (inputCost + outputCost).toInt()
    }

    /**
     * Cost math with Anthropic prompt-cache rebate.
     *
     * @param uncachedInputTokens fresh (non-cached) input tokens billed at full rate.
     * @param cacheReadTokens tokens served from the cache — 10% of full input rate.
     * @param cacheWriteTokens tokens stored on this call (cache creation) — 125%
     *     of full input rate.
     * @param outputTokens completion tokens billed at full output rate.
     */
    fun computeCostCentsWithCache(
        uncachedInputTokens: Int,
        cacheReadTokens: Int,
        cacheWriteTokens: Int,
        outputTokens: Int,
    ): Int {
        require(uncachedInputTokens >= 0) { "uncachedInputTokens must be non-negative" }
        require(cacheReadTokens >= 0) { "cacheReadTokens must be non-negative" }
        require(cacheWriteTokens >= 0) { "cacheWriteTokens must be non-negative" }
        require(outputTokens >= 0) { "outputTokens must be non-negative" }
        val uncached = uncachedInputTokens.toLong() * inputPerMTok / 1_000_000L
        // 10% of base input rate. Use *1 / 10 to keep integer math without rounding bias.
        val cacheRead = cacheReadTokens.toLong() * inputPerMTok / 10L / 1_000_000L
        // 125% of base input rate.
        val cacheWrite = cacheWriteTokens.toLong() * inputPerMTok * 125L / 100L / 1_000_000L
        val outputCost = outputTokens.toLong() * outputPerMTok / 1_000_000L
        return (uncached + cacheRead + cacheWrite + outputCost).toInt()
    }
}

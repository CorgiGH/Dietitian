package com.dietician.shared.llm

/**
 * Plan-2 Task 27 — anti-recommend provider/model exclusion list.
 *
 * Per-subject (or global via "*" scope) skip-list of `(provider, model)` tuples removed from
 * the failover chain BEFORE the Router iterates. Use cases:
 *   - subject's preference layer hard-bans a model (e.g. `friend-vegan` refuses GPT-4o on
 *     `openrouter` due to upstream policy concerns)
 *   - global ban on a model that hit a permanent moderation-rule incompatibility
 *
 * Distinct from the locked spec §7.6 anti-recommend SOURCES list (paper / influencer ban
 * list injected into system prompts) — that lives in a separate construct. This class
 * filters the PROVIDER CHAIN, not prompt content.
 *
 * Wiring: [LlmRouter.tryChain] applies `filter(chain, subjectId)` before iteration. An
 * empty post-filter chain → [LlmError.ProviderUnavailable] (no path forward).
 */
data class AntiRecommendExclusionConfig(
    /**
     * Scope key → set of (provider, model) tuples to remove from the chain. The scope key
     * is either a subject id or the literal `"*"` for global exclusion. Both subject-scoped
     * AND global exclusions are merged at filter-time.
     */
    val exclusionsByScope: Map<String, Set<ProviderModelKey>> = emptyMap(),
) {
    companion object {
        /** Default = no exclusions. Wired in DI when an operator hasn't configured overrides. */
        val EMPTY: AntiRecommendExclusionConfig = AntiRecommendExclusionConfig()
    }
}

/**
 * Identifier tuple for the exclusion set. Equality is structural — same `provider` + same
 * `model` matches regardless of where the tuple was constructed.
 */
data class ProviderModelKey(val provider: ProviderId, val model: String)

/**
 * Stateless filter — pass the config in at construction, call [filter] per route().
 */
class AntiRecommendExclusion(private val config: AntiRecommendExclusionConfig) {
    /**
     * Removes excluded `(provider, model)` entries from [chain] given [subjectId]. Order of
     * remaining entries is preserved.
     *
     * Scope merge: subject-scoped exclusions UNION'd with global (`"*"`) exclusions. Empty
     * filter results in an empty list — the caller (typically [LlmRouter]) decides how to
     * surface that (Plan-2 contract: throw [LlmError.ProviderUnavailable]).
     */
    fun filter(chain: List<LlmProvider>, subjectId: String): List<LlmProvider> {
        val subjectExcl = config.exclusionsByScope[subjectId] ?: emptySet()
        val globalExcl = config.exclusionsByScope["*"] ?: emptySet()
        if (subjectExcl.isEmpty() && globalExcl.isEmpty()) return chain
        val merged = subjectExcl + globalExcl
        return chain.filter { ProviderModelKey(it.id, it.model) !in merged }
    }
}

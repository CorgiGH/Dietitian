package com.dietician.server.llm

import com.dietician.shared.llm.AuditLogSink
import com.dietician.shared.llm.BudgetLedger
import com.dietician.shared.llm.DefaultRouterConfig
import com.dietician.shared.llm.IdempotencyCache
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.LlmRouter
import com.dietician.shared.llm.ProviderCallable
import com.dietician.shared.llm.ProviderConfig
import com.dietician.shared.llm.ProviderConfigDefaults
import com.dietician.shared.llm.ProviderFactory
import com.dietician.shared.llm.ProviderId
import com.dietician.shared.llm.RouterConfig
import com.dietician.shared.llm.SubjectCredentialStore
import com.dietician.shared.llm.Timeouts
import com.dietician.shared.llm.provider.AnthropicProvider
import com.dietician.shared.llm.provider.GeminiProvider
import com.dietician.shared.llm.provider.GroqProvider
import com.dietician.shared.llm.provider.OpenRouterProvider
import io.ktor.client.HttpClient

/**
 * Plan-2 Task 28 — Server-side [LlmRouter] factory.
 *
 * Wires a fully production-shaped Router using:
 *   - Per-subject provider construction via [ProviderFactory] (BYOK key resolution falls
 *     back to operator-provided env keys when subject has no per-provider credential).
 *   - [BudgetRepositoryAdapter] backed by Plan-3 V019 `consume_or_fail`.
 *   - [AuditLogSinkAdapter] backed by Plan-3 V018 `audit_log` writer.
 *   - [DefaultRouterConfig.default] failover chains (RC3/RC4 baked).
 *
 * ClaudeMax CLI is desktop-only — the server's dispatch table does not register it. If a
 * VICTOR_DESKTOP_TEXT chain ever reaches the server (it shouldn't; client-side path is
 * desktop-direct), the chain still has OpenRouter + Gemini before ClaudeMax, so the
 * unknown provider is logged + skipped by the Router's standard transient-failure path.
 *
 * Env-key resolution: missing keys fail fast at construction. The server cannot start
 * without OPENROUTER_API_KEY (lead provider for VICTOR_DESKTOP_TEXT + FRIEND_PHONE_TEXT) +
 * GROQ_API_KEY (lead for MODERATION).
 */
object LlmRouterFactory {
    /**
     * Build a single shared Router for the server JVM. Stateless apart from the
     * IdempotencyCache TTL window — safe for arbitrary parallel callers.
     *
     * Env vars consumed:
     *   - OPENROUTER_API_KEY (required for default chains)
     *   - ANTHROPIC_API_KEY (optional — only used when subject has no BYOK)
     *   - GEMINI_API_KEY (optional)
     *   - GROQ_API_KEY (required for MODERATION chain)
     */
    fun create(
        httpClient: HttpClient,
        credentialStore: SubjectCredentialStore,
        budget: BudgetLedger,
        auditLog: AuditLogSink,
        config: RouterConfig = DefaultRouterConfig.default,
        env: (String) -> String? = System::getenv,
    ): LlmRouter {
        val defaults = ProviderConfigDefaults(
            openRouterKey = env("OPENROUTER_API_KEY"),
            anthropicKey = env("ANTHROPIC_API_KEY"),
            geminiKey = env("GEMINI_API_KEY"),
            groqKey = env("GROQ_API_KEY"),
            timeouts = Timeouts(),
        )
        // Fail-fast: provider chains MUST have a key for their lead entry. The default
        // chains require openrouter (lead text) + groq (lead moderation).
        require(defaults.openRouterKey != null) {
            "OPENROUTER_API_KEY is required to construct LlmRouter — set env var before server boot"
        }
        require(defaults.groqKey != null) {
            "GROQ_API_KEY is required (lead provider for MODERATION chain) — set env var before server boot"
        }

        val providerFactory = ProviderFactory(
            baseClient = httpClient,
            credentialStore = credentialStore,
            defaults = defaults,
        )

        val providers: Map<ProviderId, ProviderCallable> = mapOf(
            ProviderId("openrouter") to ProviderCallable { req, model ->
                providerFactory.openRouterFor(req.subjectId).call(req, model)
            },
            ProviderId("anthropic") to ProviderCallable { req, model ->
                providerFactory.anthropicFor(req.subjectId).call(req, model)
            },
            ProviderId("gemini") to ProviderCallable { req, model ->
                providerFactory.geminiFor(req.subjectId).call(req, model)
            },
            ProviderId("groq") to ProviderCallable { req, model ->
                providerFactory.groqFor(req.subjectId).call(req, model)
            },
            // ClaudeMax CLI is desktop-only — not wired on :server.
        )

        return LlmRouter(
            config = config,
            providers = providers,
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = auditLog,
        )
    }
}

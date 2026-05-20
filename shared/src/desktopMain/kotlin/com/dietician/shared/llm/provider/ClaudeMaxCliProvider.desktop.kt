package com.dietician.shared.llm.provider

import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.ProviderId
import com.dietician.shared.llm.Role
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * ClaudeMax CLI provider — desktop actual. Council 1779276774 rewrite.
 *
 * Each [call] spawns a fresh one-shot `claude -p --output-format json` process
 * via [ClaudeCliRunner]. There is NO warm-pool: `claude -p` reads stdin to EOF,
 * answers once, and exits, so a process cannot be reused — pooling was never
 * possible against this CLI.
 *
 * Failure is loud: a non-zero CLI exit, a timeout, or a non-success result
 * envelope throws an [LlmError] so [circuitBreaker] records the failure and the
 * audit trail sees it. An empty or failed run must never look like a successful
 * empty answer — that error-swallowing was the original empty-Coach-reply bug.
 *
 * RC2 construction: the primary constructor is `internal`; callers go through
 * the [forTesting] / [production] companion factories.
 */
actual class ClaudeMaxCliProvider internal constructor(
    private val runner: ClaudeCliRunner,
    private val parser: ClaudeMaxJsonParser,
    private val circuitBreaker: CircuitBreaker,
    private val timeoutMs: Long,
) {
    actual suspend fun call(request: LlmRequest, model: String): LlmResponse {
        if (circuitBreaker.isOpen()) {
            throw LlmError.ProviderUnavailable(ProviderId(PROVIDER_ID))
        }
        return try {
            val result = withTimeout(timeoutMs) {
                runner.run(buildArgs(request, model), encodePrompt(request))
            }
            if (result.exitCode != 0) {
                throw LlmError.TransientFailure(
                    IllegalStateException(
                        "claude CLI exited ${result.exitCode}: ${result.stdout.take(500)}",
                    ),
                )
            }
            val response = parser.parse(result.stdout, model)
            circuitBreaker.recordSuccess()
            response
        } catch (_: TimeoutCancellationException) {
            circuitBreaker.recordFailure()
            throw LlmError.Timeout(PROVIDER_ID)
        } catch (e: LlmError) {
            circuitBreaker.recordFailure()
            throw e
        } catch (e: Throwable) {
            circuitBreaker.recordFailure()
            throw LlmError.TransientFailure(e)
        }
    }

    actual fun isAvailable(): Boolean = !circuitBreaker.isOpen()

    companion object {
        const val PROVIDER_ID = "claudemax-cli"

        /** Test entry — inject a [FakeClaudeCliRunner]. */
        fun forTesting(
            runner: ClaudeCliRunner,
            timeoutMs: Long = 5_000,
            circuitBreaker: CircuitBreaker = CircuitBreaker(failureThreshold = 5, resetTimeoutMs = 30_000),
            parser: ClaudeMaxJsonParser = ClaudeMaxJsonParser(),
        ): ClaudeMaxCliProvider = ClaudeMaxCliProvider(runner, parser, circuitBreaker, timeoutMs)

        /** Production entry — real `claude` subprocess, 120s timeout. */
        fun production(): ClaudeMaxCliProvider =
            ClaudeMaxCliProvider(
                runner = ProcessClaudeCliRunner(),
                parser = ClaudeMaxJsonParser(),
                circuitBreaker = CircuitBreaker(failureThreshold = 5, resetTimeoutMs = 30_000),
                timeoutMs = 120_000,
            )

        /**
         * One-shot argv. Context-isolation flags (council 1779276774): no MCP
         * servers, no slash-command skills, and per-machine system-prompt
         * sections excluded — the Coach call carries only its own prompt, not
         * the host's project context.
         */
        private fun buildArgs(request: LlmRequest, model: String): List<String> = buildList {
            add("-p")
            add("--output-format")
            add("json")
            add("--exclude-dynamic-system-prompt-sections")
            add("--strict-mcp-config")
            add("--disable-slash-commands")
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                add("--append-system-prompt")
                add(it)
            }
            model.takeIf { it.isNotBlank() }?.let {
                add("--model")
                add(it)
            }
        }

        private fun encodePrompt(request: LlmRequest): String {
            require(request.messages.all { it.role == Role.USER }) {
                "ClaudeMax one-shot expects USER-only messages; got ${request.messages.map { it.role }}"
            }
            return request.messages.joinToString("\n") { it.content }
        }
    }
}

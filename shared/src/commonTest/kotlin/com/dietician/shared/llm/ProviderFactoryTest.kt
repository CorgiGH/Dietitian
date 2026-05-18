package com.dietician.shared.llm

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Plan-2 Task 29 — ProviderFactory contract tests.
 *
 * Key assertions:
 *   - subject's BYOK key takes precedence over operator defaults.
 *   - missing BYOK falls back to defaults (operator-provided env key).
 *   - factory yields per-request provider instances (stateless reuse OK).
 */
class ProviderFactoryTest {

    private val cannedOpenRouter = """
        {
          "id":"x","model":"anthropic/claude-sonnet-4.5",
          "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
          "usage":{"prompt_tokens":3,"completion_tokens":1,"total_tokens":4}
        }
    """.trimIndent()

    private fun httpClient(captureHeaders: MutableList<String?>): HttpClient {
        val engine = MockEngine { req ->
            captureHeaders.add(req.headers[HttpHeaders.Authorization])
            respond(
                content = cannedOpenRouter,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

    @Test
    fun `openRouterFor uses subject BYOK key over default`() = runTest {
        val capturedHeaders = mutableListOf<String?>()
        val client = httpClient(capturedHeaders)
        val factory = ProviderFactory(
            baseClient = client,
            credentialStore = InMemorySubjectCredentialStore(
                mapOf(("victor" to ProviderId("openrouter")) to "sk-byok-victor"),
            ),
            defaults = ProviderConfigDefaults(
                openRouterKey = "sk-operator-default",
                anthropicKey = null,
                geminiKey = null,
                groqKey = null,
                openRouterBaseUrl = "https://openrouter.ai/api/v1",
            ),
        )
        val provider = factory.openRouterFor("victor")
        provider.call(
            LlmRequest(
                subjectId = "victor",
                task = TaskType.TEXT,
                deviceClass = DeviceClass.VICTOR_DESKTOP,
                capability = Capability.NON_STREAMING,
                messages = listOf(LlmMessage(Role.USER, "hi")),
            ),
            "anthropic/claude-sonnet-4.5",
        )
        capturedHeaders.last() shouldBe "Bearer sk-byok-victor"
    }

    @Test
    fun `openRouterFor falls back to operator default when subject has no BYOK`() = runTest {
        val capturedHeaders = mutableListOf<String?>()
        val client = httpClient(capturedHeaders)
        val factory = ProviderFactory(
            baseClient = client,
            credentialStore = InMemorySubjectCredentialStore(emptyMap()),
            defaults = ProviderConfigDefaults(
                openRouterKey = "sk-operator-default",
                anthropicKey = null,
                geminiKey = null,
                groqKey = null,
                openRouterBaseUrl = "https://openrouter.ai/api/v1",
            ),
        )
        val provider = factory.openRouterFor("any-subject")
        provider.call(
            LlmRequest(
                subjectId = "any-subject",
                task = TaskType.TEXT,
                deviceClass = DeviceClass.VICTOR_DESKTOP,
                capability = Capability.NON_STREAMING,
                messages = listOf(LlmMessage(Role.USER, "hi")),
            ),
            "anthropic/claude-sonnet-4.5",
        )
        capturedHeaders.last() shouldBe "Bearer sk-operator-default"
    }
}

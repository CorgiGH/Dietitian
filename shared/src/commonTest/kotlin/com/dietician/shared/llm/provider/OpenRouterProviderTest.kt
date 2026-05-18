package com.dietician.shared.llm.provider

import com.dietician.shared.llm.AttachmentRef
import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.DeviceClass
import com.dietician.shared.llm.LlmAttachment
import com.dietician.shared.llm.LlmMessage
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.ProviderConfig
import com.dietician.shared.llm.Role
import com.dietician.shared.llm.TaskType
import com.dietician.shared.llm.Timeouts
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OpenRouterProviderTest {
    private fun mockClient(
        captureBody: (String) -> Unit = {},
        respondJson: String,
    ): HttpClient {
        val engine = MockEngine { req ->
            captureBody(req.body.toByteArray().decodeToString())
            respond(
                content = respondJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
    }

    private val cfg = ProviderConfig(
        apiKey = "test-key",
        baseUrl = "https://openrouter.test",
        timeouts = Timeouts(),
    )

    private val okResponse = """
        {
          "id":"x","model":"anthropic/claude-sonnet-4.5",
          "choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}],
          "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}
        }
    """.trimIndent()

    @Test
    fun `text request emits string content not array`() = runTest {
        var body = ""
        val provider = OpenRouterProvider(mockClient({ body = it }, okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hello")),
        )
        val resp = provider.call(req, "anthropic/claude-sonnet-4.5")
        resp.text shouldBe "hello"
        body shouldContain "\"content\":\"hello\""
    }

    @Test
    fun `RC1 vision request includes image_url content part with base64 data URL`() = runTest {
        var body = ""
        val provider = OpenRouterProvider(mockClient({ body = it }, okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.VISION,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "what is in this image?")),
            attachments = listOf(
                LlmAttachment("image/jpeg", AttachmentRef.Bytes(byteArrayOf(1, 2, 3, 4, 5))),
            ),
        )
        val resp = provider.call(req, "anthropic/claude-sonnet-4.5")
        resp.text shouldBe "hello"
        body shouldContain "\"type\":\"image_url\""
        body shouldContain "data:image/jpeg;base64,"
        body shouldContain "\"type\":\"text\""
        body shouldContain "what is in this image?"
    }

    @Test
    fun `system prompt emitted as separate message`() = runTest {
        var body = ""
        val provider = OpenRouterProvider(mockClient({ body = it }, okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hi")),
            systemPrompt = "you are a dietician",
        )
        provider.call(req, "anthropic/claude-sonnet-4.5")
        body shouldContain "\"role\":\"system\""
        body shouldContain "you are a dietician"
    }

    @Test
    fun `cost is computed from ModelPriceLookup not provider`() = runTest {
        val provider = OpenRouterProvider(mockClient(respondJson = okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hi")),
        )
        val resp = provider.call(req, "anthropic/claude-sonnet-4.5")
        resp.inputTokens shouldBe 10
        resp.outputTokens shouldBe 5
        // anthropic/claude-sonnet-4.5: 300c/Mtok in, 1500c/Mtok out → 10*300/1M + 5*1500/1M = 0
        // (integer truncation expected; sub-cent rolls into provider margin).
        resp.costCents shouldBe 0
    }

    @Test
    fun `user authorization header is set`() = runTest {
        val engine = MockEngine { req ->
            val auth = req.headers[HttpHeaders.Authorization]
            auth shouldBe "Bearer test-key"
            respond(
                content = okResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val provider = OpenRouterProvider(client, cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hi")),
        )
        provider.call(req, "anthropic/claude-sonnet-4.5")
    }
}

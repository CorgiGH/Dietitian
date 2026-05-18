package com.dietician.shared.llm.provider

import com.dietician.shared.llm.AttachmentRef
import com.dietician.shared.llm.CacheControl
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
import io.kotest.matchers.string.shouldNotContain
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

class AnthropicProviderTest {
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
        return HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

    private val cfg = ProviderConfig(
        apiKey = "sk-ant-test",
        baseUrl = "https://api.anthropic.test",
        timeouts = Timeouts(),
    )

    private val okResponse = """
        {
          "id":"msg_x","model":"anthropic/claude-sonnet-4.5","role":"assistant",
          "content":[{"type":"text","text":"hello"}],
          "stop_reason":"end_turn",
          "usage":{"input_tokens":10,"output_tokens":5,"cache_read_input_tokens":2,"cache_creation_input_tokens":3}
        }
    """.trimIndent()

    @Test
    fun `text request emits text content block only`() = runTest {
        var body = ""
        val provider = AnthropicProvider(mockClient({ body = it }, okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hello")),
        )
        val resp = provider.call(req, "anthropic/claude-sonnet-4.5")
        resp.text shouldBe "hello"
        body shouldContain "\"type\":\"text\""
        body shouldNotContain "\"type\":\"image\""
    }

    @Test
    fun `RC1 vision request includes image content block with base64 source`() = runTest {
        var body = ""
        val provider = AnthropicProvider(mockClient({ body = it }, okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.VISION,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "what is in this image?")),
            attachments = listOf(
                LlmAttachment("image/jpeg", AttachmentRef.Bytes(byteArrayOf(1, 2, 3))),
            ),
        )
        provider.call(req, "anthropic/claude-sonnet-4.5")
        body shouldContain "\"type\":\"image\""
        body shouldContain "\"media_type\":\"image/jpeg\""
        body shouldContain "\"type\":\"base64\""
        body shouldContain "\"data\":\""
        body shouldContain "what is in this image?"
    }

    @Test
    fun `system prompt routed to top-level system field`() = runTest {
        var body = ""
        val provider = AnthropicProvider(mockClient({ body = it }, okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hi")),
            systemPrompt = "you are a dietician",
        )
        provider.call(req, "anthropic/claude-sonnet-4.5")
        body shouldContain "\"system\":\"you are a dietician\""
        // SYSTEM messages MUST NOT appear in the messages array (Anthropic rejects role=system inline).
        body shouldNotContain "\"role\":\"system\""
    }

    @Test
    fun `RC10 cache token observability surfaced in response`() = runTest {
        val provider = AnthropicProvider(mockClient(respondJson = okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hi")),
        )
        val resp = provider.call(req, "anthropic/claude-sonnet-4.5")
        resp.cacheReadTokens shouldBe 2
        resp.cacheWriteTokens shouldBe 3
    }

    // -------------------------------------------------------------------------
    // Plan-2 Task 25 — prompt caching (cache_control: ephemeral)
    // -------------------------------------------------------------------------

    private fun longUserContent(): String =
        // ~6000 chars / 4 ≈ 1500 tokens, comfortably above the 1024-token Sonnet
        // minimum for prompt caching.
        "Recipe analysis: " + "garbanzo bean tagine with preserved lemon, ".repeat(120)

    @Test
    fun `cache_control ephemeral stamped on system block when payload exceeds minimum`() = runTest {
        var body = ""
        val provider = AnthropicProvider(mockClient({ body = it }, okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, longUserContent())),
            systemPrompt = "you are a dietician " + "with deep knowledge of romanian cuisine ".repeat(50),
            cacheControl = CacheControl.EPHEMERAL,
        )
        provider.call(req, "anthropic/claude-sonnet-4.5")
        // System emitted as an ARRAY (not a string) when cache_control is present.
        body shouldContain "\"system\":["
        body shouldContain "\"cache_control\":{\"type\":\"ephemeral\"}"
        // Last user-message text block also gets cache_control.
        body shouldContain "\"type\":\"text\""
    }

    @Test
    fun `cache_control omitted when payload below minimum cacheable size`() = runTest {
        var body = ""
        val provider = AnthropicProvider(mockClient({ body = it }, okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "tiny")),
            systemPrompt = "short",
            cacheControl = CacheControl.EPHEMERAL,
        )
        provider.call(req, "anthropic/claude-sonnet-4.5")
        // cache_control field may serialize as null when not active — but it MUST NOT
        // carry the ephemeral marker.
        body shouldNotContain "\"cache_control\":{"
        body shouldNotContain "\"type\":\"ephemeral\""
        // System still serialized as a plain string when caching is disabled.
        body shouldContain "\"system\":\"short\""
    }

    @Test
    fun `cache_control omitted when CacheControl is NONE even with long payload`() = runTest {
        var body = ""
        val provider = AnthropicProvider(mockClient({ body = it }, okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, longUserContent())),
            systemPrompt = "long " + "system prompt ".repeat(200),
            cacheControl = CacheControl.NONE,
        )
        provider.call(req, "anthropic/claude-sonnet-4.5")
        body shouldNotContain "\"cache_control\":{"
        body shouldNotContain "\"type\":\"ephemeral\""
    }

    @Test
    fun `haiku model uses 2048-token minimum for cache eligibility`() = runTest {
        var body = ""
        val provider = AnthropicProvider(mockClient({ body = it }, okResponse), cfg)
        // ~1500 tokens — above Sonnet 1024 minimum BUT below Haiku 2048 minimum.
        val medium = "x".repeat(6000)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.MODERATION,
            deviceClass = DeviceClass.SERVER,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, medium)),
            cacheControl = CacheControl.EPHEMERAL,
        )
        provider.call(req, "anthropic/claude-3.5-haiku")
        // Below Haiku 2048 minimum → no ephemeral cache_control payload.
        body shouldNotContain "\"cache_control\":{"
        body shouldNotContain "\"type\":\"ephemeral\""
    }

    @Test
    fun `x-api-key header and anthropic-version header are set`() = runTest {
        val engine = MockEngine { req ->
            req.headers["x-api-key"] shouldBe "sk-ant-test"
            req.headers["anthropic-version"] shouldBe "2023-06-01"
            respond(
                content = okResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val provider = AnthropicProvider(client, cfg)
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

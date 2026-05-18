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

class GeminiProviderTest {
    private fun mockClient(
        captureBody: (String) -> Unit = {},
        captureUrl: (String) -> Unit = {},
        respondJson: String,
    ): HttpClient {
        val engine = MockEngine { req ->
            captureBody(req.body.toByteArray().decodeToString())
            captureUrl(req.url.toString())
            respond(
                content = respondJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

    private val cfg = ProviderConfig(
        apiKey = "gemini-test-key",
        baseUrl = "https://generativelanguage.googleapis.test",
        timeouts = Timeouts(),
    )

    private val okResponse = """
        {
          "candidates":[{"content":{"role":"model","parts":[{"text":"hello"}]},"finishReason":"STOP"}],
          "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"totalTokenCount":15},
          "modelVersion":"gemini-2.5-pro"
        }
    """.trimIndent()

    @Test
    fun `text request emits text-only part`() = runTest {
        var body = ""
        val provider = GeminiProvider(mockClient({ body = it }, respondJson = okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hello")),
        )
        val resp = provider.call(req, "google/gemini-2.5-pro")
        resp.text shouldBe "hello"
        body shouldContain "\"text\":\"hello\""
    }

    @Test
    fun `RC1 vision request includes inline_data part with mime_type and base64`() = runTest {
        var body = ""
        val provider = GeminiProvider(mockClient({ body = it }, respondJson = okResponse), cfg)
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
        provider.call(req, "google/gemini-2.5-pro")
        body shouldContain "\"inline_data\""
        body shouldContain "\"mime_type\":\"image/jpeg\""
        body shouldContain "\"data\":"
        body shouldContain "what is in this image?"
    }

    @Test
    fun `system prompt routed to systemInstruction field`() = runTest {
        var body = ""
        val provider = GeminiProvider(mockClient({ body = it }, respondJson = okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hi")),
            systemPrompt = "you are a dietician",
        )
        provider.call(req, "google/gemini-2.5-pro")
        body shouldContain "\"systemInstruction\""
        body shouldContain "you are a dietician"
    }

    @Test
    fun `api key sent as query parameter`() = runTest {
        var url = ""
        val provider = GeminiProvider(mockClient(captureUrl = { url = it }, respondJson = okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hi")),
        )
        provider.call(req, "google/gemini-2.5-pro")
        url shouldContain "key=gemini-test-key"
        url shouldContain "google/gemini-2.5-pro:generateContent"
    }

    @Test
    fun `assistant role maps to model in gemini`() = runTest {
        var body = ""
        val provider = GeminiProvider(mockClient({ body = it }, respondJson = okResponse), cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(
                LlmMessage(Role.USER, "hi"),
                LlmMessage(Role.ASSISTANT, "previous"),
                LlmMessage(Role.USER, "next"),
            ),
        )
        provider.call(req, "google/gemini-2.5-pro")
        body shouldContain "\"role\":\"model\""
        body shouldContain "\"role\":\"user\""
    }
}

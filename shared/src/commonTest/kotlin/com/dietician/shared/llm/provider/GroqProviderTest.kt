package com.dietician.shared.llm.provider

import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.DeviceClass
import com.dietician.shared.llm.LlmMessage
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.ProviderConfig
import com.dietician.shared.llm.ProviderId
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

class GroqProviderTest {
    private val okResponse = """
        {
          "id":"x","model":"llama-3.3-70b-versatile",
          "choices":[{"index":0,"message":{"role":"assistant","content":"hi"},"finish_reason":"stop"}],
          "usage":{"prompt_tokens":7,"completion_tokens":3,"total_tokens":10}
        }
    """.trimIndent()

    @Test
    fun `Groq delegates to OpenAI-compat shape and tags audit with groq provider id`() = runTest {
        var url = ""
        var body = ""
        val engine = MockEngine { req ->
            url = req.url.toString()
            body = req.body.toByteArray().decodeToString()
            respond(okResponse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val cfg = ProviderConfig(
            apiKey = "groq-test-key",
            baseUrl = "https://api.groq.com/openai/v1",
            timeouts = Timeouts(),
        )
        val provider = GroqProvider(client, cfg)
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.MODERATION,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "moderate this")),
        )
        val resp = provider.call(req, "llama-3.3-70b-versatile")
        resp.provider shouldBe ProviderId("groq")
        resp.text shouldBe "hi"
        url shouldContain "api.groq.com/openai/v1/chat/completions"
        body shouldContain "\"model\":\"llama-3.3-70b-versatile\""
    }
}

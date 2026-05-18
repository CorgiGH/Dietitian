package com.dietician.shared.llm.provider

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

class OllamaProviderImplTest {
    private val okEmbed = """{"embedding":[0.1, -0.2, 0.3]}"""

    @Test
    fun `embed posts prompt and returns FloatArray`() = runTest {
        var url = ""
        var body = ""
        val engine = MockEngine { req ->
            url = req.url.toString()
            body = req.body.toByteArray().decodeToString()
            respond(okEmbed, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        val provider = OllamaProviderImpl(client)
        val vec = provider.embed("hello world", "bge-m3")
        url shouldContain "localhost:11434/api/embeddings"
        body shouldContain "\"model\":\"bge-m3\""
        body shouldContain "\"prompt\":\"hello world\""
        vec.size shouldBe 3
    }

    @Test
    fun `isAvailable returns true on desktop`() {
        val provider = OllamaProviderImpl(HttpClient { })
        provider.isAvailable() shouldBe true
    }

    @Test
    fun `NoOpOllamaProvider reports unavailable and throws on embed`() = runTest {
        NoOpOllamaProvider.isAvailable() shouldBe false
        try {
            NoOpOllamaProvider.embed("x", "y")
            error("expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // ok
        }
    }
}

package com.dietician.shared.llm.provider

import com.dietician.shared.llm.FinishReason
import com.dietician.shared.llm.ProviderId
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ClaudeMaxStreamParserTest {
    private val parser = ClaudeMaxStreamParser()

    @Test
    fun `parses full stream-json event sequence into LlmResponse`() {
        val stream = """
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_1","model":"claude-sonnet-4.5","usage":{"input_tokens":10}}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello "}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"world"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":5}}

            event: message_stop
            data: {"type":"message_stop"}
        """.trimIndent()
        val r = parser.parse(stream, "anthropic/claude-sonnet-4.5")
        r.text shouldBe "Hello world"
        r.inputTokens shouldBe 10
        r.outputTokens shouldBe 5
        r.finishReason shouldBe FinishReason.STOP
        r.model shouldBe "claude-sonnet-4.5"
        r.provider shouldBe ProviderId("claudemax-cli")
    }

    @Test
    fun `bare JSON lines without event prefix also parse`() {
        val stream = """
            {"type":"message_start","message":{"id":"m","model":"claude-3-5-sonnet-latest","usage":{"input_tokens":3}}}
            {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}
            {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1}}
            {"type":"message_stop"}
        """.trimIndent()
        val r = parser.parse(stream, "claude-3-5-sonnet-latest")
        r.text shouldBe "ok"
        r.inputTokens shouldBe 3
        r.outputTokens shouldBe 1
    }

    @Test
    fun `max_tokens stop_reason maps to MAX_TOKENS`() {
        val stream = """
            {"type":"message_start","message":{"id":"m","model":"x","usage":{"input_tokens":1}}}
            {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"long..."}}
            {"type":"message_delta","delta":{"stop_reason":"max_tokens"},"usage":{"output_tokens":4096}}
            {"type":"message_stop"}
        """.trimIndent()
        val r = parser.parse(stream, "anthropic/claude-sonnet-4.5")
        r.finishReason shouldBe FinishReason.MAX_TOKENS
    }

    @Test
    fun `tool_use stop_reason maps to TOOL_USE`() {
        val stream = """
            {"type":"message_start","message":{"id":"m","model":"x","usage":{"input_tokens":1}}}
            {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":1}}
            {"type":"message_stop"}
        """.trimIndent()
        val r = parser.parse(stream, "anthropic/claude-sonnet-4.5")
        r.finishReason shouldBe FinishReason.TOOL_USE
    }

    @Test
    fun `empty stream returns empty text with zero tokens`() {
        val r = parser.parse("", "anthropic/claude-sonnet-4.5")
        r.text shouldBe ""
        r.inputTokens shouldBe 0
        r.outputTokens shouldBe 0
        r.finishReason shouldBe FinishReason.STOP
    }

    @Test
    fun `cost is computed via ModelPriceLookup`() {
        val stream = """
            {"type":"message_start","message":{"id":"m","model":"claude-3-5-sonnet-latest","usage":{"input_tokens":1000}}}
            {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"x"}}
            {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1000}}
            {"type":"message_stop"}
        """.trimIndent()
        val r = parser.parse(stream, "claude-3-5-sonnet-latest")
        // 1000 in * 300 / 1M = 0 (trunc), 1000 out * 1500 / 1M = 1 → total 1 cent.
        r.costCents shouldBe 1
    }

    @Test
    fun `malformed lines are skipped not thrown`() {
        val stream = """
            not-json-at-all
            event: noise
            data: {"type":"message_start","message":{"id":"m","model":"x","usage":{"input_tokens":1}}}
            data: {malformed json
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1}}
            {"type":"message_stop"}
        """.trimIndent()
        val r = parser.parse(stream, "anthropic/claude-sonnet-4.5")
        r.text shouldBe "ok"
    }
}

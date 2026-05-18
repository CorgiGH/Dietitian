package com.dietician.shared.llm

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class RoutingRulesTest {
    private fun req(task: TaskType, device: DeviceClass): LlmRequest =
        LlmRequest(
            subjectId = "victor",
            task = task,
            deviceClass = device,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "ping")),
        )

    @Test
    fun `VICTOR_DESKTOP TEXT hits VICTOR_DESKTOP_TEXT chain`() {
        val chain = RoutingRules.selectChain(
            DefaultRouterConfig.default,
            req(TaskType.TEXT, DeviceClass.VICTOR_DESKTOP),
        )
        chain shouldBe DefaultRouterConfig.VICTOR_DESKTOP_TEXT
    }

    @Test
    fun `FRIEND_PHONE MODERATION hits FRIEND_MODERATION chain`() {
        val chain = RoutingRules.selectChain(
            DefaultRouterConfig.default,
            req(TaskType.MODERATION, DeviceClass.FRIEND_PHONE),
        )
        chain shouldBe DefaultRouterConfig.FRIEND_MODERATION
    }

    @Test
    fun `SERVER EMBEDDING hits SERVER_EMBEDDING chain`() {
        val chain = RoutingRules.selectChain(
            DefaultRouterConfig.default,
            req(TaskType.EMBEDDING, DeviceClass.SERVER),
        )
        chain shouldBe DefaultRouterConfig.SERVER_EMBEDDING
    }

    @Test
    fun `ANY deviceClass falls through to VICTOR_DESKTOP_TEXT when no exact match`() {
        // Default config has no ChainKey(ANY, TEXT) — fallback `when` returns VICTOR_DESKTOP_TEXT.
        val chain = RoutingRules.selectChain(
            DefaultRouterConfig.default,
            req(TaskType.TEXT, DeviceClass.ANY),
        )
        chain shouldBe DefaultRouterConfig.VICTOR_DESKTOP_TEXT
    }

    @Test
    fun `VISION (placeholder) routes to text chain until Batch B vision wiring lands`() {
        val chain = RoutingRules.selectChain(
            DefaultRouterConfig.default,
            req(TaskType.VISION, DeviceClass.VICTOR_DESKTOP),
        )
        chain shouldBe DefaultRouterConfig.VICTOR_DESKTOP_TEXT
    }

    @Test
    fun `caller-supplied override beats default chains`() {
        val customProvider = LlmProvider.Ollama(
            id = ProviderId("ollama"),
            model = "llama3.1:8b",
            endpoint = "http://localhost:11434",
        )
        val customConfig = RouterConfig(
            chains = mapOf(
                ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.TEXT) to listOf(customProvider),
            ),
        )
        val chain = RoutingRules.selectChain(customConfig, req(TaskType.TEXT, DeviceClass.VICTOR_DESKTOP))
        chain.size shouldBe 1
        chain[0].shouldBeInstanceOf<LlmProvider.Ollama>()
    }

    @Test
    fun `empty config falls back to defaults exhaustively per task and device class`() {
        val empty = RouterConfig(chains = emptyMap())
        TaskType.values().forEach { task ->
            DeviceClass.values().forEach { device ->
                val chain = RoutingRules.selectChain(empty, req(task, device))
                // Every (task, device) returns a non-empty chain — exhaustive `when` proved.
                (chain.isNotEmpty()) shouldBe true
            }
        }
    }
}

package com.dietician.server.coach

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CoachDtosTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `reserve request round-trips`() {
        val payload =
            CoachReserveRequest(
                idempotencyKey = "11111111-1111-1111-1111-111111111111",
                prompt = "How do I hit 2750 kcal with chicken + rice?",
                locale = "en",
                provider = "claudemax",
                estimatedCostCents = 5,
                reservationTtlSeconds = 60,
            )
        val s = json.encodeToString(CoachReserveRequest.serializer(), payload)
        val back = json.decodeFromString(CoachReserveRequest.serializer(), s)
        assertEquals(payload, back)
    }

    @Test
    fun `reserve response carries reservationId + auditId + redactedPrompt`() {
        val r =
            CoachReserveResponse(
                reservationId = "22222222-2222-2222-2222-222222222222",
                auditId = "33333333-3333-3333-3333-333333333333",
                redactedPromptHash = "deadbeef",
                reservedUntilEpochMs = 1_780_000_000_000L,
            )
        val s = json.encodeToString(CoachReserveResponse.serializer(), r)
        val back = json.decodeFromString(CoachReserveResponse.serializer(), s)
        assertEquals(r, back)
    }

    @Test
    fun `reserve rejected envelope carries reason + budget snapshot`() {
        val r =
            CoachReserveRejected(
                reason = "over_budget",
                capUsd = 5.00,
                spentUsd = 5.00,
            )
        val s = json.encodeToString(CoachReserveRejected.serializer(), r)
        val back = json.decodeFromString(CoachReserveRejected.serializer(), s)
        assertEquals(r, back)
    }

    @Test
    fun `commit request usage fields are required`() {
        val req =
            CoachCommitRequest(
                idempotencyKey = "11111111-1111-1111-1111-111111111111",
                status = "success",
                promptTokens = 42,
                completionTokens = 100,
                costCents = 5,
                provider = "claudemax",
                latencyMs = 3200,
                responseHash = "cafef00d",
            )
        val s = json.encodeToString(CoachCommitRequest.serializer(), req)
        val back = json.decodeFromString(CoachCommitRequest.serializer(), s)
        assertEquals(req, back)
    }

    @Test
    fun `commit response round-trips`() {
        val r = CoachCommitResponse(auditId = "a", status = "success")
        val s = json.encodeToString(CoachCommitResponse.serializer(), r)
        val back = json.decodeFromString(CoachCommitResponse.serializer(), s)
        assertEquals(r, back)
    }

    @Test
    fun `stream request round-trips`() {
        val req =
            CoachStreamRequest(
                idempotencyKey = "11111111-1111-1111-1111-111111111111",
                prompt = "what to eat for protein",
                locale = "ro",
            )
        val s = json.encodeToString(CoachStreamRequest.serializer(), req)
        val back = json.decodeFromString(CoachStreamRequest.serializer(), s)
        assertEquals(req, back)
    }
}

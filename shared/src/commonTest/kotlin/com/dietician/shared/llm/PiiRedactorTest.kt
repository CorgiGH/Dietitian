package com.dietician.shared.llm

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

class PiiRedactorTest {
    private val redactor = PiiRedactor()

    @Test
    fun `redacts email`() {
        val r = redactor.redact("Contact me at victor.vasiloi@gmail.com please")
        r.text shouldContain "[REDACTED_EMAIL]"
        r.text shouldNotContain "victor.vasiloi@gmail.com"
        r.entities.map { it.label } shouldContain "EMAIL"
        r.entities.first { it.label == "EMAIL" }.original shouldBe "victor.vasiloi@gmail.com"
    }

    @Test
    fun `redacts Romanian phone international form`() {
        val r = redactor.redact("Call +40721234567 anytime")
        r.text shouldContain "[REDACTED_PHONE]"
        r.text shouldNotContain "+40721234567"
        r.entities.map { it.label } shouldContain "PHONE"
    }

    @Test
    fun `redacts Romanian phone national form`() {
        val r = redactor.redact("Sun la 0721234567 ma rog")
        r.text shouldContain "[REDACTED_PHONE]"
        r.entities.first { it.label == "PHONE" }.original shouldBe "0721234567"
    }

    @Test
    fun `redacts CNP`() {
        // 13 digits, first digit ∈ {1,2,5,6,7}.
        val r = redactor.redact("CNP-ul meu este 1900101226789 oficial")
        r.text shouldContain "[REDACTED_CNP]"
        r.entities.map { it.label } shouldContain "CNP"
    }

    @Test
    fun `non-CNP-leading digit does not match CNP`() {
        // 13 digits starting with 9 (invalid CNP century marker) → no CNP match.
        val r = redactor.redact("Random 9900101226789 number")
        r.entities.none { it.label == "CNP" } shouldBe true
    }

    @Test
    fun `redacts Romanian IBAN`() {
        val r = redactor.redact("Transfer to RO49AAAA1B31007593840000")
        r.text shouldContain "[REDACTED_IBAN]"
        r.entities.first { it.label == "IBAN" }.original shouldBe "RO49AAAA1B31007593840000"
    }

    @Test
    fun `redacts PERSON_PREFIX honorific`() {
        val r = redactor.redact("Vorbiti cu dl Popescu si doamna Maria")
        r.text shouldContain "[REDACTED_PERSON_PREFIX]"
        r.entities.count { it.label == "PERSON_PREFIX" } shouldBe 2
    }

    @Test
    fun `idempotent re-redaction is a no-op`() {
        val once = redactor.redact("Email me at user@example.com")
        val twice = redactor.redact(once.text)
        twice.text shouldBe once.text
        twice.entities.size shouldBe 0
    }

    @Test
    fun `multi-pattern hit returns all entities`() {
        // IBAN: RO + 2 check digits + 4-letter bank + 16-char BBAN = 24 chars total.
        val r = redactor.redact(
            "Sun +40721234567 sau scrie la a@b.co — CNP 2900101226789 IBAN RO12BTRL1234567890123456",
        )
        val labels = r.entities.map { it.label }.toSet()
        labels shouldContain "PHONE"
        labels shouldContain "EMAIL"
        labels shouldContain "CNP"
        labels shouldContain "IBAN"
        r.text shouldContain "[REDACTED_PHONE]"
        r.text shouldContain "[REDACTED_EMAIL]"
        r.text shouldContain "[REDACTED_CNP]"
        r.text shouldContain "[REDACTED_IBAN]"
    }

    @Test
    fun `empty input yields empty entities`() {
        val r = redactor.redact("")
        r.text shouldBe ""
        r.entities.size shouldBe 0
    }

    @Test
    fun `text without PII passes through unchanged`() {
        val r = redactor.redact("Today I ate quinoa and grilled chicken.")
        r.text shouldBe "Today I ate quinoa and grilled chicken."
        r.entities.size shouldBe 0
    }

    @Test
    fun `multiple emails all redacted`() {
        val r = redactor.redact("Send to a@x.co and b@y.org")
        r.entities.count { it.label == "EMAIL" } shouldBe 2
    }
}

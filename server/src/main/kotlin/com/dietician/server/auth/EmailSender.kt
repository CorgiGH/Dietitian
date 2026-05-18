package com.dietician.server.auth

/**
 * Abstract mail-sending surface so [AuthService] is testable without a
 * live Resend account. Production wiring: [ResendClient].
 */
interface EmailSender {
    /**
     * Sends an HTML email. Returns true if the provider accepted the message,
     * false if it rejected (rate-limit, bad address, etc.). Throws on
     * transport failure — caller decides whether to retry.
     */
    suspend fun send(
        to: String,
        from: String,
        subject: String,
        htmlBody: String,
    ): Boolean
}

/**
 * No-op sender for tests that don't exercise the email path.
 */
class NoopEmailSender : EmailSender {
    val sent = mutableListOf<SentRecord>()

    data class SentRecord(val to: String, val from: String, val subject: String, val htmlBody: String)

    override suspend fun send(to: String, from: String, subject: String, htmlBody: String): Boolean {
        sent += SentRecord(to, from, subject, htmlBody)
        return true
    }
}

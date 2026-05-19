package com.dietician.server.auth

import org.slf4j.LoggerFactory

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
 * No-op sender for tests + the tracer-bullet first VPS deploy when
 * `RESEND_API_KEY` is unset. Captures every send into [sent] (tests inspect
 * this) and ALSO logs the body at INFO so the operator can grep magic-link
 * tokens out of `journalctl` / `/opt/dietician/logs/backend.log` for headless
 * smoke testing (council 1779188964 tracer-bullet ruling — `MagicLinkService`
 * is in-memory only so there is no Postgres-side SQL-grep path; the log line
 * IS the operator-visible surface).
 *
 * Production wiring uses [ResendClient] instead — `DieticianModule.kt:77-84`
 * picks based on `RESEND_API_KEY` presence — so this logging is dev/CI only
 * and never fires when an API key is configured.
 */
class NoopEmailSender : EmailSender {
    private val log = LoggerFactory.getLogger(NoopEmailSender::class.java)
    val sent = mutableListOf<SentRecord>()

    data class SentRecord(val to: String, val from: String, val subject: String, val htmlBody: String)

    override suspend fun send(to: String, from: String, subject: String, htmlBody: String): Boolean {
        sent += SentRecord(to, from, subject, htmlBody)
        log.info(
            "[NoopEmailSender DEV/CI] No real email transport configured (RESEND_API_KEY unset). " +
                "Would-be email: subject={} to={} htmlBody={}",
            subject,
            to,
            htmlBody,
        )
        return true
    }
}

package com.dietician.server.audit

/**
 * Closed enumeration of `audit_log.kind` values (V018 schema).
 *
 * Plan-2 Router emits the LLM_CALL variants. Plan-3 endpoints emit the rest.
 *
 * AI Act Art 5(1)(f): the audit surface NEVER includes actions that infer
 * emotion/mood from food-logging gaps. There is no `mood_inferred`,
 * `compulsion_detected`, or `shame_signal`. The closest we go is logging the
 * EXPLICIT rule that fired (kcal-floor, trigger-phrase, variety-drop) plus
 * the user's explicit response.
 *
 * Council 1779062699 RC12 baseline: required actions for the Plan-3 first
 * batch + Plan-2 contract surface.
 */
object AuditLogActions {
    // ----- LLM (Plan-2 Router emits) -----
    const val LLM_CALL = "llm_call"
    const val LLM_CALL_PII_QUEUED_FOR_REVIEW = "llm_call_pii_queued_for_review"

    // ----- Subject lifecycle -----
    const val SUBJECT_REDACT = "subject_redact"
    const val SUBJECT_CREDENTIAL_REVOKED = "subject_credential_revoked"

    // ----- Consent -----
    const val CONSENT_GRANT = "consent_grant"
    const val CONSENT_WITHDRAW = "consent_withdraw"

    // ----- Auth -----
    const val SIGN_IN = "sign_in"
    const val SIGN_OUT_ALL_SESSIONS = "sign_out_all_sessions"

    // ----- Moderator (Plan-2) -----
    const val MODERATOR_VERDICT = "moderator_verdict"

    // ----- GDPR ops -----
    const val DSAR_EXPORT = "dsar_export"

    // ----- Cron / system -----
    const val BACKUP_COMPLETED = "backup_completed"
    const val BACKUP_FAILED = "backup_failed"
    const val AUDIT_PRUNE_COMPLETED = "audit_prune_completed"
}

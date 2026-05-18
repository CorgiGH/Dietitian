package com.dietician.server.middleware

import com.dietician.server.db.DatabaseFactory
import java.sql.Connection
import java.util.UUID

/**
 * Wraps a request-handler body in [DatabaseFactory.withSubject] so RLS
 * policies fire against the authenticated caller's subject id. Routes that
 * need per-subject DB access should compose:
 *
 *   val subjectId = call.requireSubject(auth) ?: return@post
 *   withRlsContext(db, subjectId) { conn -> ... }
 *
 * Thin shim — exists so handler code reads handler-style instead of having
 * to import DatabaseFactory directly + so it's grep-able as a single name
 * across the codebase.
 */
inline fun <T> withRlsContext(
    db: DatabaseFactory,
    subjectId: UUID,
    crossinline block: (Connection) -> T,
): T = db.withSubject(subjectId) { conn -> block(conn) }

/**
 * System-context variant — for cron / admin paths that legitimately need to
 * read NULL-tolerant RLS tables (audit_log, consents) without a subject.
 */
inline fun <T> withSystemRlsContext(
    db: DatabaseFactory,
    crossinline block: (Connection) -> T,
): T = db.withSystemContext { conn -> block(conn) }

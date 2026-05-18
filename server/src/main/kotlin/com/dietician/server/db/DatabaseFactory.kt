package com.dietician.server.db

import com.dietician.server.observability.Counters
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.Connection
import javax.sql.DataSource

/**
 * Postgres connection factory for the Dietician VPS backend.
 *
 * Wraps Hikari + Flyway + an RLS-aware [withSubject] helper. Every handler
 * MUST acquire connections through [withSubject] (or [withSystemContext]) —
 * raw `dataSource.connection` access is forbidden and policed by
 * [RlsBypassPreventionTest] (Council 1779120000 RC2).
 *
 * Env-driven constructor variant (no-arg) — defaults match the VPS layout
 * documented in `docs/runbooks/restart.md`:
 *   DIETICIAN_DB_URL       — JDBC URL
 *   DIETICIAN_DB_USER      — username
 *   DIETICIAN_DB_PASSWORD  — password (env fallback; tmpfs path preferred)
 *
 * Two-belt RLS defense:
 *   1. [withSubject] sets `SET LOCAL app.current_subject_id = ?` inside the
 *      transaction. Transaction-scoped GUC auto-resets on commit/rollback.
 *   2. Hikari `connectionInitSql = "RESET app.current_subject_id"` fires on
 *      every checkout. If a future handler somehow bypasses [withSubject],
 *      the prior tenant's GUC won't persist.
 */
class DatabaseFactory(
    private val url: String,
    private val username: String,
    private val password: String,
) {
    constructor() : this(
        url = System.getenv("DIETICIAN_DB_URL") ?: "jdbc:postgresql://127.0.0.1:5432/dietician",
        username = System.getenv("DIETICIAN_DB_USER") ?: "dietician_app",
        password = readPassword(),
    )

    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    val dataSource: DataSource

    init {
        val cfg = HikariConfig().apply {
            jdbcUrl = url
            this.username = this@DatabaseFactory.username
            this.password = this@DatabaseFactory.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30_000
            idleTimeout = 600_000
            maxLifetime = 1_800_000
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            // [Council 1779120000 RC2] Defensive reset on every connection
            // checkout. PG resets `SET LOCAL` on COMMIT but NOT on
            // connection-return. This belt guarantees a fresh GUC even if a
            // future handler misuses the pool.
            connectionInitSql = "SELECT set_config('app.current_subject_id', '', false)"
        }
        log.info("DatabaseFactory connecting to {} as {}", url, username)
        dataSource = HikariDataSource(cfg)
        runMigrations(url, username, password)
        log.info("DatabaseFactory: Flyway migrations applied; pool size {}", cfg.maximumPoolSize)
    }

    /**
     * Run [block] inside a transaction with `app.current_subject_id` set to
     * [subjectId] so RLS policies enforce row-level isolation.
     *
     * `null` subjectId = system context. RLS policies that include
     * `subject_id IS NULL OR …` (e.g. `audit_log`, `consents`) remain
     * queryable. Tables with strict per-subject policies return zero rows.
     *
     * Transaction-scoped — `SET LOCAL` auto-resets on commit/rollback.
     */
    fun <T> withSubject(subjectId: String?, block: (Connection) -> T): T {
        // `dataSource.connection` is intentionally used HERE — this is the
        // sole entry point. `RlsBypassPreventionTest` enforces no other file
        // contains this literal.
        @Suppress("ktlint:standard:property-naming")
        val ds = dataSource
        ds.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.createStatement().use { st ->
                    if (subjectId != null) {
                        // Validate UUID format defensively before interpolation.
                        // SET LOCAL doesn't accept bind parameters via JDBC
                        // prepareStatement on Postgres ≤17. UUID grammar is
                        // injection-safe but verify shape first.
                        require(subjectId.matches(UUID_RE)) {
                            "subjectId must be a UUID, got: $subjectId"
                        }
                        st.execute("SET LOCAL app.current_subject_id = '$subjectId'")
                    } else {
                        st.execute("SELECT set_config('app.current_subject_id', '', true)")
                    }
                }
                Counters.rlsContextSetTotal.increment()
                val result = block(conn)
                conn.commit()
                return result
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            }
        }
    }

    /**
     * Convenience overload accepting [java.util.UUID]. Forwards to the string
     * variant after `.toString()`.
     */
    fun <T> withSubject(subjectId: java.util.UUID, block: (Connection) -> T): T =
        withSubject(subjectId.toString(), block)

    /**
     * System-context transactions (cron, admin, bootstrap). GUC is unset so
     * tables with NULL-tolerant RLS policies (e.g. `audit_log`, `consents`)
     * remain readable; tables with strict policies return zero rows.
     */
    fun <T> withSystemContext(block: (Connection) -> T): T = withSubject(null, block)

    /**
     * Shuts the Hikari pool. Tests should call this in `@AfterAll`.
     */
    fun close() {
        (dataSource as? HikariDataSource)?.close()
    }

    companion object {
        private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        private fun readPassword(): String {
            // [Council 1779120000 RC10] Tmpfs path for VPS-restart-safe key
            // unlock. See docs/runbooks/restart.md for the operator flow.
            val tmpfs = java.io.File("/run/dietician-keys/db.passphrase")
            return if (tmpfs.exists()) tmpfs.readText().trim()
            else System.getenv("DIETICIAN_DB_PASSWORD")
                ?: error(
                    "DIETICIAN_DB_PASSWORD not set and /run/dietician-keys/db.passphrase absent — " +
                        "run /opt/dietician/bin/unlock first; see docs/runbooks/restart.md",
                )
        }
    }
}

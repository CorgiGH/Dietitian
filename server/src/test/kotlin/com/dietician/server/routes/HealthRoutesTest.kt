package com.dietician.server.routes

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.auth.MagicLinkService
import com.dietician.server.auth.NoopEmailSender
import com.dietician.server.auth.SessionStore
import com.dietician.server.cron.CronBootstrap
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.middleware.SESSION_COOKIE
import com.dietician.server.repo.HealthRepository
import com.dietician.server.repo.SubjectRepository
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for [installHealthRoutes] — Plan-3 Task 37/38.
 *
 * Boots a Testcontainer Postgres, seeds a fixed Victor subject, then drives
 * `/health/deep` (unauth) + `/diag` (Victor-only) through testApplication.
 */
@Testcontainers
class HealthRoutesTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "health_app_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS health_app")
                    st.execute("CREATE ROLE health_app LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO health_app")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO health_app")
                }
            }
            bootstrapped = true
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            dbRef?.close()
        }
    }

    private fun freshDb(): DatabaseFactory {
        bootstrap()
        val d = DatabaseFactory(pg.jdbcUrl, "health_app", APP_PW)
        dbRef = d
        return d
    }

    private fun stopKoinIfRunning() {
        if (GlobalContext.getOrNull() != null) GlobalContext.stopKoin()
    }

    private fun freshModule(db: DatabaseFactory, sessions: SessionStore, cron: CronBootstrap) = module {
        single { db }
        single { sessions }
        single { cron }
        single { SubjectRepository(get<DatabaseFactory>()) }
        single { HealthRepository(get<DatabaseFactory>()) }
        single { AuditLogWriter(get<DatabaseFactory>()) }
        single { MagicLinkService() }
        single { NoopEmailSender() }
        single {
            AuthService(
                subjects = get(),
                magicLinks = get(),
                sessions = get(),
                email = get<NoopEmailSender>(),
                audit = get(),
            )
        }
    }

    @Test
    fun `GET health deep returns aggregate with RC13 fields`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        val cron = CronBootstrap(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        application {
            install(Koin) { modules(freshModule(db, sessions, cron)) }
            install(ContentNegotiation) { json() }
            installHealthRoutes()
        }
        try {
            val resp = client.get("/health/deep")
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            // RC13 mandatory fields
            assertEquals("ok", body["status"]!!.jsonPrimitive.content)
            assertNotNull(body["tombstone_grace_stale_count"])
            assertNotNull(body["queue_depths"])
            assertTrue(body.containsKey("audit_log_last_pruned_at"))
            assertTrue(body.containsKey("embedding_provider_version"))
            assertTrue(body.containsKey("last_backup_at"))
        } finally {
            cron.shutdown()
            stopKoinIfRunning()
        }
    }

    @Test
    fun `GET diag without session returns 401`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        val cron = CronBootstrap(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        application {
            install(Koin) { modules(freshModule(db, sessions, cron)) }
            install(ContentNegotiation) { json() }
            installHealthRoutes()
        }
        try {
            val resp = client.get("/diag")
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        } finally {
            cron.shutdown()
            stopKoinIfRunning()
        }
    }

    @Test
    fun `GET diag with non-Victor session returns 403`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        val cron = CronBootstrap(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        application {
            install(Koin) { modules(freshModule(db, sessions, cron)) }
            install(ContentNegotiation) { json() }
            installHealthRoutes()
        }
        try {
            val intruderId = SubjectRepository(db).create("Intruder", "intruder-${UUID.randomUUID()}@example.com")
            val sessionId = sessions.create(intruderId).sessionId
            val resp = client.get("/diag") { cookie(SESSION_COOKIE, sessionId) }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
        } finally {
            cron.shutdown()
            stopKoinIfRunning()
        }
    }

    @Test
    fun `GET diag with Victor session returns 200 and includes cron schedule`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        val cron = CronBootstrap(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        // Schedule one job so cron_next_fires is non-empty.
        cron.schedule("audit-prune", { it.plusDays(1) }) { /* no-op */ }
        // Use the default Victor id baked into HealthRoutes.
        val victorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        application {
            install(Koin) { modules(freshModule(db, sessions, cron)) }
            install(ContentNegotiation) { json() }
            installHealthRoutes()
        }
        try {
            // Victor is pre-seeded in V013 with subject_id = 0000...0001.
            // No insert needed; just create a session for that canonical id.
            val sessionId = sessions.create(victorId).sessionId
            // Give the cron loop a moment to record next-fire.
            Thread.sleep(150)
            val resp = client.get("/diag") { cookie(SESSION_COOKIE, sessionId) }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            assertNotNull(body["schema_version"])
            assertNotNull(body["cron_next_fires_epoch_seconds"])
            // Schema version should be 'V021' or later (we just bumped to V021 in Task 33).
            val schemaVer = body["schema_version"]!!.jsonPrimitive.content
            assertTrue(schemaVer.isNotBlank(), "schema_version must be populated")
            assertTrue(
                Regex("^\\d+(\\.\\d+)?$").matches(schemaVer) || schemaVer.startsWith("V"),
                "schema_version should look like a Flyway version, got: $schemaVer",
            )
            // Audit row for the diag access was not required by spec; we don't assert it.
            // Ensure AuditLogActions is still importable (compile-only reference).
            assertNotNull(AuditLogActions.AUDIT_PRUNE_COMPLETED)
        } finally {
            cron.shutdown()
            stopKoinIfRunning()
        }
    }
}

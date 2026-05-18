package com.dietician.server.routes

import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.auth.MagicLinkService
import com.dietician.server.auth.NoopEmailSender
import com.dietician.server.auth.SessionStore
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.middleware.RateLimiter
import com.dietician.server.middleware.SESSION_COOKIE
import com.dietician.server.repo.SubjectRepository
import io.ktor.client.request.cookie
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
class ReceiptsRoutesTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "receipts_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS receipts_app_test")
                    st.execute("CREATE ROLE receipts_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO receipts_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO receipts_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "receipts_app_test", APP_PW)
        dbRef = d
        return d
    }

    private fun stopKoinIfRunning() {
        if (GlobalContext.getOrNull() != null) GlobalContext.stopKoin()
    }

    private fun createSubjectAndCookie(db: DatabaseFactory, sessions: SessionStore): Pair<UUID, String> {
        val email = "r-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("Receipts", email)
        return subjectId to sessions.create(subjectId).sessionId
    }

    private fun freshModule(db: DatabaseFactory, sessions: SessionStore) = module {
        single { db }
        single { sessions }
        single { SubjectRepository(get<DatabaseFactory>()) }
        single { AuditLogWriter(get<DatabaseFactory>()) }
        single { MagicLinkService() }
        single { RateLimiter() }
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
    fun `receipts upload accepts camera-OCR source + persists raw bytes + audit-logs`(@TempDir tmp: Path) = testApplication {
        stopKoinIfRunning()
        // Route honors DIETICIAN_LLM_RAW_DIR via env OR system property — JVM
        // can't setenv so tests use the property fallback.
        System.setProperty("DIETICIAN_LLM_RAW_DIR", tmp.toString())
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installReceiptsRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        val imgBytes = byteArrayOf(0x49, 0x46, 0x4F, 0x4F, 0x42, 0x41, 0x52)
        val resp = client.post("/receipts/upload") {
            cookie(SESSION_COOKIE, sessionId)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            imgBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"r.jpg\"")
                            },
                        )
                        append("source", "camera_ocr")
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Accepted, resp.status)
        val json = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val receiptId = json["receipt_id"]?.jsonPrimitive?.content
        assertNotNull(receiptId)
        assertEquals("queued_for_ocr", json["status"]?.jsonPrimitive?.content)
        // The raw dir should contain at least one file ending in .bin
        val rawFiles = Files.list(tmp).use { it.toList() }
        assertTrue(rawFiles.any { it.fileName.toString().endsWith(".bin") }, "raw bin file should be written")
        // Audit-log row written.
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE subject_id = '$subjectId' AND kind = 'receipt_upload'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1)
            }
        }
        stopKoinIfRunning()
    }

    @Test
    fun `receipts upload accepts mega_connect source per Council A20`(@TempDir tmp: Path) = testApplication {
        stopKoinIfRunning()
        System.setProperty("DIETICIAN_LLM_RAW_DIR", tmp.toString())
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installReceiptsRoutes()
        }
        val (_, sessionId) = createSubjectAndCookie(db, sessions)
        val resp = client.post("/receipts/upload") {
            cookie(SESSION_COOKIE, sessionId)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            byteArrayOf(0x4D, 0x45, 0x47, 0x41),
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/octet-stream")
                                append(HttpHeaders.ContentDisposition, "filename=\"m.bin\"")
                            },
                        )
                        append("source", "mega_connect")
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Accepted, resp.status)
        stopKoinIfRunning()
    }

    @Test
    fun `receipts upload without session returns 401`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installReceiptsRoutes()
        }
        val resp = client.post("/receipts/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData { append("source", "camera_ocr") },
                ),
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        stopKoinIfRunning()
    }

    @Test
    fun `receipts upload with invalid source returns 400`(@TempDir tmp: Path) = testApplication {
        stopKoinIfRunning()
        System.setProperty("DIETICIAN_LLM_RAW_DIR", tmp.toString())
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installReceiptsRoutes()
        }
        val (_, sessionId) = createSubjectAndCookie(db, sessions)
        val resp = client.post("/receipts/upload") {
            cookie(SESSION_COOKIE, sessionId)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            byteArrayOf(0x01, 0x02),
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "filename=\"x.png\"")
                            },
                        )
                        append("source", "bogus_source")
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        stopKoinIfRunning()
    }
}

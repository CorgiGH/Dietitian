package com.dietician.server.di

import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.auth.EmailSender
import com.dietician.server.auth.MagicLinkService
import com.dietician.server.auth.NoopEmailSender
import com.dietician.server.auth.ResendClient
import com.dietician.server.auth.SessionStore
import com.dietician.server.cron.AuditPruneCron
import com.dietician.server.cron.BackupCron
import com.dietician.server.cron.CronBootstrap
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.middleware.RateLimiter
import com.dietician.server.observability.Metrics
import com.dietician.server.repo.AuditRepository
import com.dietician.server.repo.HealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.dietician.server.repo.BudgetRepository
import com.dietician.server.repo.ConsentRepository
import com.dietician.server.repo.CredentialRepository
import com.dietician.server.repo.EventRepository
import com.dietician.server.repo.PaperFetchQueueRepository
import com.dietician.server.repo.SubjectRepository
import org.koin.dsl.module

/**
 * Server-side Koin module. Wires the production graph rebuilt from the
 * already-shipped components (Tasks 9-14 + Task 17 first half + RateLimiter).
 *
 * Email sender selection:
 *   - If `RESEND_API_KEY` env is present → real [ResendClient].
 *   - Otherwise → [NoopEmailSender] (dev / CI; `requestMagicLink` will still
 *     return `emailSent=true` so test flows can proceed without a key).
 *
 * Single-instance lifecycle: every binding is `single { ... }` because the
 * server runs in a single JVM and the repos / writer / auth service are all
 * stateless apart from connection-pool ownership.
 */
val dieticianModule = module {
    // ----- core infra -----
    single { DatabaseFactory() }
    single { RateLimiter() }

    // ----- repositories -----
    single { SubjectRepository(get()) }
    single { EventRepository(get()) }
    single { ConsentRepository(get()) }
    single { CredentialRepository(get()) }
    single { PaperFetchQueueRepository(get()) }
    single { BudgetRepository(get()) }
    single { AuditRepository(get()) }
    single { HealthRepository(get()) }

    // ----- audit -----
    single { AuditLogWriter(get()) }

    // ----- cron (Plan-3 Task 33/35; in-JVM scheduler per RC4) -----
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { CronBootstrap(get(), registry = Metrics.registry) }
    single { AuditPruneCron(get(), get()) }
    single {
        BackupCron(
            pgHost = System.getenv("DIETICIAN_DB_HOST") ?: "127.0.0.1",
            pgUser = System.getenv("DIETICIAN_DB_USER") ?: "dietician_app",
            pgDb = System.getenv("DIETICIAN_DB_NAME") ?: "dietician",
            rcloneRemote = System.getenv("DIETICIAN_BACKUP_REMOTE") ?: "onedrive-crypt:dietician-backups",
            auditLog = get(),
        )
    }

    // ----- auth -----
    single { MagicLinkService() }
    single { SessionStore() }
    single<EmailSender> {
        val apiKey = System.getenv("RESEND_API_KEY")
        if (apiKey.isNullOrBlank()) {
            NoopEmailSender()
        } else {
            ResendClient(apiKey = apiKey)
        }
    }
    single {
        AuthService(
            subjects = get(),
            magicLinks = get(),
            sessions = get(),
            email = get(),
            audit = get(),
        )
    }
}

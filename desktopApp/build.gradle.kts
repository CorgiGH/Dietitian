@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.slf4j)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Desktop-only subprocess control: ClaudeMax CLI, Playwright, whisper.cpp, yt-dlp
    // (Subprocesses invoked via java.lang.ProcessBuilder — no extra deps)

    // PDF + image (for cookbook OCR pre-process)
    implementation(libs.pdfbox)
    implementation(libs.twelvemonkeys.imageio.core)
    implementation(libs.twelvemonkeys.imageio.jpeg)
    implementation(libs.imgscalr)

    // Markdown + YAML for wiki I/O
    implementation(libs.flexmark.all)
    implementation(libs.kaml)

    // SQLite cache
    implementation(libs.sqlite.jdbc)
    implementation(libs.hikari)

    // Credential storage
    implementation(libs.windpapi4j)
    implementation(libs.jagged.framework)
    implementation(libs.jagged.scrypt)

    // File watcher
    implementation(libs.directory.watcher)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
}

compose.desktop {
    application {
        mainClass = "com.dietician.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageName = "Dietician"
            packageVersion = "0.1.0"

            windows {
                menuGroup = "Dietician"
                upgradeUuid = "8E3B7D0C-2F4A-4D5B-9E6C-1A2B3C4D5E6F"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

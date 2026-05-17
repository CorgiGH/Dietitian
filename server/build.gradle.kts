@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.dietician.server.MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Xms256m",
        "-Xmx512m",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=200",
        "-Dfile.encoding=UTF-8"
    )
}

dependencies {
    implementation(project(":shared"))

    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Postgres (canonical store)
    implementation(libs.postgresql.jdbc)
    implementation(libs.hikari)

    // Flyway migrations
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.slf4j)

    // Markdown + YAML (wiki I/O)
    implementation(libs.flexmark.all)
    implementation(libs.kaml)

    // ONNX for VPS-side embeddings precompute
    implementation(libs.onnxruntime)

    // File watcher
    implementation(libs.directory.watcher)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test {
    useJUnitPlatform()
}

ktor {
    fatJar {
        archiveFileName.set("dietician-server.jar")
    }
}

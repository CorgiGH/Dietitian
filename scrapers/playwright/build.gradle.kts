@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.dietician.scrapers.playwright.MainKt")
    // -Xmx512m enforces the RSS ceiling per Council 3 BREAK #16.
    applicationDefaultJvmArgs =
        listOf(
            "-Xms128m",
            "-Xmx512m",
            "-XX:+UseG1GC",
            "-Dfile.encoding=UTF-8",
        )
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.playwright)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

// Build self-contained executable JAR for subprocess invocation by :desktopApp daemon
tasks.register<Jar>("scraperJar") {
    archiveBaseName.set("playwright-scraper")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "com.dietician.scrapers.playwright.MainKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

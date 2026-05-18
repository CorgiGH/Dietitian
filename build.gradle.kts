// Root build.gradle.kts — apply plugins false here, configure in module-level builds.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    // Overlay the small detekt overrides on top of the bundled default config.
    // Rules + rationale documented inline in config/detekt/detekt.yml.
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        // [Plan-3 Batch C / Task 42 + Council 1779073963 Operations action]
        // 80 pre-existing detekt issues across server + shared exist as of the
        // Batch C merge. New code MUST NOT add issues; the baseline captures the
        // current floor so CI stays green while the team works the legacy down.
        // Regenerate with `./gradlew :<module>:detektBaseline` after intentional
        // cleanup commits.
        val baselineFile = file("detekt-baseline.xml")
        if (baselineFile.exists()) baseline = baselineFile
    }
}

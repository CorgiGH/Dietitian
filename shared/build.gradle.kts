@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.kotlin.logging.jvm)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.voyager.navigator)
                implementation(libs.voyager.screenmodel)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.uiToolingPreview)

                // Choco-solver — JVM-only, belongs in jvmMain/androidMain not commonMain.
                // TEMP commented for Plan-1 (`:shared:data` ledger). Re-enable + relocate to
                // platform source sets in the meal-planning plan (later). MUST exclude xchart
                // (Android-banned per smoke test 2026-05-17). Tracking: scaffold-fix-choco-solver.
                // implementation(libs.choco.solver) {
                //     exclude(group = "org.knowm.xchart")
                // }

                // Resilience
                implementation(libs.resilience4j.circuitbreaker)
                implementation(libs.resilience4j.kotlin)
                implementation(libs.resilience4j.retry)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.property)
                implementation(libs.ktor.client.mock)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sqldelight.driver.android)
                implementation(libs.androidx.lifecycle.process)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.sqldelight.driver.sqlite)
                implementation(libs.onnxruntime)
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
                implementation(libs.robolectric)
                implementation(libs.androidx.test.core)
                implementation(libs.kotest.runner.junit5)
                implementation(libs.kotest.assertions.core)
                implementation(libs.sqldelight.driver.android)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
                implementation(libs.sqldelight.driver.sqlite)
            }
        }
    }
}

android {
    namespace = "com.dietician.shared"
    compileSdk = libs.versions.android.compile.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.min.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

sqldelight {
    databases {
        create("DieticianDatabase") {
            packageName.set("com.dietician.shared.data.sql")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/schema"))
            migrationOutputDirectory.set(file("src/commonMain/sqldelight/migrations"))
            verifyMigrations.set(true)
        }
    }
}

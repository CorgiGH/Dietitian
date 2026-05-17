# Dietician Android Proguard / R8 rules

# Kotlin metadata
-keep class kotlin.Metadata { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Choco-solver — reflection-heavy
# Per smoke test 2026-05-17, must keep solver internals reachable.
-keep class org.chocosolver.** { *; }
-dontwarn org.chocosolver.**
# xchart MUST already be excluded at Gradle level — these rules are belt-and-braces
-dontwarn org.knowm.xchart.**
-dontwarn org.knowm.**

# ehcache sizeof (transitive of choco)
-keep class org.ehcache.sizeof.** { *; }
-dontwarn org.ehcache.sizeof.**

# SLF4J / Logback
-dontwarn org.slf4j.**

# Resilience4j
-dontwarn io.github.resilience4j.**
-keep class io.github.resilience4j.** { *; }

# SQLDelight generated
-keep class com.dietician.shared.data.sql.** { *; }

# Ktor client
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

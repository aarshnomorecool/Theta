plugins {
    id("org.jetbrains.kotlin.jvm")
    id("theta.static-checks")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
val javaVersion = catalog.findVersion("java").get().requiredVersion.toInt()

kotlin {
    jvmToolchain(javaVersion)
}

// The documented pre-commit command is `testDebugUnitTest`. JVM modules have no variants, so this
// alias stops their tests from being silently skipped by that command.
tasks.register("testDebugUnitTest") {
    group = "verification"
    dependsOn("test")
}

import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
    id("theta.static-checks")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun versionOf(alias: String): String = catalog.findVersion(alias).get().requiredVersion

android {
    compileSdk {
        version = release(versionOf("compileSdk").toInt()) {
            minorApiLevel = versionOf("compileSdkMinor").toInt()
        }
    }
    buildToolsVersion = versionOf("buildTools")

    defaultConfig {
        minSdk = versionOf("minSdk").toInt()
        targetSdk = versionOf("targetSdk").toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(versionOf("java"))
        targetCompatibility = JavaVersion.toVersion(versionOf("java"))
    }

    targetProjectPath = ":app"
}

extensions.configure<KotlinBaseExtension> {
    jvmToolchain(versionOf("java").toInt())
}

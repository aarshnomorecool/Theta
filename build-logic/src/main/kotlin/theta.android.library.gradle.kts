import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

plugins {
    id("com.android.library")
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(versionOf("java"))
        targetCompatibility = JavaVersion.toVersion(versionOf("java"))
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
    }
}

extensions.configure<KotlinBaseExtension> {
    jvmToolchain(versionOf("java").toInt())
}

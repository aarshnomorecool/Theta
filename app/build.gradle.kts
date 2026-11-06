import java.util.Properties

plugins {
    id("theta.android.application")
    id("theta.android.compose")
    id("theta.android.hilt")
}

// Firebase needs a config file that must never be committed for prod. Without it the app still builds and runs, and
// the core/analytics and core/config wrappers quietly become no-ops.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun secret(name: String): String? = System.getenv(name) ?: localProperties.getProperty(name)

// Google's published test app ID. Safe to commit, and the only one debug builds ever use (ads rule 9).
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val releaseAdMobAppId = secret("ADMOB_APP_ID").orEmpty()

android {
    namespace = "com.seasaw.theta"

    defaultConfig {
        // Placeholder from product.md. Source packages use com.seasaw.theta instead because `in` is a hard keyword in
        // Kotlin, so the final store ID can change here without touching any source file.
        applicationId = "in.seasaw.browser"
        versionCode = 1
        versionName = "0.1.0"
    }

    androidResources {
        localeFilters += listOf("en", "hi", "mr", "ru")
    }

    signingConfigs {
        val keystorePath = secret("THETA_KEYSTORE_PATH")
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = secret("THETA_KEYSTORE_PASSWORD")
                keyAlias = secret("THETA_KEY_ALIAS")
                keyPassword = secret("THETA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = testAdMobAppId
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = releaseAdMobAppId
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}

// An empty ID crashes the Ads SDK on launch, so a release build without one must fail here and not in users' hands.
tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    val appId = releaseAdMobAppId
    doFirst { check(appId.isNotEmpty()) { "ADMOB_APP_ID is not set in local.properties or the environment" } }
}

dependencies {
    implementation(project(":core:ads"))
    implementation(project(":core:analytics"))
    implementation(project(":core:common"))
    implementation(project(":core:config"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:webview"))
    implementation(project(":feature:adblock"))
    implementation(project(":feature:bookmarks"))
    implementation(project(":feature:browser"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:history"))
    implementation(project(":feature:newtab"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
}

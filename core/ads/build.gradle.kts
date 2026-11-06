import java.util.Properties

plugins {
    id("theta.android.library")
    id("theta.android.hilt")
}

// Real IDs come from local.properties or CI secrets and are never committed. Debug builds ignore them entirely, so a
// developer's machine can never serve real ads during testing.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun releaseAdId(name: String): String = System.getenv(name) ?: localProperties.getProperty(name).orEmpty()

android {
    namespace = "com.seasaw.theta.core.ads"

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Google's published test units. They are safe to commit and are required in debug by ads rule 9.
            buildConfigField("String", "BANNER_AD_UNIT", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "NATIVE_AD_UNIT", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            buildConfigField("String", "BANNER_AD_UNIT", "\"${releaseAdId("ADMOB_BANNER_ID")}\"")
            buildConfigField("String", "NATIVE_AD_UNIT", "\"${releaseAdId("ADMOB_NATIVE_ID")}\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT", "\"${releaseAdId("ADMOB_INTERSTITIAL_ID")}\"")
        }
    }
}

dependencies {
    implementation(project(":core:analytics"))
    implementation(project(":core:common"))
    implementation(project(":core:config"))
    implementation(project(":core:data"))
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

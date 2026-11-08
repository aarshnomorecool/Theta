import java.util.Properties

plugins {
    id("theta.android.library")
    id("theta.android.hilt")
    id("theta.android.compose")
}

// Real IDs come from local.properties or CI secrets and are never committed. Debug builds ignore them entirely, so a
// developer's machine can never serve real ads during testing.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun releaseAdId(name: String): String = System.getenv(name) ?: localProperties.getProperty(name).orEmpty()

val testNativeUnit = "ca-app-pub-3940256099942544/2247696110"

/** BuildConfig field to the local.properties / CI secret that fills it in release builds. */
val nativeUnits = listOf(
    "NATIVE_NEW_TAB_UNIT" to "ADMOB_NATIVE_NEW_TAB_ID",
    "NATIVE_DOWNLOADS_UNIT" to "ADMOB_NATIVE_DOWNLOADS_ID",
    "NATIVE_BOOKMARKS_UNIT" to "ADMOB_NATIVE_BOOKMARKS_ID",
    "NATIVE_HISTORY_UNIT" to "ADMOB_NATIVE_HISTORY_ID",
)

android {
    namespace = "com.seasaw.theta.core.ads"

    buildFeatures {
        buildConfig = true
    }

    // One native unit per screen, so AdMob reports show what each screen earns. Interstitials and banners are not used.
    buildTypes {
        debug {
            // Google's published test native unit. Safe to commit, and required in debug by ads rule 9.
            nativeUnits.forEach { (field, _) -> buildConfigField("String", field, "\"$testNativeUnit\"") }
        }
        release {
            nativeUnits.forEach { (field, secret) -> buildConfigField("String", field, "\"${releaseAdId(secret)}\"") }
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
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

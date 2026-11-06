plugins {
    id("theta.android.library")
    id("theta.android.hilt")
}

android {
    namespace = "com.seasaw.theta.feature.adblock"

    // This module's instrumented tests include the matching budget benchmark. A debuggable APK runs largely
    // unoptimised under ART and would measure the debugger, not the matcher, so they build from the release type.
    testBuildType = "release"
}

// Keeps the documented `connectedDebugAndroidTest` command from silently skipping this module.
tasks.register("connectedDebugAndroidTest") {
    group = "verification"
    dependsOn("connectedReleaseAndroidTest")
}

dependencies {
    api(project(":feature:adblock:engine"))
    implementation(project(":core:analytics"))
    implementation(project(":core:common"))
    implementation(project(":core:config"))
    implementation(project(":core:data"))
    implementation(project(":core:webview"))

    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

plugins {
    id("theta.android.library")
    id("theta.android.hilt")
}

android {
    namespace = "com.seasaw.theta.core.webview"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.webkit)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.okhttp.tls)
}

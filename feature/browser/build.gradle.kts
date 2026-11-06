plugins {
    id("theta.android.feature")
}

android {
    namespace = "com.seasaw.theta.feature.browser"
}

dependencies {
    implementation(project(":core:ads"))
    implementation(project(":core:analytics"))
    implementation(project(":core:data"))
    implementation(project(":core:webview"))
    implementation(libs.androidx.activity.compose)
}

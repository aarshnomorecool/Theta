plugins {
    id("theta.android.library")
    id("theta.android.hilt")
}

android {
    namespace = "com.seasaw.theta.core.analytics"
}

dependencies {
    implementation(project(":core:common"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
}

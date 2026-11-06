plugins {
    id("theta.android.library")
    id("theta.android.hilt")
    id("theta.kotlin.serialization")
}

android {
    namespace = "com.seasaw.theta.core.config"
}

dependencies {
    implementation(project(":core:common"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

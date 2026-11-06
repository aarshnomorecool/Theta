plugins {
    id("theta.android.feature")
}

android {
    namespace = "com.seasaw.theta.feature.downloads"
}

dependencies {
    implementation(project(":core:ads"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
}

plugins {
    id("theta.android.library")
    id("theta.android.hilt")
}

android {
    namespace = "com.seasaw.theta.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

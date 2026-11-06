plugins {
    id("theta.android.library")
    id("theta.android.compose")
}

android {
    namespace = "com.seasaw.theta.core.ui"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
}

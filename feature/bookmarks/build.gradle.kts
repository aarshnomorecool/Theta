plugins {
    id("theta.android.feature")
}

android {
    namespace = "com.seasaw.theta.feature.bookmarks"
}

dependencies {
    implementation(project(":core:ads"))
    implementation(project(":core:data"))
}

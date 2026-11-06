plugins {
    `kotlin-dsl`
}

dependencies {
    // implementation, not compileOnly: this makes build-logic the single source of plugin versions,
    // so module build files can apply these plugins by id without restating a version.
    implementation(libs.gradle.android)
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.kotlin.compose)
    implementation(libs.gradle.kotlin.serialization)
    implementation(libs.gradle.ksp)
    implementation(libs.gradle.hilt)
    implementation(libs.gradle.detekt)
    implementation(libs.gradle.ktlint)
    implementation(libs.gradle.google.services)
    implementation(libs.gradle.crashlytics)
    implementation(libs.gradle.baselineprofile)
}

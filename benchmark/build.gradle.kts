plugins {
    id("theta.android.benchmark")
}

android {
    namespace = "com.seasaw.theta.benchmark"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}

// The command documented in product.md. The Baseline Profile plugin names the real task after its own build type.
tasks.register("connectedBenchmarkAndroidTest") {
    group = "verification"
    dependsOn("connectedBenchmarkReleaseAndroidTest")
}

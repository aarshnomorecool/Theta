plugins {
    id("theta.jvm.library")
}

dependencies {
    testImplementation(libs.junit)
}

// Run by hand when refreshing the snapshot bundled in the app. The output is committed, so a normal build
// needs no network, and installed apps get newer lists from the weekly update worker.
tasks.register<JavaExec>("updateBundledFilters") {
    group = "filters"
    description = "Downloads the filter lists and writes the pre-compiled snapshot into :feature:adblock assets."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.seasaw.theta.feature.adblock.engine.SnapshotCompilerMainKt")
    args(
        file("../src/main/assets/filters.bin").path,
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        "https://easylist-downloads.adblockplus.org/indianlist.txt",
    )
}

tasks.register<JavaExec>("updateBundledCookieList") {
    group = "filters"
    description = "Downloads the cookie notice list and writes the pre-compiled pack into :feature:adblock assets."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.seasaw.theta.feature.adblock.engine.CookiePackCompilerMainKt")
    args(
        file("../src/main/assets/cookies.bin").path,
        "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt",
    )
}

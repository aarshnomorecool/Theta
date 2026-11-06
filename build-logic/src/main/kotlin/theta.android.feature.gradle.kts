plugins {
    id("theta.android.library")
    id("theta.android.compose")
    id("theta.android.hilt")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun library(alias: String) = catalog.findLibrary(alias).get()

dependencies {
    add("implementation", project(":core:common"))
    add("implementation", project(":core:ui"))
    add("implementation", library("androidx-compose-material3"))
    add("implementation", library("androidx-hilt-navigation-compose"))
    add("implementation", library("androidx-lifecycle-runtime-compose"))
    add("implementation", library("androidx-lifecycle-viewmodel-compose"))
    add("implementation", library("androidx-navigation-compose"))

    add("testImplementation", library("junit"))
    add("testImplementation", library("mockk"))
    add("testImplementation", library("turbine"))
    add("testImplementation", library("kotlinx-coroutines-test"))
}

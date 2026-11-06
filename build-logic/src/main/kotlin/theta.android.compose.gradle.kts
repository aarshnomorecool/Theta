plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun library(alias: String) = catalog.findLibrary(alias).get()

dependencies {
    val bom = platform(library("androidx-compose-bom"))
    add("implementation", bom)
    add("androidTestImplementation", bom)
    add("implementation", library("androidx-compose-ui-tooling-preview"))
    add("debugImplementation", library("androidx-compose-ui-tooling"))
}

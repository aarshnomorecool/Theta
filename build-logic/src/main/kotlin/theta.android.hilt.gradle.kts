plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun library(alias: String) = catalog.findLibrary(alias).get()

dependencies {
    add("implementation", library("hilt-android"))
    add("ksp", library("hilt-compiler"))
}

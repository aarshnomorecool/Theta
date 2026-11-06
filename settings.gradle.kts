pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Provisions the Java 17 toolchain the project requires on machines that only have a newer JDK.
    // Build-time only: nothing from it ends up in the APK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Theta"

include(":app")
include(":benchmark")
include(":core:ads")
include(":core:analytics")
include(":core:common")
include(":core:config")
include(":core:data")
include(":core:ui")
include(":core:webview")
include(":feature:adblock")
include(":feature:adblock:engine")
include(":feature:bookmarks")
include(":feature:browser")
include(":feature:downloads")
include(":feature:history")
include(":feature:newtab")
include(":feature:settings")

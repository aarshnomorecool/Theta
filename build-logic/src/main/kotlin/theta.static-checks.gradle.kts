import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

val catalog = the<VersionCatalogsExtension>().named("libs")

configure<KtlintExtension> {
    version.set(catalog.findVersion("ktlintCli").get().requiredVersion)
    android.set(true)
    filter {
        exclude { it.file.path.contains("generated") || it.file.path.contains("build${File.separator}") }
    }
}

configure<DetektExtension> {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
}

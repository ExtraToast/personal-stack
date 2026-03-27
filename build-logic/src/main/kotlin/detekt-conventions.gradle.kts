import dev.detekt.gradle.extensions.DetektExtension

plugins {
    id("dev.detekt")
}

configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("${project.rootDir}/config/detekt/detekt.yml"))
}

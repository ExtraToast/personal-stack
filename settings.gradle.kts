rootProject.name = "personal-stack"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }
}

include(":libs:kotlin-common")
include(":platform:tooling")
include(":services:auth-api")
include(":services:assistant-api")
include(":services:system-tests")

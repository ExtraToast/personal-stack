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
// Service Dockerfiles only copy `libs/` + `services/` build scripts into the
// build context, so `platform/tooling/` is absent inside the image and Gradle
// refuses to configure a project whose directory does not exist. Include it
// only when the checkout is full (local dev, CI runs), not inside minimal
// service build contexts. Flip with PLATFORM_TOOLING=1 if you deliberately
// want to force inclusion.
if (file("platform/tooling").isDirectory || System.getenv("PLATFORM_TOOLING") == "1") {
    include(":platform:tooling")
}
include(":services:auth-api")
include(":services:assistant-api")
include(":services:system-tests")

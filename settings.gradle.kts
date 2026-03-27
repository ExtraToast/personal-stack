rootProject.name = "personal-stack"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }
}

include(":libs:kotlin-common")
include(":services:auth-api")
include(":services:assistant-api")
include(":services:system-tests")

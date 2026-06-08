pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "ExtraToastGradleConventions"
            url = uri("https://maven.pkg.github.com/ExtraToast/gradle-conventions")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
    resolutionStrategy {
        eachPlugin {
            val id = requested.id.id
            if (id.startsWith("dev.extratoast.")) {
                useModule("dev.extratoast:gradle-conventions-${id.removePrefix("dev.extratoast.")}:${requested.version}")
            }
        }
    }
}

rootProject.name = "personal-stack"

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
include(":services:knowledge-api")
include(":services:agent-gateway")
include(":services:system-tests")

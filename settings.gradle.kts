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
        maven {
            name = "ExtraToastKotlinSpringCommons"
            url = uri("https://maven.pkg.github.com/ExtraToast/kotlin-spring-commons")
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
}

include(":services:auth-api")
include(":services:assistant-api")
include(":services:knowledge-api")
include(":services:agent-gateway")
include(":services:system-tests")

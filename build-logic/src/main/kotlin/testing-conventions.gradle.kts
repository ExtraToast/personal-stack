plugins {
    java
    jacoco
    id("test-logging-conventions")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, tasks.named("integrationTest"))
    // Aggregate coverage from both unit and integration tests
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/*.exec") },
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude(
                    "**/jooq/**",
                    "**/generated/**",
                    // The Spring Boot main class is a `runApplication<X>()`
                    // one-liner and a marker `@SpringBootApplication` class.
                    // @SpringBootTest already boots it, but Kotlin compiles
                    // the top-level `fun main` to a separate `*ApplicationKt`
                    // class whose static `main` is never reached by tests
                    // (the test runner calls `SpringApplication.run`
                    // directly). Including it drags every service's
                    // coverage down for ~5 untestable instructions; a
                    // skeleton like knowledge-api Phase 4a sits at ~20 %
                    // even with the integration test green. The exclusion
                    // is the standard Spring Boot + Jacoco convention.
                    "**/*Application.class",
                    "**/*ApplicationKt.class",
                )
            }
        },
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, tasks.named("integrationTest"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/*.exec") },
    )
    classDirectories.setFrom(
        classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude(
                    "**/jooq/**",
                    "**/generated/**",
                    // The Spring Boot main class is a `runApplication<X>()`
                    // one-liner and a marker `@SpringBootApplication` class.
                    // @SpringBootTest already boots it, but Kotlin compiles
                    // the top-level `fun main` to a separate `*ApplicationKt`
                    // class whose static `main` is never reached by tests
                    // (the test runner calls `SpringApplication.run`
                    // directly). Including it drags every service's
                    // coverage down for ~5 untestable instructions; a
                    // skeleton like knowledge-api Phase 4a sits at ~20 %
                    // even with the integration test green. The exclusion
                    // is the standard Spring Boot + Jacoco convention.
                    "**/*Application.class",
                    "**/*ApplicationKt.class",
                )
            }
        },
    )
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.time=ALL-UNNAMED",
    )
}

// Integration test source set and task
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(tasks.named("integrationTest"))
}

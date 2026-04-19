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
                exclude("**/jooq/**", "**/generated/**")
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
                exclude("**/jooq/**", "**/generated/**")
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

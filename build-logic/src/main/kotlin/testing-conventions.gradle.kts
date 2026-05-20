plugins {
    java
    jacoco
    id("test-logging-conventions")
}

jacoco {
    toolVersion = "0.8.12"
}

// Per-service exclusion list. Services append IO-bound or otherwise
// untestable classes (K8s adapters, HTTP integration shells,
// process-exec wrappers) and this convention plugin folds them into
// the jacoco report + coverage-verification classDirectories.
//
// History: services used to override `classDirectories.setFrom(...)`
// directly with `classDirectories.files.map { fileTree(it) { exclude(...) } }`.
// That chain reads `.files` against the already-filtered FileTree
// the first call produced — which resolves to individual `.class`
// leaves — and `fileTree(leaf) { exclude("**/foo/**") }` then no-ops
// because directory-style patterns don't match single-file roots.
// The second exclusion list was silently dropped and the 80 % gate
// passed by counting the very classes the project intended to
// exclude. The list property below moves the chain into one place
// that rebuilds from the raw source-set output directories, so
// directory patterns match.
val jacocoExclusionPatterns: ListProperty<String> =
    objects.listProperty(String::class.java).convention(emptyList())
extensions.add("jacocoExclusionPatterns", jacocoExclusionPatterns)

// Default exclusions applied to every service:
//   - jOOQ generated sources
//   - Anything under a generated/ package
//   - The Spring Boot main class. Kotlin compiles `fun main` to a
//     separate `*ApplicationKt` whose static `main` is never reached
//     by tests — @SpringBootTest invokes SpringApplication.run
//     directly. Including the marker class drags every service's
//     coverage down for ~5 untestable instructions; the exclusion is
//     the standard Spring Boot + Jacoco convention.
val defaultJacocoExclusions =
    listOf(
        "**/jooq/**",
        "**/generated/**",
        "**/*Application.class",
        "**/*ApplicationKt.class",
    )

fun filteredClassDirectories(): FileCollection =
    files(
        sourceSets.main
            .get()
            .output.classesDirs
            .map { dir ->
                fileTree(dir) {
                    exclude(defaultJacocoExclusions + jacocoExclusionPatterns.get())
                }
            },
    )

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
    classDirectories.setFrom(filteredClassDirectories())
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, tasks.named("integrationTest"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/*.exec") },
    )
    classDirectories.setFrom(filteredClassDirectories())
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

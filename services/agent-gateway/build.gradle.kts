plugins {
    id("spring-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
    id("testing-conventions")
}

dependencies {
    implementation(project(":libs:kotlin-common"))
    implementation("org.springframework:spring-aop")
    implementation("org.aspectj:aspectjweaver:1.9.25.1")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    // Tracing runtime jars — same shape as auth-api / assistant-api so
    // TimingAutoConfiguration in kotlin-common activates.
    runtimeOnly("io.micrometer:micrometer-tracing-bridge-otel")
    runtimeOnly("io.opentelemetry:opentelemetry-exporter-otlp")
    testImplementation("org.awaitility:awaitility:4.2.2")
}

// agent-gateway is the only service in the monorepo whose hot path is
// process exec (tmux / git / ssh) — every meaningful test for those
// classes needs the real binaries on PATH, which is the integration
// image's job. The exclusions below keep the 80 % jacoco bar honest
// for the classes that *are* unit-testable (process abstraction, the
// in-memory session registry, controllers via MockMvc, the log
// tailer, the WS envelope parser) and acknowledge that TmuxClient /
// GitClient / AgentAttachHandler / the Spring Boot main class are
// covered by container-level integration tests in the system-tests
// module rather than here.
val ioBoundExclusions =
    listOf(
        "**/tmux/TmuxClient.class",
        "**/git/GitClient.class",
        "**/ws/AgentAttachHandler.class",
        "**/AgentGatewayApplication*",
    )

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude(
                    ioBoundExclusions +
                        listOf("**/jooq/**", "**/generated/**", "**/*Application.class", "**/*ApplicationKt.class"),
                )
            }
        },
    )
}

tasks.jacocoTestReport {
    classDirectories.setFrom(
        classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude(
                    ioBoundExclusions +
                        listOf("**/jooq/**", "**/generated/**", "**/*Application.class", "**/*ApplicationKt.class"),
                )
            }
        },
    )
}

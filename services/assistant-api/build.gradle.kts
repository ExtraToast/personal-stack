plugins {
    alias(libs.plugins.extratoast.spring)
    alias(libs.plugins.extratoast.detekt)
    alias(libs.plugins.extratoast.ktlint)
    alias(libs.plugins.extratoast.testing)
    alias(libs.plugins.extratoast.jooq.codegen)
}

jooqCodegen {
    schemaName.set("PUBLIC")
    packageName.set("com.jorisjonkers.personalstack.assistant.jooq")
    migrationLocations.set(listOf("filesystem:src/main/resources/db/migration"))
}

dependencies {
    implementation(libs.kotlin.commons.command)
    implementation(libs.kotlin.commons.crac)
    implementation(libs.kotlin.commons.events)
    implementation(libs.kotlin.commons.exceptions)
    implementation(libs.kotlin.commons.observability)
    implementation(libs.kotlin.commons.timing)
    implementation(libs.kotlin.commons.vault)
    implementation(libs.kotlin.commons.web)
    testImplementation(libs.kotlin.commons.archunit.test)
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    // See auth-api build.gradle.kts — needed for the
    // ApplicationTracingAspect in kotlin-commons-observability to take effect.
    // Spring Boot 4 doesn't ship a starter-aop shortcut.
    implementation("org.springframework:spring-aop")
    implementation("org.aspectj:aspectjweaver:1.9.25.1")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jooq:jooq")
    runtimeOnly("org.postgresql:postgresql")
    // Tracing runtime jars. kotlin-commons-timing's TimingAutoConfiguration becomes
    // active when these are on the classpath: spans flow to Alloy → Tempo
    // and MDC traceId/spanId start populating in the JSON log lines.
    runtimeOnly("io.micrometer:micrometer-tracing-bridge-otel")
    runtimeOnly("io.opentelemetry:opentelemetry-exporter-otlp")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
    // k3s lets Fabric8AgentRunnerOrchestratorIntegrationTest exercise
    // the orchestrator through a real Kubernetes API — the seam where
    // PR #372 (PVC patch-verb missing on the production Role) failed
    // in production despite green unit tests. The negative case in
    // that test locks the RBAC contract in.
    testImplementation("org.testcontainers:testcontainers-k3s")
    // fabric8 KubernetesClient — drives the per-workspace runner Pod
    // lifecycle. Server-side apply, no informer cache (one-shot CRUD
    // is enough), pulled in with the kubernetes-client-bom to keep
    // model + httpclient versions aligned.
    implementation(platform("io.fabric8:kubernetes-client-bom:7.7.0"))
    implementation("io.fabric8:kubernetes-client")
    testImplementation("io.fabric8:kubernetes-server-mock")
}

// agent-runner orchestration shells out to Kubernetes and HTTP from
// classes that only carry signal under an integration cluster; the
// 80 % jacoco bar stays honest for the classes that are actually
// unit-testable by registering the IO-bound shells with the shared
// `jacocoExclusionPatterns` extension from the testing convention.
//
// `VaultDeployKeyStore`, `KnowledgeMcpClient`, `LightRagClient` are
// `@ConditionalOnProperty` adapters that only wire when their
// upstream is available; unit tests can't reach them and the
// integration tier doesn't yet stand up a Vault / knowledge-api
// fixture for the assistant-api integration test suite (the
// Fabric8 orchestrator integration test covers the k8s side). They
// follow the same IO-bound exclusion treatment as
// `HttpAgentGatewayClient`. The trailing `*.class` (rather than just
// `.class`) sweeps Kotlin-generated `Outer$Inner.class` companions —
// the HTTP shells declare request/response DTOs inline and the
// companions would otherwise drag the package into the report on
// their own.
@Suppress("UNCHECKED_CAST")
(extensions.getByName("jacocoExclusionPatterns") as ListProperty<String>).addAll(
    "**/infrastructure/k8s/**",
    "**/infrastructure/integration/HttpAgentGatewayClient*.class",
    "**/infrastructure/integration/VaultDeployKeyStore*.class",
    "**/infrastructure/integration/KnowledgeMcpClient*.class",
    "**/infrastructure/integration/LightRagClient*.class",
    "**/infrastructure/ws/**",
)

// The OpenAPI contract is pinned to `services/assistant-api/openapi.json`
// (committed). The `contract-export` JUnit tag identifies the single test
// that boots the Spring context, hits `/api/v1/api-docs`, and writes the
// spec to disk. The default `integrationTest` task skips that tag — the
// spec dump is a build-time artefact, not a verification step — while the
// new `exportOpenApiSpec` task below runs only that tag and feeds the
// `services/assistant-ui` contract-typegen pipeline.
tasks.named<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
        excludeTags("contract-export")
    }
}

tasks.register<Test>("exportOpenApiSpec") {
    description = "Boots the Spring context and writes the OpenAPI spec to services/assistant-api/openapi.json"
    group = "documentation"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform {
        includeTags("contract-export")
    }
    systemProperty("openapi.spec.output", file("openapi.json").absolutePath)
    // Always re-run: the spec is derived from the live springdoc output,
    // so caching past runs would defeat the drift gate. The CI workflow
    // diffs the freshly-written file against the committed copy.
    outputs.upToDateWhen { false }
}

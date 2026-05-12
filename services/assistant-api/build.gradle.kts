plugins {
    id("spring-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
    id("testing-conventions")
    id("jooq-codegen-conventions")
}

jooqCodegen {
    schemaName = "public"
    packageName = "com.jorisjonkers.personalstack.assistant.jooq"
    migrationLocations = listOf("filesystem:src/main/resources/db/migration")
}

dependencies {
    implementation(project(":libs:kotlin-common"))
    // See auth-api build.gradle.kts — needed for the
    // ApplicationTracingAspect in kotlin-common to take effect.
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
    // Tracing runtime jars. kotlin-common's TimingAutoConfiguration becomes
    // active when these are on the classpath: spans flow to Alloy → Tempo
    // and MDC traceId/spanId start populating in the JSON log lines.
    runtimeOnly("io.micrometer:micrometer-tracing-bridge-otel")
    runtimeOnly("io.opentelemetry:opentelemetry-exporter-otlp")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
}

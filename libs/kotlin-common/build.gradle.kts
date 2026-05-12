plugins {
    id("kotlin-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
    id("test-logging-conventions")
    jacoco
}

// Opens Spring stereotype classes + their methods so CGLIB can proxy
// them. Required for ApplicationTracingAspect — without it the
// @RestControllerAdvice + future @Component classes in this module
// remain Kotlin-final and Spring AOP can't wrap them. The kotlin-spring
// plugin is already on the build-logic classpath via kotlin-allopen,
// so we apply it imperatively here without re-declaring a version.
apply(plugin = "org.jetbrains.kotlin.plugin.spring")

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
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
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.3")
    // Optional Spring deps for infrastructure adapters — provided at runtime by services
    compileOnly("org.springframework.boot:spring-boot-starter-amqp:4.0.6")
    compileOnly("org.springframework.boot:spring-boot-starter-web:4.0.6")
    // AOP + OTel API for ApplicationTracingAspect. Spring Boot 4 dropped
    // the `spring-boot-starter-aop` shortcut so the underlying artifacts
    // are pulled directly. The OTel agent (attached via -javaagent on the
    // services) provides the runtime SDK; we only need the API surface to
    // call `GlobalOpenTelemetry.getTracer(...)`. Picking 1.60.1 to match
    // the agent's bundled SDK so signatures line up at runtime.
    compileOnly("org.springframework:spring-aop:7.0.7")
    compileOnly("org.aspectj:aspectjweaver:1.9.25.1")
    compileOnly("io.opentelemetry:opentelemetry-api:1.60.1")
    compileOnly("org.springframework.boot:spring-boot-starter-validation:4.0.6")
    compileOnly("org.springframework.vault:spring-vault-core:4.0.2")
    compileOnly("org.springframework.boot:spring-boot-starter-mail:4.0.6")
    compileOnly("org.springframework:spring-context:7.0.7")
    compileOnly("org.springframework.security:spring-security-oauth2-jose:7.0.5")
    // CRaC API. Compile-only here because the runtime jar is pulled in by
    // every Spring service through spring-conventions, and consumers that
    // never enable the `crac.train.enabled` flag never load the auto-config.
    compileOnly("org.crac:crac:1.5.0")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:4.0.6")
    // jOOQ — needed by the timing ExecuteListener; runtime jar is pulled in
    // by every Spring service that applies `spring-boot-starter-jooq`, so
    // compile-only here keeps kotlin-common consumers without jOOQ unaffected.
    compileOnly("org.jooq:jooq:3.21.2")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.springframework.boot:spring-boot-starter-amqp:4.0.6")
    testImplementation("org.springframework.boot:spring-boot-starter-web:4.0.6")
    testImplementation("org.springframework:spring-aop:7.0.7")
    testImplementation("org.aspectj:aspectjweaver:1.9.25.1")
    testImplementation("io.opentelemetry:opentelemetry-api:1.60.1")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.60.1")
    testImplementation("org.springframework.boot:spring-boot-starter-mail:4.0.6")
    testImplementation("org.springframework.vault:spring-vault-core:4.0.2")
    testImplementation("org.springframework:spring-context:7.0.7")
    testImplementation("org.springframework.security:spring-security-oauth2-jose:7.0.5")
    testImplementation("org.crac:crac:1.5.0")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:4.0.6")
    testImplementation("org.jooq:jooq:3.21.2")
    testImplementation("org.springframework:spring-test:7.0.7")
    implementation("com.tngtech.archunit:archunit-junit5:1.4.2")
}

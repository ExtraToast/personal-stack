plugins {
    id("kotlin-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
    id("test-logging-conventions")
    jacoco
}

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

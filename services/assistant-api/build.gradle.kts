plugins {
    id("spring-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
    id("testing-conventions")
    id("jooq-codegen-conventions")
}

jooqCodegen {
    schemaName = "public"
    packageName = "com.jorisjonkers.privatestack.assistant.jooq"
    migrationLocations = listOf("filesystem:src/main/resources/db/migration")
}

dependencies {
    implementation(project(":libs:kotlin-common"))
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jooq:jooq")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
}

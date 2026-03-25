plugins {
    id("spring-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
    id("testing-conventions")
    id("jooq-codegen-conventions")
}

jooqCodegen {
    schemaName = "public"
    packageName = "com.jorisjonkers.privatestack.auth.jooq"
    migrationLocations = listOf("filesystem:src/main/resources/db/migration")
}

dependencies {
    implementation(project(":libs:kotlin-common"))
    implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jooq:jooq")
    runtimeOnly("org.postgresql:postgresql")
    implementation("dev.turingcomplete:kotlin-onetimepassword:2.4.0")
    implementation("commons-codec:commons-codec")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.testcontainers:junit-jupiter")
}

// Export OpenAPI spec during build for client generation
tasks.register<JavaExec>("exportOpenApiSpec") {
    group = "documentation"
    description = "Starts the application briefly to export the OpenAPI spec as JSON"
    dependsOn(tasks.named("bootJar"))
    mainClass.set("org.springframework.boot.loader.launch.JarLauncher")
    classpath = files(tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").get().archiveFile)
    args = listOf("--spring.profiles.active=openapi-export", "--server.port=8099")
    systemProperties["springdoc.api-docs.path"] = "/api/v1/api-docs"
}

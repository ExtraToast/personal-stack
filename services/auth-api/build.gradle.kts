plugins {
    id("spring-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
    id("testing-conventions")
    id("jooq-codegen-conventions")
}

jooqCodegen {
    schemaName = "public"
    packageName = "com.jorisjonkers.personalstack.auth.jooq"
    migrationLocations = listOf("filesystem:src/main/resources/db/migration")
}

dependencies {
    implementation(project(":libs:kotlin-common"))
    implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.jooq:jooq")
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.2")
    runtimeOnly("org.postgresql:postgresql")
    implementation("dev.turingcomplete:kotlin-onetimepassword:2.4.1")
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

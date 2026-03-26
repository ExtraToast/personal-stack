plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.3.20")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.5")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7")
    // jOOQ codegen via Testcontainers
    implementation("org.jooq:jooq-codegen:3.21.1")
    implementation("org.jooq:jooq-meta-extensions:3.21.1")
}

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.3.0")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.4")
    implementation("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.2")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7")
    // jOOQ codegen via Testcontainers
    implementation("org.jooq:jooq-codegen:3.19.18")
    implementation("org.jooq:jooq-meta-extensions:3.19.18")
}

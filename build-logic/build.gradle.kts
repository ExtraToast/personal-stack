plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    // Detekt and Kotlin must bump together: Detekt's task init does a strict
    // binary-compat check on the Kotlin compiler version it was built
    // against, so Kotlin can't move ahead of the latest Detekt release.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.3.21")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.6")
    implementation("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.3")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7")
    // jOOQ codegen + meta-extensions must match the runtime jOOQ version
    // selected in spring-conventions (currently 3.21.2); the codegen emits
    // refs to runtime APIs (Constants.VERSION_*, ForeignKeyRule, …) that
    // only resolve when both sides line up.
    implementation("org.jooq:jooq-codegen:3.21.3")
    implementation("org.jooq:jooq-meta-extensions:3.21.3")
}

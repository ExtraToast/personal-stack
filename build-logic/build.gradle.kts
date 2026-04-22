plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    // Pinned to 2.3.0: detekt 2.0.0-alpha.2 is the latest release and was
    // compiled against Kotlin 2.3.0. Bumping Kotlin ahead of detekt trips
    // the strict binary-compat check in Detekt's task init ("detekt was
    // compiled with Kotlin 2.3.0 but is currently running with 2.3.20").
    // Lift both once a newer detekt drops.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.3.20")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.5")
    implementation("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.2")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7")
    // jOOQ codegen via Testcontainers. Pinned to 3.19.x: 3.21 removed
    // org.jooq.impl.QOM.ForeignKeyRule, which the codegen still emits
    // references to in generated Keys.java, and the runtime classes are
    // also gone so just bumping one side doesn't help. Lift both once
    // jOOQ codegen catches up or we replace the generator output.
    implementation("org.jooq:jooq-codegen:3.21.1")
    implementation("org.jooq:jooq-meta-extensions:3.21.1")
}

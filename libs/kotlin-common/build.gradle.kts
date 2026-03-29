plugins {
    id("kotlin-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    // Optional Spring deps for infrastructure adapters — provided at runtime by services
    compileOnly("org.springframework.boot:spring-boot-starter-amqp:4.0.4")
    compileOnly("org.springframework.boot:spring-boot-starter-web:4.0.4")
    compileOnly("org.springframework.boot:spring-boot-starter-validation:4.0.4")
    compileOnly("org.springframework.vault:spring-vault-core:4.0.1")
    compileOnly("org.springframework.boot:spring-boot-starter-mail:4.0.4")
    compileOnly("org.springframework:spring-context:7.0.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.springframework.boot:spring-boot-starter-amqp:4.0.4")
    implementation("com.tngtech.archunit:archunit-junit5:1.4.0")
}

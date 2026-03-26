plugins {
    id("kotlin-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2")
    // Optional Spring deps for infrastructure adapters — provided at runtime by services
    compileOnly("org.springframework.boot:spring-boot-starter-amqp:4.0.5")
    compileOnly("org.springframework.boot:spring-boot-starter-web:4.0.5")
    compileOnly("org.springframework.boot:spring-boot-starter-validation:4.0.5")
    compileOnly("org.springframework.vault:spring-vault-core:4.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.assertj:assertj-core:3.27.7")
    implementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}

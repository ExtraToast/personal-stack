plugins {
    id("kotlin-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
}

dependencies {
    testImplementation("io.rest-assured:rest-assured:6.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.testcontainers:testcontainers:2.0.4")
    testImplementation("org.testcontainers:junit-jupiter:2.0.4")
}

tasks.test {
    useJUnitPlatform {
        includeTags("system")
    }
}

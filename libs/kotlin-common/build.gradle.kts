plugins {
    id("kotlin-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
}

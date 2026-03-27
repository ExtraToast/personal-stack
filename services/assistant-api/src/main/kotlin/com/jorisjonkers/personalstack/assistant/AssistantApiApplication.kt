package com.jorisjonkers.personalstack.assistant

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.jorisjonkers.personalstack"])
class AssistantApiApplication

fun main(args: Array<String>) {
    runApplication<AssistantApiApplication>(*args)
}

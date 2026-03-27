package com.jorisjonkers.privatestack.assistant

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.jorisjonkers.privatestack"])
class AssistantApiApplication

fun main(args: Array<String>) {
    runApplication<AssistantApiApplication>(*args)
}

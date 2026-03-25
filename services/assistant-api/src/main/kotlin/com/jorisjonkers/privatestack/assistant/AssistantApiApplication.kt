package com.jorisjonkers.privatestack.assistant

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AssistantApiApplication

fun main(args: Array<String>) {
    runApplication<AssistantApiApplication>(*args)
}

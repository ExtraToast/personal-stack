package com.jorisjonkers.personalstack.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.jorisjonkers.personalstack"])
class AuthApiApplication

fun main(args: Array<String>) {
    runApplication<AuthApiApplication>(*args)
}

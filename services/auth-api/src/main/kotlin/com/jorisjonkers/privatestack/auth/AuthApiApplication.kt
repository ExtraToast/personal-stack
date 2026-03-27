package com.jorisjonkers.privatestack.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.jorisjonkers.privatestack"])
class AuthApiApplication

fun main(args: Array<String>) {
    runApplication<AuthApiApplication>(*args)
}

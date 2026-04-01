package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.common.command.Command

data class RegisterUserCommand(
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val password: String,
) : Command

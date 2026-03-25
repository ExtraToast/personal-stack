package com.jorisjonkers.privatestack.auth.application.command

import com.jorisjonkers.privatestack.common.command.Command

data class RegisterUserCommand(
    val username: String,
    val email: String,
    val password: String,
) : Command

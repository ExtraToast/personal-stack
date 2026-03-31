package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.common.command.Command

data class ResetPasswordCommand(
    val token: String,
    val newPassword: String,
) : Command

package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.common.command.Command

data class ChangePasswordCommand(
    val userId: UserId,
    val currentPassword: String,
    val newPassword: String,
) : Command

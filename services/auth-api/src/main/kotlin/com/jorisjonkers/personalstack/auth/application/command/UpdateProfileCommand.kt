package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.common.command.Command

data class UpdateProfileCommand(
    val userId: UserId,
    val firstName: String,
    val lastName: String,
) : Command

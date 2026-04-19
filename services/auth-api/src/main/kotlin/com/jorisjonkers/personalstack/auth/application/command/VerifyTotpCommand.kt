package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.common.command.Command

data class VerifyTotpCommand(
    val userId: UserId,
    val code: String,
) : Command

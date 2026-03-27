package com.jorisjonkers.privatestack.auth.application.command

import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.common.command.Command

data class EnrollTotpCommand(
    val userId: UserId,
) : Command

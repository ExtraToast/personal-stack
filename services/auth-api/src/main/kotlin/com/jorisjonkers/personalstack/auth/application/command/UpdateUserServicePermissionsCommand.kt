package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.common.command.Command

data class UpdateUserServicePermissionsCommand(
    val userId: UserId,
    val permissions: Set<ServicePermission>,
) : Command

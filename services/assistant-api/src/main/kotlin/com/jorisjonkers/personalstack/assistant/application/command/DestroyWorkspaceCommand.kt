package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.common.command.Command

data class DestroyWorkspaceCommand(
    val id: WorkspaceId,
) : Command

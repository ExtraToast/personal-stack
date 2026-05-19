package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.common.command.Command

data class CreateWorkspaceCommand(
    val workspaceId: WorkspaceId,
    val name: String,
    val repoUrl: String?,
    val branch: String?,
) : Command

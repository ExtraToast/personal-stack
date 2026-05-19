package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.common.command.Command

data class CreateProjectCommand(
    val projectId: ProjectId,
    val name: String,
    val slug: String,
    val description: String = "",
) : Command

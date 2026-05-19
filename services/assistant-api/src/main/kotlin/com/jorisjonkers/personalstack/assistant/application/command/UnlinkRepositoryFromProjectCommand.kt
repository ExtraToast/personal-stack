package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.common.command.Command

data class UnlinkRepositoryFromProjectCommand(
    val projectId: ProjectId,
    val repositoryId: RepositoryId,
) : Command

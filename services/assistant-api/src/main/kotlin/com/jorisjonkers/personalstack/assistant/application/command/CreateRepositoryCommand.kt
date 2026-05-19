package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.common.command.Command

data class CreateRepositoryCommand(
    val repositoryId: RepositoryId,
    val name: String,
    val repoUrl: String,
    val defaultBranch: String = "main",
) : Command

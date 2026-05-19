package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.common.command.Command

data class AddGithubLinkCommand(
    val linkId: GithubLinkId,
    val projectId: ProjectId,
    val name: String,
    val repoUrl: String,
    val defaultBranch: String = "main",
) : Command

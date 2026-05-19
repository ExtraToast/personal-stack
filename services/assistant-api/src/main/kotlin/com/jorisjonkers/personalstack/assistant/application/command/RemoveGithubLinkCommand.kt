package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.common.command.Command

data class RemoveGithubLinkCommand(val linkId: GithubLinkId) : Command

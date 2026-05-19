package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.port.DeployKeyStore
import com.jorisjonkers.personalstack.assistant.domain.port.GithubLinkRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RemoveGithubLinkCommandHandler(
    private val links: GithubLinkRepository,
    private val deployKeys: DeployKeyStore,
) : CommandHandler<RemoveGithubLinkCommand> {
    @Transactional
    override fun handle(command: RemoveGithubLinkCommand) {
        val link = links.findById(command.linkId) ?: return
        runCatching { deployKeys.remove(link.projectId, link.id) }
        links.delete(link.id)
    }
}

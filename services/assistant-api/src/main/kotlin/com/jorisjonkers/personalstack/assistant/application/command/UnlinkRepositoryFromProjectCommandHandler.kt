package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.port.ProjectRepositoryRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UnlinkRepositoryFromProjectCommandHandler(
    private val junction: ProjectRepositoryRepository,
) : CommandHandler<UnlinkRepositoryFromProjectCommand> {
    @Transactional
    override fun handle(command: UnlinkRepositoryFromProjectCommand) {
        junction.unlink(command.projectId, command.repositoryId)
    }
}

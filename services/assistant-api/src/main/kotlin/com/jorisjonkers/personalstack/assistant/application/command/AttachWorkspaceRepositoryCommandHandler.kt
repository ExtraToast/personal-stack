package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepositoryRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AttachWorkspaceRepositoryCommandHandler(
    private val workspaces: WorkspaceRepository,
    private val repositories: RepositoryRepository,
    private val links: WorkspaceRepositoryRepository,
) : CommandHandler<AttachWorkspaceRepositoryCommand> {
    @Transactional
    override fun handle(command: AttachWorkspaceRepositoryCommand) {
        workspaces.findById(command.workspaceId)
            ?: throw NoSuchElementException("workspace not found: ${command.workspaceId}")
        repositories.findById(command.repositoryId)
            ?: throw NoSuchElementException("repository not found: ${command.repositoryId}")
        links.attach(command.workspaceId, command.repositoryId, isPrimary = false)
    }
}

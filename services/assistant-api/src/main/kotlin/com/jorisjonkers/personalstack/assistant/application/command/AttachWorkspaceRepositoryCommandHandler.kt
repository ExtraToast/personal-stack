package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepositoryRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AttachWorkspaceRepositoryCommandHandler(
    private val workspaces: WorkspaceRepository,
    private val repositories: RepositoryRepository,
    private val links: WorkspaceRepositoryRepository,
    private val gateway: AgentGatewayClient,
) : CommandHandler<AttachWorkspaceRepositoryCommand> {
    private val log = LoggerFactory.getLogger(AttachWorkspaceRepositoryCommandHandler::class.java)

    @Transactional
    override fun handle(command: AttachWorkspaceRepositoryCommand) {
        val workspace =
            workspaces.findById(command.workspaceId)
                ?: throw NoSuchElementException("workspace not found: ${command.workspaceId}")
        val repository =
            repositories.findById(command.repositoryId)
                ?: throw NoSuchElementException("repository not found: ${command.repositoryId}")
        links.attach(command.workspaceId, command.repositoryId, isPrimary = false)
        if (workspace.gatewayEndpoint != null && gateway.isReady(workspace)) {
            // The link is the source of truth: the entrypoint clones every
            // attached repo at the next boot. Cloning into the live workspace
            // is a convenience, so a failure here (e.g. the running gateway's
            // boot-frozen credentials cannot yet reach the repo) must not roll
            // back the attach — log and let the boot clone reconcile it.
            try {
                gateway.clone(workspace, repository.repoUrl, repository.defaultBranch)
            } catch (e: Exception) {
                log.warn(
                    "live clone of {} into workspace {} failed; attach persisted, boot clone will reconcile",
                    repository.repoUrl,
                    command.workspaceId,
                    e,
                )
            }
        }
    }
}

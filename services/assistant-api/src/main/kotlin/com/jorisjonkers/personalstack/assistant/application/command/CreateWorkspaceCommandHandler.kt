package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Creates the workspace record, provisions the runner Pod, and (if a
 * repo URL was supplied) issues a clone via the gateway. The clone
 * step is best-effort here — it can fail after the Pod is up without
 * leaving the workspace in a dead state; the user can retry by
 * issuing a clone command from the UI.
 */
@Component
class CreateWorkspaceCommandHandler(
    private val workspaces: WorkspaceRepository,
    private val orchestrator: AgentRunnerOrchestrator,
    private val gateway: AgentGatewayClient,
) : CommandHandler<CreateWorkspaceCommand> {
    private val log = LoggerFactory.getLogger(CreateWorkspaceCommandHandler::class.java)

    @Transactional
    override fun handle(command: CreateWorkspaceCommand) {
        val now = Instant.now()
        val workspace =
            Workspace(
                id = command.workspaceId,
                name = command.name,
                repoUrl = command.repoUrl,
                branch = command.branch,
                podName = null,
                pvcName = null,
                gatewayEndpoint = null,
                status = WorkspaceStatus.PENDING,
                createdAt = now,
                updatedAt = now,
            )
        workspaces.save(workspace)

        val handle = orchestrator.provision(workspace)
        val withPod =
            workspace.withPodInfo(
                podName = handle.podName,
                pvcName = handle.pvcName,
                gatewayEndpoint = handle.gatewayEndpoint,
            )
        workspaces.save(withPod)

        if (workspace.repoUrl != null) {
            runCatching { gateway.clone(withPod, workspace.repoUrl, workspace.branch) }
                .onFailure { log.warn("initial clone failed for {}: {}", workspace.id, it.message) }
        }

        log.info("workspace {} provisioned as pod {}", workspace.id, handle.podName)
    }
}

package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import com.jorisjonkers.personalstack.assistant.domain.port.GithubLinkRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Creates the workspace record, provisions the runner Pod, and (if a
 * repo URL was supplied) issues a clone via the gateway. The clone
 * step is best-effort — it can fail after the Pod is up without
 * leaving the workspace in a dead state; the user can retry by
 * issuing a clone command from the UI.
 *
 * Project-backed flavour: if `githubLinkId` is set, the link is
 * resolved here so `repoUrl` + `branch` flow from the Project's
 * canonical source rather than getting copied at form-submit time.
 * That keeps the workspace truthful when an operator later edits
 * the link.
 */
@Component
class CreateWorkspaceCommandHandler(
    private val workspaces: WorkspaceRepository,
    private val orchestrator: AgentRunnerOrchestrator,
    private val gateway: AgentGatewayClient,
    /**
     * Optional — present only when the Projects feature is wired
     * (Vault enabled). When absent, every CreateWorkspace must
     * either supply repoUrl or stay repo-less.
     */
    private val githubLinks: ObjectProvider<GithubLinkRepository>,
) : CommandHandler<CreateWorkspaceCommand> {
    private val log = LoggerFactory.getLogger(CreateWorkspaceCommandHandler::class.java)

    @Transactional
    override fun handle(command: CreateWorkspaceCommand) {
        val workspace = persistInitial(command)
        val withPod = provisionAndUpdate(workspace)
        if (withPod.repoUrl != null) {
            runCatching { gateway.clone(withPod, withPod.repoUrl, withPod.branch) }
                .onFailure { log.warn("initial clone failed for {}: {}", workspace.id, it.message) }
        }
        log.info("workspace {} provisioned as pod {}", workspace.id, withPod.podName)
    }

    private fun persistInitial(command: CreateWorkspaceCommand): Workspace {
        val now = Instant.now()
        val (repoUrl, branch) = resolveRepo(command)
        val workspace =
            Workspace(
                id = command.workspaceId,
                name = command.name,
                repoUrl = repoUrl,
                branch = branch,
                podName = null,
                pvcName = null,
                gatewayEndpoint = null,
                status = WorkspaceStatus.PENDING,
                createdAt = now,
                updatedAt = now,
                githubLinkId = command.githubLinkId,
            )
        workspaces.save(workspace)
        return workspace
    }

    private fun provisionAndUpdate(workspace: Workspace): Workspace {
        val handle = orchestrator.provision(workspace)
        val withPod =
            workspace.withPodInfo(
                podName = handle.podName,
                pvcName = handle.pvcName,
                gatewayEndpoint = handle.gatewayEndpoint,
            )
        workspaces.save(withPod)
        return withPod
    }

    private fun resolveRepo(command: CreateWorkspaceCommand): Pair<String?, String?> {
        val linkId = command.githubLinkId ?: return command.repoUrl to command.branch
        val links =
            githubLinks.ifAvailable
                ?: error("github link requested but Projects feature not wired (Vault disabled?)")
        val link = links.findById(linkId) ?: error("github link not found: $linkId")
        val branch = command.branch?.takeIf { it.isNotBlank() } ?: link.defaultBranch
        return link.repoUrl to branch
    }
}

package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceKind
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import com.jorisjonkers.personalstack.assistant.domain.port.GithubLinkRepository
import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
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
 * Repo resolution prefers [CreateWorkspaceCommand.repositoryId]
 * (the new shape). If only the legacy [CreateWorkspaceCommand.githubLinkId]
 * is set, the handler still resolves through [GithubLinkRepository]
 * and logs a deprecation warning so the migration is visible in
 * production logs.
 */
@Component
@Suppress("DEPRECATION", "LongParameterList")
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
    /**
     * The new per-Repository lookup. Same wiring rule as
     * [githubLinks] — `@ConditionalOnProperty` keeps it absent in
     * tests that disable Vault.
     */
    private val repositories: ObjectProvider<RepositoryRepository>,
) : CommandHandler<CreateWorkspaceCommand> {
    private val log = LoggerFactory.getLogger(CreateWorkspaceCommandHandler::class.java)

    @Transactional
    override fun handle(command: CreateWorkspaceCommand) {
        require(command.kind != WorkspaceKind.CHAT) {
            "CHAT workspaces are not persisted — use StartChatSessionCommand instead"
        }
        if (command.repositoryId != null && command.githubLinkId != null) {
            log.warn(
                "CreateWorkspaceCommand received both repositoryId={} and (deprecated) githubLinkId={}; " +
                    "preferring repositoryId",
                command.repositoryId,
                command.githubLinkId,
            )
        } else if (command.githubLinkId != null) {
            log.warn(
                "CreateWorkspaceCommand uses deprecated githubLinkId={}; migrate the caller to repositoryId",
                command.githubLinkId,
            )
        }
        val workspace = persistInitial(command)
        val withPod = provisionAndUpdate(workspace)
        if (withPod.repoUrl != null && command.kind == WorkspaceKind.REPO_BACKED) {
            runCatching { gateway.clone(withPod, withPod.repoUrl, withPod.branch) }
                .onFailure { log.warn("initial clone failed for {}: {}", workspace.id, it.message) }
        }
        log.info("workspace {} provisioned as pod {}", workspace.id, withPod.podName)
    }

    private fun persistInitial(command: CreateWorkspaceCommand): Workspace {
        val now = Instant.now()
        val resolved = resolveRepo(command)
        val workspace =
            Workspace(
                id = command.workspaceId,
                name = command.name,
                repoUrl = resolved.repoUrl,
                branch = resolved.branch,
                podName = null,
                pvcName = null,
                gatewayEndpoint = null,
                status = WorkspaceStatus.PENDING,
                createdAt = now,
                updatedAt = now,
                repositoryId = resolved.repositoryId,
                projectId = command.projectId,
                kind = command.kind,
                githubLinkId = resolved.legacyLinkId,
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

    private data class ResolvedRepo(
        val repoUrl: String?,
        val branch: String?,
        val repositoryId: RepositoryId?,
        val legacyLinkId: GithubLinkId?,
    )

    private fun resolveRepo(command: CreateWorkspaceCommand): ResolvedRepo =
        when {
            command.kind == WorkspaceKind.SCRATCH -> ResolvedRepo(null, null, null, null)
            command.repositoryId != null -> resolveFromRepository(command, command.repositoryId)
            command.githubLinkId != null -> resolveFromLegacyLink(command, command.githubLinkId)
            else -> ResolvedRepo(command.repoUrl, command.branch, null, null)
        }

    private fun resolveFromRepository(
        command: CreateWorkspaceCommand,
        repoId: RepositoryId,
    ): ResolvedRepo {
        val repo =
            repositories.ifAvailable?.findById(repoId)
                ?: error("repository requested but Repository feature not wired (Vault disabled?)")
        val branch = command.branch?.takeIf { it.isNotBlank() } ?: repo.defaultBranch
        // legacyLinkId mirrors repositoryId during the V9 window so
        // the orchestrator's deploy-key lookup keeps working against
        // the legacy per-link Vault path; once the orchestrator is
        // migrated this assignment goes away.
        return ResolvedRepo(
            repoUrl = repo.repoUrl,
            branch = branch,
            repositoryId = repoId,
            legacyLinkId = GithubLinkId(repoId.value),
        )
    }

    private fun resolveFromLegacyLink(
        command: CreateWorkspaceCommand,
        linkId: GithubLinkId,
    ): ResolvedRepo {
        val links =
            githubLinks.ifAvailable
                ?: error("github link requested but Projects feature not wired (Vault disabled?)")
        val link = links.findById(linkId) ?: error("github link not found: $linkId")
        val branch = command.branch?.takeIf { it.isNotBlank() } ?: link.defaultBranch
        return ResolvedRepo(
            repoUrl = link.repoUrl,
            branch = branch,
            repositoryId = RepositoryId(linkId.value),
            legacyLinkId = linkId,
        )
    }
}

package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.GithubLink
import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import com.jorisjonkers.personalstack.assistant.domain.port.GithubLinkRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant

class CreateWorkspaceCommandHandlerTest {
    private val workspaces = mockk<WorkspaceRepository>()
    private val orchestrator = mockk<AgentRunnerOrchestrator>()
    private val gateway = mockk<AgentGatewayClient>()
    private val githubLinks = mockk<GithubLinkRepository>()
    private val provider =
        mockk<ObjectProvider<GithubLinkRepository>> {
            every { ifAvailable } returns githubLinks
        }
    private val handler = CreateWorkspaceCommandHandler(workspaces, orchestrator, gateway, provider)

    @Test
    fun `handle persists workspace, provisions Pod, and clones when repo is provided`() {
        val saved = slot<Workspace>()
        every { workspaces.save(capture(saved)) } answers { saved.captured }
        every { orchestrator.provision(any()) } returns
            AgentRunnerOrchestrator.RunnerHandle(
                podName = "agent-runner-deadbeef",
                pvcName = "workspace-deadbeef",
                gatewayEndpoint = "http://agent-runner-deadbeef.agents-system.svc.cluster.local:8090",
            )
        every { gateway.clone(any(), any(), any()) } returns "/workspace/repo"

        val id = WorkspaceId.random()
        handler.handle(
            CreateWorkspaceCommand(
                workspaceId = id,
                name = "demo",
                repoUrl = "git@github.com:owner/repo.git",
                branch = null,
            ),
        )

        verify { orchestrator.provision(any()) }
        verify { gateway.clone(any(), "git@github.com:owner/repo.git", null) }
        verify(exactly = 2) { workspaces.save(any()) }
    }

    @Test
    fun `handle without repo skips the clone call`() {
        every { workspaces.save(any()) } answers { firstArg() }
        every { orchestrator.provision(any()) } returns
            AgentRunnerOrchestrator.RunnerHandle(
                podName = "p",
                pvcName = "v",
                gatewayEndpoint = "http://p.svc:8090",
            )

        handler.handle(
            CreateWorkspaceCommand(
                workspaceId = WorkspaceId.random(),
                name = "qa",
                repoUrl = null,
                branch = null,
            ),
        )

        verify(exactly = 0) { gateway.clone(any(), any(), any()) }
    }

    @Test
    fun `clone failure does not propagate`() {
        every { workspaces.save(any()) } answers { firstArg() }
        every { orchestrator.provision(any()) } returns
            AgentRunnerOrchestrator.RunnerHandle("p", "v", "http://p.svc:8090")
        every { gateway.clone(any(), any(), any()) } throws RuntimeException("offline")

        handler.handle(
            CreateWorkspaceCommand(
                workspaceId = WorkspaceId.random(),
                name = "demo",
                repoUrl = "git@github.com:owner/repo.git",
                branch = null,
            ),
        )
    }

    @Test
    fun `handle resolves repoUrl + branch from a GithubLink when linkId is set`() {
        val linkId = GithubLinkId.random()
        val link =
            GithubLink(
                id = linkId,
                projectId = ProjectId.random(),
                name = "personal-stack",
                repoUrl = "git@github.com:ExtraToast/personal-stack.git",
                defaultBranch = "main",
                vaultKeyPath = "secret/data/agents/projects/x/repos/y",
                deployKeyFingerprint = "SHA256:abcd",
                deployKeyAddedAt = Instant.now(),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        every { githubLinks.findById(linkId) } returns link
        val saved = mutableListOf<Workspace>()
        every { workspaces.save(capture(saved)) } answers { firstArg() }
        every { orchestrator.provision(any()) } returns
            AgentRunnerOrchestrator.RunnerHandle("p", "v", "http://p.svc:8090")
        every { gateway.clone(any(), any(), any()) } returns "/workspace/personal-stack"

        handler.handle(
            CreateWorkspaceCommand(
                workspaceId = WorkspaceId.random(),
                name = "demo",
                repoUrl = null,
                branch = null,
                githubLinkId = linkId,
            ),
        )

        assertThat(saved.first().repoUrl).isEqualTo("git@github.com:ExtraToast/personal-stack.git")
        assertThat(saved.first().branch).isEqualTo("main")
        assertThat(saved.first().githubLinkId).isEqualTo(linkId)
        verify { gateway.clone(any(), "git@github.com:ExtraToast/personal-stack.git", "main") }
    }
}

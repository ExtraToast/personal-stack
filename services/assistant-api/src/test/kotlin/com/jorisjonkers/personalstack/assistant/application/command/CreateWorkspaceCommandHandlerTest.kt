package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.GithubLink
import com.jorisjonkers.personalstack.assistant.domain.model.GithubLinkId
import com.jorisjonkers.personalstack.assistant.domain.model.ProjectId
import com.jorisjonkers.personalstack.assistant.domain.model.Repository
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceKind
import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import com.jorisjonkers.personalstack.assistant.domain.port.GithubLinkRepository
import com.jorisjonkers.personalstack.assistant.domain.port.RepositoryRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant

@Suppress("DEPRECATION")
class CreateWorkspaceCommandHandlerTest {
    private val workspaces = mockk<WorkspaceRepository>()
    private val orchestrator = mockk<AgentRunnerOrchestrator>()
    private val gateway = mockk<AgentGatewayClient>()
    private val githubLinks = mockk<GithubLinkRepository>()
    private val linkProvider =
        mockk<ObjectProvider<GithubLinkRepository>> {
            every { ifAvailable } returns githubLinks
        }
    private val repositories = mockk<RepositoryRepository>()
    private val repositoryProvider =
        mockk<ObjectProvider<RepositoryRepository>> {
            every { ifAvailable } returns repositories
        }
    private val handler =
        CreateWorkspaceCommandHandler(workspaces, orchestrator, gateway, linkProvider, repositoryProvider)

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

    @Test
    fun `handle resolves from repositoryId and mirrors it into the legacy linkId`() {
        val repoId = RepositoryId.random()
        val repository =
            Repository(
                id = repoId,
                name = "personal-stack",
                repoUrl = "git@github.com:ExtraToast/personal-stack.git",
                defaultBranch = "trunk",
                vaultKeyPath = "secret/data/agents/repositories/$repoId",
                deployKeyFingerprint = "SHA256:abcd",
                deployKeyAddedAt = Instant.now(),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        every { repositories.findById(repoId) } returns repository
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
                repositoryId = repoId,
            ),
        )

        assertThat(saved.first().repoUrl).isEqualTo("git@github.com:ExtraToast/personal-stack.git")
        assertThat(saved.first().branch).isEqualTo("trunk")
        assertThat(saved.first().repositoryId).isEqualTo(repoId)
        assertThat(saved.first().githubLinkId?.value).isEqualTo(repoId.value)
        verify { gateway.clone(any(), "git@github.com:ExtraToast/personal-stack.git", "trunk") }
    }

    @Test
    fun `handle SCRATCH kind ignores repoUrl and skips clone`() {
        every { workspaces.save(any()) } answers { firstArg() }
        every { orchestrator.provision(any()) } returns
            AgentRunnerOrchestrator.RunnerHandle("p", "v", "http://p.svc:8090")
        val saved = mutableListOf<Workspace>()
        every { workspaces.save(capture(saved)) } answers { firstArg() }

        handler.handle(
            CreateWorkspaceCommand(
                workspaceId = WorkspaceId.random(),
                name = "playground",
                repoUrl = "git@github.com:owner/repo.git",
                branch = "main",
                kind = WorkspaceKind.SCRATCH,
            ),
        )

        verify(exactly = 0) { gateway.clone(any(), any(), any()) }
        assertThat(saved.first().repoUrl).isNull()
        assertThat(saved.first().kind).isEqualTo(WorkspaceKind.SCRATCH)
    }

    @Test
    fun `handle CHAT kind is rejected — chat lives in ChatSession`() {
        assertThrows<IllegalArgumentException> {
            handler.handle(
                CreateWorkspaceCommand(
                    workspaceId = WorkspaceId.random(),
                    name = "chat-but-not",
                    repoUrl = null,
                    branch = null,
                    kind = WorkspaceKind.CHAT,
                ),
            )
        }
    }

    @Test
    fun `unknown repositoryId raises NoSuchElementException with the id in the message`() {
        val repoId = RepositoryId.random()
        every { repositories.findById(repoId) } returns null

        val ex =
            assertThrows<NoSuchElementException> {
                handler.handle(
                    CreateWorkspaceCommand(
                        workspaceId = WorkspaceId.random(),
                        name = "demo",
                        repoUrl = null,
                        branch = null,
                        repositoryId = repoId,
                    ),
                )
            }
        assertThat(ex.message).contains(repoId.value.toString())
    }

    @Test
    fun `unknown githubLinkId raises NoSuchElementException with the id in the message`() {
        val linkId = GithubLinkId.random()
        every { githubLinks.findById(linkId) } returns null

        val ex =
            assertThrows<NoSuchElementException> {
                handler.handle(
                    CreateWorkspaceCommand(
                        workspaceId = WorkspaceId.random(),
                        name = "demo",
                        repoUrl = null,
                        branch = null,
                        githubLinkId = linkId,
                    ),
                )
            }
        assertThat(ex.message).contains(linkId.value.toString())
    }

    @Test
    fun `repositoryId with Vault disabled raises IllegalStateException naming the feature`() {
        // Simulate the @ConditionalOnProperty-wired feature being absent.
        every { repositoryProvider.ifAvailable } returns null
        val repoId = RepositoryId.random()

        val ex =
            assertThrows<IllegalStateException> {
                handler.handle(
                    CreateWorkspaceCommand(
                        workspaceId = WorkspaceId.random(),
                        name = "demo",
                        repoUrl = null,
                        branch = null,
                        repositoryId = repoId,
                    ),
                )
            }
        assertThat(ex.message).contains("Repository feature is not configured")
        assertThat(ex.message).contains(repoId.value.toString())
    }

    @Test
    fun `githubLinkId with Projects feature disabled raises IllegalStateException`() {
        every { linkProvider.ifAvailable } returns null
        val linkId = GithubLinkId.random()

        val ex =
            assertThrows<IllegalStateException> {
                handler.handle(
                    CreateWorkspaceCommand(
                        workspaceId = WorkspaceId.random(),
                        name = "demo",
                        repoUrl = null,
                        branch = null,
                        githubLinkId = linkId,
                    ),
                )
            }
        assertThat(ex.message).contains("Projects feature is not configured")
        assertThat(ex.message).contains(linkId.value.toString())
    }

    @Test
    fun `handle prefers repositoryId when both fields are set`() {
        val repoId = RepositoryId.random()
        val repository =
            Repository(
                id = repoId,
                name = "personal-stack",
                repoUrl = "git@github.com:ExtraToast/personal-stack.git",
                defaultBranch = "main",
                vaultKeyPath = "secret/data/agents/repositories/$repoId",
                deployKeyFingerprint = null,
                deployKeyAddedAt = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        every { repositories.findById(repoId) } returns repository
        every { workspaces.save(any()) } answers { firstArg() }
        every { orchestrator.provision(any()) } returns
            AgentRunnerOrchestrator.RunnerHandle("p", "v", "http://p.svc:8090")
        every { gateway.clone(any(), any(), any()) } returns "/workspace/x"

        handler.handle(
            CreateWorkspaceCommand(
                workspaceId = WorkspaceId.random(),
                name = "demo",
                repoUrl = null,
                branch = null,
                repositoryId = repoId,
                githubLinkId = GithubLinkId.random(),
            ),
        )

        // Repository path was used (githubLinks.findById would otherwise be needed).
        verify(exactly = 0) { githubLinks.findById(any()) }
    }
}

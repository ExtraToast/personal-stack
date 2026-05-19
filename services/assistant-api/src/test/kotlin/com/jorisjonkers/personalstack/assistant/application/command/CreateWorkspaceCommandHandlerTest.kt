package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CreateWorkspaceCommandHandlerTest {
    private val workspaces = mockk<WorkspaceRepository>()
    private val orchestrator = mockk<AgentRunnerOrchestrator>()
    private val gateway = mockk<AgentGatewayClient>()
    private val handler = CreateWorkspaceCommandHandler(workspaces, orchestrator, gateway)

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
        // save called twice: once with PENDING, once with STARTING after provisioning.
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
        // No throw — clone failure is best-effort
    }
}

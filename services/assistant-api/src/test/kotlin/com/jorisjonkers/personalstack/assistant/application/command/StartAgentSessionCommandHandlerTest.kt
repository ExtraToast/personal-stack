package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentKind
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSession
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionStatus
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceAgentSessionRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class StartAgentSessionCommandHandlerTest {
    private val workspaces = mockk<WorkspaceRepository>()
    private val sessions = mockk<WorkspaceAgentSessionRepository>()
    private val gateway = mockk<AgentGatewayClient>()
    private val handler = StartAgentSessionCommandHandler(workspaces, sessions, gateway)

    @Test
    fun `handle spawns gateway agent and persists binding`() {
        val ws = workspace()
        every { workspaces.findById(ws.id) } returns ws
        val saved = mutableListOf<WorkspaceAgentSession>()
        every { sessions.save(capture(saved)) } answers { firstArg() }
        every { gateway.spawnAgent(ws, WorkspaceAgentKind.CLAUDE) } returns
            AgentGatewayClient.GatewayAgent(id = "abc12345", kind = WorkspaceAgentKind.CLAUDE, cwd = "/workspace")

        handler.handle(
            StartAgentSessionCommand(
                sessionId = WorkspaceAgentSessionId.random(),
                workspaceId = ws.id,
                kind = WorkspaceAgentKind.CLAUDE,
            ),
        )

        verify(exactly = 2) { sessions.save(any()) }
        assertThat(saved.first().status).isEqualTo(WorkspaceAgentSessionStatus.STARTING)
        assertThat(saved.last().gatewayAgentId).isEqualTo("abc12345")
        assertThat(saved.last().status).isEqualTo(WorkspaceAgentSessionStatus.RUNNING)
    }

    @Test
    fun `handle throws when workspace does not exist`() {
        val missingId = WorkspaceId.random()
        every { workspaces.findById(missingId) } returns null
        assertThrows<IllegalStateException> {
            handler.handle(
                StartAgentSessionCommand(
                    sessionId = WorkspaceAgentSessionId.random(),
                    workspaceId = missingId,
                    kind = WorkspaceAgentKind.CODEX,
                ),
            )
        }
    }

    private fun workspace() =
        Workspace(
            id = WorkspaceId.random(),
            name = "demo",
            repoUrl = null,
            branch = null,
            podName = "agent-runner-abcdef01",
            pvcName = "workspace-abcdef01",
            gatewayEndpoint = "http://x:8090",
            status = WorkspaceStatus.READY,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}

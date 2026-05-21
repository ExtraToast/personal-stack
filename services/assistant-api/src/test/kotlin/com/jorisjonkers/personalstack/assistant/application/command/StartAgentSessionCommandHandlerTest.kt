package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.application.exception.AgentRunnerUnavailableException
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
import org.springframework.web.client.ResourceAccessException
import java.time.Instant

class StartAgentSessionCommandHandlerTest {
    private val workspaces = mockk<WorkspaceRepository>()
    private val sessions = mockk<WorkspaceAgentSessionRepository>()
    private val gateway = mockk<AgentGatewayClient>()

    /** `backoffInitialMs = 0` so the retry-exhaustion test doesn't burn 6 s. */
    private val handler = StartAgentSessionCommandHandler(workspaces, sessions, gateway, backoffInitialMs = 0)

    @Test
    fun `handle spawns gateway agent and persists binding when runner is ready`() {
        val ws = workspace()
        every { workspaces.findById(ws.id) } returns ws
        every { gateway.isReady(ws) } returns true
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
    fun `handle raises NoSuchElementException when workspace does not exist`() {
        val missingId = WorkspaceId.random()
        every { workspaces.findById(missingId) } returns null

        // 404 via GlobalExceptionHandler — previously this surfaced as a
        // 500 IllegalStateException, which read as a backend bug instead
        // of "the workspace id is wrong".
        val ex =
            assertThrows<NoSuchElementException> {
                handler.handle(
                    StartAgentSessionCommand(
                        sessionId = WorkspaceAgentSessionId.random(),
                        workspaceId = missingId,
                        kind = WorkspaceAgentKind.CODEX,
                    ),
                )
            }
        assertThat(ex.message).contains(missingId.value.toString())
    }

    @Test
    fun `handle throws AgentRunnerUnavailable with status=NotReady when isReady returns false`() {
        // Regression: production hits `Connection refused` to the runner
        // Pod's Service because the Pod hasn't passed its readiness
        // probe yet. The probe is checked first so the caller gets a
        // typed 503 with `Retry-After`, not a leaky 500 wrapping a
        // ResourceAccessException.
        val ws = workspace()
        every { workspaces.findById(ws.id) } returns ws
        every { gateway.isReady(ws) } returns false

        val ex =
            assertThrows<AgentRunnerUnavailableException> {
                handler.handle(
                    StartAgentSessionCommand(
                        sessionId = WorkspaceAgentSessionId.random(),
                        workspaceId = ws.id,
                        kind = WorkspaceAgentKind.CLAUDE,
                    ),
                )
            }
        assertThat(ex.workspaceId).isEqualTo(ws.id)
        assertThat(ex.runnerStatus).isEqualTo("NotReady")
        assertThat(ex.retryAfterSeconds).isEqualTo(AgentRunnerUnavailableException.DEFAULT_RETRY_AFTER_SECONDS)
        verify(exactly = 0) { sessions.save(any()) }
        verify(exactly = 0) { gateway.spawnAgent(any(), any()) }
    }

    @Test
    fun `handle retries spawn on ResourceAccessException and succeeds when the runner comes up mid-attempt`() {
        val ws = workspace()
        every { workspaces.findById(ws.id) } returns ws
        every { gateway.isReady(ws) } returns true
        every { sessions.save(any()) } answers { firstArg() }
        // Fail twice with `Connection refused`, then succeed on the
        // third attempt — the readiness gate races with the
        // runner's HTTP listener binding, so 1-2 attempts of grace
        // is realistic in CI + prod.
        every { gateway.spawnAgent(ws, WorkspaceAgentKind.CLAUDE) }
            .throws(ResourceAccessException("Connection refused"))
            .andThenThrows(ResourceAccessException("Connection refused"))
            .andThen(
                AgentGatewayClient.GatewayAgent(id = "ok", kind = WorkspaceAgentKind.CLAUDE, cwd = "/workspace"),
            )

        handler.handle(
            StartAgentSessionCommand(
                sessionId = WorkspaceAgentSessionId.random(),
                workspaceId = ws.id,
                kind = WorkspaceAgentKind.CLAUDE,
            ),
        )

        verify(exactly = 3) { gateway.spawnAgent(ws, WorkspaceAgentKind.CLAUDE) }
        verify(exactly = 2) { sessions.save(any()) }
    }

    @Test
    fun `handle surfaces AgentRunnerUnavailable when every spawn retry hits ResourceAccessException`() {
        // 3 attempts × ConnectionRefused → typed 503 with the original
        // RestClient exception attached as the cause for log
        // correlation. The session row is the STARTING placeholder
        // that was inserted before the spawn; cleanup is the caller's
        // responsibility (no rollback here because @Transactional
        // gives us that for free on the rethrown exception).
        val ws = workspace()
        every { workspaces.findById(ws.id) } returns ws
        every { gateway.isReady(ws) } returns true
        every { sessions.save(any()) } answers { firstArg() }
        every { gateway.spawnAgent(ws, WorkspaceAgentKind.CLAUDE) } throws
            ResourceAccessException("Connection refused")

        val ex =
            assertThrows<AgentRunnerUnavailableException> {
                handler.handle(
                    StartAgentSessionCommand(
                        sessionId = WorkspaceAgentSessionId.random(),
                        workspaceId = ws.id,
                        kind = WorkspaceAgentKind.CLAUDE,
                    ),
                )
            }
        assertThat(ex.runnerStatus).isEqualTo("ConnectionRefused")
        assertThat(ex.cause).isInstanceOf(ResourceAccessException::class.java)
        verify(exactly = StartAgentSessionCommandHandler.MAX_SPAWN_ATTEMPTS) {
            gateway.spawnAgent(ws, WorkspaceAgentKind.CLAUDE)
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

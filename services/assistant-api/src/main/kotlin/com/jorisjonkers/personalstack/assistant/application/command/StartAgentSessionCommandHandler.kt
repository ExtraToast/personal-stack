package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.application.exception.AgentRunnerUnavailableException
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSession
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionStatus
import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceAgentSessionRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.ResourceAccessException
import java.time.Instant

/**
 * Two failure modes are surfaced as a typed 503 instead of an opaque
 * 500 / 502:
 *
 * 1. The workspace's runner Pod is not ready yet — `isReady()` polls
 *    the runner's `/healthz` and reports a transport-level failure
 *    before we attempt the real `POST /agents`. Throws
 *    [AgentRunnerUnavailableException] with `runnerStatus="NotReady"`.
 * 2. The runner's Service has Ready endpoints but the spawn HTTP call
 *    still gets `Connection refused` — a race between Endpoints
 *    publish and the runner's HTTP listener binding. The handler
 *    retries the spawn a bounded number of times with backoff before
 *    surfacing `runnerStatus="ConnectionRefused"`.
 *
 * The 503 carries a `Retry-After` header (via
 * [AgentRunnerUnavailableExceptionHandler]) so well-behaved clients
 * back off automatically; the UI consumes the `runnerStatus` /
 * `retryAfterSeconds` ProblemDetail extensions to render a useful
 * inline message instead of a top-right "Internal Server Error" toast.
 */
@Component
class StartAgentSessionCommandHandler(
    private val workspaces: WorkspaceRepository,
    private val sessions: WorkspaceAgentSessionRepository,
    private val gateway: AgentGatewayClient,
    /**
     * Initial backoff between spawn retries. The default matches a
     * production-tuned 1 s; tests pass `0` so the suite doesn't
     * burn 6 s on the worst-case retry scenario.
     */
    private val backoffInitialMs: Long = BACKOFF_INITIAL_MS,
) : CommandHandler<StartAgentSessionCommand> {
    private val log = LoggerFactory.getLogger(StartAgentSessionCommandHandler::class.java)

    @Transactional
    override fun handle(command: StartAgentSessionCommand) {
        val workspace =
            workspaces.findById(command.workspaceId)
                ?: throw NoSuchElementException("workspace not found: ${command.workspaceId.value}")

        gateRunnerReadiness(workspace)

        val now = Instant.now()
        val session =
            WorkspaceAgentSession(
                id = command.sessionId,
                workspaceId = workspace.id,
                kind = command.kind,
                gatewayAgentId = null,
                status = WorkspaceAgentSessionStatus.STARTING,
                createdAt = now,
                updatedAt = now,
            )
        sessions.save(session)

        val gatewayAgent = spawnAgentWithRetry(workspace, command)
        sessions.save(session.bindGatewayAgent(gatewayAgent.id))
    }

    /**
     * Pre-flight the `/healthz` probe before touching the spawn
     * endpoint so a not-yet-ready runner surfaces as a clean 503
     * rather than a `ResourceAccessException` deep in the RestClient
     * stack.
     */
    private fun gateRunnerReadiness(workspace: Workspace) {
        if (!gateway.isReady(workspace)) {
            throw AgentRunnerUnavailableException(
                workspaceId = workspace.id,
                runnerStatus = "NotReady",
            )
        }
    }

    /**
     * Retry the spawn on `ResourceAccessException` (the Spring
     * RestClient wrapping of any transport-level failure: socket
     * refused, read timeout, …). Each attempt sleeps an increasing
     * amount; after [MAX_SPAWN_ATTEMPTS] exhausted attempts the
     * caller gets a 503 instead of a 500.
     */
    private fun spawnAgentWithRetry(
        workspace: Workspace,
        command: StartAgentSessionCommand,
    ): AgentGatewayClient.GatewayAgent {
        var lastFailure: ResourceAccessException? = null
        repeat(MAX_SPAWN_ATTEMPTS) { attempt ->
            try {
                return gateway.spawnAgent(workspace, command.kind)
            } catch (ex: ResourceAccessException) {
                lastFailure = ex
                val sleepMs = backoffInitialMs * (attempt + 1)
                log.warn(
                    "agent spawn attempt {} for workspace {} failed: {} — retrying in {}ms",
                    attempt + 1,
                    workspace.id.value,
                    ex.message,
                    sleepMs,
                )
                if (sleepMs > 0) Thread.sleep(sleepMs)
            }
        }
        throw AgentRunnerUnavailableException(
            workspaceId = workspace.id,
            runnerStatus = "ConnectionRefused",
            retryAfterSeconds = AgentRunnerUnavailableException.DEFAULT_RETRY_AFTER_SECONDS,
            cause = lastFailure,
        )
    }

    companion object {
        const val MAX_SPAWN_ATTEMPTS: Int = 3
        const val BACKOFF_INITIAL_MS: Long = 1_000
    }
}

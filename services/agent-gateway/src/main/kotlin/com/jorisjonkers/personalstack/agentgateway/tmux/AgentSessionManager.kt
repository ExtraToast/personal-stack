package com.jorisjonkers.personalstack.agentgateway.tmux

import com.jorisjonkers.personalstack.agentgateway.config.GatewayProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

/**
 * In-memory registry of active agents on this Pod. The Pod is the
 * unit of restart, and assistant-api owns the source-of-truth state,
 * so persisting here would double-bookkeeper for no win.
 *
 * Concurrency: ConcurrentHashMap is enough — the only racy operation
 * is spawn-vs-stop on the same id, and that's a caller bug worth
 * surfacing as a 409.
 */
@Component
class AgentSessionManager(
    private val tmux: TmuxClient,
    private val props: GatewayProperties,
) {
    private val log = LoggerFactory.getLogger(AgentSessionManager::class.java)
    private val sessions = ConcurrentHashMap<String, AgentSession>()

    fun spawn(
        kind: AgentKind,
        workspacePath: String? = null,
    ): AgentSession {
        val id = UUID.randomUUID().toString().substring(0, 8)
        val tmuxSession = "agent-$id"
        val cwd = workspacePath ?: props.workspaceRoot
        val stateDir = tmux.ensureStateDir()
        val logFile: Path = stateDir.resolve("$tmuxSession.log")
        Files.deleteIfExists(logFile)
        Files.createFile(logFile)

        val command = commandFor(kind)
        tmux.newSession(tmuxSession, command, cwd)
        tmux.startPipeToFile(tmuxSession, logFile)

        val session =
            AgentSession(
                id = id,
                kind = kind,
                tmuxSession = tmuxSession,
                logFile = logFile,
                cwd = cwd,
                createdAt = Instant.now(),
            )
        sessions[id] = session
        log.info("spawned {} agent {} ({}) in {}", kind, id, tmuxSession, cwd)
        return session
    }

    fun stop(id: String): Boolean {
        val session = sessions.remove(id) ?: return false
        tmux.killSession(session.tmuxSession)
        log.info("stopped agent {}", id)
        return true
    }

    fun get(id: String): AgentSession? = sessions[id]

    fun list(): List<AgentSession> = sessions.values.sortedBy { it.createdAt }

    fun send(
        id: String,
        input: String,
        enter: Boolean = true,
    ) {
        val session = sessions[id] ?: error("unknown agent: $id")
        tmux.sendKeys(session.tmuxSession, input, enter = enter)
    }

    fun capture(
        id: String,
        historyLines: Int = 1_000,
    ): String {
        val session = sessions[id] ?: error("unknown agent: $id")
        return tmux.capture(session.tmuxSession, historyLines)
    }

    private fun commandFor(kind: AgentKind): List<String> =
        when (kind) {
            AgentKind.CLAUDE -> listOf(props.cli.claude)
            AgentKind.CODEX -> listOf(props.cli.codex)
            AgentKind.SHELL -> listOf("/bin/bash", "-l")
        }
}

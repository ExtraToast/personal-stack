package com.jorisjonkers.personalstack.agentgateway.tmux

import com.jorisjonkers.personalstack.agentgateway.config.GatewayProperties
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    // The pipe-pane log only conducts the live stream, so it is capped
    // rather than kept whole: this trims any session log that outgrows
    // its cap so a long-lived agent cannot fill the runner disk. Active
    // tailers restart from the new beginning on the next poll.
    private val trimmer =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "agent-log-trimmer").apply { isDaemon = true }
        }

    init {
        val period = props.tmux.logTrimIntervalSeconds
        trimmer.scheduleWithFixedDelay(::trimOversizedLogs, period, period, TimeUnit.SECONDS)
    }

    @PreDestroy
    fun shutdown() {
        trimmer.shutdownNow()
    }

    private fun trimOversizedLogs() {
        val cap = props.tmux.logCapBytes
        sessions.values.forEach { session ->
            runCatching {
                val file = session.logFile
                if (Files.exists(file) && Files.size(file) > cap) {
                    FileChannel.open(file, StandardOpenOption.WRITE).use { it.truncate(0) }
                    log.info("trimmed agent {} log past {} bytes", session.id, cap)
                }
            }.onFailure { log.warn("trim of {} failed: {}", session.logFile, it.message) }
        }
    }

    fun spawn(
        kind: AgentKind,
        workspacePath: String? = null,
        resumeCliSessionId: String? = null,
    ): AgentSession {
        val id = UUID.randomUUID().toString().substring(0, 8)
        val tmuxSession = "agent-$id"
        val cwd = workspacePath ?: props.workspaceRoot
        val stateDir = tmux.ensureStateDir()
        val logFile: Path = stateDir.resolve("$tmuxSession.log")
        Files.deleteIfExists(logFile)
        Files.createFile(logFile)

        val (command, cliSessionId) = commandAndSessionIdFor(kind, resumeCliSessionId)
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
                cliSessionId = cliSessionId,
            )
        sessions[id] = session
        log.info("spawned {} agent {} ({}) in {} cliSessionId={}", kind, id, tmuxSession, cwd, cliSessionId)
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

    fun captureWithEscapes(id: String): String {
        val session = sessions[id] ?: error("unknown agent: $id")
        return tmux.captureWithEscapes(session.tmuxSession)
    }

    fun resize(
        id: String,
        cols: Int,
        rows: Int,
    ) {
        val session = sessions[id] ?: error("unknown agent: $id")
        tmux.resize(session.tmuxSession, cols, rows)
    }

    /**
     * Build the CLI command and return the native session id alongside it.
     *
     * For Claude: when [resumeCliSessionId] is provided the existing session
     * is continued via `--resume <id>` and that id is echoed back. Otherwise
     * a fresh UUID is generated and passed as `--session-id <uuid>` so the
     * conversation can be resumed in a future Pod restart. For Codex no
     * deterministic create-time flag exists; async discovery from
     * `$CODEX_HOME/sessions` is a follow-up. Shell has no session id.
     */
    private fun commandAndSessionIdFor(
        kind: AgentKind,
        resumeCliSessionId: String?,
    ): Pair<List<String>, String?> =
        when (kind) {
            AgentKind.CLAUDE -> {
                if (resumeCliSessionId != null) {
                    val cmd =
                        listOf(props.cli.claude) + props.cli.claudeArgs +
                            listOf("--resume", resumeCliSessionId)
                    cmd to resumeCliSessionId
                } else {
                    val cliSessionId = UUID.randomUUID().toString()
                    val cmd =
                        listOf(props.cli.claude) + props.cli.claudeArgs +
                            listOf("--session-id", cliSessionId)
                    cmd to cliSessionId
                }
            }
            AgentKind.CODEX -> (listOf(props.cli.codex) + props.cli.codexArgs) to null
            AgentKind.SHELL -> listOf("/bin/bash", "-l") to null
        }
}

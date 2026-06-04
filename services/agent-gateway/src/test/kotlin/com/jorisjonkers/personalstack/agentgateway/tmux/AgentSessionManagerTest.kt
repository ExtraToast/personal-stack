package com.jorisjonkers.personalstack.agentgateway.tmux

import com.jorisjonkers.personalstack.agentgateway.config.GatewayProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class AgentSessionManagerTest {
    private val tmux = mockk<TmuxClient>(relaxed = true)

    private fun manager(tmp: Path): AgentSessionManager {
        val props =
            GatewayProperties(
                workspaceRoot = "/workspace",
                tmux = GatewayProperties.Tmux(socketName = "agent-gw", stateDir = tmp.toString()),
                cli =
                    GatewayProperties.Cli(
                        claude = "/usr/local/bin/claude",
                        codex = "/usr/local/bin/codex",
                        claudeArgs = listOf("--dangerously-skip-permissions"),
                        codexArgs = listOf("--dangerously-bypass-approvals-and-sandbox"),
                    ),
                git = GatewayProperties.Git(deployKeyDir = "/x"),
            )
        every { tmux.ensureStateDir() } returns tmp
        return AgentSessionManager(tmux, props)
    }

    @Test
    fun `spawn registers session and starts tmux + pipe`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.CLAUDE, workspacePath = "/workspace/repo")
        assertThat(s.kind).isEqualTo(AgentKind.CLAUDE)
        assertThat(s.tmuxSession).isEqualTo("agent-${s.id}")
        assertThat(s.cwd).isEqualTo("/workspace/repo")
        assertThat(s.logFile.parent).isEqualTo(tmp)
        assertThat(s.cliSessionId).isNotNull()
        verify {
            tmux.newSession(
                s.tmuxSession,
                match { cmd ->
                    cmd.containsAll(listOf("/usr/local/bin/claude", "--dangerously-skip-permissions")) &&
                        cmd.contains("--session-id") &&
                        cmd[cmd.indexOf("--session-id") + 1] == s.cliSessionId
                },
                "/workspace/repo",
            )
        }
        verify { tmux.startPipeToFile(s.tmuxSession, s.logFile) }
        assertThat(mgr.get(s.id)).isEqualTo(s)
        assertThat(mgr.list()).hasSize(1)
    }

    @Test
    fun `spawn launches claude in full-trust mode with configured flags`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.CLAUDE)
        // Claude gets --session-id <uuid> appended so native resume is possible.
        assertThat(s.cliSessionId).isNotNull()
        verify {
            tmux.newSession(
                s.tmuxSession,
                match { cmd ->
                    cmd.containsAll(listOf("/usr/local/bin/claude", "--dangerously-skip-permissions")) &&
                        cmd.contains("--session-id") &&
                        cmd[cmd.indexOf("--session-id") + 1] == s.cliSessionId
                },
                "/workspace",
            )
        }
    }

    @Test
    fun `spawn resumes claude session when resumeCliSessionId is provided`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val previousId = "prev-session-uuid"
        val s = mgr.spawn(AgentKind.CLAUDE, resumeCliSessionId = previousId)
        assertThat(s.cliSessionId).isEqualTo(previousId)
        verify {
            tmux.newSession(
                s.tmuxSession,
                match { cmd ->
                    cmd.containsAll(listOf("/usr/local/bin/claude", "--dangerously-skip-permissions")) &&
                        cmd.contains("--resume") &&
                        cmd[cmd.indexOf("--resume") + 1] == previousId &&
                        !cmd.contains("--session-id")
                },
                "/workspace",
            )
        }
    }

    @Test
    fun `spawn resumes last codex session when LATEST sentinel is provided`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.CODEX, resumeCliSessionId = "LATEST")
        assertThat(s.cliSessionId).isNull()
        verify {
            tmux.newSession(
                s.tmuxSession,
                match { cmd -> cmd.containsAll(listOf("/usr/local/bin/codex", "resume", "--last")) },
                "/workspace",
            )
        }
    }

    @Test
    fun `spawn resumes specific codex session when a UUID is provided`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val sessionId = "prev-codex-uuid"
        val s = mgr.spawn(AgentKind.CODEX, resumeCliSessionId = sessionId)
        assertThat(s.cliSessionId).isEqualTo(sessionId)
        verify {
            tmux.newSession(
                s.tmuxSession,
                match { cmd ->
                    cmd.containsAll(listOf("/usr/local/bin/codex", "resume", sessionId)) &&
                        !cmd.contains("--last")
                },
                "/workspace",
            )
        }
    }

    @Test
    fun `spawn launches codex with approval+sandbox bypass flag`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.CODEX)
        // Codex has no deterministic create-time session id flag; discovery is async.
        assertThat(s.cliSessionId).isNull()
        verify {
            tmux.newSession(
                s.tmuxSession,
                listOf("/usr/local/bin/codex", "--dangerously-bypass-approvals-and-sandbox"),
                "/workspace",
            )
        }
    }

    @Test
    fun `spawn launches shell bare with no trust flags`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.SHELL)
        assertThat(s.cliSessionId).isNull()
        verify { tmux.newSession(s.tmuxSession, listOf("/bin/bash", "-l"), "/workspace") }
    }

    @Test
    fun `spawn uses workspaceRoot when no path provided`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.CODEX)
        assertThat(s.cwd).isEqualTo("/workspace")
    }

    @Test
    fun `stop kills tmux session and removes from registry`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.SHELL)
        assertThat(mgr.stop(s.id)).isTrue
        assertThat(mgr.get(s.id)).isNull()
        verify { tmux.killSession(s.tmuxSession) }
    }

    @Test
    fun `stop returns false for unknown id`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        assertThat(mgr.stop("does-not-exist")).isFalse
    }

    @Test
    fun `send delegates to tmux sendKeys with enter flag`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.CLAUDE)
        mgr.send(s.id, "list files", enter = true)
        verify { tmux.sendKeys(s.tmuxSession, "list files", enter = true) }
    }

    @Test
    fun `capture delegates to tmux capture`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.CLAUDE)
        every { tmux.capture(s.tmuxSession, 1_000) } returns "screen content"
        assertThat(mgr.capture(s.id)).isEqualTo("screen content")
    }

    @Test
    fun `captureWithEscapes delegates to tmux captureWithEscapes`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.CLAUDE)
        every { tmux.captureWithEscapes(s.tmuxSession) } returns "ansi screen"
        assertThat(mgr.captureWithEscapes(s.id)).isEqualTo("ansi screen")
    }

    @Test
    fun `resize delegates to tmux resize`(
        @TempDir tmp: Path,
    ) {
        val mgr = manager(tmp)
        val s = mgr.spawn(AgentKind.CLAUDE)
        mgr.resize(s.id, 100, 30)
        verify { tmux.resize(s.tmuxSession, 100, 30) }
    }

    @Test
    fun `trims a session log once it outgrows its disk cap`(
        @TempDir tmp: Path,
    ) {
        val props =
            GatewayProperties(
                workspaceRoot = "/workspace",
                tmux =
                    GatewayProperties.Tmux(
                        socketName = "agent-gw",
                        stateDir = tmp.toString(),
                        logCapBytes = 64,
                        logTrimIntervalSeconds = 1,
                    ),
                cli = GatewayProperties.Cli(claude = "/c", codex = "/x"),
                git = GatewayProperties.Git(deployKeyDir = "/x"),
            )
        every { tmux.ensureStateDir() } returns tmp
        val mgr = AgentSessionManager(tmux, props)
        try {
            val s = mgr.spawn(AgentKind.SHELL)
            Files.write(s.logFile, ByteArray(200) { 'x'.code.toByte() })
            assertThat(Files.size(s.logFile)).isEqualTo(200L)
            await().atMost(Duration.ofSeconds(5)).until { Files.size(s.logFile) == 0L }
        } finally {
            mgr.shutdown()
        }
    }
}

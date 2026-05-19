package com.jorisjonkers.personalstack.agentgateway.tmux

import com.jorisjonkers.personalstack.agentgateway.config.GatewayProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AgentSessionManagerTest {
    private val tmux = mockk<TmuxClient>(relaxed = true)

    private fun manager(tmp: Path): AgentSessionManager {
        val props =
            GatewayProperties(
                workspaceRoot = "/workspace",
                tmux = GatewayProperties.Tmux(socketName = "agent-gw", stateDir = tmp.toString()),
                cli = GatewayProperties.Cli(claude = "/usr/local/bin/claude", codex = "/usr/local/bin/codex"),
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
        verify { tmux.newSession(s.tmuxSession, listOf("/usr/local/bin/claude"), "/workspace/repo") }
        verify { tmux.startPipeToFile(s.tmuxSession, s.logFile) }
        assertThat(mgr.get(s.id)).isEqualTo(s)
        assertThat(mgr.list()).hasSize(1)
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
}

package com.jorisjonkers.personalstack.agentgateway.tmux

import com.jorisjonkers.personalstack.agentgateway.config.GatewayProperties
import com.jorisjonkers.personalstack.agentgateway.process.ProcessRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class TmuxClientTest {
    private val runner = mockk<ProcessRunner>(relaxed = true)
    private val props =
        GatewayProperties(
            workspaceRoot = "/workspace",
            tmux = GatewayProperties.Tmux(socketName = "agent-gw", stateDir = "/tmp/agent-gateway-test"),
            cli = GatewayProperties.Cli(claude = "claude", codex = "codex"),
            git = GatewayProperties.Git(deployKeyDir = "/var/run/secrets/agents/github-deploy-key"),
        )
    private val client = TmuxClient(runner, props)

    @Test
    fun `newSession invokes tmux with socket name and cwd`() {
        val argv = slot<List<String>>()
        every { runner.run(capture(argv), any(), any(), any(), any()) } returns
            ProcessRunner.Result(0, "", "")

        client.newSession("agent-abc", listOf("claude"), "/workspace/repo")

        assertThat(argv.captured).containsSubsequence("tmux", "-L", "agent-gw")
        assertThat(argv.captured).contains("-s", "agent-abc")
        assertThat(argv.captured).contains("-c", "/workspace/repo")
        assertThat(argv.captured).endsWith("claude")
    }

    @Test
    fun `sendKeys with enter sends the text then Enter as a separate invocation`() {
        every { runner.run(any(), any(), any(), any(), any()) } returns ProcessRunner.Result(0, "", "")

        client.sendKeys("agent-abc", "hello world")

        verify {
            runner.run(
                match { it.containsAll(listOf("send-keys", "-t", "agent-abc:0.0", "-l", "hello world")) },
                any(), any(), any(), any(),
            )
            runner.run(
                match { it.containsAll(listOf("send-keys", "-t", "agent-abc:0.0", "Enter")) },
                any(), any(), any(), any(),
            )
        }
    }

    @Test
    fun `listSessions parses tmux list-sessions output`() {
        every { runner.run(any(), any(), any(), any(), any()) } returns
            ProcessRunner.Result(0, "agent-abc\nagent-def\n", "")

        assertThat(client.listSessions()).containsExactly("agent-abc", "agent-def")
    }

    @Test
    fun `listSessions returns empty list when tmux server is not running`() {
        every { runner.run(any(), any(), any(), any(), any()) } returns
            ProcessRunner.Result(1, "", "no server running")

        assertThat(client.listSessions()).isEmpty()
    }

    @Test
    fun `ensureStateDir creates the directory`(@TempDir tmp: Path) {
        val withTmp =
            props.copy(
                tmux = props.tmux.copy(stateDir = tmp.resolve("agent-gw").toString()),
            )
        val tmuxWithTmp = TmuxClient(runner, withTmp)
        val dir = tmuxWithTmp.ensureStateDir()
        assertThat(File(dir.toString())).exists().isDirectory()
    }
}

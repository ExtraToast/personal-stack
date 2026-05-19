package com.jorisjonkers.personalstack.agentgateway.tmux

import com.jorisjonkers.personalstack.agentgateway.config.GatewayProperties
import com.jorisjonkers.personalstack.agentgateway.process.ProcessRunner
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Wraps the tmux CLI. Every method shells out — the gateway never
 * embeds libtmux because tmux itself is the cheapest, most portable
 * implementation of "a pty I can stream from N readers at once".
 *
 * One tmux server per Pod (`-L <socketName>` keeps it off the user's
 * default server). Each agent is one session with one window with one
 * pane: nesting more than that adds no value and complicates
 * pipe-pane targeting.
 */
@Component
class TmuxClient(
    private val runner: ProcessRunner,
    private val props: GatewayProperties,
) {
    private val log = LoggerFactory.getLogger(TmuxClient::class.java)

    fun ensureStateDir(): Path {
        val dir = Path(props.tmux.stateDir)
        Files.createDirectories(dir)
        return dir
    }

    fun newSession(name: String, command: List<String>, cwd: String, env: Map<String, String> = emptyMap()) {
        ensureStateDir()
        val argv =
            mutableListOf(
                "tmux", "-L", props.tmux.socketName,
                "new-session", "-d",
                "-s", name,
                "-x", "200", "-y", "50",
                "-c", cwd,
            ) + command
        runner.run(argv, env = env)
        log.info("tmux session {} created in {}", name, cwd)
    }

    fun killSession(name: String) {
        runner.run(
            listOf("tmux", "-L", props.tmux.socketName, "kill-session", "-t", name),
            checked = false,
        )
    }

    fun sendKeys(session: String, text: String, enter: Boolean = true) {
        val argv =
            mutableListOf(
                "tmux", "-L", props.tmux.socketName,
                "send-keys", "-t", "$session:0.0",
                "-l", text,
            )
        runner.run(argv)
        if (enter) {
            runner.run(
                listOf(
                    "tmux", "-L", props.tmux.socketName,
                    "send-keys", "-t", "$session:0.0", "Enter",
                ),
            )
        }
    }

    fun sendKey(session: String, key: String) {
        runner.run(
            listOf(
                "tmux", "-L", props.tmux.socketName,
                "send-keys", "-t", "$session:0.0", key,
            ),
        )
    }

    fun capture(session: String, historyLines: Int = 1_000): String =
        runner.run(
            listOf(
                "tmux", "-L", props.tmux.socketName,
                "capture-pane", "-p",
                "-S", "-$historyLines",
                "-t", "$session:0.0",
            ),
        ).stdout

    fun listSessions(): List<String> {
        val result =
            runner.run(
                listOf(
                    "tmux", "-L", props.tmux.socketName,
                    "list-sessions", "-F", "#{session_name}",
                ),
                checked = false,
            )
        if (result.exitCode != 0) return emptyList()
        return result.stdout.lines().filter { it.isNotBlank() }
    }

    fun startPipeToFile(session: String, file: Path) {
        runner.run(
            listOf(
                "tmux", "-L", props.tmux.socketName,
                "pipe-pane", "-O", "-t", "$session:0.0",
                "cat >> ${file.toAbsolutePath()}",
            ),
        )
    }

    fun sessionExists(name: String): Boolean = name in listSessions()
}

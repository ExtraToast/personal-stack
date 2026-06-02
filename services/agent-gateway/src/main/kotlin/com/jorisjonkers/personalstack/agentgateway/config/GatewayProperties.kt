package com.jorisjonkers.personalstack.agentgateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "agent-gateway")
data class GatewayProperties(
    val workspaceRoot: String,
    val tmux: Tmux,
    val cli: Cli,
    val git: Git,
) {
    data class Tmux(
        val socketName: String,
        val stateDir: String,
        // Poll cadence for the pipe-pane log tailer. Lower = more
        // responsive streamed output at the cost of more wakeups.
        val tailIntervalMs: Long = 40,
    )

    data class Cli(
        val claude: String,
        val codex: String,
        // The runner container is the sandbox (unprivileged user, only a
        // git deploy key, no host access), so the CLIs launch with every
        // approval/permission/sandbox prompt bypassed. Kept as config so a
        // flag rename upstream is a redeploy-free value flip.
        val claudeArgs: List<String> = emptyList(),
        val codexArgs: List<String> = emptyList(),
    )

    data class Git(
        val deployKeyDir: String,
    )
}

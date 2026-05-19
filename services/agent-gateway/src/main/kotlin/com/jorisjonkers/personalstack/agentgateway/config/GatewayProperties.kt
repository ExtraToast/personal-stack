package com.jorisjonkers.personalstack.agentgateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "agent-gateway")
data class GatewayProperties(
    val workspaceRoot: String,
    val tmux: Tmux,
    val cli: Cli,
    val git: Git,
) {
    data class Tmux(val socketName: String, val stateDir: String)

    data class Cli(val claude: String, val codex: String)

    data class Git(val deployKeyDir: String)
}

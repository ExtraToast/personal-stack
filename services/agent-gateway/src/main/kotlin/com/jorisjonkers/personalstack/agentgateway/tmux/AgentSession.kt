package com.jorisjonkers.personalstack.agentgateway.tmux

import java.nio.file.Path
import java.time.Instant

data class AgentSession(
    val id: String,
    val kind: AgentKind,
    val tmuxSession: String,
    val logFile: Path,
    val cwd: String,
    val createdAt: Instant,
    // Native CLI session id for resume on wake. Set by the gateway for
    // Claude (from the --session-id flag); null for Shell sessions and
    // for Codex until async discovery is implemented.
    val cliSessionId: String? = null,
)

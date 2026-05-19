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
)

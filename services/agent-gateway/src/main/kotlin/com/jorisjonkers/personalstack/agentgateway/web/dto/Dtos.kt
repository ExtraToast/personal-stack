package com.jorisjonkers.personalstack.agentgateway.web.dto

import com.jorisjonkers.personalstack.agentgateway.tmux.AgentKind

data class SpawnAgentRequest(
    val kind: AgentKind,
    val workspacePath: String? = null,
)

data class SendInputRequest(
    val input: String,
    val enter: Boolean = true,
)

data class AgentResponse(
    val id: String,
    val kind: AgentKind,
    val cwd: String,
    val createdAt: String,
)

data class CloneRequest(
    val repoUrl: String,
    val branch: String? = null,
    val intoDir: String? = null,
)

data class PushRequest(
    val repoDir: String,
    val branch: String? = null,
)

data class OpenPrRequest(
    val repoDir: String,
    val title: String,
    val body: String,
    val base: String = "main",
)

data class GitOperationResponse(
    val ok: Boolean,
    val output: String,
)

data class GitVerifyRequest(
    val repoUrl: String,
    val branch: String? = null,
)

data class GitVerifyResponse(
    val read: Boolean,
    val write: Boolean,
    val detail: String,
)

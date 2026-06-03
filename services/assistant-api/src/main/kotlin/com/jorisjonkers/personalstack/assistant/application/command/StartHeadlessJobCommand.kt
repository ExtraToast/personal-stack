package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentKind
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.common.command.Command

data class StartHeadlessJobCommand(
    val sessionId: WorkspaceAgentSessionId,
    val workspaceId: WorkspaceId,
    val kind: WorkspaceAgentKind,
    val prompt: String,
    val timeoutSeconds: Long? = null,
) : Command

package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionId
import com.jorisjonkers.personalstack.common.command.Command

data class SendUserInputCommand(
    val sessionId: WorkspaceAgentSessionId,
    val text: String,
    val enter: Boolean = true,
) : Command<Unit>

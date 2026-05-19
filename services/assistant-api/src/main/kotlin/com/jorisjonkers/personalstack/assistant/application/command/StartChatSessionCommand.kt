package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.ChatSessionId
import com.jorisjonkers.personalstack.common.command.Command
import java.util.UUID

data class StartChatSessionCommand(
    val sessionId: ChatSessionId,
    val userId: UUID,
    val title: String? = null,
) : Command

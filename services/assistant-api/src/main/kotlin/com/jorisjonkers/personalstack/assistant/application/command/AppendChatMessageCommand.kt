package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.ChatMessageId
import com.jorisjonkers.personalstack.assistant.domain.model.ChatMessageRole
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSessionId
import com.jorisjonkers.personalstack.common.command.Command

data class AppendChatMessageCommand(
    val messageId: ChatMessageId,
    val sessionId: ChatSessionId,
    val role: ChatMessageRole,
    val body: String,
) : Command

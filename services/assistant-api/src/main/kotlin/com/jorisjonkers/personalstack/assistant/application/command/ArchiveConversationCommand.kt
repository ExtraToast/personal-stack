package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.ConversationId
import com.jorisjonkers.personalstack.common.command.Command

data class ArchiveConversationCommand(
    val conversationId: ConversationId,
    val userId: String,
) : Command

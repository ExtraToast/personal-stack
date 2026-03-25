package com.jorisjonkers.privatestack.assistant.application.command

import com.jorisjonkers.privatestack.assistant.domain.model.ConversationId
import com.jorisjonkers.privatestack.common.command.Command

data class ArchiveConversationCommand(
    val conversationId: ConversationId,
    val userId: String,
) : Command

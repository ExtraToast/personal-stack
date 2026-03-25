package com.jorisjonkers.privatestack.assistant.application.command

import com.jorisjonkers.privatestack.assistant.domain.model.ConversationId
import com.jorisjonkers.privatestack.common.command.Command
import java.util.UUID

data class StartConversationCommand(
    val conversationId: ConversationId,
    val userId: UUID,
    val title: String,
) : Command

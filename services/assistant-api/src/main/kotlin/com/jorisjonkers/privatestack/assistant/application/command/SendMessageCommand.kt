package com.jorisjonkers.privatestack.assistant.application.command

import com.jorisjonkers.privatestack.assistant.domain.model.ConversationId
import com.jorisjonkers.privatestack.assistant.domain.model.MessageId
import com.jorisjonkers.privatestack.assistant.domain.model.MessageRole
import com.jorisjonkers.privatestack.common.command.Command

data class SendMessageCommand(
    val messageId: MessageId,
    val conversationId: ConversationId,
    val userId: String,
    val content: String,
    val role: MessageRole,
) : Command

package com.jorisjonkers.privatestack.assistant.application.command

import com.jorisjonkers.privatestack.common.command.Command
import java.util.UUID

data class StartConversationCommand(
    val userId: UUID,
    val title: String,
) : Command

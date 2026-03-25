package com.jorisjonkers.privatestack.assistant.application.command

import com.jorisjonkers.privatestack.assistant.domain.port.ConversationRepository
import com.jorisjonkers.privatestack.common.command.CommandHandler
import org.springframework.stereotype.Service

@Service
class StartConversationCommandHandler(
    private val conversationRepository: ConversationRepository,
) : CommandHandler<StartConversationCommand> {

    override fun handle(command: StartConversationCommand) {
        // TODO: implement conversation creation logic
    }
}

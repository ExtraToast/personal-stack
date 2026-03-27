package com.jorisjonkers.privatestack.assistant.application.command

import com.jorisjonkers.privatestack.assistant.domain.model.ConversationStatus
import com.jorisjonkers.privatestack.assistant.domain.port.ConversationRepository
import com.jorisjonkers.privatestack.common.command.CommandHandler
import com.jorisjonkers.privatestack.common.exception.DomainException
import com.jorisjonkers.privatestack.common.exception.NotFoundException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ArchiveConversationCommandHandler(
    private val conversationRepository: ConversationRepository,
) : CommandHandler<ArchiveConversationCommand> {
    @Suppress("ThrowsCount")
    override fun handle(command: ArchiveConversationCommand) {
        val conversation =
            conversationRepository.findById(command.conversationId)
                ?: throw NotFoundException("Conversation", command.conversationId.value.toString())

        val requestingUserId =
            runCatching { UUID.fromString(command.userId) }.getOrNull()
                ?: throw DomainException("Invalid userId format: ${command.userId}", "INVALID_USER_ID")

        if (conversation.userId != requestingUserId) {
            throw DomainException(
                "User ${command.userId} does not own conversation ${command.conversationId.value}",
                "FORBIDDEN",
            )
        }

        val archived =
            conversation.copy(
                status = ConversationStatus.ARCHIVED,
                updatedAt = Instant.now(),
            )
        conversationRepository.save(archived)
    }
}

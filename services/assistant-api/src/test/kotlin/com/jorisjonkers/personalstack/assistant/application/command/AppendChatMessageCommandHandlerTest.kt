package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.ChatMessage
import com.jorisjonkers.personalstack.assistant.domain.model.ChatMessageId
import com.jorisjonkers.personalstack.assistant.domain.model.ChatMessageRole
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSession
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSessionId
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSessionKind
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSessionStatus
import com.jorisjonkers.personalstack.assistant.domain.port.ChatMessageRepository
import com.jorisjonkers.personalstack.assistant.domain.port.ChatSessionRepository
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class AppendChatMessageCommandHandlerTest {
    private val sessions = mockk<ChatSessionRepository>()
    private val messages = mockk<ChatMessageRepository>()
    private val handler = AppendChatMessageCommandHandler(sessions, messages)

    private val session =
        ChatSession(
            id = ChatSessionId.random(),
            userId = UUID.randomUUID(),
            title = "x",
            status = ChatSessionStatus.ACTIVE,
            kind = ChatSessionKind.PLAIN,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `handle persists the message and bumps session updatedAt`() {
        every { sessions.findById(session.id) } returns session
        val saved = slot<ChatMessage>()
        every { messages.save(capture(saved)) } answers { saved.captured }
        every { sessions.save(any()) } answers { firstArg() }

        val mid = ChatMessageId.random()
        handler.handle(AppendChatMessageCommand(mid, session.id, ChatMessageRole.USER, "Hello"))

        assertThat(saved.captured.id).isEqualTo(mid)
        assertThat(saved.captured.role).isEqualTo(ChatMessageRole.USER)
        assertThat(saved.captured.body).isEqualTo("Hello")
        verify { sessions.save(any()) }
    }

    @Test
    fun `handle rejects a blank body`() {
        every { sessions.findById(session.id) } returns session
        assertThrows<IllegalArgumentException> {
            handler.handle(AppendChatMessageCommand(ChatMessageId.random(), session.id, ChatMessageRole.USER, "  "))
        }
    }

    @Test
    fun `handle throws NotFound for an unknown session`() {
        every { sessions.findById(any()) } returns null
        assertThrows<NotFoundException> {
            handler.handle(
                AppendChatMessageCommand(
                    messageId = ChatMessageId.random(),
                    sessionId = ChatSessionId.random(),
                    role = ChatMessageRole.USER,
                    body = "hey",
                ),
            )
        }
    }
}

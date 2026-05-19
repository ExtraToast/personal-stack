package com.jorisjonkers.personalstack.assistant.persistence

import com.jorisjonkers.personalstack.assistant.IntegrationTestBase
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSession
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSessionId
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSessionStatus
import com.jorisjonkers.personalstack.assistant.domain.port.ChatSessionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

class JooqChatSessionRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var sessions: ChatSessionRepository

    private fun newSession(
        userId: UUID = UUID.randomUUID(),
        title: String? = "x",
    ) = ChatSession(
        id = ChatSessionId.random(),
        userId = userId,
        title = title,
        status = ChatSessionStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `save and findById round-trip with null title`() {
        val s = newSession(title = null)
        sessions.save(s)
        val loaded = sessions.findById(s.id)
        assertThat(loaded).isNotNull
        assertThat(loaded!!.title).isNull()
        assertThat(loaded.status).isEqualTo(ChatSessionStatus.ACTIVE)
    }

    @Test
    fun `findAllByUserId returns only the user's sessions`() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        sessions.save(newSession(userId = userA, title = "A1"))
        sessions.save(newSession(userId = userA, title = "A2"))
        sessions.save(newSession(userId = userB, title = "B"))

        val forA = sessions.findAllByUserId(userA)
        assertThat(forA).hasSize(2)
        assertThat(forA.map { it.title }).containsExactlyInAnyOrder("A1", "A2")
    }

    @Test
    fun `save updates the row on conflict`() {
        val s = newSession()
        sessions.save(s)
        sessions.save(s.copy(status = ChatSessionStatus.ARCHIVED, updatedAt = Instant.now()))

        val loaded = sessions.findById(s.id)
        assertThat(loaded!!.status).isEqualTo(ChatSessionStatus.ARCHIVED)
    }

    @Test
    fun `delete removes the row`() {
        val s = newSession()
        sessions.save(s)
        sessions.delete(s.id)
        assertThat(sessions.findById(s.id)).isNull()
    }
}

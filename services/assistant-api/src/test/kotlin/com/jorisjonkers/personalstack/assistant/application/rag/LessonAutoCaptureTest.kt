package com.jorisjonkers.personalstack.assistant.application.rag

import com.jorisjonkers.personalstack.assistant.config.RagProperties
import com.jorisjonkers.personalstack.assistant.domain.model.Turn
import com.jorisjonkers.personalstack.assistant.domain.model.TurnId
import com.jorisjonkers.personalstack.assistant.domain.model.TurnRole
import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentKind
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSession
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionStatus
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.assistant.domain.port.KnowledgeWritePort
import com.jorisjonkers.personalstack.assistant.domain.port.TurnRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceAgentSessionRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class LessonAutoCaptureTest {
    private val workspaces = mockk<WorkspaceRepository>()
    private val sessions = mockk<WorkspaceAgentSessionRepository>()
    private val turns = mockk<TurnRepository>()
    private val extractor = LessonExtractor()
    private val kbWrite = mockk<KnowledgeWritePort>(relaxed = true)
    private val rag =
        RagProperties(
            enabled = true,
            knowledgeMcpUrl = "http://kb",
            knowledgeMcpToken = "",
            lightragUrl = "http://lr",
        )

    private val capture = LessonAutoCapture(workspaces, sessions, turns, extractor, kbWrite, rag)

    @Test
    fun `capture ingests one note per extracted candidate`() {
        val ws = workspace(repoUrl = "git@github.com:owner/personal-stack.git")
        val s = session(ws.id)
        every { sessions.findById(s.id) } returns s
        every { workspaces.findById(ws.id) } returns ws
        every { turns.findBySessionId(s.id, any()) } returns
            listOf(
                turn(TurnRole.USER, "how does flannel work over tailscale?", 1, s.id),
                turn(TurnRole.AGENT, "It uses --flannel-iface=tailscale0. ".repeat(20), 2, s.id),
            )

        capture.capture(s.id)

        verify {
            kbWrite.ingestNote(
                match { it.contains("how does flannel") },
                match { it.startsWith("Q: how does flannel") },
                "project:personal-stack",
                match { it.contains("source:agents-ui") },
            )
        }
    }

    @Test
    fun `capture is a no-op when RAG is disabled`() {
        val disabledRag = rag.copy(enabled = false)
        val withDisabled = LessonAutoCapture(workspaces, sessions, turns, extractor, kbWrite, disabledRag)
        withDisabled.capture(WorkspaceAgentSessionId.random())
        verify(exactly = 0) { kbWrite.ingestNote(any(), any(), any(), any()) }
    }

    @Test
    fun `capture bucket caps writes per session`() {
        val ws = workspace()
        val s = session(ws.id)
        every { sessions.findById(s.id) } returns s
        every { workspaces.findById(ws.id) } returns ws
        // Five capture-worthy pairs in a row, bucket capacity is 3.
        val pairs =
            (1..5).flatMap { i ->
                listOf(
                    turn(TurnRole.USER, "how about $i?", i * 2L, s.id),
                    turn(TurnRole.AGENT, "long answer $i. ".repeat(30), i * 2L + 1, s.id),
                )
            }
        every { turns.findBySessionId(s.id, any()) } returns pairs

        capture.capture(s.id)

        verify(exactly = 3) { kbWrite.ingestNote(any(), any(), any(), any()) }
    }

    private fun workspace(repoUrl: String? = null) =
        Workspace(
            id = WorkspaceId.random(),
            name = "demo",
            repoUrl = repoUrl,
            branch = null,
            podName = null,
            pvcName = null,
            gatewayEndpoint = null,
            status = WorkspaceStatus.READY,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun session(workspaceId: WorkspaceId) =
        WorkspaceAgentSession(
            id = WorkspaceAgentSessionId.random(),
            workspaceId = workspaceId,
            kind = WorkspaceAgentKind.CLAUDE,
            gatewayAgentId = "abc12345",
            status = WorkspaceAgentSessionStatus.RUNNING,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun turn(role: TurnRole, body: String, sec: Long, sid: WorkspaceAgentSessionId) =
        Turn(
            id = TurnId.random(),
            sessionId = sid,
            role = role,
            body = body,
            createdAt = Instant.parse("2026-05-19T10:00:00Z").plusSeconds(sec),
        )
}

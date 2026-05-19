package com.jorisjonkers.personalstack.agentgateway.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.jorisjonkers.personalstack.agentgateway.tmux.AgentKind
import com.jorisjonkers.personalstack.agentgateway.tmux.AgentSession
import com.jorisjonkers.personalstack.agentgateway.tmux.AgentSessionManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Path
import java.time.Instant

class AgentControllerTest {
    private val sessions = mockk<AgentSessionManager>()
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(AgentController(sessions))
            .setControllerAdvice(ErrorAdvice())
            .build()
    private val mapper = ObjectMapper()

    private val sample =
        AgentSession(
            id = "abc12345",
            kind = AgentKind.CLAUDE,
            tmuxSession = "agent-abc12345",
            logFile = Path.of("/tmp/agent.log"),
            cwd = "/workspace/repo",
            createdAt = Instant.parse("2026-05-19T10:00:00Z"),
        )

    @Test
    fun `POST agents spawns and returns 201 with session`() {
        every { sessions.spawn(AgentKind.CLAUDE, "/workspace/repo") } returns sample
        mockMvc
            .perform(
                post("/agents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"kind":"CLAUDE","workspacePath":"/workspace/repo"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("abc12345"))
            .andExpect(jsonPath("$.kind").value("CLAUDE"))
    }

    @Test
    fun `GET agents lists sessions`() {
        every { sessions.list() } returns listOf(sample)
        mockMvc
            .perform(get("/agents"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("abc12345"))
    }

    @Test
    fun `GET agents id returns 404 when unknown`() {
        every { sessions.get("nope") } returns null
        mockMvc.perform(get("/agents/nope")).andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE agents id returns 204 on success`() {
        every { sessions.stop("abc12345") } returns true
        mockMvc.perform(delete("/agents/abc12345")).andExpect(status().isNoContent)
    }

    @Test
    fun `POST agents id send returns 202`() {
        every { sessions.send("abc12345", "hi", true) } returns Unit
        mockMvc
            .perform(
                post("/agents/abc12345/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"input":"hi","enter":true}"""),
            ).andExpect(status().isAccepted)
    }
}

package com.jorisjonkers.personalstack.assistant.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.jorisjonkers.personalstack.assistant.application.query.RepositoryQueryService
import com.jorisjonkers.personalstack.assistant.domain.model.Repository
import com.jorisjonkers.personalstack.assistant.domain.model.RepositoryId
import com.jorisjonkers.personalstack.common.command.CommandBus
import com.jorisjonkers.personalstack.common.web.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class RepositoryControllerTest {
    private val commandBus = mockk<CommandBus>(relaxed = true)
    private val query = mockk<RepositoryQueryService>()
    private val objectMapper = ObjectMapper()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = RepositoryController(commandBus, query)
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    private fun repo(id: RepositoryId = RepositoryId.random()) =
        Repository(
            id = id,
            name = "personal-stack",
            repoUrl = "git@github.com:o/r.git",
            defaultBranch = "main",
            vaultKeyPath = "x",
            deployKeyFingerprint = null,
            deployKeyAddedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `POST creates repository and returns 201`() {
        every { query.get(any()) } returns RepositoryQueryService.RepositoryDetail(repo(), emptyList())
        mockMvc
            .perform(
                post("/api/v1/repositories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "name" to "personal-stack",
                                "repoUrl" to "git@github.com:o/r.git",
                                "defaultBranch" to "main",
                            ),
                        ),
                    ),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("personal-stack"))
        verify { commandBus.dispatch(any()) }
    }

    @Test
    fun `POST with blank name returns 422`() {
        mockMvc
            .perform(
                post("/api/v1/repositories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf("name" to "", "repoUrl" to "x", "defaultBranch" to "main"),
                        ),
                    ),
            ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `GET list returns array`() {
        every { query.list() } returns listOf(repo(), repo())
        mockMvc
            .perform(get("/api/v1/repositories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `GET by id returns detail envelope`() {
        val r = repo()
        every { query.get(r.id) } returns RepositoryQueryService.RepositoryDetail(r, emptyList())
        mockMvc
            .perform(get("/api/v1/repositories/${r.id.value}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.repository.id").value(r.id.value.toString()))
            .andExpect(jsonPath("$.attachedProjects").isArray)
    }

    @Test
    fun `GET by id with unknown id returns 404`() {
        every { query.get(any()) } returns null
        mockMvc
            .perform(get("/api/v1/repositories/${UUID.randomUUID()}"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST attach key returns 202`() {
        mockMvc
            .perform(
                post("/api/v1/repositories/${UUID.randomUUID()}/key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "privateKeyOpenssh" to
                                    "-----BEGIN OPENSSH PRIVATE KEY-----\nx\n-----END OPENSSH PRIVATE KEY-----",
                                "publicKeyOpenssh" to "ssh-ed25519 AAAAxxx me",
                                "knownHosts" to null,
                            ),
                        ),
                    ),
            ).andExpect(status().isAccepted)
        verify { commandBus.dispatch(any()) }
    }

    @Test
    fun `DELETE returns 204`() {
        mockMvc
            .perform(delete("/api/v1/repositories/${UUID.randomUUID()}"))
            .andExpect(status().isNoContent)
        verify { commandBus.dispatch(any()) }
    }
}

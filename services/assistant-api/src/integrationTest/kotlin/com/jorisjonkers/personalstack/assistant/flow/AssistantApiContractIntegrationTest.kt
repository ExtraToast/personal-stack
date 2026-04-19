package com.jorisjonkers.personalstack.assistant.flow

import com.fasterxml.jackson.databind.ObjectMapper
import com.jorisjonkers.personalstack.assistant.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class AssistantApiContractIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }

    @Test
    fun `OpenAPI spec endpoint returns valid JSON`() {
        mockMvc
            .perform(get("/api/v1/api-docs"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info").exists())
            .andExpect(jsonPath("$.paths").exists())
    }

    @Test
    fun `conversation creation response matches expected schema`() {
        val userId = UUID.randomUUID().toString()

        mockMvc
            .perform(
                post("/api/v1/conversations")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("title" to "Contract Test Chat"))),
            ).andExpect(status().isCreated)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.userId").exists())
            .andExpect(jsonPath("$.title").value("Contract Test Chat"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists())
    }

    @Test
    fun `message response matches expected schema`() {
        val userId = UUID.randomUUID().toString()

        val convResult =
            mockMvc
                .perform(
                    post("/api/v1/conversations")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("title" to "Message Schema Test"))),
                ).andExpect(status().isCreated)
                .andReturn()

        val conversationId = objectMapper.readTree(convResult.response.contentAsString)["id"].asText()

        mockMvc
            .perform(
                post("/api/v1/conversations/$conversationId/messages")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("content" to "Schema check"))),
            ).andExpect(status().isCreated)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.conversationId").exists())
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.content").value("Schema check"))
            .andExpect(jsonPath("$.createdAt").exists())
    }

    @Test
    fun `health endpoint response matches schema`() {
        mockMvc
            .perform(get("/api/v1/health"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.service").value("assistant-api"))
    }

    @Test
    fun `conversation list response is valid JSON array`() {
        val userId = UUID.randomUUID().toString()

        // Create a conversation so the list is non-empty
        mockMvc.perform(
            post("/api/v1/conversations")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to "List Test Chat"))),
        )

        mockMvc
            .perform(get("/api/v1/conversations").header("X-User-Id", userId))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].title").exists())
            .andExpect(jsonPath("$[0].status").exists())
    }
}

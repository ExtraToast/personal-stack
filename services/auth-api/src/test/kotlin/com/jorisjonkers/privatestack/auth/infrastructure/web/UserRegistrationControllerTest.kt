package com.jorisjonkers.privatestack.auth.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.privatestack.auth.application.query.GetUserQueryService
import com.jorisjonkers.privatestack.auth.domain.model.Role
import com.jorisjonkers.privatestack.auth.domain.model.User
import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.auth.infrastructure.web.dto.RegisterUserRequest
import com.jorisjonkers.privatestack.common.command.CommandBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class UserRegistrationControllerTest {

    private val commandBus = mockk<CommandBus>(relaxed = true)
    private val getUserQueryService = mockk<GetUserQueryService>()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var mockMvc: MockMvc

    private val savedUser = User(
        id = UserId(UUID.randomUUID()),
        username = "alice",
        email = "alice@example.com",
        role = Role.USER,
        totpEnabled = false,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(UserRegistrationController(commandBus, getUserQueryService))
            .build()
    }

    @Test
    fun `POST register returns 201 with user response`() {
        every { getUserQueryService.findByUsername("alice") } returns savedUser

        val request = RegisterUserRequest(
            username = "alice",
            email = "alice@example.com",
            password = "securepass123",
        )

        mockMvc.post("/api/v1/users/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.username") { value("alice") }
            jsonPath("$.email") { value("alice@example.com") }
            jsonPath("$.role") { value("USER") }
        }

        verify { commandBus.dispatch(any()) }
    }

    @Test
    fun `POST register dispatches RegisterUserCommand`() {
        every { getUserQueryService.findByUsername("bob") } returns savedUser.copy(username = "bob")

        val request = RegisterUserRequest(
            username = "bob",
            email = "bob@example.com",
            password = "securepass123",
        )

        mockMvc.post("/api/v1/users/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
        }

        verify { commandBus.dispatch(any()) }
    }
}

package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.SessionLoginRequest
import com.jorisjonkers.personalstack.common.web.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class SessionLoginControllerTest {
    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val totpService = mockk<TotpService>()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var mockMvc: MockMvc

    private val userId = UserId(UUID.randomUUID())

    private val credentials =
        UserCredentials(
            userId = userId,
            username = "alice",
            passwordHash = "hashed-password",
            totpSecret = null,
            totpEnabled = false,
            emailConfirmed = true,
            role = Role.USER,
        )

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    SessionLoginController(userRepository, passwordEncoder, totpService),
                ).setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `session login with valid credentials returns success`() {
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true

        val request = SessionLoginRequest(username = "alice", password = "securepass123")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.totpRequired") { value(false) }
            }
    }

    @Test
    fun `session login with invalid password returns 400`() {
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("wrongpassword", "hashed-password") } returns false

        val request = SessionLoginRequest(username = "alice", password = "wrongpassword")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `session login with non-existent user returns 400`() {
        every { userRepository.findCredentialsByUsername("unknown") } returns null

        val request = SessionLoginRequest(username = "unknown", password = "securepass123")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `session login with TOTP enabled but no code returns totpRequired`() {
        val totpCredentials = credentials.copy(totpEnabled = true, totpSecret = "totp-secret")

        every { userRepository.findCredentialsByUsername("alice") } returns totpCredentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true

        val request = SessionLoginRequest(username = "alice", password = "securepass123")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.totpRequired") { value(true) }
                jsonPath("$.success") { value(false) }
            }
    }

    @Test
    fun `session login with valid TOTP code returns success`() {
        val totpCredentials = credentials.copy(totpEnabled = true, totpSecret = "totp-secret")

        every { userRepository.findCredentialsByUsername("alice") } returns totpCredentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true
        every { totpService.verifyCode("totp-secret", "123456") } returns true

        val request = SessionLoginRequest(username = "alice", password = "securepass123", totpCode = "123456")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.totpRequired") { value(false) }
            }
    }

    @Test
    fun `session login with invalid TOTP code returns 400`() {
        val totpCredentials = credentials.copy(totpEnabled = true, totpSecret = "totp-secret")

        every { userRepository.findCredentialsByUsername("alice") } returns totpCredentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true
        every { totpService.verifyCode("totp-secret", "000000") } returns false

        val request = SessionLoginRequest(username = "alice", password = "securepass123", totpCode = "000000")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `session login with unconfirmed email returns 400`() {
        val unconfirmedCredentials = credentials.copy(emailConfirmed = false)

        every { userRepository.findCredentialsByUsername("alice") } returns unconfirmedCredentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true

        val request = SessionLoginRequest(username = "alice", password = "securepass123")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `session login response does not contain tokens`() {
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true

        val request = SessionLoginRequest(username = "alice", password = "securepass123")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.accessToken") { doesNotExist() }
                jsonPath("$.refreshToken") { doesNotExist() }
                jsonPath("$.totpChallengeToken") { doesNotExist() }
            }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `session login with blank username returns 422`() {
        val request = SessionLoginRequest(username = "", password = "securepass123")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isUnprocessableEntity() }
            }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `session login with blank password returns 422`() {
        val request = SessionLoginRequest(username = "alice", password = "")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isUnprocessableEntity() }
            }
    }

    @Test
    fun `session login with TOTP disabled ignores totpCode field`() {
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true

        val request = SessionLoginRequest(username = "alice", password = "securepass123", totpCode = "123456")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
            }
    }

    @Test
    fun `session login with TOTP enabled but no secret throws error`() {
        val totpCredentials = credentials.copy(totpEnabled = true, totpSecret = null)

        every { userRepository.findCredentialsByUsername("alice") } returns totpCredentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true

        val request = SessionLoginRequest(username = "alice", password = "securepass123", totpCode = "123456")

        mockMvc
            .post("/api/v1/auth/session-login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }
}

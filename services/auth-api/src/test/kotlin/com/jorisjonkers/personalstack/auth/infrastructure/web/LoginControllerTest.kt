package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.auth.infrastructure.security.TokenService
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.LoginRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.TotpChallengeRequest
import com.jorisjonkers.personalstack.common.web.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class LoginControllerTest {
    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val tokenService = mockk<TokenService>()
    private val totpService = mockk<TotpService>()
    private val jwtDecoder = mockk<JwtDecoder>()
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
            role = Role.USER,
        )

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    LoginController(userRepository, passwordEncoder, tokenService, totpService, jwtDecoder),
                ).setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `POST login returns tokens when TOTP is not enabled`() {
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true
        every { tokenService.createAccessToken("alice", userId.value.toString(), listOf("ROLE_USER")) } returns "access-token"
        every { tokenService.createRefreshToken(userId.value.toString()) } returns "refresh-token"

        val request = LoginRequest(username = "alice", password = "securepass123")

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.totpRequired") { value(false) }
                jsonPath("$.accessToken") { value("access-token") }
                jsonPath("$.refreshToken") { value("refresh-token") }
            }
    }

    @Test
    fun `POST login returns TOTP challenge when TOTP is enabled`() {
        val totpCredentials = credentials.copy(totpEnabled = true, totpSecret = "totp-secret")

        every { userRepository.findCredentialsByUsername("alice") } returns totpCredentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true
        every {
            tokenService.createTotpChallengeToken(
                userId = userId.value.toString(),
                username = "alice",
            )
        } returns "challenge-token"

        val request = LoginRequest(username = "alice", password = "securepass123")

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.totpRequired") { value(true) }
                jsonPath("$.totpChallengeToken") { value("challenge-token") }
                jsonPath("$.accessToken") { doesNotExist() }
            }
    }

    @Test
    fun `POST login returns 400 for invalid credentials`() {
        every { userRepository.findCredentialsByUsername("unknown") } returns null

        val request = LoginRequest(username = "unknown", password = "securepass123")

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST login returns 400 for wrong password`() {
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("wrongpassword", "hashed-password") } returns false

        val request = LoginRequest(username = "alice", password = "wrongpassword")

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST totp-challenge returns tokens for valid code`() {
        val totpCredentials = credentials.copy(totpEnabled = true, totpSecret = "totp-secret")

        val mockJwt =
            Jwt
                .withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject(userId.value.toString())
                .claim("type", "totp_challenge")
                .claim("username", "alice")
                .build()

        every { jwtDecoder.decode("challenge-token") } returns mockJwt
        every { userRepository.findCredentialsByUsername("alice") } returns totpCredentials
        every { totpService.verifyCode("totp-secret", "123456") } returns true
        every { tokenService.createAccessToken("alice", userId.value.toString(), listOf("ROLE_USER")) } returns "access-token"
        every { tokenService.createRefreshToken(userId.value.toString()) } returns "refresh-token"

        val request = TotpChallengeRequest(totpChallengeToken = "challenge-token", code = "123456")

        mockMvc
            .post("/api/v1/auth/totp-challenge") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.totpRequired") { value(false) }
                jsonPath("$.accessToken") { value("access-token") }
                jsonPath("$.refreshToken") { value("refresh-token") }
            }
    }

    @Test
    fun `POST totp-challenge returns 400 for invalid TOTP code`() {
        val totpCredentials = credentials.copy(totpEnabled = true, totpSecret = "totp-secret")

        val mockJwt =
            Jwt
                .withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject(userId.value.toString())
                .claim("type", "totp_challenge")
                .claim("username", "alice")
                .build()

        every { jwtDecoder.decode("challenge-token") } returns mockJwt
        every { userRepository.findCredentialsByUsername("alice") } returns totpCredentials
        every { totpService.verifyCode("totp-secret", "000000") } returns false

        val request = TotpChallengeRequest(totpChallengeToken = "challenge-token", code = "000000")

        mockMvc
            .post("/api/v1/auth/totp-challenge") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST totp-challenge returns 400 for invalid challenge token`() {
        every { jwtDecoder.decode("invalid-token") } throws JwtException("Invalid token")

        val request = TotpChallengeRequest(totpChallengeToken = "invalid-token", code = "123456")

        mockMvc
            .post("/api/v1/auth/totp-challenge") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }
}

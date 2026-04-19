package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.auth.infrastructure.security.TokenService
import com.jorisjonkers.personalstack.common.web.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class RateLimitingTest {
    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val tokenService = mockk<TokenService>()
    private val totpService = mockk<TotpService>()
    private val jwtDecoder = mockk<JwtDecoder>()
    private val objectMapper = jacksonObjectMapper()
    private lateinit var loginMockMvc: MockMvc
    private lateinit var sessionLoginMockMvc: MockMvc

    private val userId = UserId(UUID.randomUUID())

    private val credentials =
        UserCredentials(
            userId = userId,
            username = "alice",
            firstName = "",
            lastName = "",
            passwordHash = "hashed-password",
            totpSecret = null,
            totpEnabled = false,
            emailConfirmed = true,
            role = Role.USER,
        )

    @BeforeEach
    fun setUp() {
        loginMockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    LoginController(userRepository, passwordEncoder, tokenService, totpService, jwtDecoder),
                ).setControllerAdvice(GlobalExceptionHandler())
                .build()

        sessionLoginMockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    SessionLoginController(userRepository, passwordEncoder, totpService, Duration.ofDays(30)),
                ).setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    private fun runConcurrent(
        mvc: MockMvc,
        path: String,
        body: String,
        count: Int = 10,
    ): Pair<Int, Int> {
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val latch = CountDownLatch(count)
        val executor = Executors.newFixedThreadPool(count)

        repeat(count) {
            executor.submit {
                try {
                    val result =
                        mvc
                            .post(path) {
                                contentType = MediaType.APPLICATION_JSON
                                content = body
                            }.andReturn()
                    if (result.response.status == 200) {
                        successCount.incrementAndGet()
                    } else {
                        errorCount.incrementAndGet()
                    }
                } catch (_: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()
        return successCount.get() to errorCount.get()
    }

    @Test
    fun `login endpoint handles concurrent requests`() {
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true
        every { tokenService.createAccessToken(any(), any(), any()) } returns "access-token"
        every { tokenService.createRefreshToken(any()) } returns "refresh-token"

        val body = objectMapper.writeValueAsString(mapOf("username" to "alice", "password" to "securepass123"))
        val (successes, _) = runConcurrent(loginMockMvc, "/api/v1/auth/login", body)

        assertThat(successes).isEqualTo(10)
    }

    @Test
    fun `session-login endpoint handles concurrent requests`() {
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("securepass123", "hashed-password") } returns true

        val body = objectMapper.writeValueAsString(mapOf("username" to "alice", "password" to "securepass123"))
        val (successes, _) = runConcurrent(sessionLoginMockMvc, "/api/v1/auth/session-login", body)

        assertThat(successes).isGreaterThan(0)
    }

    @Test
    fun `token refresh handles concurrent requests`() {
        val mockJwt =
            Jwt
                .withTokenValue("refresh-token")
                .header("alg", "RS256")
                .subject(userId.value.toString())
                .claim("type", "refresh")
                .build()

        every { jwtDecoder.decode("refresh-token") } returns mockJwt
        every { userRepository.findById(userId) } returns
            com.jorisjonkers.personalstack.auth.domain.model.User(
                id = userId,
                username = "alice",
                email = "alice@example.com",
                firstName = "",
                lastName = "",
                role = Role.USER,
                emailConfirmed = true,
                totpEnabled = false,
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
            )
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { tokenService.createAccessToken(any(), any(), any()) } returns "new-access"
        every { tokenService.createRefreshToken(any()) } returns "new-refresh"

        val body = objectMapper.writeValueAsString(mapOf("refreshToken" to "refresh-token"))
        val (successes, _) = runConcurrent(loginMockMvc, "/api/v1/auth/refresh", body)

        assertThat(successes).isEqualTo(10)
    }
}
